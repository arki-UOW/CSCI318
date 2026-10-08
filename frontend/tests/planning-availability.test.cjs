const test = require('node:test');
const assert = require('node:assert/strict');
const fs = require('node:fs');
const path = require('node:path');
const vm = require('node:vm');

// Execute the production request handler with DOM/network boundaries replaced.
const app = fs.readFileSync(path.join(__dirname, '../app.js'), 'utf8');
const handler = app.slice(app.indexOf('async function generatePlan('), app.indexOf("$('#plan-form').addEventListener"));

test('planning sends exact slots alongside capacity, while minute-only clients stay compatible', async () => {
  const requests = [];
  const context = {
    API: { planning: 'http://localhost:8084/api' }, state: {},
    $: () => ({ value: '2026-10-12' }), setBusy() {}, feedback() {}, render() {},
    request: async (url, options) => {
      requests.push({ url, body: JSON.parse(options.body) });
      return { endDate: '2026-10-19' };
    }
  };
  vm.createContext(context);
  vm.runInContext(handler, context);
  const minutes = { '2026-10-12': 60 };
  const slots = { '2026-10-12': [{ start: '09:00', end: '10:00' }] };
  await context.generatePlan(minutes, {}, slots);
  assert.deepEqual(requests[0].body.availabilitySlots, slots);
  assert.deepEqual(requests[0].body.dailyAvailabilityMinutes, minutes);
  await context.generatePlan(minutes, {});
  assert.equal(Object.hasOwn(requests[1].body, 'availabilitySlots'), false);
});
