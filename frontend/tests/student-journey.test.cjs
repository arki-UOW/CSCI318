const {test} = require('node:test');
const assert = require('node:assert/strict');
const fs = require('node:fs');
const path = require('node:path');
const {JSDOM} = require('jsdom');

test('UC-03/04/05/07/11/12/14: student workflow through the complete document and bound controls', async () => {
  const root = path.resolve(__dirname, '..');
  const dom = new JSDOM(fs.readFileSync(path.join(root,'index.html'),'utf8'), {url:'http://localhost:3000',runScripts:'outside-only'});
  const w=dom.window;
  try {
    w.Headers=Headers;w.scrollTo=()=>{};w.HTMLElement.prototype.scrollIntoView=function(){};
    w.HTMLDialogElement.prototype.showModal=function(){this.open=true;};
    w.HTMLDialogElement.prototype.close=function(){this.open=false;};
    let respond=()=>{throw new Error('Unexpected request');};
    w.fetch=async (url, options)=>respond(url, options);
    const NativeDate=w.Date;
    w.Date=class extends NativeDate {constructor(...args){super(...(args.length?args:['2026-10-15T17:30:00Z']));}static now(){return new NativeDate('2026-10-15T17:30:00Z').getTime();}};
    w.eval(['student-workflow.js','browser-timezone.js','dashboard-stream.js','app.js'].map(f=>fs.readFileSync(path.join(root,f),'utf8')).join('\n')+
      '\nwindow.audit={state,render,renderCalendar,renderWeek,renderAvailability,generatePlan,sendAvailability,signOut,populateAccount,loadCalendar};');
    const a=w.audit, s=a.state, $=selector=>w.document.querySelector(selector);
    const reply=(value,status=200)=>({ok:status<400,status,headers:new Headers({'Content-Type':'application/json'}),json:async()=>value});
    const tick=()=>new Promise(resolve=>setImmediate(resolve));
    Object.assign(s,{token:'fixture',account:{username:'student',displayName:'Student',timezone:'Australia/Sydney',timezoneAutomatic:false,
      primaryColor:'#245d45',accentColor:'#d69b38',backgroundColor:'#f5f7f5',surfaceColor:'#ffffff',textColor:'#17201d'},
      subjects:[{id:'s',code:'CSCI318',name:'Software Engineering',weeklyStudyTargetMinutes:240}],
      assessments:[{id:'a',subjectId:'s',title:'Report',dueDate:'2026-10-20',status:'INCOMPLETE',estimatedMinutes:60},
        {id:'late',subjectId:'s',title:'Overdue exam',dueDate:'2026-10-02',status:'INCOMPLETE'}],
      sessions:[{id:'r',subjectId:'s',description:'Draft report',studyDate:'2026-10-16',durationMinutes:45}],
      week:{weekStart:'2026-10-12',weekEnd:'2026-10-18',overdue:[{id:'late',subjectId:'s',title:'Overdue exam',dueDate:'2026-10-02',status:'INCOMPLETE'}],studyProgress:[{subjectId:'s',studiedMinutes:45,targetMinutes:240,state:'BEHIND_TARGET'}]}});
    a.populateAccount();a.render();a.renderCalendar();a.renderWeek();
    assert.equal($('#plan-start').value,'2026-10-16');
    assert.equal($('#plan-start').min,'2026-10-16');
    assert.match($('#dashboard-assessments').textContent,/Overdue exam/);
    assert.match($('#dashboard-sessions').textContent,/Draft report/);
    assert.match($('#activity-history').textContent,/Draft report/);
    assert.match($('#month-grid [data-calendar-id="deadline:a"]').textContent,/Deadline.*Report/);
    $('#month-grid [data-calendar-id="deadline:a"]').click();
    assert.equal($('#assessment-dialog').open,true);assert.equal($('#assessment-id').value,'a');
    s.assessments[0].dueDate='2026-10-22';a.renderCalendar();
    assert.ok($('#month-grid [data-calendar-date="2026-10-22"] [data-calendar-id="deadline:a"]'));
    assert.equal($('#month-grid [data-calendar-date="2026-10-20"] [data-calendar-id="deadline:a"]'),null);
    $('#activity-history [data-session-id="r"]').click();
    assert.equal($('#session-dialog').open,true);assert.equal($('#session-minutes').value,'45');
    $('#activity-subject').value='s';$('#activity-subject').dispatchEvent(new w.Event('change'));
    assert.ok([...$('#activity-assessment').options].some(o=>o.value==='a'));
    assert.match($('#outline-file').accept,/\.txt/);

    const old={id:'old',version:1,startDate:'2026-10-12',endDate:'2026-10-18',items:[],explanation:'getIncompleteAssessments SECRET DIAGNOSTIC',
      availability:{dailyAvailabilityMinutes:{'2026-10-12':120,'2026-10-14':120},availabilitySlots:{'2026-10-12':[{start:'18:00',end:'20:00'}],'2026-10-14':[{start:'18:00',end:'20:00'}]}}};
    const current={...old,id:'current',version:2};s.plan=current;s.planHistory=[current,old];a.render();
    assert.ok($('#profile #plan-history'));assert.equal($('#plan #plan-history'),null);
    respond=url=>{assert.match(url,/plans\/old$/);return reply(old);};
    $('[data-history-id="old"]').click();await tick();
    assert.equal(s.plan.id,'current');assert.equal(s.historyPlan.id,'old');
    assert.doesNotMatch($('#plan-list').textContent,/SECRET DIAGNOSTIC|getIncompleteAssessments/);
    $('#reuse-availability').click();
    assert.match($('#availability-summary').textContent,/Monday 19 Oct/);
    assert.match($('#availability-summary').textContent,/Wednesday 21 Oct/);
    assert.equal($('#generate-chat-plan').disabled,false);
    $('#clear-availability').click();assert.equal($('#generate-chat-plan').disabled,true);
    $('#slot-date').value='2026-10-19';$('#slot-start').value='18:00';$('#slot-end').value='20:00';
    $('#availability-slot-form').dispatchEvent(new w.Event('submit', {cancelable:true}));
    assert.equal(s.availabilityMinutes['2026-10-19'],120);assert.equal($('#generate-chat-plan').disabled,false);
    $('#slot-start').value='19:00';$('#availability-slot-form').dispatchEvent(new w.Event('submit',{cancelable:true}));
    assert.match($('#chat-feedback').textContent,/overlaps/);assert.equal(s.availabilityMinutes['2026-10-19'],120);
    respond=()=>reply({message:'Upstream HTTP 503. The provider is temporarily unavailable.'},503);
    await a.sendAvailability('Monday evenings',$('#availability-chat-form button'));
    assert.match($('#chat-feedback').textContent,/temporarily busy/);assert.doesNotMatch($('#chat-feedback').textContent,/HTTP|503/);
    assert.equal($('#generate-chat-plan').disabled,true);
    await a.generatePlan({'2026-10-19':60},$('#generate-chat-plan'),null);
    assert.equal(s.plan.id,'current');assert.equal(s.planHistory.length,2);

    respond=(url,options)=>{
      if(url.endsWith('/planning/plans') && options.method==='POST') return reply({...current,id:'new',version:3,startDate:'2026-10-16',endDate:'2026-10-22',items:[{date:'2026-10-19',subjectId:'s',assessmentId:'a',title:'Report',allocatedMinutes:60}]},201);
      if(url.endsWith('/calendar/completed')) return reply([]);
      if(url.includes('/calendar?')) return reply([{id:'block',subjectId:'s',assessmentId:'a',planId:'new',title:'Report',type:'STUDY_SESSION',status:'PLANNED',startAt:'2026-10-19T18:00:00',endAt:'2026-10-19T19:00:00'}]);
      throw new Error(url);
    };
    await a.generatePlan({'2026-10-19':60},$('#generate-chat-plan'),null);
    assert.equal(s.plan.id,'new');assert.ok($('#month-grid [data-calendar-id="block"]'));
    assert.match($('#dashboard-calendar').textContent,/Report/);
    const savedRespond=respond;
    respond=url=>{assert.match(url,/calendar/);return reply([]);};
    await a.loadCalendar();
    assert.match($('#plan-list').textContent,/No upcoming study blocks/);
    assert.doesNotMatch($('#plan-list').textContent,/Report/);
    // Saved snapshots remain intact; current plan never resurrects deleted calendar blocks.
    assert.equal(s.plan.items.length,1);
    respond=savedRespond;await a.loadCalendar();
    if (process.env.UI_PREVIEW_DIR) {
      const output=path.resolve(process.env.UI_PREVIEW_DIR);fs.mkdirSync(output,{recursive:true});
      for (const view of ['dashboard','plan','calendar','profile','activity']) {
        const clone=w.document.documentElement.cloneNode(true);
        clone.querySelector('body').classList.remove('auth-required');clone.querySelector('#auth-shell').hidden=true;
        clone.querySelectorAll('.view').forEach(node=>node.classList.toggle('active',node.id===view));
        clone.querySelectorAll('script').forEach(node=>node.remove());
        clone.querySelectorAll('dialog').forEach(node=>node.removeAttribute('open'));
        clone.querySelector('#page-title').textContent=`Study Leftovers · ${view} (test fixture)`;
        fs.writeFileSync(path.join(output,view+'.html'),'<!DOCTYPE html>\n'+clone.outerHTML);
      }
      fs.copyFileSync(path.join(root,'styles.css'),path.join(output,'styles.css'));
      fs.cpSync(path.join(root,'assets'),path.join(output,'assets'),{recursive:true});
    }
    await a.signOut(false);
    assert.equal(s.historyPlan,null);assert.equal(s.completedCalendar.length,0);
    assert.doesNotMatch($('#dashboard-sessions').textContent,/Draft report/);
    assert.equal($('#history-preview').textContent,'');
  } finally { w.close(); }
});
