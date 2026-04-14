(function () {
  function init() {
    var input = document.getElementById('ro-search-input');
    var dropdown = document.getElementById('ro-search-dropdown');
    if (!input || !dropdown) return;

    input.addEventListener('keydown', function (e) {
      if (e.key === 'Escape') {
        dropdown.innerHTML = '';
        input.value = '';
        input.blur();
        return;
      }
      var items = dropdown.querySelectorAll('.ro-suggestion-item');
      if (!items.length) return;
      var current = dropdown.querySelector('.ro-suggestion-item.active');
      var idx = Array.prototype.indexOf.call(items, current);
      if (e.key === 'ArrowDown') {
        e.preventDefault();
        if (current) current.classList.remove('active');
        items[(idx + 1) % items.length].classList.add('active');
      } else if (e.key === 'ArrowUp') {
        e.preventDefault();
        if (current) current.classList.remove('active');
        items[(idx - 1 + items.length) % items.length].classList.add('active');
      } else if (e.key === 'Enter' && current) {
        e.preventDefault();
        var link = current.querySelector('a');
        if (link) window.location = link.href;
      }
    });

    document.addEventListener('click', function (e) {
      if (!input.contains(e.target) && !dropdown.contains(e.target)) {
        dropdown.innerHTML = '';
      }
    });
  }
  if (document.readyState === 'loading') {
    document.addEventListener('DOMContentLoaded', init);
  } else {
    init();
  }
})();
