/* Calendar dates are account-local civil dates. Never shift them through UTC. */
const StudentWorkflow = (() => {
  function now(timeZone, instant = new Date()) {
    const parts = new Intl.DateTimeFormat('en-CA', {timeZone, year: 'numeric', month: '2-digit',
      day: '2-digit', hour: '2-digit', minute: '2-digit', second: '2-digit', hourCycle: 'h23'})
      .formatToParts(instant);
    const p = Object.fromEntries(parts.map(part => [part.type, part.value]));
    return `${p.year}-${p.month}-${p.day}T${p.hour}:${p.minute}:${p.second}`;
  }
  function add(date, days) {
    const value = new Date(`${date}T12:00:00Z`);
    value.setUTCDate(value.getUTCDate() + days);
    return value.toISOString().slice(0, 10);
  }
  function calendar(entries = [], assessments = [], sessions = []) {
    return [...entries,
      ...assessments.filter(item => item.dueDate).map(item => ({id: `deadline:${item.id}`,
        source: 'assessment', sourceId: item.id, subjectId: item.subjectId, title: item.title,
        type: 'DEADLINE', status: item.status === 'COMPLETED' ? 'COMPLETED' : 'PLANNED',
        dateOnly: true, startAt: `${item.dueDate}T00:00:00`, endAt: `${add(item.dueDate, 1)}T00:00:00`})),
      ...sessions.map(item => ({id: `activity:${item.id}`, source: 'activity', sourceId: item.id,
        subjectId: item.subjectId, title: item.description, type: 'RECORDED', status: 'COMPLETED',
        durationMinutes: item.durationMinutes, dateOnly: true,
        startAt: `${item.studyDate}T00:00:00`, endAt: `${add(item.studyDate, 1)}T00:00:00`}))];
  }
  function reuse(availability, start, currentTime) {
    const minutes = {}, slots = {};
    for (let offset = 0; offset < 7; offset++) {
      const date = add(start, offset);
      const weekday = new Date(`${date}T12:00:00Z`).getUTCDay();
      const old = Object.keys(availability.dailyAvailabilityMinutes || {}).find(key =>
        new Date(`${key}T12:00:00Z`).getUTCDay() === weekday);
      if (!old) continue;
      if (availability.availabilitySlots != null) {
        slots[date] = (availability.availabilitySlots[old] || []).filter(slot => `${date}T${slot.start.length === 5 ? slot.start + ':00' : slot.start}` > currentTime);
        minutes[date] = slots[date].reduce((sum, slot) => sum +
          (Number(slot.end.slice(0, 2)) * 60 + Number(slot.end.slice(3, 5))) -
          (Number(slot.start.slice(0, 2)) * 60 + Number(slot.start.slice(3, 5))), 0);
      } else {
        const left = date === currentTime.slice(0, 10)
          ? Math.max(0, 1440 - Number(currentTime.slice(11, 13)) * 60 - Number(currentTime.slice(14, 16)) - 1) : 1440;
        minutes[date] = Math.min(availability.dailyAvailabilityMinutes[old], left);
      }
    }
    return {minutes, slots: availability.availabilitySlots == null ? null : slots};
  }
  return {now, add, calendar, reuse};
})();
if (typeof module !== 'undefined') module.exports = StudentWorkflow;
