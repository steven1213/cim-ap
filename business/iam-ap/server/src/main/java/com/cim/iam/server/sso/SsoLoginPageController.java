package com.cim.iam.server.sso;

import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * SSO 轻量登录页（方案 C：登录页从 IAM 控制台 SPA 剥离，由 IAM 后端直接 serve）。
 *
 * <p><b>为何需要</b>：接入 ap（如 rms-ap）自身无登录逻辑，未登录时整页跳转到 IAM 的登录页。
 * 若登录页挂在 IAM 控制台 SPA（5171）内，则登录入口与重型管理控制台同生命周期、且引入了
 * 「已登录会话自动回弹」的循环跳转隐患。本控制器在 IAM <b>后端（8081）</b> 直出一个零框架依赖的
 * HTML 登录页，使登录入口与控制台彻底解耦，且成为<b>所有客户端（RMS、IAM 控制台、未来 MDS/MES）统一的唯一登录页</b>。</p>
 *
 * <p><b>视觉契约</b>：本页的版式/设计令牌与 IAM 控制台（5171）登录页保持一致——左栏品牌+主视觉、右栏表单，
 * 含语言切换。样式以内联 CSS 复刻 {@code styles.css} 的登录页设计令牌，确保两处视觉同源。</p>
 *
 * <p><b>文案契约</b>：页面文案不硬编码，而是<b>运行时拉取同一后端 i18n bundle</b>
 * （{@code GET /api/i18n/messages?lang=}，公开端点），键名与控制台完全一致（{@code iam.login.*}）。
 * 因此控制台改文案不发版时，本页自动同步，且 zh/en 切换与控制台行为一致。拉取失败时回退内置中英词典。</p>
 *
 * <p><b>登录契约</b>：先 {@code GET /api/v1/login/salt} 取客户端盐，浏览器内
 * PBKDF2WithHmacSHA256 第一层派生（明文口令不出浏览器），再 {@code POST /api/v1/login}；
 * 成功后整页跳转 {@code redirect?token=...&refreshToken=...}，由接入 ap 的 /sso 回调落地。</p>
 *
 * <p><b>安全</b>：本端点无令牌即可访问（安全链 {@code anyRequest().permitAll()}，过滤器对无令牌请求直接放行），
 * 其依赖的 {@code /api/v1/login}、{@code /api/v1/login/salt}、{@code /api/i18n/messages} 同为公开端点。
 * 页面同源（8081）调用上述接口，不涉及跨域。{@code crypto.subtle} 要求安全上下文——localhost 视为安全上下文，生产须 HTTPS。</p>
 */
@RestController
public class SsoLoginPageController {

