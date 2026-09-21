/** 后台管理：菜品增删改（需 ADMIN 角色，接口由 AdminFilter 保护） */
(function () {
    const $ = (id) => document.getElementById(id);
    const form = () => $('form-dish');
    let dishes = [];

    document.addEventListener('DOMContentLoaded', async () => {
        App.bindInputs();
        const me = await App.renderNav('admin');
        if (!me || !me.loggedIn || me.user.role !== 'ADMIN') {
            App.toast('需要管理员权限', 'err');
            setTimeout(() => location.href = App.base + '/static/index.html', 800);
            return;
        }
        $('btn-new').onclick = () => openModal(null);
        $('btn-close').onclick = () => $('modal').classList.remove('show');
        $('keyword').addEventListener('input', render);
        $('form-dish').onsubmit = save;
        await load();
    });

    async function load() {
        try {
            const data = await App.get('/api/admin/dish');
            dishes = data.list || [];
            render();
        } catch (e) {
            App.toast(e.message, 'err');
        }
    }

    function render() {
        const kw = $('keyword').value.trim().toLowerCase();
        const list = dishes.filter(d => !kw || d.name.toLowerCase().includes(kw) || d.category.includes(kw)
            || (d.description || '').toLowerCase().includes(kw));
        $('empty').hidden = list.length > 0;
        $('tbody').innerHTML = list.map(d => `
            <tr>
                <td>${App.media(d.imageUrl, 'avatar-sm')}</td>
                <td><b>${App.esc(d.name)}</b></td>
                <td><span class="tag gray">${App.esc(d.category)}</span></td>
                <td class="price">${App.money(d.price)}</td>
                <td>${d.stock}</td>
                <td><span class="tag ${d.status === 1 ? 'green' : 'gray'}">${d.status === 1 ? '上架' : '下架'}</span></td>
                <td class="muted" style="max-width:220px">${App.esc(d.description || '')}</td>
                <td>
                    <button class="btn btn-sm btn-outline" data-edit="${d.id}">编辑</button>
                    <button class="btn btn-sm btn-danger" data-del="${d.id}">删除</button>
                </td>
            </tr>`).join('');
        $('tbody').querySelectorAll('[data-edit]').forEach(b => b.onclick = () => {
            openModal(dishes.find(x => x.id === Number(b.dataset.edit)));
        });
        $('tbody').querySelectorAll('[data-del]').forEach(b => b.onclick = async () => {
            if (!confirm('确定删除该菜品？')) return;
            try {
                await App.request('/api/admin/dish?id=' + b.dataset.del, { method: 'DELETE' });
                App.toast('删除成功', 'ok');
                await load();
            } catch (e) { App.toast(e.message, 'err'); }
        });
    }

    function openModal(dish) {
        App.clearErrors();
        const f = form();
        f.reset();
        $('modal-title').textContent = dish ? '编辑菜品' : '新增菜品';
        if (dish) App.fillForm(f, dish);
        else f.status.value = '1';
        $('modal').classList.add('show');
    }

    async function save(e) {
        e.preventDefault();
        App.clearErrors();
        const f = form();
        const btn = $('btn-save');
        const fd = new FormData(f);
        const id = Number(f.id.value || 0);
        const fileEl = f.querySelector('[name="file"]');
        btn.disabled = true; btn.textContent = '保存中…';
        try {
            let data;
            if (id > 0) {
                data = await App.request('/api/admin/dish', { method: 'PUT', body: fd });
            } else {
                data = await App.request('/api/admin/dish', { method: 'POST', body: fd });
            }
            App.toast(data.message || '保存成功', 'ok');
            $('modal').classList.remove('show');
            dishes = data.list || [];
            fileEl.value = '';
            render();
        } catch (err) {
            if (err.data && err.data.form) App.fillForm(f, err.data.form);
            App.showErrors(err.data && err.data.errors ? err.data.errors : { name: err.message });
            App.toast(err.message, 'err', 3200);
        } finally {
            btn.disabled = false; btn.textContent = '保存';
        }
    }
})();
