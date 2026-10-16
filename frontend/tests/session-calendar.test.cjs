const test = require('node:test');
const assert = require('node:assert/strict');
const fs = require('node:fs');
const path = require('node:path');
const vm = require('node:vm');
const app = fs.readFileSync(path.join(__dirname, '../app.js'), 'utf8');
const errorCode = app.slice(app.indexOf('function studentError('), app.indexOf('function setAuthMode('));
const requestCode = app.slice(app.indexOf('async function request('), app.indexOf('function setAuthMode('));
const calendarCode = app.slice(app.indexOf('async function loadCalendar('), app.indexOf('function calendarEvent('));
const deferred = () => { let resolve; const promise = new Promise(r => { resolve = r; }); return {promise, resolve}; };
function networkContext(fetch) {
  const context = { state: {token: 'A', sessionEpoch: 1}, Headers, accountTimezone: () => 'UTC', fetch,
    signOut: () => { context.signedOut = context.state.token; } };
  vm.createContext(context); vm.runInContext(errorCode + requestCode, context); return context;
}
test('old response bodies cannot overwrite a new account, even after headers arrived', async () => {
  const body = deferred();
  const c = networkContext(async () => ({ok: true, status: 200, headers: new Headers({'content-type': 'application/json'}), json: () => body.promise}));
  const pending = c.request('/calendar');
  await new Promise(resolve => setImmediate(resolve));
  c.state.token = 'B'; c.state.sessionEpoch++;
  body.resolve([{title: 'private A'}]);
  await assert.rejects(pending, error => error.staleSession === true);
});
test('old 401 does not sign out the current account', async () => {
  const response = deferred(); const c = networkContext(() => response.promise);
  const pending = c.request('/subjects'); c.state.token = 'B'; c.state.sessionEpoch++;
  response.resolve({ok: false, status: 401, json: async () => ({message: 'expired A'})});
  await assert.rejects(pending, error => error.staleSession === true);
  assert.equal(c.signedOut, undefined); assert.equal(c.state.token, 'B');
});
test('current 401 still signs out the expired account', async () => {
  const c = networkContext(async () => ({ok:false, status:401, json:async()=>({message:'expired'})}));
  await assert.rejects(c.request('/subjects'), /expired/); assert.equal(c.signedOut,'A');
});
function calendarContext() {
  const pending = []; const c = {state:{token:'A', calendarEntries:[], calendarWeek: new Date(2026,9,12)},
    API:{planning:'/api'}, monthRange:()=>({from:new Date(2026,9,1),to:new Date(2026,10,1)}),
    mondayFor:d=>d,addDays:(d,n)=>new Date(d.getTime()+n*86400000),localDate:d=>d.toISOString().slice(0,10),
    request:url=>{if(url.endsWith('/completed')) return Promise.resolve([]);const p=deferred();pending.push(p);return p.promise;},renderCalendar(){},renderWeek(){},render(){},feedback(){}};
  c.calendarItems=()=>c.state.calendarEntries;vm.createContext(c);vm.runInContext(calendarCode,c);return {c,pending};
}
test('later calendar navigation wins when responses arrive out of order', async () => {
  const {c,pending}=calendarContext();const first=c.loadCalendar();const second=c.loadCalendar();
  pending[1].resolve([{id:'new range'}]); await second;
  pending[0].resolve([{id:'old range'}]); await first;
  assert.equal(c.state.calendarEntries[0].id,'new range');
});
test('calendar response is discarded after an account switch', async () => {
  const {c,pending}=calendarContext();const call=c.loadCalendar();c.state.token='B';pending[0].resolve([{id:'A private'}]);await call;
  assert.equal(c.state.calendarEntries.length,0);
});
test('overnight entries span month boundaries and midnight ends are exclusive', () => {
  const {c}=calendarContext();c.state.calendarEntries=[{id:'overnight',startAt:'2026-10-31T23:00:00',endAt:'2026-11-01T01:00:00'}, {id:'midnight',startAt:'2026-10-31T23:00:00',endAt:'2026-11-01T00:00:00'}];
  assert.equal(c.entriesForDate('2026-10-31').length,2);
  assert.equal(c.entriesForDate('2026-11-01').length,1);
  assert.equal(c.entriesForDate('2026-11-01')[0].id,'overnight');
});

test('sign-out clears private DOM and memory before the logout network response', async () => {
  const logout = deferred(); const nodes = new Map();
  const node = id => {
    if (!nodes.has(id)) nodes.set(id, {textContent:'private A', value:'private A', hidden:false, classList:{toggle(){}},
      replaceChildren(){this.textContent='';}, reset(){this.value='';}, close(){this.closed=true;}});
    return nodes.get(id);
  };
  const c = {state:{token:'A',sessionEpoch:1,calendarRequest:1,assistantHistory:['private A'],calendarEntries:[{title:'private A'}]},
    dashboardStream:{stop(){}}, request:()=>logout.promise, API:{accounts:'/accounts'},
    localStorage:{removeItem(){}}, setAssistantOpen(){}, setAuthMode(){},
    document:{body:{classList:{add(){}}},querySelectorAll:selector=>[node(selector)],getElementById:node},
    $:node, StudentWorkflow: require('../student-workflow.js'), accountToday:()=> '2026-10-11', localDate:()=> '2026-10-11',mondayFor:d=>d, setBusy(button, busy){button.disabled=busy;},
    render(){},renderCalendar(){},renderWeek(){},renderUploadQueue(){},renderProfilePictures(){}};
  vm.createContext(c);
  vm.runInContext(app.slice(app.indexOf('function renderAvailability()'), app.indexOf('function clearAvailability(')), c);
  vm.runInContext(app.slice(app.indexOf('async function signOut('),app.indexOf("$('#signup-tab').addEventListener")),c);
  const pending = c.signOut();
  assert.equal(c.state.token,null); assert.equal(c.state.sessionEpoch,2);
  assert.equal(c.state.assistantHistory.length,0); assert.equal(c.state.calendarEntries.length,0);
  for (const id of ['study-chat-messages','chat-messages','review','manual-assessments','.feedback','#app-announcer']) {
    assert.equal(node(id).textContent,'',id);
  }
  assert.equal(node('form').value,''); assert.equal(node('dialog[open]').closed,true);
  assert.equal(node('#availability-summary').innerHTML, 'No times added yet.');
  assert.equal(node('#generate-chat-plan').disabled, true);
  assert.equal(node('#availability-chat-form button').disabled, false);
  logout.resolve(); await pending;
});

test('calendar completion cannot disclose its review date after switching during refresh', async () => {
  const refresh=deferred(); let handler; const messages=[];
  const c={state:{sessionEpoch:1,currentView:'calendar'},API:{planning:'/api'},
    $:selector=>selector==='#complete-calendar-entry'?{addEventListener:(name,fn)=>handler=fn}:{value:'A-entry',close(){}},
    setBusy(){}, request:async()=>({nextReview:{startAt:'private A review'}}),loadCalendar:()=>refresh.promise,
    formatDateTime:x=>x,feedback:(...args)=>messages.push(args)};
  vm.createContext(c);vm.runInContext(app.slice(app.indexOf("$('#complete-calendar-entry').addEventListener"),app.indexOf('function setAssistantOpen(')),c);
  const pending=handler({currentTarget:{}}); await new Promise(resolve=>setImmediate(resolve));
  c.state.sessionEpoch=2;refresh.resolve();await pending;assert.equal(messages.length,0);
});
