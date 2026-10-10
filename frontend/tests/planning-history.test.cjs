const test = require('node:test');
const assert = require('node:assert/strict');
const fs = require('node:fs');
const path = require('node:path');
const vm = require('node:vm');
const app = fs.readFileSync(path.join(__dirname, '../app.js'), 'utf8');
const generation = app.slice(app.indexOf('async function generatePlan('), app.indexOf("$('#plan-form').addEventListener"));
const selection = app.slice(app.indexOf('async function selectPlan('), app.indexOf("$('#plan-history').addEventListener"));

test('regeneration uses the selected historical plan and retains exact slots', async () => {
  const requests = [];
  const slots = {'2026-10-12': [{start: '09:00', end: '10:00'}]};
  const context = {API: {planning: '/api'}, state: {plan: {id: 'previous'}, planHistory: [{id: 'previous'}], availability: slots, availabilityMinutes: {'2026-10-12': 60}},
    $: () => ({value: '2026-10-12'}), setBusy() {}, feedback() {}, render() {},
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
    $: () => ({value: '2026-10-12'}), setBusy() {}, feedback: (...args) => messages.push(args), render() {},
    request: async () => {throw new Error('Provider quota reached');}};
  vm.createContext(context); vm.runInContext(generation, context);
  await context.generatePlan({'2026-10-12': 60}, {}, undefined, 'old');
  assert.equal(context.state.plan, original);
  assert.equal(context.state.planHistory.length, 1);
  assert.ok(messages.some(message => message[1] === 'Provider quota reached'));
});

test('history selection retrieves the saved plan rather than regenerating it', async () => {
  const calls = [];
  const context = {API: {planning: '/api'}, state: {}, render() {},
    request: async url => {calls.push(url); return {id: 'saved', version: 1};}};
  vm.createContext(context); vm.runInContext(selection, context);
  await context.selectPlan('saved');
  assert.deepEqual(calls, ['/api/planning/plans/saved']);
  assert.equal(context.state.plan.id, 'saved');
});
