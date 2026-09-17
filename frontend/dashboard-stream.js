/* Authenticated SSE over fetch: native EventSource cannot send the existing bearer header. */
class DashboardStreamClient {
  constructor({ endpoint, getToken, getTimezone, onSnapshot, onStatus, onUnauthorized }) {
    Object.assign(this, { endpoint, getToken, getTimezone, onSnapshot, onStatus, onUnauthorized });
    this.active = false;
  }
  start() {
    this.stop();
    if (!this.getToken()) return;
    this.active = true;
    this.controller = new AbortController();
    this.run(this.controller);
  }
  stop() {
    this.active = false;
    this.controller?.abort();
    clearTimeout(this.timer);
    this.resume?.();
  }
  async run(controller) {
    let failures = 0;
    while (this.active && this.controller === controller && this.getToken()) {
      let reader;
      try {
        this.onStatus('connecting');
        const response = await fetch(this.endpoint, {
          headers: { Authorization: `Bearer ${this.getToken()}`, 'X-Study-Timezone': this.getTimezone(),
            Accept: 'text/event-stream' },
          signal: controller.signal, cache: 'no-store'
        });
        if (response.status === 401) { this.stop(); this.onUnauthorized(); return; }
        if (!response.ok || !response.body) throw new Error('Live dashboard connection failed');
        if (!response.headers.get('content-type')?.includes('text/event-stream')) throw new Error('Unexpected stream format');
        reader = response.body.getReader();
        const decoder = new TextDecoder();
        let buffer = '';
        failures = 0;
        this.onStatus('live');
        while (this.active && this.controller === controller) {
          const { value, done } = await reader.read();
          if (done) break;
          buffer += decoder.decode(value, { stream: true });
          if (buffer.length > 2_000_000) throw new Error('Stream frame is too large');
          let delimiter;
          while ((delimiter = /\r?\n\r?\n/.exec(buffer))) {
            const frame = buffer.slice(0, delimiter.index);
            buffer = buffer.slice(delimiter.index + delimiter[0].length);
            const snapshot = DashboardStreamClient.parse(frame);
            if (snapshot && this.active && this.controller === controller) this.onSnapshot(snapshot);
          }
        }
      } catch (error) {
        if (controller.signal.aborted) return;
        failures++;
        this.onStatus('offline');
      } finally {
        if (reader) { try { await reader.cancel(); } catch { /* already aborted */ } }
      }
      if (!this.active || this.controller !== controller) return;
      // One-minute server connections revalidate account/session ownership on reconnect.
      await new Promise(resolve => {
        this.resume = resolve;
        this.timer = setTimeout(resolve, Math.min(15_000, 1000 * (2 ** failures)));
      });
    }
  }
  static parse(frame) {
    let event = 'message';
    const data = [];
    for (const line of frame.split(/\r?\n/)) {
      if (line.startsWith('event:')) event = line.slice(6).trim();
      if (line.startsWith('data:')) data.push(line.slice(5).replace(/^ /, ''));
    }
    if (event !== 'dashboard' || !data.length) return null;
    const snapshot = JSON.parse(data.join('\n'));
    if (!snapshot.week || !snapshot.stream) throw new Error('Invalid dashboard snapshot');
    return snapshot;
  }
}
if (typeof module !== 'undefined') module.exports = DashboardStreamClient;
