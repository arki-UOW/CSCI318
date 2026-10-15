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
    API: {planning: '/api'}, Date,
    $: selector => {
      if (!elements.has(selector)) elements.set(selector, {value: '2026-10-12', classList: {toggle() {}}});
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
});
