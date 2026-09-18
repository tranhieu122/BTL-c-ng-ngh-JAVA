const {test} = require('node:test');
const assert = require('node:assert/strict');
const fs = require('node:fs');
const vm = require('node:vm');
async function moduleFor(name, globals = {}) {
    const module = new vm.SourceTextModule(fs.readFileSync(`src/main/resources/static/js/modules/${name}.js`, 'utf8'), {context: vm.createContext({AbortSignal, TextEncoder, ...globals})});
    await module.link(() => { throw new Error('Unexpected dependency'); });
    await module.evaluate(); return module.namespace;
}
async function harness(fetchSnapshot = async () => ({status: 200, ok: true, json: async () => ({recovered: true})})) {
    const {connectRealtime} = await moduleFor('realtime');
    const sources = [], timers = new Map(), snapshots = [], statuses = []; let expired = 0, timerId = 0;
    class Source {
        constructor(url) { this.url = url; this.listeners = {}; this.closed = false; sources.push(this); }
        addEventListener(name, listener) { this.listeners[name] = listener; }
        close() { this.closed = true; }
        emit(data) { this.listeners.snapshot({data: JSON.stringify(data)}); }
    }
    const client = connectRealtime({url: '/events/stream', snapshotUrl: '/events/snapshot', EventSourceClass: Source,
        fetchSnapshot, onSnapshot: data => snapshots.push(data), onStatus: value => statuses.push(value), onExpired: () => expired++,
        schedule: (fn, delay) => { const id = ++timerId; timers.set(id, {fn, delay}); return id; }, cancel: id => timers.delete(id)});
    return {client, sources, timers, snapshots, statuses, expired: () => expired};
}
test('avatar frontend rejects oversize, unsupported type, fake extension and empty file', async () => {
    const {avatarFileError} = await moduleFor('profile');
    for (const file of [{name:'a.png',type:'image/png',size:2097153}, {name:'a.svg',type:'image/svg+xml',size:10},
        {name:'a.png.exe',type:'image/png',size:10}, {name:'a.jpg',type:'application/pdf',size:10}, {name:'a.png',type:'image/png',size:0}]) assert.ok(avatarFileError(file));
    assert.equal(avatarFileError({name:'A.JPG', type:'image/jpeg', size:2097152}), '');
});
test('only one stream starts, stop closes stream and rejects late events', async () => {
    const h = await harness(); h.client.start(); h.client.start(); assert.equal(h.sources.length, 1);
    h.sources[0].emit({name:'Owner'}); assert.equal(h.snapshots.length, 1);
    h.client.stop(); assert.equal(h.sources[0].closed, true);
    h.sources[0].emit({name:'Stale'}); assert.equal(h.snapshots.length, 1);
    h.client.start(); assert.equal(h.sources.length, 2); h.client.stop();
});
test('disconnect resyncs via HTTP then reconnect snapshot replaces missed state without duplicate registration', async () => {
    const h = await harness(); h.client.start(); h.sources[0].emit({version:1});
    await h.sources[0].onerror(); assert.equal(h.sources[0].closed, true); assert.equal(h.timers.size, 1);
    assert.equal(h.snapshots.at(-1).recovered, true);
    const timer = [...h.timers.values()][0]; assert.equal(timer.delay, 1000); h.timers.clear(); timer.fn();
    assert.equal(h.sources.length, 2); h.sources[1].emit({version:3}); assert.equal(h.snapshots.at(-1).version, 3);
    h.sources[0].emit({version:2}); assert.equal(h.snapshots.at(-1).version, 3); h.client.stop();
});
test('expired session stops retries and does not expose stale snapshot', async () => {
    const h = await harness(async () => ({status:401, ok:false})); h.client.start(); await h.sources[0].onerror();
    assert.equal(h.expired(), 1); assert.equal(h.timers.size, 0); assert.equal(h.snapshots.length, 0);
});
test('pagehide while fallback fetch is pending prevents late render and reconnect', async () => {
    let resolve; const h = await harness(() => new Promise(done => { resolve = done; }));
    h.client.start(); const pending = h.sources[0].onerror(); h.client.stop();
    resolve({status:200,ok:true,json:async()=>({private:'stale'})}); await pending;
    assert.equal(h.snapshots.length, 0); assert.equal(h.timers.size, 0);
});
test('session-expired SSE event closes connection immediately', async () => {
    const h = await harness(); h.client.start(); h.sources[0].listeners['session-expired']();
    assert.equal(h.sources[0].closed, true); assert.equal(h.expired(), 1); assert.equal(h.timers.size, 0);
});
