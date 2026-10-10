(function () {
  fetch('/api/trial')
    .then(function (r) { return r.json(); })
    .then(function (s) {
      if (s.expired) { window.location.href = '/trial-expired.html'; return; }
      var bar = document.createElement('div');
      bar.textContent = 'Free trial: ' + s.daysLeft + (s.daysLeft === 1 ? ' day' : ' days') + ' left';
      bar.style.cssText = 'position:fixed;bottom:10px;right:10px;z-index:9999;' +
        'background:#fbbf24;color:#0b132b;padding:6px 12px;border-radius:6px;' +
        'font:600 13px Segoe UI,Arial,sans-serif;box-shadow:0 2px 6px rgba(0,0,0,.3);';
      document.body.appendChild(bar);
    })
    .catch(function () {});
})();