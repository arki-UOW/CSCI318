const test = require('node:test');
const assert = require('node:assert/strict');
const fs = require('node:fs');
const path = require('node:path');
const vm = require('node:vm');
const app = fs.readFileSync(path.join(__dirname, '../app.js'), 'utf8');
const source = app.slice(app.indexOf('function renderAvailability()'), app.indexOf("$('#availability-chat-form').addEventListener"));

function harness(response) {
  const elements = new Map();
  const context = {
    state: {availability: {'2026-10-12': [{start: '18:00', end: '20:00'}]}, availabilityMinutes: {'2026-10-12': 120}, availabilityReady: true},
    API: {planning: '/api'}, Date, StudentWorkflow: require('../student-workflow.js'),
    $: selector => {
      if (!elements.has(selector)) elements.set(selector, {value: '2026-10-12', classList: {toggle() {}}, addEventListener() {}});
      return elements.get(selector);
    },
    esc: value => value, appendChatMessage() {}, setBusy() {}, feedback() {},
    request: async () => response()
  };
  vm.createContext(context);
  vm.runInContext(source, context);
  return {context, elements};
}

test('named dates render correctly and clarification keeps generation disabled despite existing slots', async () => {
  const {context, elements} = harness(() => ({
    availability: {'2026-10-12': [{start: '18:00', end: '20:00'}]},
    dailyAvailabilityMinutes: {'2026-10-12': 120}, reply: 'What time on Wednesday?', readyToPlan: false
  }));
  context.renderAvailability();
  assert.match(elements.get('#availability-summary').innerHTML, /Monday,? 12 Oct/);
  assert.equal(elements.get('#generate-chat-plan').disabled, false);
  await context.sendAvailability('Wednesday too', {});
  assert.equal(elements.get('#generate-chat-plan').disabled, true);
});

test('successful interpretation enables planning; failed update cannot reuse readiness', async () => {
  let fail = false;
  const {context, elements} = harness(() => {
    if (fail) throw new Error('Provider unavailable');
    return {availability: {'2026-10-14': [{start: '18:00', end: '20:00'}]},
      dailyAvailabilityMinutes: {'2026-10-14': 120}, readyToPlan: true, reply: 'Wednesday 14 Oct'};
  });
  await context.sendAvailability('Wednesday 6-8pm', {});
  assert.equal(elements.get('#generate-chat-plan').disabled, false);
  assert.match(elements.get('#availability-summary').innerHTML, /Wednesday,? 14 Oct/);
  fail = true;
  await context.sendAvailability('Change Wednesday', {});
  assert.equal(elements.get('#generate-chat-plan').disabled, true);
  assert.equal(context.state.availabilityMinutes['2026-10-14'], 120);
  assert.equal(elements.get('#availability-message').value, 'Change Wednesday');
});

test('Start over clears locally without a provider request', async () => {
  let calls = 0;
  const {context, elements} = harness(() => {calls++; throw new Error('503');});
  await context.sendAvailability('Clear my availability and let me start again.', {});
  assert.equal(calls, 0);
  assert.equal(Object.keys(context.state.availability).length, 0);
  assert.equal(elements.get('#generate-chat-plan').disabled, true);
});

for (const change of ['clear', 'week', 'account', 'new request']) {
  test('late availability cannot overwrite ' + change, async () => {
    let resolve;
    const {context, elements} = harness(() => new Promise(done => {resolve = done;}));
    const pending = context.sendAvailability('Monday evenings', {});
    const finish = resolve;
    if (change === 'clear') context.clearAvailability();
    if (change === 'week') {elements.get('#plan-start').value = '2026-10-19'; context.clearAvailability();}
    if (change === 'account') {context.state.token = 'new-account'; context.state.availability = {};}
    if (change === 'new request') {
      const newer = context.sendAvailability('Wednesday evenings', {});
      resolve({availability: {'2026-10-14': [{start: '18:00', end: '20:00'}]}, dailyAvailabilityMinutes: {'2026-10-14': 120}, readyToPlan: true});
      await newer;
    }
    finish({availability: {'2026-10-12': [{start: '18:00', end: '20:00'}]}, dailyAvailabilityMinutes: {'2026-10-12': 120}, readyToPlan: true});
    await pending;
    assert.equal(context.state.availability['2026-10-12'], undefined);
    if (change === 'new request') assert.equal(context.state.availabilityMinutes['2026-10-14'], 120);
  });
}
