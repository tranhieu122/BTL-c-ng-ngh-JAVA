const assert = require('node:assert/strict');
const { test } = require('node:test');
const fs = require('node:fs');
const path = require('node:path');
const vm = require('node:vm');

class Element {
    constructor() {
        this.value = ''; this.dataset = {}; this.textContent = ''; this.children = [];
        this.listeners = new Map(); this.styles = {};
        this.classList = { add() {}, remove() {}, toggle() {} };
        this.style = { setProperty: (key, value) => { this.styles[key] = value; } };
    }
    addEventListener(name, callback) {
        if (!this.listeners.has(name)) this.listeners.set(name, []);
        this.listeners.get(name).push(callback);
    }
    dispatchEvent(event) { for (const callback of this.listeners.get(event.type) || []) callback(event); }
    click() { this.dispatchEvent({ type: 'click' }); }
    querySelector() { return null; }
    querySelectorAll() { return []; }
    setAttribute() {}
    focus() {}
    append(...children) { this.children.push(...children); }
    replaceChildren(...children) { this.children = [...children]; }
}

const storage = () => {
    const entries = {};
    Object.defineProperties(entries, {
        getItem: { value: (key) => entries[key] ?? null },
        setItem: { value: (key, value) => { entries[key] = String(value); } },
        removeItem: { value: (key) => { delete entries[key]; } }
    });
    return entries;
};

async function loadModule(file, globals) {
    const context = vm.createContext({ ...globals, Event, console, clearTimeout: globals.window.clearTimeout,
        setTimeout: globals.window.setTimeout, CSS: { escape: (value) => value } });
    const cache = new Map();
    async function load(filename) {
        filename = path.resolve(filename);
        if (cache.has(filename)) return cache.get(filename);
        const module = new vm.SourceTextModule(fs.readFileSync(filename, 'utf8'), { context, identifier: filename });
        cache.set(filename, module);
        await module.link((specifier, importing) => load(path.resolve(path.dirname(importing.identifier), specifier)));
        return module;
    }
    const module = await load('src/main/resources/static/js/modules/' + file);
    await module.evaluate();
    return module.namespace;
}

function submissionFixture(owner, session = storage()) {
    const form = new Element(); form.dataset = { editing: 'false', draftOwner: owner };
    const values = { title: 'Giáo trình Java', authorName: 'Author', description: 'Nội dung mô tả đầy đủ cho tài liệu học tập Java',
        categoryId: '1', facultyId: '2', departmentId: '3', summary: 'Academic summary', keywords: 'Java, Spring',
        learningResourceType: 'LECTURE', educationLevel: 'UNIVERSITY', languageCode: 'en', licenseType: 'CC_BY' };
    const fields = Object.fromEntries(Object.entries(values).map(([name, value]) => [name, Object.assign(new Element(), { value })]));
    form.elements = { namedItem: (name) => fields[name] };
    const autosave = new Element(); const score = new Element(); const message = new Element(); const strong = new Element();
    score.querySelector = () => strong;
    const layout = new Element();
    layout.querySelector = (selector) => ({ '[data-readiness-score]': score, '[data-readiness-message]': message })[selector] || new Element();
    form.closest = () => layout;
    form.querySelector = (selector) => selector === '[data-submission-autosave]' ? autosave : null;
    const document = new Element(); const logout = new Element();
    document.querySelectorAll = (selector) => selector === '[data-document-submission]' ? [form]
        : selector === 'form[action$="/logout"]' ? [logout] : [];
    document.createElement = () => new Element(); document.createTextNode = (text) => ({ textContent: text });
    let timer;
    const window = Object.assign(new Element(), { setTimeout: (callback) => { timer = callback; return 1; }, clearTimeout: () => { timer = null; } });
    return { form, logout, autosave, fields, strong, score, message, document, window, sessionStorage: session,
        localStorage: storage(), flush: () => { if (timer) timer(); } };
}

test('server keyword matches remain visible when only hidden metadata matched', async () => {
    const card = new Element(); card.dataset = { title: 'Giáo trình Java', category: 'Lập trình' }; card.textContent = 'Giáo trình Java';
    const filter = new Element(); filter.dataset.categoryFilter = ''; filter.textContent = 'Tất cả';
    const input = new Element(); input.value = 'summaryonlytoken';
    const count = new Element(); const empty = new Element();
    const document = new Element();
    document.querySelectorAll = (selector) => selector === '[data-category-filter]' ? [filter]
        : selector === '[data-document-card]' ? [card] : [];
    document.querySelector = (selector) => ({ '[data-search-input]': input, '[data-visible-count]': count,
        '[data-filter-empty]': empty })[selector] || null;
    const module = await loadModule('catalog.js', { document, window: {} });
    module.initCatalog();
    assert.equal(card.hidden, false); assert.equal(count.textContent, '1'); assert.equal(empty.hidden, true);
});

