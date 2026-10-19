const {test} = require('node:test');
const assert = require('node:assert/strict');
const vm = require('node:vm');
const collection = require('../../postman/Study-Leftovers.postman_collection.json');
const source = collection.event.find(e=>e.listen==='prerequest').script.exec.join('\n');

test('Postman plans use tomorrow in the configured account timezone, independently of the runner timezone', () => {
  const scenarios = [
    ['2026-10-18T13:30:00Z','Australia/Sydney','2026-10-19'],
    ['2026-10-18T13:30:00Z','America/New_York','2026-10-18'],
    ['2026-12-31T12:30:00Z','Pacific/Kiritimati','2027-01-01'],
    ['2026-12-31T12:30:00Z','Pacific/Honolulu','2026-12-31'],
    ['2026-10-03T20:00:00Z','Australia/Sydney','2026-10-04']
  ];
  for (const [instant, zone, today] of scenarios) {
    const NativeDate=Date;
    class FrozenDate extends NativeDate {constructor(...args){super(...(args.length?args:[instant]));}}
    const vars=new Map([['timezone',zone]]);
    vm.runInNewContext(source,{Date:FrozenDate,pm:{info:{requestName:'AI-01'},collectionVariables:{get:k=>vars.get(k),set:(k,v)=>vars.set(k,v)}}});
    assert.equal(vars.get('today'),today);
    const start=new Date(today+'T12:00:00Z');start.setUTCDate(start.getUTCDate()+1);
    const body=JSON.parse(vars.get('planBody'));
    assert.equal(body.startDate,start.toISOString().slice(0,10));assert.equal(vars.get('planStart'),body.startDate);
    assert.equal(Object.keys(body.dailyAvailabilityMinutes).length,7);
    for(let i=0;i<7;i++) {const d=new Date(start);d.setUTCDate(d.getUTCDate()+i);
      assert.equal(body.dailyAvailabilityMinutes[d.toISOString().slice(0,10)], d.getUTCDay()%6===0?120:90);}
    const monday=new Date(today+'T12:00:00Z');monday.setUTCDate(monday.getUTCDate()-(monday.getUTCDay()+6)%7);
    assert.equal(vars.get('weekStart'),monday.toISOString().slice(0,10));
  }
});

test('Postman excessive-capacity assertion does not accept a generic HTTP 400', () => {
  const source=collection.item.find(i=>i.name.includes('reject excessive requested availability')).event[0].script.exec.join('\n');
  const run=error=>vm.runInNewContext(source,{pm:{test:(_name,fn)=>fn(),response:{to:{have:{status:code=>assert.equal(code,400)}},json:()=>error},
    expect:value=>({to:{eql:expected=>assert.equal(value,expected)}})}});
  run({validationErrors:{'dailyAvailabilityMinutes[2026-10-20]':'must be less than or equal to 1440'}});
  assert.throws(()=>run({message:'Choose today or a future date'}));
  assert.throws(()=>run({message:'Required header is missing'}));
});
