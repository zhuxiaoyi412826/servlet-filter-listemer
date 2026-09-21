/** 后台：用户管理（查询、改角色、禁用/启用、重置密码、删除） */
(function () {
    const $ = (id) => document.getElementById(id);
    const esc = App.esc;
    const state = { page: 1, size: 10, keyword: '', role: '', status: '' };

    AdminTabs.register('user', load);

    document.addEventListener('DOMContentLoaded', () => {
        $('u-search').onclick = () => { read(); state.page = 1; load(); };
        $('u-refresh').onclick = () => load();
        $('u-reset').onclick = () => {
            $('u-keyword').value = ''; $('u-role').value = ''; $('u-status').value = '';
            read(); state.page = 1; load();
        };
    });

    function read() {
        state.keyword = $('u-keyword').value.trim();
        state.role = $('u-role').value;
        state.status = $('u-status').value;
    }

    function query() {
        return '/api/admin/user?page=' + state.page + '&size=' + state.size
            + '&keyword=' + encodeURIComponent(state.keyword)
            + '&role=' + state.role + '&status=' + state.status;
    }

    const filters = () => ({ page: state.page, size: state.size, keyword: state.keyword, role: state.role, status: state.status });

    async function load() {
        try {
            const data = await App.get(query());
            state.page = data.page;
            state.size = data.size;
            renderStats(data.stats || {});
            renderList(data.list || [], data.total || 0);
            renderPager(data.page, data.pages || 1, data.total || 0);
        } catch (e) {
            App.toast(e.message, 'err');
        }
    }

    function renderStats(s) {
        $('user-overview').innerHTML = [
            box('用户总数', s.total || 0, '人'),
            box('今日新增', s.todayNew || 0, '人'),
            box('近 7 天新增', s.weekNew || 0, '人'),
            box('管理员', s.admins || 0, '人'),
            box('已禁用', s.disabled || 0, '人')
        ].join('');
    }

    const box = (label, value, unit) =>
        `<div class="stat-box"><div class="stat-label">${label}</div>
         <div class="stat-value">${value}<small>${unit || ''}</small></div></div>`;

    function renderList(list, total) {
        $('u-empty').hidden = total > 0;
        $('u-tbody').innerHTML = list.map(u => `
            <tr>
                <td>
                    <div class="flex" style="gap:8px">
                        ${App.avatar(u.avatar, 'avatar-sm')}
                        <div style="line-height:1.25">
                            <div style="font-weight:600">${esc(u.nickname || u.username)}</div>
                            <div class="muted" style="font-size:12px">@${esc(u.username)}</div>
                        </div>
                    </div>
                </td>
                <td>${esc(u.phone || '—')}</td>
                <td><span class="tag ${u.role === 'ADMIN' ? 'orange' : 'gray'}">${u.role === 'ADMIN' ? '管理员' : '普通用户'}</span></td>
                <td><span class="tag ${u.status === 1 ? 'green' : 'red'}">${u.status === 1 ? '正常' : '已禁用'}</span></td>
                <td>${u.orderCount}</td>
                <td class="price">${App.money(u.totalSpend)}</td>
                <td class="muted">${esc(u.createdAt || '')}</td>
                <td>
                    <button class="btn btn-sm btn-outline" data-role="${u.id}" data-v="${u.role === 'ADMIN' ? 'USER' : 'ADMIN'}">
                        ${u.role === 'ADMIN' ? '降为普通' : '设为管理'}</button>
                    <button class="btn btn-sm ${u.status === 1 ? 'btn-danger' : 'btn-outline'}" data-st="${u.id}" data-v="${u.status === 1 ? 0 : 1}">
                        ${u.status === 1 ? '禁用' : '启用'}</button>
                    <button class="btn btn-sm btn-ghost" data-reset="${u.id}">重置密码</button>
                    <button class="btn btn-sm btn-danger" data-del="${u.id}">删除</button>
                </td>
            </tr>`).join('');

        const body = $('u-tbody');
        body.querySelectorAll('[data-role]').forEach(b => b.onclick = () => call({ id: Number(b.dataset.role), setRole: b.dataset.v },
            '确定将该用户设为' + (b.dataset.v === 'ADMIN' ? '管理员' : '普通用户') + '？'));
        body.querySelectorAll('[data-st]').forEach(b => b.onclick = () => call({ id: Number(b.dataset.st), setStatus: b.dataset.v },
            b.dataset.v === '0' ? '禁用后该账号将无法登录，确定？' : '确定启用该账号？'));
        body.querySelectorAll('[data-reset]').forEach(b => b.onclick = () => call({ id: Number(b.dataset.reset), reset: true }, '确定重置该用户的密码？'));
        body.querySelectorAll('[data-del]').forEach(b => b.onclick = () => del(Number(b.dataset.del)));
    }

    async function call(payload, tip) {
        if (tip && !confirm(tip)) return;
        try {
            const data = await App.put('/api/admin/user', Object.assign(payload, filters()));
            App.toast(data.message || '操作成功', 'ok', 3600);
            apply(data);
        } catch (e) {
            App.toast(e.message, 'err');
        }
    }

    async function del(id) {
        if (!confirm('删除用户后其历史订单仍会保留，确定删除？')) return;
        try {
            const data = await App.request('/api/admin/user?' + new URLSearchParams(Object.assign({ id: id }, filters())),
                { method: 'DELETE' });
            App.toast(data.message || '删除成功', 'ok');
            apply(data);
        } catch (e) {
            App.toast(e.message, 'err');
        }
    }

    /** 写操作接口统一回显最新列表 */
    function apply(data) {
        state.page = data.page || state.page;
        renderStats(data.stats || {});
        renderList(data.list || [], data.total || 0);
        renderPager(data.page, data.pages || 1, data.total || 0);
    }

    function renderPager(page, pages, total) {
        if (pages <= 1) { $('u-pager').innerHTML = `<span class="muted">共 ${total} 条</span>`; return; }
        $('u-pager').innerHTML =
            `<button class="btn btn-sm btn-outline" data-p="${page - 1}" ${page <= 1 ? 'disabled' : ''}>上一页</button>
             <span class="muted">第 ${page} / ${pages} 页 · 共 ${total} 条</span>
             <button class="btn btn-sm btn-outline" data-p="${page + 1}" ${page >= pages ? 'disabled' : ''}>下一页</button>`;
        $('u-pager').querySelectorAll('[data-p]').forEach(b => b.onclick = async () => {
            state.page = Number(b.dataset.p);
            await load();
        });
    }
})();
