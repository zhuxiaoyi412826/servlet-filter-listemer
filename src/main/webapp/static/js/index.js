/** 点餐首页：菜品展示、分类筛选、搜索、购物车与下单 */
(function () {
    const state = { category: '', keyword: '', user: null, cart: { items: [], total: 0, count: 0 } };
    const $ = (id) => document.getElementById(id);

    async function boot() {
        App.bindInputs();
        state.user = await App.renderNav('home').catch(() => null);
        bindEvents();
        await loadDishes();
        await refreshCart();
        await loadOnline();
    }

    function bindEvents() {
        $('btn-search').onclick = () => { state.keyword = $('kw').value.trim(); loadDishes(); };
        $('kw').addEventListener('keydown', e => { if (e.key === 'Enter') $('btn-search').click(); });
        $('btn-reset').onclick = () => {
            state.keyword = ''; state.category = ''; $('kw').value = '';
            document.querySelectorAll('#categories .chip').forEach(c => c.classList.remove('active'));
            document.querySelector('#categories .chip')?.classList.add('active');
            loadDishes();
        };
        $('btn-cart').onclick = () => openCart(true);
        $('btn-close-drawer').onclick = () => openCart(false);
        $('mask').onclick = () => openCart(false);
        $('btn-close-checkout').onclick = () => $('checkout').classList.remove('show');
        $('btn-checkout').onclick = openCheckout;
        $('checkout-form').onsubmit = submitOrder;
    }

    async function loadDishes() {
        try {
            const qs = new URLSearchParams();
            if (state.category) qs.set('category', state.category);
            if (state.keyword) qs.set('keyword', state.keyword);
            const data = await App.get('/api/dishes?' + qs.toString());
            renderCategories(data.categories || []);
            const list = data.list || [];
            $('list-count').textContent = `共 ${list.length} 道菜品`;
            $('list-title').textContent = state.category || state.keyword ? '筛选结果' : '全部菜品';
            if (!list.length) {
                $('empty').hidden = false;
                $('dish-list').innerHTML = '';
                return;
            }
            $('empty').hidden = true;
            $('dish-list').innerHTML = list.map(d => `
                <div class="card dish-card">
                    ${App.media(d.imageUrl)}
                    <div class="dish-body">
                        <div class="dish-title"><b>${App.esc(d.name)}</b><span class="tag gray">${App.esc(d.category)}</span></div>
                        <div class="dish-desc">${App.esc(d.description || '暂无描述')}</div>
                        <div class="between mt-16">
                            <span class="price"><small>¥</small>${Number(d.price).toFixed(2)}</span>
                            <span class="tag ${d.stock > 0 ? 'green' : 'gray'}">${d.stock > 0 ? '剩 ' + d.stock + ' 份' : '已售罄'}</span>
                        </div>
                        <button class="btn btn-block mt-16" data-add="${d.id}" ${d.stock <= 0 ? 'disabled' : ''}>
                            ➕ 加入购物车
                        </button>
                    </div>
                </div>`).join('');
            $('dish-list').querySelectorAll('[data-add]').forEach(btn => {
                btn.onclick = () => addToCart(btn.dataset.add);
            });
        } catch (e) {
            App.toast(e.message, 'err');
        }
    }

    function renderCategories(categories) {
        const box = $('categories');
        const all = `<span class="chip ${state.category ? '' : 'active'}" data-cat="">全部</span>`;
        box.innerHTML = all + categories.map(c =>
            `<span class="chip ${state.category === c ? 'active' : ''}" data-cat="${App.esc(c)}">${App.esc(c)}</span>`).join('');
        box.querySelectorAll('.chip').forEach(chip => {
            chip.onclick = () => { state.category = chip.dataset.cat; loadDishes(); };
        });
    }

    async function addToCart(dishId) {
        requireLogin();
        try {
            const data = await App.post('/api/cart', { dishId: Number(dishId), quantity: 1 });
            state.cart = data;
            renderCart();
            App.toast(`已加入：${data.addedDish}`, 'ok', 1500);
        } catch (e) {
            App.toast(e.message, 'err');
        }
    }

    async function refreshCart() {
        try {
            state.cart = await App.get('/api/cart');
            renderCart();
        } catch (e) {
            /* 未登录时购物车为空 */
        }
    }

    function renderCart() {
        const c = state.cart || { items: [], total: 0, count: 0 };
        $('cart-count').textContent = c.count || 0;
        $('cart-total').textContent = App.money(c.total);
        $('cart-list').innerHTML = (c.items || []).length === 0
            ? `<div class="empty">购物车还是空的<br/><span class="muted">先去挑两道菜吧～</span></div>`
            : (c.items || []).map(it => `
                <div class="cart-row">
                    ${App.media(it.imageUrl, 'avatar-sm')}
                    <div style="flex:1">
                        <div style="font-weight:700">${App.esc(it.name)}</div>
                        <div class="muted" style="font-size:13px">${App.money(it.price)} / 份</div>
                    </div>
                    <div class="qty">
                        <button data-dec="${it.dishId}">−</button>
                        <span>${it.quantity}</span>
                        <button data-inc="${it.dishId}">＋</button>
                    </div>
                    <div style="width:70px;text-align:right;font-weight:700">${App.money(it.amount)}</div>
                    <button class="btn-link" data-del="${it.dishId}">删除</button>
                </div>`).join('');
        bindCartOperators();
    }

    function bindCartOperators() {
        $('cart-list').querySelectorAll('[data-inc]').forEach(b => b.onclick = async () => {
            await cartOp({ dishId: Number(b.dataset.inc), quantity: 1 });
        });
        $('cart-list').querySelectorAll('[data-dec]').forEach(b => b.onclick = async () => {
            await cartOp({ dishId: Number(b.dataset.dec), quantity: -1 });
        });
        $('cart-list').querySelectorAll('[data-del]').forEach(b => b.onclick = async () => {
            try {
                state.cart = await App.request('/api/cart?dishId=' + b.dataset.del, { method: 'DELETE' });
                renderCart();
            } catch (e) { App.toast(e.message, 'err'); }
        });
    }

    async function cartOp(body) {
        try {
            const cur = (state.cart.items || []).find(i => i.dishId === body.dishId);
            const next = (cur ? cur.quantity : 0) + body.quantity;
            if (next <= 0) {
                state.cart = await App.request('/api/cart?dishId=' + body.dishId, { method: 'DELETE' });
            } else {
                state.cart = await App.post('/api/cart', { dishId: body.dishId, quantity: next, action: 'set' });
            }
            renderCart();
        } catch (e) { App.toast(e.message, 'err'); }
    }

    function openCart(open) {
        $('drawer').classList.toggle('show', open);
        $('mask').classList.toggle('show', open);
        if (open) refreshCart();
    }

    function openCheckout() {
        requireLogin();
        if (!state.cart.count) { App.toast('购物车是空的', 'warn'); return; }
        $('checkout-total').textContent = App.money(state.cart.total);
        $('checkout').classList.add('show');
    }

    async function submitOrder(e) {
        e.preventDefault();
        requireLogin();
        const form = $('checkout-form');
        const btn = $('btn-submit-order');
        btn.disabled = true; btn.textContent = '提交中…';
        try {
            const data = await App.post('/api/orders', {
                remark: form.remark.value
            });
            App.toast(`下单成功：${data.order.orderNo} · 余额扣款 ${App.money(data.paid)}，剩余 ${App.money(data.balance)}`, 'ok', 3200);
            $('checkout').classList.remove('show');
            openCart(false);
            state.cart = { items: [], total: 0, count: 0 };
            renderCart();
            setTimeout(() => location.href = App.base + '/static/orders.html', 900);
        } catch (err) {
            App.toast(err.message, 'err');
        } finally {
            btn.disabled = false; btn.textContent = '提交订单';
        }
    }

    async function loadOnline() {
        try {
            const s = await App.get('/api/stat/online');
            $('online').innerHTML = `<i class="dot"></i>当前 ${s.online} 人在线 · 累计访问 ${s.pv} 次`;
        } catch (e) { /* 忽略 */ }
    }

    function requireLogin() {
        if (!state.user || !state.user.loggedIn) {
            App.toast('请先登录后再点餐', 'warn');
            location.href = App.base + '/static/login.html?redirect=' + encodeURIComponent(location.pathname);
            throw new Error('需要登录');
        }
    }

    document.addEventListener('DOMContentLoaded', boot);
})();
