/* DOM content is created as text; provider output never becomes arbitrary HTML. */
const StudentControls = (() => {
  function zones(current = 'UTC') {
    return [...new Set([...(typeof Intl.supportedValuesOf === 'function' ? Intl.supportedValuesOf('timeZone') : []),
      'UTC', 'Asia/Kolkata', 'Australia/Sydney', 'Australia/Perth', 'Europe/London', 'America/New_York', 'Asia/Tokyo', current])].sort();
  }
  function zoneLabel(zone) {
    const parts = zone.replaceAll('_', ' ').split('/');
    const city = parts.pop();
    const offset = new Intl.DateTimeFormat('en', {timeZone:zone,timeZoneName:'shortOffset'}).formatToParts(new Date()).find(p=>p.type==='timeZoneName').value;
    return `${city}${zone === 'Asia/Calcutta' ? ' (Kolkata)' : ''} — ${parts.join(' / ') || 'Universal time'} (${offset})`;
  }
  function renderReply(element, text) {
    element.replaceChildren();
    for (const paragraph of text.split(/\n\s*\n/)) {
      const p = document.createElement('p');
      // Preserve line breaks and math delimiters for the local KaTeX renderer.
      const readable = paragraph.replace(/^#{1,6} +/gm, '').replace(/^[-*] +/gm, '• ');
      for (const [index, part] of readable.split(/\*\*([^*]+)\*\*/g).entries()) {
        if (index % 2) { const strong=document.createElement('strong');strong.textContent=part;p.append(strong); }
        else p.append(document.createTextNode(part));
      }
      element.append(p);
    }
  }
  return {zones, zoneLabel, renderReply};
})();
if (typeof module !== 'undefined') module.exports = StudentControls;