    private static final String LOGIN_HTML = """
            <!doctype html>
            <html lang="zh-CN">
            <head>
            <meta charset="utf-8"/>
            <meta name="viewport" content="width=device-width, initial-scale=1"/>
            <title>CIM 统一登录</title>
            <style>
            :root{
              --font-sans:'Inter',-apple-system,BlinkMacSystemFont,'Segoe UI','PingFang SC','Microsoft YaHei',sans-serif;
              --fs-xs:12px; --fs-sm:12.5px; --fs-base:13px; --fs-xl:20px;
              --bg:#eef1f6; --panel:#fff; --panel-3:#eceff5; --line-strong:#ccd4e2;
              --text:#0d1626; --text-2:#46536b; --muted:#7d8899;
              --primary:#1f5fe0; --primary-d:#1749b4; --primary-ring:rgba(31,95,224,.2);
              --err:#cf2f24; --err-soft:#fcebe9;
              --radius-sm:6px; --ring:0 0 0 3px var(--primary-ring);
            }
            *{box-sizing:border-box;}
            html,body{height:100%;}
            body{margin:0;font-family:var(--font-sans);font-size:var(--fs-base);line-height:1.5;color:var(--text);background:var(--bg);}
            .login-page{min-height:100vh;display:grid;grid-template-columns:minmax(0,1.02fr) minmax(0,.98fr);}
            .login-visual{position:relative;display:flex;flex-direction:column;justify-content:space-between;padding:34px 42px;color:#dfe7f6;background:radial-gradient(110% 110% at 6% 4%,#1e459f 0%,#142a63 40%,#080e1c 100%);overflow:hidden;}
            .login-visual::after{content:'';position:absolute;right:-140px;bottom:-170px;width:420px;height:420px;border-radius:50%;background:radial-gradient(circle,rgba(91,75,216,.45),transparent 64%);pointer-events:none;}
            .lg-brand{display:flex;align-items:center;gap:10px;font-size:var(--fs-base);font-weight:650;color:#fff;}
            .logo{width:34px;height:34px;border-radius:8px;background:#fff;color:#1e459f;display:flex;align-items:center;justify-content:center;font-weight:700;letter-spacing:.02em;}
            .login-hero{position:relative;z-index:1;max-width:430px;}
            .login-hero h2{margin:0 0 10px;font-size:24px;line-height:1.3;font-weight:700;letter-spacing:-.015em;color:#fff;}
            .login-hero p{margin:0 0 20px;font-size:var(--fs-sm);line-height:1.7;color:rgba(223,231,246,.74);}
            .login-points{list-style:none;margin:0;padding:0;display:flex;flex-direction:column;gap:10px;}
            .login-points li{display:flex;align-items:center;gap:9px;font-size:var(--fs-xs);color:rgba(223,231,246,.9);}
            .login-points svg{flex:0 0 auto;color:#8fb2ff;}
            .lg-foot{position:relative;z-index:1;font-family:monospace;font-size:10px;letter-spacing:.04em;color:rgba(223,231,246,.5);}
            .login-panel{position:relative;display:flex;align-items:center;justify-content:center;padding:32px 22px;background:var(--bg);}
            .login-lang{position:absolute;top:18px;right:22px;display:flex;gap:6px;}
            .login-lang button{font-family:var(--font-sans);font-size:var(--fs-xs);padding:4px 9px;border:1px solid var(--line-strong);background:var(--panel);color:var(--muted);border-radius:999px;cursor:pointer;}
            .login-lang button.active{background:var(--primary);border-color:var(--primary);color:#fff;}
            .login-card{width:100%;max-width:348px;}
            .login-card h1{font-size:var(--fs-xl);font-weight:700;letter-spacing:-.015em;margin:0;}
            .login-card .lead{margin:5px 0 0;font-size:var(--fs-xs);color:var(--muted);}
            .login-card form{margin-top:18px;}
            .field{margin-top:14px;}
            .field-label{display:block;font-size:10px;font-weight:650;letter-spacing:.08em;text-transform:uppercase;color:var(--muted);margin-bottom:6px;}
            input{width:100%;padding:7px 10px;font-family:inherit;font-size:var(--fs-sm);color:var(--text);background:var(--panel);border:1px solid var(--line-strong);border-radius:var(--radius-sm);transition:border-color .14s ease,box-shadow .14s ease;}
            input:focus{outline:none;border-color:var(--primary);box-shadow:var(--ring);}
            .pwd-wrap{position:relative;}
            .pwd-wrap input{padding-right:38px;}
            .pwd-toggle{position:absolute;right:4px;bottom:3px;width:30px;height:30px;padding:0;border:none;background:transparent;color:var(--muted);cursor:pointer;font-size:15px;}
            .login-row{display:flex;align-items:center;justify-content:space-between;gap:10px;margin-top:12px;}
            .check{display:inline-flex;align-items:center;gap:7px;font-size:var(--fs-xs);color:var(--text-2);cursor:pointer;user-select:none;white-space:nowrap;}
            .check input[type=checkbox]{appearance:none;-webkit-appearance:none;position:relative;width:14px;height:14px;padding:0;margin:0;border:1px solid var(--line-strong);border-radius:3px;background:var(--panel);cursor:pointer;flex:0 0 auto;transition:.14s ease;}
            .check input[type=checkbox]:checked{background:var(--primary);border-color:var(--primary);}
            .check input[type=checkbox]:checked::after{content:'';position:absolute;left:4px;top:1.5px;width:4px;height:7px;border:solid #fff;border-width:0 2px 2px 0;transform:rotate(45deg);}
            .caps-hint{margin:6px 0 0;font-size:var(--fs-xs);color:var(--warn);}
            .alert{display:none;align-items:flex-start;gap:8px;padding:9px 12px;border-radius:var(--radius-sm);font-size:var(--fs-xs);border:1px solid transparent;margin-top:12px;}
            .alert.err{color:var(--err);background:var(--err-soft);border-color:rgba(207,47,36,.24);}
            .hint{margin:9px 0 0;font-size:var(--fs-xs);color:var(--muted);line-height:1.6;}
            button.submit{width:100%;margin-top:16px;padding:9px;font-family:inherit;font-size:var(--fs-base);font-weight:600;color:#fff;background:var(--primary);border:1px solid transparent;border-radius:var(--radius-sm);cursor:pointer;transition:.14s ease;}
            button.submit:hover:not(:disabled){background:var(--primary-d);}
            button.submit:disabled{opacity:.5;cursor:not-allowed;}
            @media (max-width:900px){
              .login-page{grid-template-columns:minmax(0,1fr);}
              .login-visual{display:none;}
            }
            </style>
            </head>
            <body>
            <div class="login-page">
              <section class="login-visual">
                <div class="lg-brand"><div class="logo">IA</div><span id="brand">CIM IAM</span></div>
                <div class="login-hero">
                  <h2 id="heroTitle">统一身份认证</h2>
                  <p id="heroDesc">一个身份，贯通所有 CIM 应用。</p>
                  <ul class="login-points">
                    <li><svg width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"><path d="M12 3l7 3v5c0 4-3 7-7 9-4-2-7-5-7-9V6l7-3z"/></svg><span id="ptRs">RS256 签名令牌</span></li>
                    <li><svg width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"><circle cx="8" cy="15" r="4"/><path d="M10.8 12.2L20 3M16 3h4v4"/></svg><span id="ptPb">PBKDF2 口令派生</span></li>
                    <li><svg width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"><rect x="3" y="3" width="7" height="7" rx="1"/><rect x="14" y="3" width="7" height="7" rx="1"/><rect x="3" y="14" width="7" height="7" rx="1"/><rect x="14" y="14" width="7" height="7" rx="1"/></svg><span id="ptAd">基于应用的准入控制</span></li>
                  </ul>
                </div>
                <div class="lg-foot">CIM-AP / IAM-AP · © 2026</div>
              </section>
              <section class="login-panel">
                <div class="login-lang">
                  <button id="langZh" class="active" data-lang="zh-CN">中文</button>
                  <button id="langEn" data-lang="en-US">EN</button>
                </div>
                <div class="login-card">
                  <h1 id="title">登录</h1>
                  <p class="lead" id="lead">使用统一身份继续</p>
                  <form id="f" onsubmit="return false;">
                    <div class="field">
                      <label class="field-label" for="u" id="lblUser">用户名</label>
                      <input id="u" autocomplete="username" placeholder="admin"/>
                    </div>
                    <div class="field">
                      <label class="field-label" for="p" id="lblPwd">口令</label>
                      <div class="pwd-wrap">
                        <input id="p" type="password" autocomplete="current-password" placeholder="password"/>
                        <button type="button" class="pwd-toggle" id="pw" title="显示口令">👁</button>
                      </div>
                    </div>
                    <div class="login-row">
                      <label class="check"><input type="checkbox" id="rm"/> <span id="lblRem">记住用户名</span></label>
                    </div>
                    <div class="alert err" id="e"><span id="eMsg"></span></div>
                    <button type="submit" class="submit" id="b">登录</button>
                  </form>
                  <p class="hint" id="hint">CIM-AP / IAM-AP · 统一身份认证</p>
                </div>
              </section>
            </div>
            <script>
            (function(){
              var q = new URLSearchParams(location.search);
              var app = q.get('app') || '';
              var redirect = q.get('redirect') || '';
              var ROUNDS = 100000;
              var currentLang = 'zh-CN';

              var MAP = {
                'iam.login.brand':'brand',
                'iam.login.hero.title':'heroTitle',
                'iam.login.hero.desc':'heroDesc',
                'iam.login.point.rs256':'ptRs',
                'iam.login.point.pbkdf2':'ptPb',
                'iam.login.point.admission':'ptAd',
                'iam.login.title':'title',
                'iam.login.lead':'lead',
                'iam.login.username':'lblUser',
                'iam.login.password':'lblPwd',
                'iam.login.remember':'lblRem',
                'iam.login.submit':'b',
                'iam.login.hint':'hint'
              };
              var FALLBACK = {
                'zh-CN':{
                  'iam.login.brand':'CIM IAM','iam.login.hero.title':'统一身份认证',
                  'iam.login.hero.desc':'一个身份，贯通所有 CIM 应用。',
                  'iam.login.point.rs256':'RS256 签名令牌','iam.login.point.pbkdf2':'PBKDF2 口令派生',
                  'iam.login.point.admission':'基于应用的准入控制',
                  'iam.login.title':'登录','iam.login.lead':'使用统一身份继续',
                  'iam.login.username':'用户名','iam.login.password':'口令',
                  'iam.login.passwordPlaceholder':'password','iam.login.remember':'记住用户名',
                  'iam.login.submit':'登录','iam.login.submitting':'登录中…',
                  'iam.login.hint':'CIM-AP / IAM-AP · 统一身份认证',
                  'iam.login.showPwd':'显示口令','iam.login.hidePwd':'隐藏口令',
                  'iam.login.failed':'登录失败，请重试','iam.login.capsOn':'大写锁定已开启'
                },
                'en-US':{
                  'iam.login.brand':'CIM IAM','iam.login.hero.title':'Unified Identity',
                  'iam.login.hero.desc':'One identity for all CIM applications.',
                  'iam.login.point.rs256':'RS256 signed tokens','iam.login.point.pbkdf2':'PBKDF2 password derivation',
                  'iam.login.point.admission':'Per-application admission',
                  'iam.login.title':'Sign in','iam.login.lead':'Continue with your identity',
                  'iam.login.username':'Username','iam.login.password':'Password',
                  'iam.login.passwordPlaceholder':'password','iam.login.remember':'Remember username',
                  'iam.login.submit':'Sign in','iam.login.submitting':'Signing in…',
                  'iam.login.hint':'CIM-AP / IAM-AP · Unified identity',
                  'iam.login.showPwd':'Show password','iam.login.hidePwd':'Hide password',
                  'iam.login.failed':'Sign in failed, please retry','iam.login.capsOn':'Caps Lock is on'
                }
              };

              var bundle = {};
              var uEl = document.getElementById('u');
              var pEl = document.getElementById('p');
              var bEl = document.getElementById('b');
              var rmEl = document.getElementById('rm');
              var pwEl = document.getElementById('pw');
              var eEl = document.getElementById('e');
              var eMsg = document.getElementById('eMsg');
              var langZh = document.getElementById('langZh');
              var langEn = document.getElementById('langEn');

              function txt(key){ return bundle[key] || (FALLBACK[currentLang] && FALLBACK[currentLang][key]) || (FALLBACK['zh-CN'][key]); }
              function render(){
                for (var k in MAP){ var el = document.getElementById(MAP[k]); if (el && bundle[k]) el.textContent = bundle[k]; }
                var ph = txt('iam.login.passwordPlaceholder'); if (ph) pEl.placeholder = ph;
                if (app) document.getElementById('lead').textContent = '登录以访问 ' + app;
                bEl.textContent = txt('iam.login.submit');
              }
              function updateLangButtons(){
                langZh.classList.toggle('active', currentLang === 'zh-CN');
                langEn.classList.toggle('active', currentLang === 'en-US');
              }
              function loadLang(lang){
                return fetch('/api/i18n/messages?lang=' + encodeURIComponent(lang), {credentials:'same-origin'})
                  .then(function(r){ return r.json(); })
                  .then(function(j){
                    var m = j && j.data && j.data.messages;
                    bundle = (m && Object.keys(m).length) ? m : (FALLBACK[lang] || FALLBACK['zh-CN']);
                    render();
                  })
                  .catch(function(){ bundle = FALLBACK[lang] || FALLBACK['zh-CN']; render(); });
              }

              // 记住用户名（仅用户名，口令从不落盘）
              var saved = null;
              try { saved = localStorage.getItem('iam-last-user'); } catch(e){}
              if (saved) { uEl.value = saved; pEl.focus(); } else { uEl.focus(); }

              function toHex(buf){ var s=''; var u=new Uint8Array(buf); for (var i=0;i<u.length;i++) s += u[i].toString(16).padStart(2,'0'); return s; }
              function derive(password, salt, rounds){
                var enc = new TextEncoder();
                return crypto.subtle.importKey('raw', enc.encode(password), {name:'PBKDF2'}, false, ['deriveBits'])
                  .then(function(km){
                    return crypto.subtle.deriveBits({name:'PBKDF2', salt: enc.encode(salt), iterations:rounds, hash:'SHA-256'}, km, 256);
                  }).then(function(bits){ return toHex(bits); });
              }
              function showErr(msg){ eMsg.textContent = msg || txt('iam.login.failed'); eEl.style.display = 'flex'; }

              pwEl.addEventListener('click', function(){
                var shown = pEl.type === 'text';
                pEl.type = shown ? 'password' : 'text';
                pwEl.textContent = shown ? '👁' : '🙈';
                pwEl.title = shown ? txt('iam.login.showPwd') : txt('iam.login.hidePwd');
              });

              document.getElementById('f').addEventListener('submit', function(){
                var username = uEl.value.trim();
                var password = pEl.value;
                eEl.style.display = 'none'; eMsg.textContent = '';
                if (!username || !password){ showErr(''); return; }
                bEl.disabled = true; bEl.textContent = txt('iam.login.submitting');
                fetch('/api/v1/login/salt?username=' + encodeURIComponent(username), {credentials:'same-origin'})
                  .then(function(r){ return r.json(); })
                  .then(function(salBody){
                    var clientSalt = (salBody && salBody.data && salBody.data.clientSalt) ? salBody.data.clientSalt : '';
                    return derive(password, clientSalt, ROUNDS).then(function(clientHash){
                      return fetch('/api/v1/login', {
                        method:'POST', headers:{'Content-Type':'application/json'}, credentials:'same-origin',
                        body: JSON.stringify({username:username, credential:clientHash, clientSalt:clientSalt})
                      }).then(function(loginRes){ return loginRes.json().then(function(b){ return {ok:loginRes.ok, b:b}; }); })
                      .then(function(res){
                        if (!res.ok || res.b.code !== 0) throw new Error((res.b && res.b.msg) || txt('iam.login.failed'));
                        var data = res.b.data;
                        try { if (rmEl.checked) localStorage.setItem('iam-last-user', username); else localStorage.removeItem('iam-last-user'); } catch(e){}
                        if (!redirect) { location.assign('/'); return; }
                        var sep = redirect.indexOf('?') >= 0 ? '&' : '?';
                        location.assign(redirect + sep + 'token=' + encodeURIComponent(data.token)
                          + '&refreshToken=' + encodeURIComponent(data.refreshToken));
                      });
                    });
                  })
                  .catch(function(e){ showErr((e && e.message) || txt('iam.login.failed')); bEl.disabled = false; bEl.textContent = txt('iam.login.submit'); });
              });

              langZh.addEventListener('click', function(){ if (currentLang!=='zh-CN'){ currentLang='zh-CN'; updateLangButtons(); loadLang(currentLang); } });
              langEn.addEventListener('click', function(){ if (currentLang!=='en-US'){ currentLang='en-US'; updateLangButtons(); loadLang(currentLang); } });

              loadLang(currentLang);
            })();
            </script>
            </body>
            </html>
            """;

    @GetMapping(value = "/login", produces = MediaType.TEXT_HTML_VALUE)
    public String loginPage() {
        return LOGIN_HTML;
    }
}
