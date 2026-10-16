const {test} = require('node:test');
const assert = require('node:assert/strict');
const workflow = require('../student-workflow.js');

test('account timezone defines today at the Sydney midnight boundary', () => {
  const instant = new Date('2026-10-15T14:00:00Z');
  assert.equal(workflow.now('Australia/Sydney', instant), '2026-10-16T01:00:00');
  assert.equal(workflow.now('UTC', instant), '2026-10-15T14:00:00');
});

test('reusing Monday/Wednesday availability on Friday moves to upcoming weekdays', () => {
  const result = workflow.reuse({dailyAvailabilityMinutes: {'2026-10-12':120,'2026-10-14':120},
    availabilitySlots: {'2026-10-12':[{start:'18:00',end:'20:00'}],'2026-10-14':[{start:'18:00',end:'20:00'}]}}, '2026-10-16', '2026-10-16T04:30:00');
  assert.deepEqual(result.minutes, {'2026-10-19':120, '2026-10-21':120});
  assert.equal(result.slots['2026-10-19'][0].start,'18:00');
});

test('reusing availability excludes elapsed slots and caps flexible capacity today', () => {
  const saved = {dailyAvailabilityMinutes:{'2026-10-09':180},availabilitySlots:{'2026-10-09':[{start:'15:00',end:'18:00'}]}};
  assert.equal(workflow.reuse(saved,'2026-10-16','2026-10-16T17:30:00').minutes['2026-10-16'],0);
  assert.equal(workflow.reuse({dailyAvailabilityMinutes:{'2026-10-09':180}},'2026-10-16','2026-10-16T23:30:00').minutes['2026-10-16'],29);
});

test('calendar deadlines and activity retain source identities and follow edits/removals', () => {
  const assessment = {id:'a',subjectId:'s',title:'Report',dueDate:'2026-10-02',status:'INCOMPLETE'};
  const session = {id:'r',subjectId:'s',description:'Draft report',studyDate:'2026-10-16',durationMinutes:45};
  let items=workflow.calendar([], [assessment], [session]);
  assert.equal(items[0].sourceId,'a');assert.equal(items[0].startAt,'2026-10-02T00:00:00');
  assert.equal(items[1].dateOnly,true);assert.equal(items[1].durationMinutes,45);
  items=workflow.calendar([], [{...assessment,dueDate:'2026-10-30'}], []);
  assert.equal(items.length,1);assert.equal(items[0].id,'deadline:a');assert.equal(items[0].startAt,'2026-10-30T00:00:00');
});
