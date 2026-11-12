/* ============================================================================
 * CIM 统一登录页脚本（IAM 后端直出，见 SsoLoginPageController）
 *
 * 契约：
 *   1) 文案不硬编码——运行时拉同一后端的 i18n bundle（GET /api/i18n/messages?lang=），
 *      键名与控制台完全一致（iam.login.*）；拉取失败才用下面的内置词典。
 *      内置词典**只留 zh-CN**：能拉不到 bundle 时，/api/v1/login 基本也够呛，
 *      再维护一份英文副本只会与 i18n 库里的译文各自漂移。
 *   2) 口令两层派生——先 GET /api/v1/login/salt 取客户端盐，浏览器内 PBKDF2
 *      第一层派生，明文口令不出浏览器；再 POST /api/v1/login。
 *   3) 成功后带 token/refreshToken 整页跳 redirect（各 ap 的 /sso 回调落地）；
 *      没有 redirect 时停在「已登录」提示，不跳到后端根路径（那是 404）。
 *
 * 零依赖：不引任何框架/构建产物，保证 React 挂掉时登录仍可用。
 * ========================================================================== */
(function () {
  'use strict';

  var q = new URLSearchParams(location.search);
  var app = q.get('app') || '';
  var redirect = q.get('redirect') || '';
  var ROUNDS = 100000;
  var currentLang = 'zh-CN';

  // DOM 元素 id → i18n 键。新增文案时两处都要加（本表 + IamConsoleCatalog）。
  var MAP = {
    'iam.login.brand': 'brand',
    'iam.login.hero.title': 'heroTitle',
    'iam.login.hero.desc': 'heroDesc',
    'iam.login.point.rs256': 'ptRs',
    'iam.login.point.pbkdf2': 'ptPb',
    'iam.login.point.admission': 'ptAd',
    'iam.login.title': 'title',
    'iam.login.lead': 'lead',
    'iam.login.username': 'lblUser',
    'iam.login.password': 'lblPwd',
    'iam.login.remember': 'lblRem',
    'iam.login.submit': 'b',
    'iam.login.hint': 'hint',
    'iam.login.signedIn': 'okTitle',
    'iam.login.signedInHint': 'okMsg'
  };

  var FALLBACK = {
    'iam.login.brand': 'CIM IAM',
    'iam.login.hero.title': '统一身份认证',
    'iam.login.hero.desc': '一个身份，贯通所有 CIM 应用。',
    'iam.login.point.rs256': 'RS256 签名令牌',
    'iam.login.point.pbkdf2': 'PBKDF2 口令派生',
    'iam.login.point.admission': '基于应用的准入控制',
    'iam.login.title': '登录',
    'iam.login.lead': '使用统一身份继续',
    'iam.login.username': '用户名',
    'iam.login.password': '口令',
    'iam.login.passwordPlaceholder': 'password',
    'iam.login.remember': '记住用户名',
    'iam.login.submit': '登录',
    'iam.login.submitting': '登录中…',
    'iam.login.hint': '口令在浏览器内完成第一层 PBKDF2 派生，明文不会上传到服务端。',
    'iam.login.showPwd': '显示口令',
    'iam.login.hidePwd': '隐藏口令',
    'iam.login.failed': '登录失败，请重试',
    'iam.login.forApp': '登录以访问 {{app}}',
    'iam.login.signedIn': '已登录',
    'iam.login.signedInHint': '请从您的应用入口继续。'
  };

  var bundle = {};
  var uEl = document.getElementById('u');
  var pEl = document.getElementById('p');
  var bEl = document.getElementById('b');
  var rmEl = document.getElementById('rm');
  var pwEl = document.getElementById('pw');
  var eEl = document.getElementById('e');
  var eMsg = document.getElementById('eMsg');
  var fEl = document.getElementById('f');
  var okEl = document.getElementById('ok');
  var langZh = document.getElementById('langZh');
  var langEn = document.getElementById('langEn');
  var submitting = false;

  function txt(key) {
    return bundle[key] || FALLBACK[key] || key;
  }

  function render() {
    for (var k in MAP) {
      var el = document.getElementById(MAP[k]);
      if (el && bundle[k]) el.textContent = bundle[k];
    }
    pEl.placeholder = txt('iam.login.passwordPlaceholder');
    bEl.textContent = txt('iam.login.submit');
    pwEl.title = txt('iam.login.showPwd');
    pwEl.setAttribute('aria-label', txt('iam.login.showPwd'));
    if (app) {
      document.getElementById('lead').textContent = txt('iam.login.forApp').replace('{{app}}', app);
    }
  }

  function updateLangButtons() {
    langZh.classList.toggle('active', currentLang === 'zh-CN');
    langEn.classList.toggle('active', currentLang === 'en-US');
  }

  function loadLang(lang) {
    currentLang = lang;
    updateLangButtons();
    return fetch('/api/i18n/messages?lang=' + encodeURIComponent(lang), { credentials: 'same-origin' })
      .then(function (r) { return r.json(); })
      .then(function (j) {
        var m = j && j.data && j.data.messages;
        bundle = m && Object.keys(m).length ? m : {};
        render();
      })
      .catch(function () {
        bundle = {};
        render();
      });
  }

  // 记住用户名（仅用户名，口令从不落盘）
  var saved = null;
  try { saved = localStorage.getItem('iam-last-user'); } catch (e) { /* 隐私模式下不可用 */ }
  if (saved) {
    uEl.value = saved;
    pEl.focus();
  } else {
    uEl.focus();
  }

  function toHex(buf) {
    var u = new Uint8Array(buf);
    var s = '';
    for (var i = 0; i < u.length; i++) s += u[i].toString(16).padStart(2, '0');
    return s;
  }

  function derive(password, salt, rounds) {
    var enc = new TextEncoder();
    return crypto.subtle
      .importKey('raw', enc.encode(password), { name: 'PBKDF2' }, false, ['deriveBits'])
      .then(function (km) {
        return crypto.subtle.deriveBits(
          { name: 'PBKDF2', salt: enc.encode(salt), iterations: rounds, hash: 'SHA-256' },
          km,
          256
        );
      })
      .then(toHex);
  }

  function showErr(msg) {
    eMsg.textContent = msg || txt('iam.login.failed');
    eEl.classList.add('on');
  }

  function hideErr() {
    eEl.classList.remove('on');
    eMsg.textContent = '';
  }

  /** 登录成功但调用方没给回跳地址：停在提示态，不把用户甩到后端根路径。 */
  function showSignedIn() {
    fEl.hidden = true;
    document.getElementById('lead').hidden = true;
    document.getElementById('hint').hidden = true;
    okEl.hidden = false;
  }

  pwEl.addEventListener('click', function () {
    var shown = pEl.type === 'text';
    pEl.type = shown ? 'password' : 'text';
    pwEl.textContent = shown ? '👁' : '🙈';
    pwEl.title = txt(shown ? 'iam.login.showPwd' : 'iam.login.hidePwd');
    pwEl.setAttribute('aria-label', pwEl.title);
    pEl.focus();
  });

  fEl.addEventListener('submit', function () {
    if (submitting) return;
    var username = uEl.value.trim();
    var password = pEl.value;
    hideErr();
    if (!username || !password) {
      showErr('');
      return;
    }
    // crypto.subtle 只在安全上下文可用（https 或 localhost）。非安全上下文下
    // 它是 undefined，先判一下，否则抛出的 TypeError 会盖住真正的原因。
    if (!(window.crypto && window.crypto.subtle)) {
      showErr(txt('iam.login.failed'));
      return;
    }

    submitting = true;
    bEl.disabled = true;
    bEl.textContent = txt('iam.login.submitting');

    fetch('/api/v1/login/salt?username=' + encodeURIComponent(username), { credentials: 'same-origin' })
      .then(function (r) { return r.json(); })
      .then(function (saltBody) {
        var clientSalt = saltBody && saltBody.data && saltBody.data.clientSalt ? saltBody.data.clientSalt : '';
        return derive(password, clientSalt, ROUNDS).then(function (clientHash) {
          return fetch('/api/v1/login', {
            method: 'POST',
            headers: { 'Content-Type': 'application/json' },
            credentials: 'same-origin',
            body: JSON.stringify({ username: username, credential: clientHash, clientSalt: clientSalt })
          }).then(function (loginRes) {
            return loginRes.json().then(function (body) { return { ok: loginRes.ok, body: body }; });
          }).then(function (res) {
            if (!res.ok || res.body.code !== 0) {
              throw new Error((res.body && res.body.msg) || txt('iam.login.failed'));
            }
            var data = res.body.data || {};
            try {
              if (rmEl.checked) localStorage.setItem('iam-last-user', username);
              else localStorage.removeItem('iam-last-user');
            } catch (e) { /* 忽略 */ }
            if (!redirect) {
              submitting = false;
              bEl.disabled = false;
              showSignedIn();
              return;
            }
            var sep = redirect.indexOf('?') >= 0 ? '&' : '?';
            location.assign(
              redirect + sep +
              'token=' + encodeURIComponent(data.token) +
              '&refreshToken=' + encodeURIComponent(data.refreshToken)
            );
          });
        });
      })
      .catch(function (e) {
        submitting = false;
        bEl.disabled = false;
        bEl.textContent = txt('iam.login.submit');
        showErr(e && e.message);
      });
  });

  langZh.addEventListener('click', function () { if (currentLang !== 'zh-CN') loadLang('zh-CN'); });
  langEn.addEventListener('click', function () { if (currentLang !== 'en-US') loadLang('en-US'); });

  loadLang(currentLang);
})();
