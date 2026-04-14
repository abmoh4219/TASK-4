(function () {
  // Order countdown timer — reads data-expires-at (epoch ms) and updates every second.
  function updateCountdowns() {
    var nodes = document.querySelectorAll('[data-expires-at]');
    var now = Date.now();
    nodes.forEach(function (el) {
      var expiresAt = parseInt(el.getAttribute('data-expires-at'), 10);
      if (isNaN(expiresAt)) return;
      var remaining = expiresAt - now;
      if (remaining <= 0) {
        el.textContent = '00:00';
        el.classList.add('ro-urgent');
        return;
      }
      var min = Math.floor(remaining / 60000);
      var sec = Math.floor((remaining % 60000) / 1000);
      el.textContent = (min < 10 ? '0' + min : min) + ':' + (sec < 10 ? '0' + sec : sec);
      if (remaining < 5 * 60 * 1000) {
        el.classList.add('ro-urgent');
      }
    });
  }
  setInterval(updateCountdowns, 1000);
  updateCountdowns();

  // Flash auto-dismiss after 4 seconds
  setTimeout(function () {
    document.querySelectorAll('.ro-flash').forEach(function (el) {
      el.style.opacity = '0';
      setTimeout(function () { el.remove(); }, 400);
    });
  }, 4000);

  // CSRF: forward token from <meta> tag to every HTMX request.
  document.body.addEventListener('htmx:configRequest', function (evt) {
    var token = document.querySelector('meta[name="_csrf"]');
    var header = document.querySelector('meta[name="_csrf_header"]');
    if (token && header && token.getAttribute('content') && header.getAttribute('content')) {
      evt.detail.headers[header.getAttribute('content')] = token.getAttribute('content');
    }
  });

  // HTMX loading state — toggle class on body during requests.
  document.body.addEventListener('htmx:beforeRequest', function () {
    document.body.classList.add('htmx-loading');
  });
  document.body.addEventListener('htmx:afterRequest', function () {
    document.body.classList.remove('htmx-loading');
  });
})();
