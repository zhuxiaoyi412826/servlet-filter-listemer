/** 后台：订单管理（分页查询、状态流转、明细展开） */
(function () {
    const $ = (id) => document.getElementById(id);
    const esc = App.esc;
    const TAGS = {
        CREATED: ['gray', '待处理'],
        PAID: ['blue', '已收款'],
        COMPLETED: ['green', '已完成'],
        CANCELLED: ['red', '已取消']
    };
    const state = { page: 1, size: 10, orderNo: '', keyword: '', status: '', from: '', to: '' };

    AdminTabs.register('order', load);

    document.addEventListener('DOMContentLoaded', () => {
        $('o-search').onclick = () => { read(); state.page = 1; load(); };
        $('o-refresh').onclick = () => load();
        $('o-reset').onclick = () => {
            $('o-orderNo').value = ''; $('o-keyword').value = ''; $('o-status').value = '';
            $('o-from').value = ''; $('o-to').value = '';
            read(); state.page = 1; load();
        };
    });

    function read() {
        state.orderNo = $('o-orderNo').value.trim();
        state.keyword = $('o-keyword').value.trim();
        state.status = $('o-status').value;
        state.from = $('o-from').value;
        state.to = $('o-to').value;
    }

    function query(page) {
        return '/api/admin/orders?page=' + (page || state.page) + '&size=' + state.size
            + '&orderNo=' + encodeURIComponent(state.orderNo)
            + '&keyword=' + encodeURIComponent(state.keyword)
            + '&status=' + state.status + '&from=' + state.from + '&to=' + state.to;
    }

    async function load() {
        try {
            const data = await App.get(query());
            state.page = data.page;
            state.size = data.size;
            renderOverview(data.overview || {});
            renderList(data.list || [], data.total || 0);
            renderPager(data.page, data.pages || 1, data.total || 0);
        } catch (e) {
            App.toast(e.message, 'err');
        }
    }

    function renderOverview(o) {
        $('order-overview').innerHTML = [
            box('订单总数', o.total || 0, '笔'),
            box('有效订单', o.valid || 0, '笔'),
            box('已收款', App.money(o.revenue || 0), ''),
            box('待处理', o.created || 0, '笔'),
            box('已完成', o.completed || 0, '笔'),
            box('已取消', o.cancelled || 0, '笔')
        ].join('');
    }

    const box = (label, value, unit) =>
        `<div class="stat-box"><div class="stat-label">${label}</div>
         <div class="stat-value">${value}<small>${unit || ''}</small></div></div>`;

    function renderList(list, total) {
        $('o-empty').hidden = total > 0;
        $('o-tbody').innerHTML = list.map(o => {
            const t = TAGS[o.status] || ['gray', o.status];
            const u = o.user || {};
            const name = u.nickname || u.username || ('用户#' + o.userId);
            return `<tr>
                <td><b>${esc(o.orderNo)}</b><div class="muted" style="font-size:12px">
                    <a href="#" data-detail="${o.id}">明细 ${(o.items || []).length} 项 ▾</a></div></td>
                <td>${App.avatar(u.avatar, 'avatar-sm').replace('<img', '<img style="width:28px;height:28px;vertical-align:middle"')}
                    <div style="display:inline-block;line-height:1.2;margin-left:6px">
                        <div style="font-weight:600">${esc(name)}</div>
                        <div class="muted" style="font-size:12px">${esc(u.phone || '')}</div>
                    </div></td>
                <td class="price">${App.money(o.totalAmount)}</td>
                <td>${payLabel(o.payType)}</td>
                <td><span class="tag ${t[0]}">${t[1]}</span></td>
                <td class="muted">${esc(o.createdAt || '')}</td>
                <td>${actions(o)}</td>
            </tr>
            <tr class="detail-row" id="detail-${o.id}" hidden><td colspan="7">
                <div class="detail-box">${(o.items || []).map(it => `
                    <div class="detail-item"><span>${App.media(it.imageUrl, 'avatar-sm')}</span>
                        <span style="flex:1">${esc(it.dishName)}</span>
                        <span class="muted">${App.money(it.price)} × ${it.quantity}</span>
                        <b>${App.money(it.amount)}</b></div>`).join('')}
                    ${o.remark ? `<div class="muted" style="margin-top:8px">备注：${esc(o.remark)}</div>` : ''}
                </div></td></tr>`;
        }).join('');

        $('o-tbody').querySelectorAll('[data-detail]').forEach(a => a.onclick = (e) => {
            e.preventDefault();
            const row = $('detail-' + a.dataset.detail);
            row.hidden = !row.hidden;
        });
        $('o-tbody').querySelectorAll('[data-act]').forEach(b => b.onclick = () => update(Number(b.dataset.act), b.dataset.to));
    }

    const payLabel = (p) => ({ WECHAT: '微信', ALIPAY: '支付宝', CASH: '餐到付款' }[p] || esc(p));

    function actions(o) {
        const btn = (to, text, cls) =>
            `<button class="btn btn-sm ${cls || 'btn-outline'}" data-act="${o.id}" data-to="${to}">${text}</button> `;
        switch (o.status) {
            case 'CREATED': return btn('PAID', '标记收款') + btn('COMPLETED', '完成') + btn('CANCELLED', '取消', 'btn-danger');
            case 'PAID': return btn('COMPLETED', '完成') + btn('CANCELLED', '取消', 'btn-danger');
            case 'COMPLETED': return btn('CANCELLED', '取消并退库存', 'btn-danger');
            case 'CANCELLED': return btn('CREATED', '恢复订单');
            default: return '';
        }
    }

    async function update(id, status) {
        const tip = { PAID: '标记为已收款？', COMPLETED: '标记为已完成？', CANCELLED: '取消该订单并回滚库存？', CREATED: '恢复该订单？' }[status];
        if (!confirm(tip || '确认操作？')) return;
        try {
            const data = await App.put('/api/admin/orders', {
                id: id, setStatus: status,
                page: state.page, size: state.size, orderNo: state.orderNo, keyword: state.keyword,
                status: $('o-status').value, from: state.from, to: state.to
            });
            App.toast(data.message || '操作成功', 'ok');
            renderOverview(data.overview || {});
            renderList(data.list || [], data.total || 0);
            renderPager(data.page, data.pages || 1, data.total || 0);
        } catch (e) {
            App.toast(e.message, 'err');
        }
    }

    function renderPager(page, pages, total) {
        if (pages <= 1) { $('o-pager').innerHTML = `<span class="muted">共 ${total} 条</span>`; return; }
        let html = `<button class="btn btn-sm btn-outline" data-p="${page - 1}" ${page <= 1 ? 'disabled' : ''}>上一页</button>`;
        html += `<span class="muted">第 ${page} / ${pages} 页 · 共 ${total} 条</span>`;
        html += `<button class="btn btn-sm btn-outline" data-p="${page + 1}" ${page >= pages ? 'disabled' : ''}>下一页</button>`;
        $('o-pager').innerHTML = html;
        $('o-pager').querySelectorAll('[data-p]').forEach(b => b.onclick = async () => {
            state.page = Number(b.dataset.p);
            await load();
        });
    }
})();
