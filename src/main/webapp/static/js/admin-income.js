/** 后台：收入管理（营收概览、每日趋势、菜品销量榜、支付方式分布） */
(function () {
    const $ = (id) => document.getElementById(id);
    const esc = App.esc;
    let days = 7;

    AdminTabs.register('income', load);

    document.addEventListener('DOMContentLoaded', () => {
        $('in-days').onchange = () => { days = Number($('in-days').value); load(); };
        $('in-refresh').onclick = () => load();
    });

    async function load() {
        try {
            const data = await App.get('/api/admin/income?days=' + days + '&top=8');
            renderOverview(data.overview || {}, data.users || {});
            renderChart(data.daily || []);
            renderDishes(data.dishes || []);
            renderPays(data.payTypes || []);
        } catch (e) {
            App.toast(e.message, 'err');
        }
    }

    const box = (label, value, unit, sub) =>
        `<div class="stat-box"><div class="stat-label">${label}</div>
         <div class="stat-value">${value}<small>${unit || ''}</small></div>
         <div class="stat-sub">${sub || ''}</div></div>`;

    function renderOverview(o, users) {
        const growth = o.yesterdayRevenue > 0
            ? '较昨日 ' + (((o.todayRevenue - o.yesterdayRevenue) / o.yesterdayRevenue) * 100).toFixed(1) + '%'
            : '暂无昨日数据';
        $('income-overview').innerHTML = [
            box('累计营收', App.money(o.revenue), '', '有效订单 ' + (o.validOrders || 0) + ' 笔'),
            box('今日营收', App.money(o.todayRevenue), '', growth),
            box('本月营收', App.money(o.monthRevenue), '', '今日订单 ' + (o.todayOrders || 0) + ' 笔'),
            box('客单价', App.money(o.avgOrderValue), '', '按有效订单计算'),
            box('取消金额', App.money(o.cancelledAmount), '', '不计入营收'),
            box('注册用户', users.total || 0, ' 人', '其中管理员 ' + (users.admins || 0) + ' 人')
        ].join('');
    }

    /** 纯 CSS 柱状图：高度按当日营收占比计算 */
    function renderChart(daily) {
        const list = daily || [];
        const max = Math.max(...list.map(d => Number(d.revenue) || 0), 1);
        $('in-range').textContent = list.length ? (list[0].day + ' 至 ' + list[list.length - 1].day) : '';
        $('in-chart').innerHTML = list.map(d => {
            const v = Number(d.revenue) || 0;
            const h = Math.max(Math.round((v / max) * 100), v > 0 ? 4 : 1);
            return `<div class="chart-col" title="${esc(d.day)} 营收 ${App.money(v)} / ${d.orders} 单">
                        <div class="chart-val">${v > 0 ? App.money(v) : ''}</div>
                        <div class="chart-bar" style="height:${h}%"></div>
                        <div class="chart-lab">${String(d.day).slice(5)}</div>
                    </div>`;
        }).join('') || '<div class="empty">暂无营收数据</div>';
    }

    function renderDishes(dishes) {
        const list = dishes || [];
        if (!list.length) { $('in-dishes').innerHTML = '<div class="empty">暂无销量数据</div>'; return; }
        const max = Math.max(...list.map(d => Number(d.quantity) || 0), 1);
        $('in-dishes').innerHTML = list.map((d, i) => `
            <div class="rank-row">
                <span class="rank-no ${i < 3 ? 'top' : ''}">${i + 1}</span>
                <span class="rank-name">${esc(d.name)}</span>
                <span class="tag gray">${esc(d.category || '—')}</span>
                <span class="rank-track"><i style="width:${Math.round(((Number(d.quantity) || 0) / max) * 100)}%"></i></span>
                <span class="muted">${d.quantity} 份</span>
                <b class="price">${App.money(d.revenue)}</b>
            </div>`).join('');
    }

    const payName = (p) => ({ WECHAT: '微信支付', ALIPAY: '支付宝', CASH: '餐到付款' }[p] || p);

    function renderPays(pays) {
        const list = pays || [];
        if (!list.length) { $('in-pays').innerHTML = '<div class="empty">暂无支付数据</div>'; return; }
        const total = list.reduce((s, p) => s + (Number(p.revenue) || 0), 0) || 1;
        $('in-pays').innerHTML = list.map(p => {
            const pct = ((Number(p.revenue) || 0) / total) * 100;
            return `<div class="pay-row">
                        <div class="between"><b>${esc(payName(p.payType))}</b>
                            <span class="muted">${App.money(p.revenue)} · ${p.orders} 单</span></div>
                        <div class="pay-track"><i style="width:${pct.toFixed(1)}%"></i></div>
                        <div class="muted">占比 ${pct.toFixed(1)}%</div>
                    </div>`;
        }).join('');
    }
})();
