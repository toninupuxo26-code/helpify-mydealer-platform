(function () {
  'use strict';

  var header = document.querySelector('[data-header]');
  var menuButton = document.querySelector('[data-menu-toggle]');
  var menu = document.querySelector('[data-menu]');

  function setMenu(open) {
    if (!menuButton || !menu) return;
    menuButton.setAttribute('aria-expanded', open ? 'true' : 'false');
    menu.classList.toggle('is-open', open);
    document.body.classList.toggle('menu-open', open);
  }

  if (menuButton && menu) {
    menuButton.addEventListener('click', function () {
      setMenu(menuButton.getAttribute('aria-expanded') !== 'true');
    });

    menu.addEventListener('click', function (event) {
      if (event.target.closest('a')) setMenu(false);
    });

    document.addEventListener('keydown', function (event) {
      if (event.key === 'Escape') setMenu(false);
    });
  }

  function updateHeader() {
    if (header) header.classList.toggle('is-scrolled', window.scrollY > 110);
  }
  updateHeader();
  window.addEventListener('scroll', updateHeader, { passive: true });

  var reducedMotion = window.matchMedia && window.matchMedia('(prefers-reduced-motion: reduce)').matches;
  var items = document.querySelectorAll('.reveal');
  if ('IntersectionObserver' in window && !reducedMotion) {
    var observer = new IntersectionObserver(function (entries) {
      entries.forEach(function (entry) {
        if (entry.isIntersecting) {
          entry.target.classList.add('is-visible');
          observer.unobserve(entry.target);
        }
      });
    }, { threshold: 0.12, rootMargin: '0px 0px -30px' });
    items.forEach(function (item) { observer.observe(item); });
  } else {
    items.forEach(function (item) { item.classList.add('is-visible'); });
  }

  var cookiePanel = document.querySelector('[data-cookie-panel]');
  var cookieButton = document.querySelector('[data-cookie-accept]');
  var cookieKey = 'helpify_cookie_consent_v1';
  try {
    if (cookiePanel && localStorage.getItem(cookieKey) === 'accepted') cookiePanel.hidden = true;
  } catch (error) {
    // Local storage may be unavailable in strict privacy mode.
  }
  if (cookiePanel && cookieButton) {
    cookieButton.addEventListener('click', function () {
      try { localStorage.setItem(cookieKey, 'accepted'); } catch (error) {}
      cookiePanel.hidden = true;
    });
  }
}());
