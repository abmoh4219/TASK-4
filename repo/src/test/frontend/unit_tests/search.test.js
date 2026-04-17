/**
 * @jest-environment jsdom
 *
 * Unit tests for src/main/resources/static/js/search.js — the unified search
 * bar's keyboard navigation. Loads the real script into a JSDOM sandbox,
 * renders the expected DOM skeleton (input + dropdown + suggestion items),
 * and simulates keyboard events to assert .active cycles through the items
 * in both directions.
 */

const fs = require('fs');
const path = require('path');

const SEARCH_JS = fs.readFileSync(
  path.resolve(__dirname, '../../../main/resources/static/js/search.js'),
  'utf8'
);

function loadSearch() {
  // eslint-disable-next-line no-new-func
  new Function(SEARCH_JS)();
}

function seedDom() {
  document.body.innerHTML = `
    <input id="ro-search-input" type="text" value="calc">
    <div id="ro-search-dropdown">
      <div class="ro-suggestion-item"><a href="/catalog/1">Calculus I</a></div>
      <div class="ro-suggestion-item"><a href="/catalog/2">Calculus II</a></div>
      <div class="ro-suggestion-item"><a href="/catalog/3">Linear Algebra</a></div>
    </div>
  `;
}

function key(input, keyName) {
  const ev = new KeyboardEvent('keydown', { key: keyName, bubbles: true, cancelable: true });
  input.dispatchEvent(ev);
  return ev;
}

describe('search keyboard navigation', () => {
  test('no-ops cleanly when input or dropdown missing', () => {
    document.body.innerHTML = '';
    expect(() => loadSearch()).not.toThrow();
  });

  test('ArrowDown selects first item', () => {
    seedDom();
    loadSearch();
    const input = document.getElementById('ro-search-input');
    const items = document.querySelectorAll('.ro-suggestion-item');
    key(input, 'ArrowDown');
    expect(items[0].classList.contains('active')).toBe(true);
  });

  test('ArrowDown twice selects second item, wrapping past the last back to first', () => {
    seedDom();
    loadSearch();
    const input = document.getElementById('ro-search-input');
    const items = document.querySelectorAll('.ro-suggestion-item');
    key(input, 'ArrowDown'); // 0
    key(input, 'ArrowDown'); // 1
    expect(items[1].classList.contains('active')).toBe(true);
    key(input, 'ArrowDown'); // 2
    key(input, 'ArrowDown'); // wraps → 0
    expect(items[0].classList.contains('active')).toBe(true);
    expect(items[2].classList.contains('active')).toBe(false);
  });

  test('ArrowUp from first wraps to last', () => {
    seedDom();
    loadSearch();
    const input = document.getElementById('ro-search-input');
    const items = document.querySelectorAll('.ro-suggestion-item');
    key(input, 'ArrowDown'); // 0 active
    key(input, 'ArrowUp');   // wrap to last
    expect(items[2].classList.contains('active')).toBe(true);
  });

  test('Escape clears the input value and the dropdown', () => {
    seedDom();
    loadSearch();
    const input = document.getElementById('ro-search-input');
    const dropdown = document.getElementById('ro-search-dropdown');
    key(input, 'Escape');
    expect(input.value).toBe('');
    expect(dropdown.innerHTML).toBe('');
  });

  test('Enter on an active item navigates to its anchor href', () => {
    seedDom();
    loadSearch();
    const input = document.getElementById('ro-search-input');
    // Spy window.location assignment.
    delete window.location;
    window.location = { _href: null };
    Object.defineProperty(window.location, 'toString', {
      value: function () { return this._href; }
    });
    // Simulate selection of the second item.
    document.querySelectorAll('.ro-suggestion-item')[1].classList.add('active');
    key(input, 'Enter');
    // The code sets window.location = link.href; with our stub the assignment
    // replaces the window.location object with a string-ish value.
    expect(String(window.location)).toContain('/catalog/2');
  });

  test('clicking outside both input and dropdown clears the dropdown', () => {
    seedDom();
    loadSearch();
    const dropdown = document.getElementById('ro-search-dropdown');
    const outside = document.createElement('button');
    outside.textContent = 'x';
    document.body.appendChild(outside);
    outside.dispatchEvent(new MouseEvent('click', { bubbles: true }));
    expect(dropdown.innerHTML).toBe('');
  });

  test('clicking inside the dropdown does NOT clear it', () => {
    seedDom();
    loadSearch();
    const dropdown = document.getElementById('ro-search-dropdown');
    dropdown.querySelectorAll('.ro-suggestion-item')[0]
        .dispatchEvent(new MouseEvent('click', { bubbles: true }));
    expect(dropdown.querySelectorAll('.ro-suggestion-item').length).toBe(3);
  });

  test('non-navigation keys with no items are no-ops', () => {
    document.body.innerHTML = `
      <input id="ro-search-input" type="text">
      <div id="ro-search-dropdown"></div>
    `;
    loadSearch();
    const input = document.getElementById('ro-search-input');
    expect(() => key(input, 'ArrowDown')).not.toThrow();
  });
});
