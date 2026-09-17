const { test } = require('node:test');
const assert = require('node:assert/strict');
const DashboardStreamClient = require('../dashboard-stream.js');

test('parses dashboard frames, CRLF and multi-line data; ignores keepalives', () => {
  assert.equal(DashboardStreamClient.parse(': keepalive'), null);
  assert.deepEqual(DashboardStreamClient.parse('event: dashboard\r\ndata: {"week": {},\r\ndata: "stream": {"source":"KAFKA_STREAMS"}}'),
    { week: {}, stream: { source: 'KAFKA_STREAMS' } });
  assert.throws(() => DashboardStreamClient.parse('event: dashboard\ndata: {"other":1}'));
});
test('fragmented UTF-8 frames update without polling; auth stays in headers and stop cancels', async () => {
  let requested; let controller; const snapshots = []; const statuses = [];
  const oldFetch = global.fetch;
  global.fetch = async (url, options) => {
    requested = { url, options };
    return { ok: true, status: 200, headers: new Headers({ 'content-type': 'text/event-stream' }),
      body: new ReadableStream({ start(value) { controller = value; } }) };
  };
  const client = new DashboardStreamClient({ endpoint: 'http://test/dashboard/stream', getToken: () => 'secret-token',
    getTimezone: () => 'Australia/Sydney', onSnapshot: value => snapshots.push(value),
    onStatus: value => statuses.push(value), onUnauthorized: () => assert.fail('not expired') });
  try {
    client.start(); await new Promise(resolve => setImmediate(resolve));
    const frame = new TextEncoder().encode('event: dashboard\r\ndata: {"week":{"label":"Étude"},"stream":{}}\r\n\r\n');
    for (let index = 0; index < frame.length; index++) controller.enqueue(frame.slice(index, index + 1));
    await new Promise(resolve => setImmediate(resolve));
    assert.equal(snapshots.length, 1); assert.equal(snapshots[0].week.label, 'Étude');
    assert.equal(requested.options.headers.Authorization, 'Bearer secret-token');
    assert.equal(requested.options.headers['X-Study-Timezone'], 'Australia/Sydney');
    assert.ok(!requested.url.includes('secret-token')); assert.ok(statuses.includes('live'));
    client.stop(); controller.close();
    assert.ok(requested.options.signal.aborted);
  } finally { client.stop(); global.fetch = oldFetch; }
});
