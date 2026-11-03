(function () {
  if (window.__miroBanner) return;
  window.__miroBanner = true;
  var appRe = /(get now|install|open in app|download (the |our )?app|on the google play|get it on|use (our|the) app|app store)/i;
  var closeRe = /^(x|\u00d7|\u2715|\u2716|\u2717|close|dismiss|no thanks|not now)$/i;
  var vis = function (e) {
    var r = e.getBoundingClientRect(), s = getComputedStyle(e);
    return r.width > 2 && r.height > 2 && s.display !== 'none' && s.visibility !== 'hidden';
  };
  function tryClose() {
    var els = document.querySelectorAll('div,section,aside,header,nav,[role=dialog],[class*=banner],[class*=app],[id*=banner]');
    var n = Math.min(els.length, 2500);
    for (var i = 0; i < n; i++) {
      var el = els[i];
      if (!vis(el)) continue;
      var r = el.getBoundingClientRect();
      if (r.height > 240 || r.width < 200) continue;
      if (!(r.top < 160 || r.bottom > window.innerHeight - 160)) continue;
      var t = (el.innerText || '').trim();
      if (!t || t.length > 220 || !appRe.test(t)) continue;
      var kids = el.querySelectorAll('button,a,span,div,i,svg,img,[role=button]');
      var close = null;
      for (var k = 0; k < kids.length; k++) {
        var c = kids[k];
        if (!vis(c)) continue;
        var lab = ((c.innerText || '') + '').trim();
        var attr = ((c.getAttribute('aria-label') || '') + ' ' + (c.getAttribute('title') || '') + ' ' + (typeof c.className === 'string' ? c.className : '') + ' ' + (c.id || ''));
        if (closeRe.test(lab) || /close|dismiss|cross|times/i.test(attr)) { close = c; break; }
      }
      if (close) close.click();
      el.style.setProperty('display', 'none', 'important');   // make sure it is really gone
      return true;
    }
    return false;
  }
  var tries = 0;
  var iv = setInterval(function () {
    if (tryClose() || ++tries > 60) clearInterval(iv);
  }, 200);
})();