test('readiness updates its widget outside the form', async () => {
    const fixture = submissionFixture('a');
    const module = await loadModule('document-submission.js', fixture);
    module.initDocumentSubmission();
    assert.equal(fixture.strong.textContent, '83%');
    fixture.fields.title.value = '';
    fixture.form.dispatchEvent(new Event('input')); fixture.flush();
    assert.equal(fixture.strong.textContent, '67%');
});

test('draft preserves academic fields, survives submission failure and is isolated by account', async () => {
    const shared = storage(); const key = 'edurepo-document-submission-draft:a';
    const first = submissionFixture('a', shared);
    (await loadModule('document-submission.js', first)).initDocumentSubmission();
    first.form.dispatchEvent(new Event('input')); first.flush();
    const draft = JSON.parse(shared.getItem(key));
    assert.equal(draft.summary, 'Academic summary'); assert.equal(draft.keywords, 'Java, Spring');
    assert.equal(draft.licenseType, 'CC_BY'); assert.equal(draft.languageCode, 'en');
    first.form.dispatchEvent(new Event('submit'));
    assert.ok(shared.getItem(key), 'Do not erase the draft before server success');
    const second = submissionFixture('b', shared);
    (await loadModule('document-submission.js', second)).initDocumentSubmission();
    assert.equal(second.autosave.children.length, 0, 'Another account must not see a restore button');
    const restored = submissionFixture('a', shared); restored.fields.summary.value = '';
    (await loadModule('document-submission.js', restored)).initDocumentSubmission();
    restored.autosave.children.find((child) => child.textContent === 'Khôi phục').click();
    assert.equal(restored.fields.summary.value, 'Academic summary');
});

test('blocked browser storage does not break submission initialization', async () => {
    const fixture = submissionFixture('a');
    fixture.sessionStorage = { getItem() { throw Error('blocked'); }, setItem() { throw Error('blocked'); } };
    fixture.localStorage = { removeItem() { throw Error('blocked'); } };
    (await loadModule('document-submission.js', fixture)).initDocumentSubmission();
    fixture.form.dispatchEvent(new Event('input')); fixture.flush();
    assert.match(fixture.autosave.textContent, /Không thể tự lưu/);
    assert.equal(fixture.strong.textContent, '83%');
});

test('leaving the page after logout cannot recreate the deleted draft', async () => {
    const fixture = submissionFixture('a');
    (await loadModule('document-submission.js', fixture)).initDocumentSubmission();
    fixture.form.dispatchEvent(new Event('input')); fixture.flush();
    assert.ok(fixture.sessionStorage.getItem('edurepo-document-submission-draft:a'));
    fixture.logout.dispatchEvent(new Event('submit'));
    fixture.window.dispatchEvent(new Event('pagehide'));
    fixture.flush();
    assert.equal(fixture.sessionStorage.getItem('edurepo-document-submission-draft:a'), null);
});

test('authentication pages expose exactly one primary heading', () => {
    const templates = ['login.html', 'register.html', 'forgot-password.html', 'verify-register.html',
        'verify-reset.html', 'reset-password.html', 'access-denied.html'];
    for (const template of templates) {
        const source = fs.readFileSync(path.join('src/main/resources/templates/auth', template), 'utf8');
        assert.equal((source.match(/<h1(?:\s|>)/g) || []).length, 1, `${template} must have one h1`);
    }
});

test('shared UI keeps destructive confirmation deliberate and mobile navigation keyboard-safe', () => {
    const dialog = fs.readFileSync('src/main/resources/templates/fragments/ui.html', 'utf8');
    const navigation = fs.readFileSync('src/main/resources/static/js/modules/navigation.js', 'utf8');
    assert.doesNotMatch(dialog, /confirm-dialog-backdrop[^>]*data-confirm-cancel/);
    assert.match(navigation, /event\.key === "Escape"/);
    assert.match(navigation, /event\.key !== "Tab"/);
    assert.match(navigation, /focusableElements/);
});

test('shared controls provide immediate press feedback without ignoring reduced motion', () => {
    const styles = fs.readFileSync('src/main/resources/static/css/components/ui-system.css', 'utf8');
    assert.match(styles, /--press-transition:\s*60ms/);
    assert.match(styles, /:active:not\(:disabled\):not\(\[aria-disabled="true"\]\)/);
    assert.match(styles, /interaction-pop-in/);
    assert.match(styles, /@media \(prefers-reduced-motion: reduce\)/);
});

test('navigation performance keeps optional modules lazy and motion accessible', () => {
    const main = fs.readFileSync('src/main/resources/static/js/main.js', 'utf8');
    const styles = fs.readFileSync('src/main/resources/static/css/components/performance.css', 'utf8');
    assert.match(main, /loadFeature\(/);
    assert.doesNotMatch(main, /^import \{ initCatalog \}/m);
    assert.match(styles, /@view-transition/);
    assert.match(styles, /content-visibility:\s*auto/);
    assert.match(styles, /prefers-reduced-motion:\s*reduce/);
    assert.match(styles, /\.site-header \.topbar[\s\S]*backdrop-filter:\s*none/);
});
