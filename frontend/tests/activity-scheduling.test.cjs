const {test} = require('node:test');
const assert = require('node:assert/strict');
const fs = require('node:fs');
const path = require('node:path');
const {JSDOM} = require('jsdom');
const workflow = require('../student-workflow.js');

test('new calendar defaults stay in the account day, avoid elapsed time and cross midnight', () => {
  assert.deepEqual(workflow.nextStudyWindow('2026-10-19T15:30:00'),
    {start:'2026-10-19T18:00', end:'2026-10-19T19:00', earliest:'2026-10-19T15:31'});
  assert.equal(workflow.nextStudyWindow('2026-10-19T22:30:00', '2026-10-12').start, '2026-10-19T22:31');
  assert.equal(workflow.nextStudyWindow('2026-12-31T23:59:50').end, '2027-01-01T01:00');
  assert.equal(workflow.minutesBetween('2026-11-01T01:00', '2026-11-01T03:00'), 120);
  assert.equal(workflow.minutesBetween('2027-03-14T01:00', '2027-03-14T03:00'), 120);
});

test('completed activity stays historical; scheduling creates a future calendar item without recording completed work', async () => {
  const root = path.resolve(__dirname, '..');
  const dom = new JSDOM(fs.readFileSync(path.join(root, 'index.html'), 'utf8'), {url:'http://localhost:3000',runScripts:'outside-only'});
  const w = dom.window, $ = selector => w.document.querySelector(selector);
  try {
    w.Headers = Headers; w.scrollTo = () => {};
    w.HTMLElement.prototype.scrollIntoView = () => {};
    w.HTMLDialogElement.prototype.showModal = function(){ this.open=true; };
    w.HTMLDialogElement.prototype.close = function(){ this.open=false; };
    const NativeDate = w.Date;
    w.Date = class extends NativeDate { constructor(...args){super(...(args.length ? args : ['2026-10-18T13:30:00Z']));} };
    const posted = [], entries = [];
    const reply = (value, status=200) => ({ok:true,status,headers:new Headers({'Content-Type':'application/json'}),json:async()=>value});
    w.fetch = async (url, options={}) => {
      if (options.method === 'POST') {
        assert.ok(url.endsWith('/calendar'), 'Scheduling must never POST completed activity');
        const body = JSON.parse(options.body); posted.push(body);
        const entry = {...body,id:'scheduled',status:'PLANNED'}; entries.push(entry); return reply(entry,201);
      }
      if (url.endsWith('/calendar/completed')) return reply([]);
      if (url.includes('/calendar?')) return reply(entries);
      throw new Error('Unexpected request: '+url);
    };
    w.eval(['student-workflow.js','browser-timezone.js','student-controls.js','vendor/katex/katex.min.js','vendor/katex/auto-render.min.js','dashboard-stream.js','app.js']
      .map(f=>fs.readFileSync(path.join(root,f),'utf8')).join('\n') + '\nwindow.audit={state,populateAccount,render,show,openCalendarDialog,formatTime};');
    const a=w.audit;
    Object.assign(a.state,{token:'fixture',account:{username:'student',timezone:'Australia/Sydney',timezoneAutomatic:false,
      primaryColor:'#245d45',accentColor:'#d69b38',backgroundColor:'#f5f7f5',surfaceColor:'#ffffff',textColor:'#17201d'},
      subjects:[{id:'s',code:'CSCI318',name:'Software Engineering'}],
      assessments:[{id:'a',subjectId:'s',title:'Assignment',dueDate:'2026-12-20',status:'INCOMPLETE'}]});
    a.populateAccount(); a.render(); a.show('activity');
    assert.equal($('#activity-date').max,'2026-10-19');
    $('#activity-date').value='2026-10-20'; assert.equal($('#activity-date').validity.rangeOverflow,true);
    $('#activity-date').value='2026-10-18'; assert.equal($('#activity-date').checkValidity(),true);
    $('#activity-subject').value='s'; $('#activity-subject').dispatchEvent(new w.Event('change'));
    $('#activity-assessment').value='a'; $('#activity-description').value='Practice questions';
    $('#schedule-study-session').click();
    assert.equal($('#calendar-dialog').open,true);
    assert.equal($('#calendar-start').value,'2026-10-19T18:00');
    assert.equal($('#calendar-start').min,'2026-10-19T00:31');
    assert.equal($('#calendar-end').value,'2026-10-19T18:45');
    assert.equal($('#calendar-assessment').value,'a');
    $('#calendar-start').value='2026-10-18T18:00'; $('#calendar-start').dispatchEvent(new w.Event('change'));
    assert.equal($('#calendar-start').validity.rangeUnderflow,true);
    $('#calendar-form').dispatchEvent(new w.Event('submit',{cancelable:true}));
    assert.equal(posted.length,0);
    $('#calendar-start').value='2026-11-20T16:00'; $('#calendar-start').dispatchEvent(new w.Event('change'));
    assert.equal($('#calendar-end').value,'2026-11-20T16:45');
    $('#calendar-form').dispatchEvent(new w.Event('submit',{cancelable:true}));
    for(let i=0;i<8;i++) await new Promise(resolve=>setImmediate(resolve));
    assert.equal(posted.length,1); assert.equal(posted[0].assessmentId,'a');
    assert.equal(posted[0].startAt,'2026-11-20T16:00');
    assert.equal(a.state.currentView,'calendar');
    assert.ok($('#month-grid [data-calendar-id="scheduled"]'));
    assert.equal(a.state.sessions.length,0); assert.equal(a.state.completedCalendar.length,0);
    $('#month-today').click(); $('#week-today').click();
    for(let i=0;i<8;i++) await new Promise(resolve=>setImmediate(resolve));
    assert.equal(a.state.calendarWeek.getDate(),19);
    assert.ok($('#month-grid .today[data-calendar-date="2026-10-19"]'));
    assert.ok($('#week-grid .today [data-new-calendar-date="2026-10-19"]'));
    // A browser DST gap must not rewrite another account timezone's civil calendar values.
    a.openCalendarDialog({id:'dst',title:'Study',type:'STUDY_SESSION',status:'PLANNED',startAt:'2027-03-14T02:30:00',endAt:'2027-03-14T03:30:00'});
    assert.equal($('#calendar-start').value,'2027-03-14T02:30');
    assert.match(a.formatTime('2027-03-14T02:30:00'),/2:30/);
    for(const date of ['2026-11-01','2027-03-14']) {
      $('#plan-start').value=date; $('#plan-start').dispatchEvent(new w.Event('change'));
      $('#slot-date').value=date; $('#slot-start').value='01:00'; $('#slot-end').value='03:00';
      $('#availability-slot-form').dispatchEvent(new w.Event('submit',{cancelable:true}));
      assert.equal(a.state.availabilityMinutes[date],120, 'API civil duration must not follow browser DST');
    }
  } finally { w.close(); }
});
