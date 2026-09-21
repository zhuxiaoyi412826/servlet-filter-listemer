/**
 * 通用前端工具：Ajax 封装（含 Cookie/Session 凭证）、提示、金额格式化、
 * 登录态导航渲染等。原生 JS，无任何前端框架。
 */
const App = (() => {
    /** 上下文路径：Jetty(/) 下为 ''，Tomcat(/restaurant) 部署时自动取到 '/restaurant' */
    const BASE = (function () {
        if (window.CTX !== undefined) return window.CTX;
        const p = location.pathname, i = p.indexOf('/static/');
        return i >= 0 ? p.slice(0, i) : p.replace(/\/+$/, '');
    })();

    /** 统一的 fetch 调用，自动解析 {code,msg,data} 结构 */
    async function request(url, options = {}) {
        const opts = Object.assign({ credentials: 'same-origin' }, options);
        opts.headers = Object.assign({ 'Accept': 'application/json' }, opts.headers || {});
        if (opts.body && typeof opts.body === 'object' && !(opts.body instanceof FormData)) {
            opts.headers['Content-Type'] = 'application/json;charset=UTF-8';
            opts.body = JSON.stringify(opts.body);
        }
        const res = await fetch(BASE + url, opts);
        let json = null;
        try { json = await res.json(); } catch (e) { /* 非 JSON 响应 */ }
        if (res.status === 401) {
            handleUnauthorized(json);
        }
        if (!json) throw new Error('服务未返回有效数据，HTTP ' + res.status);
        if (json.code === 0) return json.data;
        // 业务失败：携带原始响应，便于前端做字段级回显
        const err = new Error(json.msg || '操作失败');
        err.code = json.code;
        err.data = json.data;
        err.response = json;
        throw err;
    }

    function handleUnauthorized(json) {
        const skip = ['/static/login.html', '/static/register.html'];
        const here = location.pathname;
        if (skip.some(p => here.endsWith(p))) return;
        const back = encodeURIComponent(location.pathname + location.search);
        location.href = BASE + '/static/login.html?redirect=' + back;
    }

    const get = (url) => request(url, { method: 'GET' });
    const post = (url, body) => request(url, { method: 'POST', body: body || {} });
    const put = (url, body) => request(url, { method: 'PUT', body: body || {} });
    const del = (url) => request(url, { method: 'DELETE' });

    /** 文件上传（头像、菜品图） */
    function upload(url, file, field = 'file') {
        const fd = new FormData();
        fd.append(field, file);
        return request(url, { method: 'POST', body: fd });
    }

    function toast(msg, type = 'ok', ms = 2400) {
        const box = document.getElementById('toast');
        if (!box) return;
        const el = document.createElement('div');
        el.className = 'toast ' + (type === 'ok' ? 'ok' : type === 'err' ? 'err' : 'warn');
        el.textContent = msg;
        box.appendChild(el);
        setTimeout(() => el.remove(), ms);
    }

    const money = (v) => '¥' + Number(v || 0).toFixed(2);
    const esc = (s) => String(s == null ? '' : s).replace(/[&<>"']/g, c =>
        ({ '&': '&amp;', '<': '&lt;', '>': '&gt;', '"': '&quot;', "'": '&#39;' }[c]));
    const param = (name) => new URLSearchParams(location.search).get(name);

    /** 是否是图片地址（否则视为 emoji 占位图） */
    function isImageUrl(src) {
        return !!src && (/^(https?:)?\/\//.test(src) || src.indexOf('/static/') >= 0);
    }

    /** 渲染图片或 emoji */
    function media(src, cls = 'dish-media') {
        if (isImageUrl(src)) return `<div class="${cls}"><img src="${esc(src)}" alt=""></div>`;
        return `<div class="${cls}">${esc(src || '🍽️')}</div>`;
    }

    /** 头像渲染 */
    function avatar(src, size = 'avatar-sm') {
        if (isImageUrl(src)) return `<img src="${esc(src)}" class="${size}" alt="头像">`;
        return `<div class="${size}">${esc(src || '🙂')}</div>`;
    }

    /** 顶部导航：根据登录态渲染用户区域 */
    async function renderNav(active) {
        const nav = document.getElementById('nav-links');
        const userBox = document.getElementById('nav-user');
        let user = null;
        try { user = await App.get('/api/auth/me'); } catch (e) { /* 未登录或异常 */ }
        if (nav) {
            nav.querySelectorAll('a[data-key]').forEach(a => {
                a.classList.toggle('active', a.dataset.key === active);
            });
        }
        // 「后台管理」入口仅管理员可见：默认在 HTML 里 hidden，这里只有是 ADMIN 才放开。
        // 隐藏而不是禁用，避免普通用户看到入口或误点到 AdminFilter 的 403。
        toggleAdminNav(user);
        if (userBox) {
            userBox.innerHTML = user && user.loggedIn
                ? `<div class="flex" style="gap:10px">
                        ${avatar(user.user && user.user.avatar, 'avatar-sm')}
                        <div style="line-height:1.25">
                            <div style="font-weight:700;font-size:14px">${esc((user.user && (user.user.nickname || user.user.username)) || '未登录')}</div>
                            <div class="muted" style="font-size:12px">${user.user.role === 'ADMIN' ? '管理员' : '普通用户'}</div>
                        </div>
                        <button class="btn-sm btn-outline" id="btn-logout">退出</button>
                   </div>`
                : `<div class="flex" style="gap:8px">
                        <a class="btn btn-sm btn-ghost" href="${BASE}/static/login.html">登录</a>
                        <a class="btn btn-sm" href="${BASE}/static/register.html">注册</a>
                   </div>`;
            const out = document.getElementById('btn-logout');
            if (out) out.onclick = async () => {
                await get('/api/auth/logout').catch(() => { });
                location.href = BASE + '/static/index.html';
            };
        }
        return user;
    }

    /** 是否为管理员（配合 renderNav 控制后台入口显隐） */
    const isAdmin = (user) => !!user && user.loggedIn === true
        && !!user.user && user.user.role === 'ADMIN';

    /**
     * 显示/隐藏导航栏「后台管理」入口。
     * 同时写 hidden 属性和 inline style：hidden 可能被作者样式覆盖，
     * inline style 优先级最高，双保险确保一定藏得住。
     */
    function toggleAdminNav(user) {
        const show = isAdmin(user);
        document.querySelectorAll('a[data-key="admin"]').forEach(a => {
            a.hidden = !show;
            a.style.display = show ? '' : 'none';
        });
    }

    // 脚本一执行就先藏起来（不等 /api/auth/me 异步返回），消除拿到用户之前的可见窗口；
    // 是管理员的话，稍后由 renderNav 再放开。
    toggleAdminNav(null);

    /** 字段级错误回显：errors {field:message} */
    function showErrors(errors) {
        if (!errors) return;
        Object.keys(errors).forEach(field => {
            const tip = document.querySelector(`[data-error="${field}"]`);
            const input = document.querySelector(`[name="${field}"]`);
            if (tip) tip.textContent = errors[field];
            if (input) input.classList.add('error');
        });
    }

    function clearErrors() {
        document.querySelectorAll('[data-error]').forEach(el => el.textContent = '');
        document.querySelectorAll('.error').forEach(el => el.classList.remove('error'));
    }

    function bindInputs() {
        document.querySelectorAll('[name]').forEach(el => {
            el.addEventListener('input', () => {
                const tip = document.querySelector(`[data-error="${el.name}"]`);
                if (tip) tip.textContent = '';
                el.classList.remove('error');
            });
        });
    }

    /** 表单值回显 */
    function fillForm(form, data) {
        if (!form || !data) return;
        Object.keys(data).forEach(k => {
            const el = form.querySelector(`[name="${k}"]`);
            if (el && data[k] != null) el.value = data[k];
        });
    }

    return { base: BASE, get, post, put, del, upload, request, toast, money, esc, param, renderNav, isAdmin, showErrors, clearErrors, bindInputs, fillForm, media, avatar, isImageUrl };
})();
