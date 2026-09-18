const {test} = require('node:test');
const assert = require('node:assert/strict');
const fs = require('node:fs');
const vm = require('node:vm');
async function load(globals = {}) {
    const mod = new vm.SourceTextModule(fs.readFileSync('src/main/resources/static/js/modules/realtime-regions.js','utf8'),
        {context: vm.createContext({AbortController, Event, ...globals})});
    await mod.link(() => {}); await mod.evaluate(); return mod.namespace;
}
test('region versions isolate profile, unrelated documents and track removal', async () => {
    const {regionVersion} = await load();
    const region = {dataset:{liveSource:'documents',liveId:'1'}};
    const snapshot = {documents:[{id:1,status:'DRAFT'},{id:2,status:'SUBMITTED'}]};
    const version = regionVersion(region,snapshot);
    snapshot.profile = {fullName:'New name'};
    snapshot.documents[1].status='APPROVED';
    assert.equal(regionVersion(region,snapshot),version);
    snapshot.documents.shift();
    assert.notEqual(regionVersion(region,snapshot),version);
});
test('review transition preserves comment and scores, resets obsolete decision and locks removed work', async () => {
    const comment = {value:'Góp ý chưa gửi'}, rubric = {hidden:false,disabled:false,score:4}, submit = {};
    const notice = {setAttribute(){},dataset:{}};
    const form = {dataset:{},prepend(){},querySelector(selector){
        return {'[data-review-submit]':submit,'.rubric-fieldset':rubric,'[data-live-review-notice]':notice}[selector];
    }};
    const select = {value:'REJECTED',closest:()=>form,replaceChildren(...options){this.options=options;},dispatchEvent(){}};
    const badge = {dataset:{liveDocumentStatus:'1'}};
    const document = {querySelector:selector=>selector==='[data-review-action]'?select:badge,createElement:()=>({})};
    const {syncReviewActions} = await load({document});
    syncReviewActions({reviewQueue:[{id:1,status:'APPROVED'}]});
    assert.equal(select.value,''); assert.equal(select.options[1].value,'PUBLISHED');
    assert.equal(rubric.disabled,true); assert.equal(rubric.score,4); assert.equal(comment.value,'Góp ý chưa gửi');
    syncReviewActions({reviewQueue:[]});
    assert.equal(submit.disabled,true); assert.equal(select.disabled,true);
    syncReviewActions({reviewQueue:[{id:1,status:'SUBMITTED'}]});
    assert.equal(submit.disabled,false); assert.equal(rubric.disabled,false);
    assert.equal(rubric.score,4);
});
async function regionsHarness() {
    const listeners={}, lifecycle={}, timers=new Map(), requests=[];
    let timerId=0, updated=0;
    const region={dataset:{liveRegion:'documents',liveSource:'documents'},children:[],contains:()=>false,
        querySelector:()=>null, replaceChildren(...children){this.children=children;}};
    const message={};
    const document={activeElement:null,querySelectorAll:()=>[region],querySelector:selector=>selector==='[data-live-message]'?message:null,
        addEventListener:(name,fn)=>listeners[name]=fn};
    const window={location:{href:'http://localhost/documents?status=DRAFT&page=2'},
        setTimeout(fn){const id=++timerId;timers.set(id,fn);return id;},clearTimeout:id=>timers.delete(id),
        addEventListener:(name,fn)=>lifecycle[name]=fn};
    class DOMParser {parseFromString(text){return {querySelector:()=>({childNodes:[text],querySelector:()=>null})};}}
    const fetch=(url,options)=>new Promise(resolve=>requests.push({url,options,resolve}));
    const {initRealtimeRegions}=await load({document,window,DOMParser,fetch});
    initRealtimeRegions(()=>updated++);
    const emit=status=>listeners['realtime:snapshot']({detail:{documents:[{id:1,status}]}});
    const complete=async(index,text)=>{requests[index].resolve({ok:true,redirected:false,text:async()=>text});await new Promise(setImmediate);};
    return {region,emit,complete,requests,lifecycle,timers,message,updated:()=>updated};
}
test('refresh retains URL filters and catches up when snapshot changes during fetch',async()=>{
    const h=await regionsHarness();h.emit('DRAFT');h.emit('SUBMITTED');
    assert.equal(h.requests.length,1);
    assert.equal(h.requests[0].url,'http://localhost/documents?status=DRAFT&page=2');
    assert.equal(h.requests[0].options.cache,'no-store');
    await h.complete(0,'draft');
    const retry=[...h.timers.values()][0];h.timers.clear();retry();
    assert.equal(h.requests.length,2);await h.complete(1,'submitted');
    assert.deepEqual(h.region.children,['submitted']);assert.equal(h.timers.size,0);
});
test('pagehide prevents pending HTML from replacing private page content',async()=>{
    const h=await regionsHarness();h.emit('DRAFT');h.lifecycle.pagehide();
    assert.equal(h.requests[0].options.signal.aborted,true);
    await h.complete(0,'late content');assert.deepEqual(h.region.children,[]);assert.equal(h.updated(),0);
});

test('restoring page cannot apply response from the previous page lifecycle',async()=>{
    const h=await regionsHarness();h.emit('DRAFT');h.lifecycle.pagehide();h.lifecycle.pageshow();
    await h.complete(0,'old lifecycle');assert.deepEqual(h.region.children,[]);
    const retry=[...h.timers.values()][0];h.timers.clear();retry();
    await h.complete(1,'current lifecycle');assert.deepEqual(h.region.children,['current lifecycle']);
});

test('review filter applies to new cards and initializes once without clearing search', async () => {
    const listeners={}; const search={value:'toán',addEventListener:(name,fn)=>listeners[name]=fn};
    const status={value:'APPROVED',addEventListener(){}}; const count={},empty={};
    let cards=[{dataset:{title:'Toán',status:'APPROVED'}}], registrations=0;
    const list={dataset:{},querySelectorAll:()=>cards,addEventListener(name,fn){listeners[name]=fn;registrations++;}};
    const document={querySelector:selector=>({'[data-review-list]':list,'[data-review-search]':search,
        '[data-review-status]':status,'[data-review-count]':count,'[data-review-empty]':empty}[selector])};
    const module=new vm.SourceTextModule(fs.readFileSync('src/main/resources/static/js/modules/review-queue.js','utf8'),{context:vm.createContext({document})});
    await module.link(()=>{});await module.evaluate();module.namespace.initReviewQueue();
    cards=[{dataset:{title:'Toán mới',status:'APPROVED'}},{dataset:{title:'Toán chờ',status:'SUBMITTED'}},{dataset:{title:'Văn',status:'APPROVED'}}];
    listeners['realtime:updated']();module.namespace.initReviewQueue();
    assert.equal(registrations,1);assert.equal(search.value,'toán');assert.equal(count.textContent,'1 tài liệu');
    assert.deepEqual(cards.map(card=>card.hidden),[false,true,true]);
    cards=[];listeners['realtime:updated']();assert.equal(empty.hidden,false);
    cards=[{dataset:{title:'Toán trở lại',status:'APPROVED'}}];listeners['realtime:updated']();
    assert.equal(empty.hidden,true);assert.equal(list.hidden,false);
});
