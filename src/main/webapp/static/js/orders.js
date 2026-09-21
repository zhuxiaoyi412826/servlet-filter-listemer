/** 订单列表：加载、取消订单 */
(function () {
    const $ = (id) => document.getElementById(id);

    document.addEventListener('DOMContentLoaded', async () => {
        await App.renderNav('orders');
        $('btn-refresh').onclick = load;
        await load();
    });

    async function load() {
        try {
            const list = await App.get('/api/orders');
            if (!list.length) {
                $('orders').innerHTML = `<div class="empty">还没有订单，<a href="${App.base}/static/index.html" style="color:var(--primary)">去点餐 →</a></div>`;
                return;
            }
            $('orders').innerHTML = list.map(o => {
                const statusText = { CREATED: '待接单', PAID: '已支付', CANCELLED: '已取消' }[o.status] || o.status;
                const statusClass = o.status === 'CANCELLED' ? 'gray' : (o.status === 'PAID' ? 'green' : '');
                return `
                <div class="card order-card">
                    <div class="between">
                        <div>
                            <b>订单号：</b>${App.esc(o.orderNo)}
                            <span class="tag ${statusClass}" style="margin-left:8px">${statusText}</span>
                        </div>
                        <span class="muted" style="font-size:13px">${App.esc(String(o.createdAt || '').replace('T', ' '))}</span>
                    </div>
                    <div style="margin-top:12px">
                        ${(o.items || []).map(it => `
                            <div class="order-item">
                                ${App.media(it.imageUrl, 'avatar-sm')}
                                <div style="flex:1">
                                    <div style="font-weight:700">${App.esc(it.dishName)}</div>
                                    <div class="muted" style="font-size:13px">${App.money(it.price)} × ${it.quantity}</div>
                                </div>
                                <b>${App.money(it.amount)}</b>
                            </div>`).join('')}
                    </div>
                    <div class="between mt-16">
                        <div class="muted" style="font-size:13px">
                            支付方式：${payText(o.payType)}${o.remark ? ' · 备注：' + App.esc(o.remark) : ''}
                        </div>
                        <div class="flex" style="gap:12px">
                            <b class="price">${App.money(o.totalAmount)}</b>
                            ${o.status === 'CREATED'
                        ? `<button class="btn btn-sm btn-danger" data-cancel="${o.id}">取消订单</button>`
                        : ''}
                        </div>
                    </div>
                </div>`;
            }).join('');
            $('orders').querySelectorAll('[data-cancel]').forEach(btn => {
                btn.onclick = async () => {
                    if (!confirm('确定要取消该订单吗？库存将自动返还')) return;
                    try {
                        await App.request('/api/orders?id=' + btn.dataset.cancel, { method: 'DELETE' });
                        App.toast('订单已取消', 'ok');
                        await load();
                    } catch (e) { App.toast(e.message, 'err'); }
                };
            });
        } catch (e) {
            App.toast(e.message, 'err');
        }
    }

    function payText(type) {
        return { CASH: '餐到付款', WECHAT: '微信支付', ALIPAY: '支付宝' }[type] || type;
    }
})();
