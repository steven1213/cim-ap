package com.cim.iam.server.sso;

import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * SSO 轻量登录页（方案 C：登录页从 IAM 控制台 SPA 剥离，由 IAM 后端直接 serve）。
 *
 * <p><b>为什么需要它</b>：接入 ap（如 rms-ap）自身无登录逻辑，未登录时整页跳转到 IAM 的登录页。
 * 若登录页挂在 IAM 控制台 SPA（5171）内，则登录入口与重型管理控制台同生命周期、且引入了
 * 「已登录会话自动回弹」的循环跳转隐患。本控制器在 IAM <b>后端（8081）</b> 直出一个零框架依赖的
 * HTML 登录页，使登录入口与控制台彻底解耦。</p>
 *
 * <p><b>契约</b>：页面读取 URL 的 {@code app}（接入码，仅用于展示）与 {@code redirect}（回跳地址）；
 * 登录流程与 IAM Web 控制台完全一致——先 {@code GET /api/v1/login/salt} 取客户端盐，浏览器内
 * PBKDF2WithHmacSHA256 第一层派生（明文口令不出浏览器），再 {@code POST /api/v1/login}；
 * 成功后整页跳转 {@code redirect?token=...&refreshToken=...}，由接入 ap 的 /sso 回调落地。</p>
 *
 * <p><b>安全</b>：本端点无令牌即可访问（安全链 {@code anyRequest().permitAll()}，过滤器对无令牌请求直接放行），
 * 其依赖的 {@code /api/v1/login} 与 {@code /api/v1/login/salt} 同为公开端点。页面同源（8081）调用
 * 上述接口，不涉及跨域。{@code crypto.subtle} 要求安全上下文——localhost 视为安全上下文，生产须 HTTPS。</p>
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
              :root { --bg:#0b1220; --card:#111c33; --fg:#e6edf6; --muted:#93a4bd; --accent:#378ADD; --err:#E24B4A; }
              * { box-sizing:border-box; }
              body { margin:0; min-height:100vh; display:flex; align-items:center; justify-content:center;
                     background:linear-gradient(135deg,#0b1220,#13233f); color:var(--fg);
                     font-family: system-ui, "Segoe UI", "PingFang SC", "Microsoft YaHei", sans-serif; }
              .card { width:360px; max-width:92vw; background:var(--card); border:1px solid #21304d;
                      border-radius:14px; padding:28px 26px; box-shadow:0 10px 40px rgba(0,0,0,.35); }
              .brand { display:flex; align-items:center; gap:10px; margin-bottom:18px; }
              .logo { width:36px; height:36px; border-radius:9px; background:var(--accent); color:#fff;
                      display:flex; align-items:center; justify-content:center; font-weight:700; }
              h1 { font-size:18px; margin:0; }
              .sub { color:var(--muted); font-size:13px; margin:2px 0 18px; }
              label { display:block; font-size:13px; color:var(--muted); margin:12px 0 6px; }
              input { width:100%; padding:10px 12px; border-radius:9px; border:1px solid #2a3a59;
                      background:#0c1626; color:var(--fg); font-size:14px; }
              input:focus { outline:none; border-color:var(--accent); }
              button { margin-top:18px; width:100%; padding:11px; border:0; border-radius:9px;
                       background:var(--accent); color:#fff; font-size:14px; font-weight:600; cursor:pointer; }
              button:disabled { opacity:.6; cursor:default; }
              .err { margin-top:14px; color:var(--err); font-size:13px; min-height:18px; }
              .hint { margin-top:14px; color:var(--muted); font-size:12px; text-align:center; }
            </style>
            </head>
            <body>
              <form class="card" id="f" onsubmit="return false;">
                <div class="brand"><div class="logo">IA</div><div><h1>CIM 统一登录</h1></div></div>
                <div class="sub" id="sub">请登录以继续</div>
                <label for="u">用户名</label>
                <input id="u" autocomplete="username" placeholder="admin"/>
                <label for="p">口令</label>
                <input id="p" type="password" autocomplete="current-password" placeholder="password"/>
                <button id="b" type="submit">登录</button>
                <div class="err" id="e"></div>
                <div class="hint">CIM-AP / IAM-AP · 统一身份认证</div>
              </form>
            <script>
              var q = new URLSearchParams(location.search);
              var app = q.get('app') || '';
              var redirect = q.get('redirect') || '';
              if (app) document.getElementById('sub').textContent = '登录以访问 ' + app;
              var btn = document.getElementById('b');
              var errEl = document.getElementById('e');
              var ROUNDS = 100000;
              function toHex(buf){ var s=''; var u=new Uint8Array(buf);
                for (var i=0;i<u.length;i++) s += u[i].toString(16).padStart(2,'0'); return s; }
              function derive(password, salt, rounds){
                var enc = new TextEncoder();
                return crypto.subtle.importKey('raw', enc.encode(password), {name:'PBKDF2'}, false, ['deriveBits'])
                  .then(function(km){
                    return crypto.subtle.deriveBits({name:'PBKDF2', salt: enc.encode(salt), iterations:rounds, hash:'SHA-256'}, km, 256);
                  }).then(function(bits){ return toHex(bits); });
              }
              document.getElementById('f').addEventListener('submit', function(){
                var username = document.getElementById('u').value.trim();
                var password = document.getElementById('p').value;
                errEl.textContent = '';
                if (!username || !password){ errEl.textContent = '请输入用户名与口令'; return; }
                btn.disabled = true; btn.textContent = '登录中…';
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
                        if (!res.ok || res.b.code !== 0) throw new Error((res.b && res.b.msg) || '登录失败');
                        var data = res.b.data;
                        if (!redirect) { location.assign('/'); return; }
                        var sep = redirect.indexOf('?') >= 0 ? '&' : '?';
                        location.assign(redirect + sep + 'token=' + encodeURIComponent(data.token)
                          + '&refreshToken=' + encodeURIComponent(data.refreshToken));
                      });
                    });
                  })
                  .catch(function(e){ errEl.textContent = (e && e.message) || '登录失败'; btn.disabled = false; btn.textContent = '登录'; });
              });
            </script>
            </body>
            </html>
            """;

    @GetMapping(value = "/login", produces = MediaType.TEXT_HTML_VALUE)
    public String loginPage() {
        return LOGIN_HTML;
    }
}
