/** 金币余额：余额概览、自定义金额充值、流水（含下单消费/退款）分页查询 */
(function () {
    const $ = (id) => document.getElementById(id);
    const state = { page: 1, size: 8 };

    const TAGS = {
        INIT: ['blue', '初始赠金'],
        SPEND: ['red', '下单消费'],
        REFUND: ['orange', '订单退款'],
        RECHARGE: ['green', '充值']
    };

    document.addEventListener('DOMContentLoaded', () => {
        if (!$('form-recharge')) return;
        $('form-recharge').onsubmit = recharge;
        load();
    });

    async function load() {
        try {
            const data = await App.get('/api/balance?page=' + state.page + '&size=' + state.size);
            render(data);
        } catch (e) {
            App.toast(e.message, 'err');
        }
    }

    function render(d) {
        $('bal-balance').textContent = App.money(d.balance || 0);
        $('bal-spent').textContent = App.money(d.totalSpent || 0);
        $('bal-income').textContent = App.money(d.totalIncome || 0);

        const list = d.list || [];
        $('bal-empty').hidden = list.length > 0;
        $('bal-tbody').innerHTML = list.map(row).join('');
        renderPager(d.page || 1, d.pages || 1, d.total || 0);
    }

    function row(l) {
        const spend = Number(l.changeAmount) < 0;
        const tag = TAGS[l.type] || (spend ? TAGS.SPEND : TAGS.RECHARGE);
        const color = spend ? 'var(--danger)' : 'var(--success)';
        return `<tr>
            <td class="muted">${App.esc(l.createdAt || '')}</td>
            <td><span class="tag ${tag[0]}">${tag[1]}</span></td>
            <td style="color:${color};font-weight:700">${spend ? '-' : '+'}${App.money(Math.abs(l.changeAmount || 0))}</td>
            <td>${App.money(l.balanceAfter || 0)}</td>
            <td>${App.esc(l.remark || '')}</td>
        </tr>`;
    }

    function renderPager(page, pages, total) {
        if (pages <= 1) {
            $('bal-pager').innerHTML = `<span class="muted">共 ${total} 条</span>`;
            return;
        }
        let html = `<button class="btn btn-sm btn-outline" data-p="${page - 1}" ${page <= 1 ? 'disabled' : ''}>上一页</button>`;
        html += `<span class="muted">第 ${page} / ${pages} 页 · 共 ${total} 条</span>`;
        html += `<button class="btn btn-sm btn-outline" data-p="${page + 1}" ${page >= pages ? 'disabled' : ''}>下一页</button>`;
        $('bal-pager').innerHTML = html;
        $('bal-pager').querySelectorAll('[data-p]').forEach(b => {
            b.onclick = () => { state.page = Number(b.dataset.p); load(); };
        });
    }

    async function recharge(e) {
        e.preventDefault();
        const amount = $('recharge-amount').value.trim();
        const remark = $('recharge-remark').value.trim();
        if (!amount) { App.toast('请输入充值金额', 'err'); return; }
        if (Number(amount) <= 0) { App.toast('充值金额必须大于 0', 'err'); return; }
        const btn = $('btn-recharge');
        btn.disabled = true; btn.textContent = '充值中…';
        try {
            const data = await App.post('/api/balance', { amount: amount, remark: remark });
            App.toast('充值成功，当前余额 ' + App.money(data.balance), 'ok');
            $('recharge-amount').value = '';
            $('recharge-remark').value = '';
            state.page = 1;
            await load();
        } catch (err) {
            App.toast(err.message, 'err');
        } finally {
            btn.disabled = false; btn.textContent = '充值';
        }
    }
})();
