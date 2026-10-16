const test = require('node:test');
const assert = require('node:assert/strict');
const fs = require('node:fs');
const path = require('node:path');
const vm = require('node:vm');
const app = fs.readFileSync(path.join(__dirname, '../app.js'), 'utf8');
const generation = app.slice(app.indexOf('async function generatePlan('), app.indexOf("$('#plan-form').addEventListener"));
const selection = app.slice(app.indexOf('async function selectPlan('), app.indexOf("$('#plan-history').addEventListener"));
const historyRendering = app.slice(app.indexOf('function renderPlanHistory()'), app.indexOf('async function selectPlan('));

test('regeneration updates the current plan using explicitly confirmed slots', async () => {
  const requests = [];
  const slots = {'2026-10-12': [{start: '09:00', end: '10:00'}]};
  const context = {API: {planning: '/api'}, state: {plan: {id: 'previous'}, planHistory: [{id: 'previous'}], availabilityReady: true, availability: slots, availabilityMinutes: {'2026-10-12': 60}},
    $: () => ({value: '2026-10-12'}), setBusy() {}, feedback() {}, render() {}, loadCalendar: async () => {},
    request: async (url, options) => { requests.push({url, body: JSON.parse(options.body)}); return {id: 'new', version: 2, endDate: '2026-10-18'}; }};
  vm.createContext(context); vm.runInContext(generation + selection, context);
  await context.regenerateSelectedPlan({});
  assert.equal(requests[0].url, '/api/planning/plans/previous/regenerate');
  assert.deepEqual(requests[0].body.availabilitySlots, slots);
  assert.equal(context.state.plan.id, 'new');
  assert.equal(context.state.planHistory.length, 2);
});

test('failed regeneration preserves the selected plan and history', async () => {
  const messages = [];
  const original = {id: 'old'};
  const context = {API: {planning: '/api'}, state: {plan: original, planHistory: [original]},
    $: () => ({value: '2026-10-12'}), setBusy() {}, feedback: (...args) => messages.push(args), render() {}, loadCalendar: async () => {},
    request: async () => {throw new Error('Provider quota reached');}};
  vm.createContext(context); vm.runInContext(generation, context);
  await context.generatePlan({'2026-10-12': 60}, {}, undefined, 'old');
  assert.equal(context.state.plan, original);
  assert.equal(context.state.planHistory.length, 1);
  assert.ok(messages.some(message => message[1] === 'Provider quota reached'));
});

test('history selection retrieves the saved plan rather than regenerating it', async () => {
  const calls = [];
  const context = {API: {planning: '/api'}, state: {plan: {id: 'current'}}, render() {}, loadCalendar: async () => {},
    request: async url => {calls.push(url); return {id: 'saved', version: 1};}};
  vm.createContext(context); vm.runInContext(selection, context);
  await context.selectPlan('saved');
  assert.deepEqual(calls, ['/api/planning/plans/saved']);
  assert.equal(context.state.historyPlan.id, 'saved');
  assert.equal(context.state.plan.id, 'current');
});

test('empty and failed history have distinct explanations instead of a empty history panel', () => {
  const elements = {};
  const context = {state: {planHistory: []}, esc: value => value,
    $: id => elements[id] ||= {addEventListener(){}}};
  vm.createContext(context); vm.runInContext(historyRendering, context);
  context.renderPlanHistory();
  assert.match(elements['#plan-history'].innerHTML, /No saved plans yet/);
  assert.match(elements['#plan-history-help'].textContent, /Failed attempts do not create saved plans/);
  context.state.serviceFailures = ['planHistory'];
  context.renderPlanHistory();
  assert.match(elements['#plan-history'].innerHTML, /could not be loaded/);
  assert.match(elements['#plan-history-help'].textContent, /Refresh history/);
});

test('rapid version selection cannot display an older response last', async () => {
  const pending = {};
  const context = {API: {planning: '/api'}, state: {plan: {id: 'current'}}, render() {}, loadCalendar: async () => {},
    request: url => new Promise(resolve => {pending[url] = resolve;})};
  vm.createContext(context); vm.runInContext(selection, context);
  const first = context.selectPlan('first');
  const second = context.selectPlan('second');
  pending['/api/planning/plans/second']({id: 'second'}); await second;
  pending['/api/planning/plans/first']({id: 'first'}); await first;
  assert.equal(context.state.historyPlan.id, 'second');
  assert.equal(context.state.plan.id, 'current');
});

test('regeneration cannot silently reuse availability awaiting clarification', async () => {
  let generated = false;
  const messages = [];
  const context = {state: {plan: {id: 'saved'}, availabilityReady: false, availabilityMinutes: {'2026-10-12': 60}},
    feedback: (...args) => messages.push(args), generatePlan: () => {generated = true;}};
  vm.createContext(context); vm.runInContext(selection, context);
  await context.regenerateSelectedPlan({});
  assert.equal(generated, false);
  assert.match(messages[0][1], /Confirm your study times/);
});

test('refresh history preserves a version saved while the history request was in flight', async () => {
  let finish;
  const context = {API: {planning: '/api'}, state: {planHistory: []}, render() {}, loadCalendar: async () => {}, setBusy() {},
    request: () => new Promise(resolve => {finish = resolve;})};
  vm.createContext(context); vm.runInContext(selection, context);
  const pending = context.refreshPlanHistory({});
  context.state.plan = {id: 'new', version: 2};
  context.state.planHistory = [context.state.plan];
  finish([{id: 'old', version: 1}]); await pending;
  assert.deepEqual(Array.from(context.state.planHistory, plan => plan.id), ['new', 'old']);
  assert.equal(context.state.plan.id, 'new');
});

test('concurrent generation is prevented and completion respects cleared availability', async () => {
  let finish;
  let calls = 0;
  const elements = {};
  const context = {API: {planning: '/api'}, state: {availabilityReady: true, availabilityMinutes: {'2026-10-12': 60}},
    $: id => elements[id] ||= {value: '2026-10-12'}, render() {}, loadCalendar: async () => {}, feedback() {}, setBusy() {},
    request: () => {calls++; return new Promise(resolve => {finish = resolve;});}};
  vm.createContext(context); vm.runInContext(generation, context);
  const pending = context.generatePlan({'2026-10-12': 60}, {});
  await context.generatePlan({'2026-10-12': 60}, {});
  assert.equal(calls, 1);
  context.state.availabilityReady = false;
  context.state.availabilityMinutes = {};
  finish({id: 'new', version: 1}); await pending;
  assert.equal(elements['#generate-chat-plan'].disabled, true);
  assert.equal(context.state.planPending, false);
});
