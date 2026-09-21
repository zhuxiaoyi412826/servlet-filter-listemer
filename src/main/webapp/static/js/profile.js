/** 个人中心：资料回显、保存、头像上传 */
(function () {
    const $ = (id) => document.getElementById(id);
    let me = null;

    document.addEventListener('DOMContentLoaded', async () => {
        App.bindInputs();
        me = await App.renderNav('profile');
        if (!me || !me.loggedIn) {
            location.href = App.base + '/static/login.html?redirect=' + encodeURIComponent(location.pathname);
            return;
        }
        render(me);
        $('btn-pick').onclick = () => $('file').click();
        $('file').onchange = uploadAvatar;
        $('form-profile').onsubmit = save;
    });

    function render(me) {
        const u = me.user;
        $('avatar-box').innerHTML = App.isImageUrl(u.avatar)
            ? `<img src="${App.esc(u.avatar)}" class="avatar-lg" alt="头像">`
            : `<div class="avatar-lg">🙂</div>`;
        $('form-profile').username.value = u.username || '';
        $('form-profile').nickname.value = u.nickname || '';
        $('form-profile').phone.value = u.phone || '';
        $('form-profile').address.value = u.address || '';
        $('role').value = u.role === 'ADMIN' ? '管理员' : '普通用户';

        const infra = me.infra || {};
        $('infra').innerHTML = `
            <div>数据库：${infra.dbReady ? '<span class="tag green">已连接</span>' : '<span class="tag gray">未连接</span>'}</div>
            <div style="margin-top:4px">对象存储：${infra.minioReady ? '<span class="tag green">MinIO</span>' : '<span class="tag gray">本地降级</span>'}</div>
            <div style="margin-top:4px">存储桶：${App.esc(infra.minioBucket || '-')}</div>
            <div style="margin-top:4px">在线人数：${me.onlineCount || 0}</div>
            <div style="margin-top:4px">累计访问：${(me.runtime && me.runtime.pv) || 0}</div>`;
    }

    async function uploadAvatar() {
        const file = $('file').files[0];
        if (!file) return;
        App.toast('正在上传头像…', 'warn', 6000);
        try {
            const data = await App.upload('/api/user/avatar', file);
            App.toast('头像已更新（' + data.storage + '）', 'ok');
            me = await App.get('/api/auth/me');
            render(me);
            await App.renderNav('profile');
        } catch (e) {
            App.toast(e.message, 'err');
        }
    }

    async function save(e) {
        e.preventDefault();
        App.clearErrors();
        const form = $('form-profile');
        const btn = $('btn-save');
        btn.disabled = true; btn.textContent = '保存中…';
        try {
            const data = await App.post('/api/user/profile', {
                nickname: form.nickname.value.trim(),
                phone: form.phone.value.trim(),
                address: form.address.value.trim(),
                avatar: me.user.avatar || ''
            });
            App.toast('资料已保存', 'ok');
            me = await App.get('/api/auth/me');
            render(me);
            await App.renderNav('profile');
        } catch (err) {
            if (err.data && err.data.form) App.fillForm(form, err.data.form);
            App.showErrors(err.data && err.data.errors ? err.data.errors : { nickname: err.message });
            App.toast(err.message, 'err');
        } finally {
            btn.disabled = false; btn.textContent = '保存资料';
        }
    }
})();
