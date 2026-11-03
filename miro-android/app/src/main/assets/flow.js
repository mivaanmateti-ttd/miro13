function miroStep(allowPay, nativeTap) {
  var vis = function (e) {
    var r = e.getBoundingClientRect(), s = getComputedStyle(e);
    return r.width > 4 && r.height > 4 && s.visibility !== 'hidden' && s.display !== 'none';
  };
  var label = function (e) {
    return ((e.innerText || e.value || e.getAttribute('aria-label') || '') + '').replace(/\s+/g, ' ').trim();
  };
  var stop = function (reason) { return { action: 'stop', reason: reason }; };

  // Signature of this page, so the caller can tell when the next page has appeared.
  var sig = location.href + '|' + ((document.body && document.body.innerText) || '').slice(0, 300);

  var inputs = [].slice.call(document.querySelectorAll('input,select,textarea')).filter(vis);

  // Human checks always stay with the person.
  var humanField = inputs.some(function (e) {
    return /captcha|otp|one.?time|verification.?code|security.?code/i.test(
      [e.name, e.id, e.placeholder, e.getAttribute('aria-label')].join(' '));
  });
  var humanBox = [].slice.call(document.querySelectorAll(
    'iframe[src*="recaptcha"],iframe[src*="hcaptcha"],.g-recaptcha,.h-captcha')).some(vis);
  if (humanField || humanBox) return stop('Please complete the CAPTCHA / OTP yourself, then tap Fill + Continue again.');

  // Required boxes that are still empty, invalid boxes, unticked required consent.
  var empty = inputs.filter(function (e) {
    var t = (e.type || '').toLowerCase();
    if (t === 'hidden' || t === 'checkbox' || t === 'radio' || t === 'file') return false;
    var req = e.required || e.getAttribute('aria-required') === 'true';
    return req && !String(e.value || '').trim();
  });
  if (empty.length) return stop('A required field is empty. Fill it yourself, then tap Fill + Continue again.');
  if (inputs.some(function (e) { return e.getAttribute('aria-invalid') === 'true'; }))
    return stop('A field looks invalid. Please correct it, then tap Fill + Continue again.');
  if (inputs.some(function (e) { return e.type === 'checkbox' && e.required && !e.checked; }))
    return stop('Please tick the required consent box yourself, then tap Fill + Continue again.');

  var buttons = [].slice.call(document.querySelectorAll(
    'button,input[type=submit],input[type=button],a[role=button],[role=button]')).filter(function (e) {
    return vis(e) && !e.disabled && e.getAttribute('aria-disabled') !== 'true';
  });
  var payRe = /\bpay\b|payment|place (your )?order|\bbuy\b|purchase|checkout|donate/i;
  var nextRe = /\b(continue|next|proceed)\b/i;
  var submitRe = /^(submit|confirm|register|apply|send|book( now)?)(\s+\w+){0,3}[\s\u203a>\u00bb\u2192]*$/i;

  var skipRe = /shopping|browsing|as guest|later|cancel|\bback\b|previous|\bedit\b/i;
  var payBtnRe = /^(pay|make (a )?payment|proceed to pay|pay securely)\b/i;
  var pick = function (re, max) {
    return buttons.filter(function (e) {
      var l = label(e);
      return l && l.length < max && re.test(l) && !skipRe.test(l) && (allowPay || !payRe.test(l));
    })[0];
  };
  var pay = allowPay ? pick(payBtnRe, 40) : null;
  var next = pick(nextRe, 40);
  var sub = pick(submitRe, 40);
  if (sub === next) sub = null;

  var tapped = [];
  var targets = [];
  var tap = function (e) {
    var r = e.getBoundingClientRect();
    if (r.top < 0 || r.bottom > window.innerHeight) {
      e.scrollIntoView({ block: 'center' });
      r = e.getBoundingClientRect();
    }
    tapped.push(label(e));
    if (nativeTap) {
      // Report where to tap so the app can send a real finger tap (sites ignore scripted clicks for payments).
      var vv = window.visualViewport;
      var sc = (vv && vv.scale) || 1, ox = (vv && vv.offsetLeft) || 0, oy = (vv && vv.offsetTop) || 0;
      var dpr = window.devicePixelRatio || 1;
      targets.push({ x: (r.left + r.width / 2 - ox) * sc * dpr, y: (r.top + r.height / 2 - oy) * sc * dpr });
    } else {
      e.click();
    }
  };
  // Second page: a Pay button wins over everything else; otherwise Continue / Submit.
  if (pay) tap(pay);
  else {
    if (next) tap(next);
    if (sub) tap(sub);
  }
  if (tapped.length) return { action: nativeTap ? 'tap' : 'clicked', reason: tapped.join('\u201d and \u201c'), sig: sig, targets: targets };

  if (buttons.some(function (e) { return payRe.test(label(e)); }))
    return stop('Reached the payment step. Please complete the payment yourself.');
  return stop('No Continue or Submit button found on this page.');
}
