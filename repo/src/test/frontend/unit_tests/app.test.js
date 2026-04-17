/**
 * @jest-environment jsdom
 *
 * Unit tests for src/main/resources/static/js/app.js:
 *   - countdown timer (formatting, "urgent" class at <5 min, "00:00" at expiry)
 *   - flash auto-dismiss
 *   - HTMX CSRF header forwarding from <meta> tags
 *   - HTMX loading class toggles on body
 */

const fs = require('fs');
const path = require('path');

const APP_JS = fs.readFileSync(
  path.resolve(__dirname, '../../../main/resources/static/js/app.js'),
  'utf8'
);

function loadApp() {
  // Each test starts with a clean DOM and re-evaluates the IIFE in this realm.
  // eslint-disable-next-line no-new-func
  new Function(APP_JS)();
}

describe('countdown timer', () => {
  beforeEach(() => {
    jest.useFakeTimers();
    document.body.innerHTML = '';
  });
  afterEach(() => jest.useRealTimers());

  test('renders MM:SS with leading zeros and marks urgent under 5 minutes', () => {
    // Expire at Date.now + 2 min 5 s.
    const now = 1_700_000_000_000;
    jest.setSystemTime(now);
    const expiry = now + (2 * 60 + 5) * 1000;
    const span = document.createElement('span');
    span.setAttribute('data-expires-at', String(expiry));
    document.body.appendChild(span);

    loadApp();

    expect(span.textContent).toBe('02:05');
    expect(span.classList.contains('ro-urgent')).toBe(true);
  });

  test('renders formatted MM:SS and does NOT mark urgent above 5 minutes', () => {
    const now = 1_700_000_000_000;
    jest.setSystemTime(now);
    const expiry = now + (10 * 60 + 30) * 1000;
    const span = document.createElement('span');
    span.setAttribute('data-expires-at', String(expiry));
    document.body.appendChild(span);

    loadApp();

    expect(span.textContent).toBe('10:30');
    expect(span.classList.contains('ro-urgent')).toBe(false);
  });

  test('shows 00:00 and urgent class after expiry', () => {
    const now = 1_700_000_000_000;
    jest.setSystemTime(now);
    const span = document.createElement('span');
    span.setAttribute('data-expires-at', String(now - 1000)); // already past
    document.body.appendChild(span);

    loadApp();

    expect(span.textContent).toBe('00:00');
    expect(span.classList.contains('ro-urgent')).toBe(true);
  });

  test('ignores elements with no data-expires-at attribute', () => {
    document.body.innerHTML = '<span id="x"></span>';
    loadApp();
    expect(document.getElementById('x').textContent).toBe('');
  });

  test('ignores elements with non-numeric data-expires-at', () => {
    const span = document.createElement('span');
    span.setAttribute('data-expires-at', 'not-a-number');
    document.body.appendChild(span);
    loadApp();
    expect(span.textContent).toBe('');
  });

  test('ticks the clock every second', () => {
    const now = 1_700_000_000_000;
    jest.setSystemTime(now);
    const span = document.createElement('span');
    span.setAttribute('data-expires-at', String(now + 10 * 60 * 1000)); // 10:00
    document.body.appendChild(span);

    loadApp();
    expect(span.textContent).toBe('10:00');
    // advanceTimersByTime both fires the interval AND advances Date.now() by
    // the same amount — no need to also call setSystemTime separately.
    jest.advanceTimersByTime(1000);
    expect(span.textContent).toBe('09:59');
  });
});

describe('flash auto-dismiss', () => {
  beforeEach(() => {
    jest.useFakeTimers();
    document.body.innerHTML = '';
  });
  afterEach(() => jest.useRealTimers());

  test('fades and removes .ro-flash after 4s + 400ms', () => {
    document.body.innerHTML = '<div class="ro-flash" id="f">msg</div>';
    loadApp();
    const el = document.getElementById('f');
    expect(el).not.toBeNull();
    jest.advanceTimersByTime(4000);
    expect(el.style.opacity).toBe('0');
    jest.advanceTimersByTime(400);
    expect(document.getElementById('f')).toBeNull();
  });
});

describe('HTMX CSRF forwarding', () => {
  beforeEach(() => {
    document.head.innerHTML = '';
    document.body.innerHTML = '';
  });

  test('adds the CSRF header from <meta> tags to every request', () => {
    document.head.innerHTML =
      '<meta name="_csrf" content="abc-123">' +
      '<meta name="_csrf_header" content="X-CSRF-TOKEN">';
    loadApp();

    const evt = new Event('htmx:configRequest');
    evt.detail = { headers: {} };
    document.body.dispatchEvent(evt);

    expect(evt.detail.headers['X-CSRF-TOKEN']).toBe('abc-123');
  });

  test('does nothing if meta tags are missing', () => {
    loadApp();
    const evt = new Event('htmx:configRequest');
    evt.detail = { headers: {} };
    document.body.dispatchEvent(evt);
    expect(Object.keys(evt.detail.headers).length).toBe(0);
  });

  test('does nothing if meta content values are empty', () => {
    document.head.innerHTML =
      '<meta name="_csrf" content=""><meta name="_csrf_header" content="">';
    loadApp();
    const evt = new Event('htmx:configRequest');
    evt.detail = { headers: {} };
    document.body.dispatchEvent(evt);
    expect(Object.keys(evt.detail.headers).length).toBe(0);
  });
});

describe('HTMX loading class', () => {
  beforeEach(() => { document.body.className = ''; });

  test('htmx:beforeRequest adds .htmx-loading; htmx:afterRequest removes it', () => {
    loadApp();
    document.body.dispatchEvent(new Event('htmx:beforeRequest'));
    expect(document.body.classList.contains('htmx-loading')).toBe(true);
    document.body.dispatchEvent(new Event('htmx:afterRequest'));
    expect(document.body.classList.contains('htmx-loading')).toBe(false);
  });
});
