const test = require('node:test');
const assert = require('node:assert/strict');
const fs = require('node:fs');
const path = require('node:path');
const vm = require('node:vm');

const app = fs.readFileSync(path.join(__dirname, '../app.js'), 'utf8');
for (const [editor, next] of [
  ['manualAssessmentEditor', 'syncManualAssessments'],
  ['assessmentEditor', 'syncReviewDraft']
]) {
  test(`${editor} renders a zero estimate without excluding it from valid hours`, () => {
    const source = app.slice(app.indexOf(`function ${editor}(`), app.indexOf(`function ${next}(`));
    const context = {esc: value => value == null ? '' : String(value)};
    vm.createContext(context);
    vm.runInContext(source, context);
    const markup = context[editor]({title: 'No remaining work', estimatedHours: 0}, 0);
    const hours = markup.match(/<input\b[^>]*class="hours"[^>]*>/)?.[0];
    assert.ok(hours, 'The rendered editor must expose an hours field');
    const attributes = Object.fromEntries([...hours.matchAll(/([\w-]+)="([^"]*)"/g)].map(([, key, value]) => [key, value]));
    assert.equal(attributes.type, 'number');
    assert.equal(attributes.value, '0', 'Explicit zero must survive rendering');
    assert.equal(attributes.min, '0', 'Native form validation must accept zero and reject negative hours');
    assert.equal(attributes.step, '0.1', 'Fractional-hour entry remains supported');
  });
}
