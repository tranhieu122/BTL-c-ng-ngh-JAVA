const assert = require('node:assert/strict');
const { test } = require('node:test');
const fs = require('node:fs');

const template = fs.readFileSync('src/main/resources/templates/fragments/document-assistant.html', 'utf8');
const script = fs.readFileSync('src/main/resources/static/js/modules/document-assistant.js', 'utf8');
const styles = fs.readFileSync('src/main/resources/static/css/components/document-assistant.css', 'utf8');

test('assistant controls are keyboard and screen-reader accessible', () => {
    assert.match(template, /role="dialog"/);
    assert.match(template, /role="log"/);
    assert.match(template, /aria-live="polite"/);
    assert.match(template, /aria-controls="document-assistant-panel"/);
    assert.match(template, /<label[^>]*for="document-assistant-input"/);
    assert.match(script, /event\.key === "Escape"/);
    assert.match(script, /form\.addEventListener\("submit"/);
});

test('assistant prevents repeated requests and renders server text without HTML injection', () => {
    assert.match(script, /if \(pending\) return/);
    assert.match(script, /submitButton\.disabled = true/);
    assert.match(script, /input\.disabled = true/);
    assert.match(script, /node\.textContent = text/);
    assert.doesNotMatch(script, /innerHTML/);
});

test('assistant renders backend suggestions as clickable follow-up buttons', () => {
    assert.match(script, /response\.suggestions/);
    assert.match(script, /data-assistant-suggestion/);
    assert.match(script, /dataset\.assistantSuggestion = text/);
    assert.match(script, /sendMessage\(suggestion\.dataset\.assistantSuggestion\.trim\(\)\)/);
});

test('assistant keeps short conversation context and renders document quick actions', () => {
    assert.match(script, /let conversationContext = null/);
    assert.match(script, /contextKeyword: conversationContext\.keyword/);
    assert.match(script, /if \(payload\.context && payload\.type !== "ERROR"\) conversationContext = payload\.context/);
    assert.match(script, /conversationContext = null/);
    assert.match(script, /item\.actions/);
    assert.match(script, /action\.message/);
});

test('assistant uses the Thymeleaf-provided endpoint instead of a hard-coded root URL', () => {
    assert.match(template, /data-assistant-url=@\{\/api\/document-assistant\}/);
    assert.match(script, /root\.dataset\.assistantUrl/);
    assert.match(script, /url\.searchParams\.set\("message", message\)/);
    assert.doesNotMatch(script, /fetch\(`\/api\/document-assistant/);
});

test('assistant has bounded desktop and mobile layouts', () => {
    assert.match(styles, /width: min\(21\.5rem, calc\(100vw - 2rem\)\)/);
    assert.match(styles, /height: min\(31\.5rem, calc\(100dvh - 6rem\)\)/);
    assert.match(styles, /@media \(max-width: 640px\)/);
    assert.match(styles, /width: 100%/);
    assert.match(styles, /100dvh/);
});
