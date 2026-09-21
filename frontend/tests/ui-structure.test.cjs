const { test } = require('node:test');
const assert = require('node:assert/strict');
const fs = require('node:fs');
const path = require('node:path');

const frontend = path.join(__dirname, '..');
const index = fs.readFileSync(path.join(frontend, 'index.html'), 'utf8');
const about = fs.readFileSync(path.join(frontend, 'about.html'), 'utf8');

function between(source, start, end) {
  return source.slice(source.indexOf(start), source.indexOf(end));
}

test('subject creation is at the top of Subjects and absent from Dashboard', () => {
  const dashboard = between(index, '<section id="dashboard"', '<section id="subjects"');
  const subjects = between(index, '<section id="subjects"', '<section id="assessments"');
  assert.doesNotMatch(dashboard, /id="upload"/);
  assert.match(subjects, /id="upload"/);
  assert.ok(subjects.indexOf('id="upload"') < subjects.indexOf('SUBJECT OVERVIEW'));
});

test('profile and contributor content reflects the current requirements', () => {
  assert.match(index, /Profile picture<\/strong><small>PNG, JPEG or WebP · up to 10 MB/);
  for (const name of ['Arkajit Goswami', 'Zarif Abrar Islam', 'Mathew Clephas']) {
    assert.match(about, new RegExp(name));
  }
  assert.match(about, /Role: Planning Service \+ Streaming \+ Integration/);
});

test('timezone helper loads before the application module', () => {
  assert.ok(index.indexOf('browser-timezone.js') < index.indexOf('type="module" src="app.js"'));
});
