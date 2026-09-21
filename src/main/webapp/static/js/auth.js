/** 登录页与注册页：表单提交、字段回显、错误回显、验证码倒计时 */
(function () {
    const $ = (sel) => document.querySelector(sel);
    let countdown = 0;

    const isLoginPage = () => !!$('#form-pwd');
    const isRegisterPage = () => !!$('#form-register');

    document.addEventListener('DOMContentLoaded', () => {
        App.bindInputs();
        if (isLoginPage()) initLogin();
        if (isRegisterPage()) initRegister();
    });

    function redirectAfterLogin() {
        const back = App.param('redirect');
        location.href = back ? decodeURIComponent(back) : App.base + '/static/index.html';
    }

    function initLogin() {
        // Tab 切换
        document.querySelectorAll('.tabs button').forEach(btn => {
            btn.onclick = () => {
                document.querySelectorAll('.tabs button').forEach(b => b.classList.remove('active'));
                btn.classList.add('active');
                const isPwd = btn.dataset.tab === 'pwd';
                $('#form-pwd').style.display = isPwd ? '' : 'none';
                $('#form-phone').style.display = isPwd ? 'none' : '';
                App.clearErrors();
            };
        });

        $('#form-pwd').onsubmit = async (e) => {
            e.preventDefault();
            App.clearErrors();
            const body = {
                account: $('#account').value.trim(),
                password: $('#password').value,
                rememberMe: $('#rememberMe').checked
            };
            // 登录失败时保留账号输入（前端本地回显）
            try {
                const data = await App.post('/api/auth/login', body);
                App.toast(`欢迎回来，${data.user.nickname || data.user.username}`, 'ok');
                redirectAfterLogin();
            } catch (err) {
                if (err.data && err.data.account !== undefined) $('#account').value = err.data.account;
                if (err.data && err.data.rememberMe !== undefined) $('#rememberMe').checked = !!err.data.rememberMe;
                App.showErrors({ password: err.message });
                App.toast(err.message, 'err');
            }
        };

        $('#btn-code').onclick = async () => {
            const phone = $('#phone').value.trim();
            App.clearErrors();
            if (!/^1[3-9]\d{9}$/.test(phone)) {
                App.showErrors({ phone: '请输入正确的 11 位手机号' });
                return;
            }
            try {
                const data = await App.post('/api/auth/phone/code', { phone });
                $('#code').value = data.devCode;   // 演示：自动回填验证码
                App.toast(`验证码已发送（演示码：${data.devCode}）`, 'ok', 4000);
                startCountdown();
            } catch (err) {
                App.showErrors({ phone: err.message });
                App.toast(err.message, 'err');
            }
        };

        $('#form-phone').onsubmit = async (e) => {
            e.preventDefault();
            App.clearErrors();
            try {
                const data = await App.post('/api/auth/phone/login', {
                    phone: $('#phone').value.trim(),
                    code: $('#code').value.trim(),
                    rememberMe: $('#rememberMePhone').checked
                });
                App.toast(data.isNewUser ? '注册成功，已自动登录' : '登录成功', 'ok');
                redirectAfterLogin();
            } catch (err) {
                App.showErrors({ code: err.message });
                App.toast(err.message, 'err');
            }
        };
    }

    function startCountdown() {
        const btn = $('#btn-code');
        countdown = 60;
        btn.disabled = true;
        const timer = setInterval(() => {
            btn.textContent = countdown + ' 秒后重发';
            countdown--;
            if (countdown < 0) {
                clearInterval(timer);
                btn.disabled = false;
                btn.textContent = '重新获取';
            }
        }, 1000);
    }

    function initRegister() {
        const form = $('#form-register');
        form.onsubmit = async (e) => {
            e.preventDefault();
            App.clearErrors();
            const btn = $('#btn-register');
            const body = {
                username: form.username.value.trim(),
                password: form.password.value,
                confirm: form.confirm.value,
                phone: form.phone.value.trim(),
                nickname: form.nickname.value.trim(),
                address: form.address.value.trim()
            };
            btn.disabled = true; btn.textContent = '提交中…';
            try {
                const data = await App.post('/api/auth/register', body);
                App.toast('注册成功，已自动登录', 'ok');
                location.href = App.base + '/static/index.html';
            } catch (err) {
                // 后端返回 {form, errors}，直接回显
                if (err.data && err.data.form) App.fillForm(form, err.data.form);
                App.showErrors(err.data && err.data.errors ? err.data.errors : { username: err.message });
                App.toast(err.message, 'err', 3200);
            } finally {
                btn.disabled = false; btn.textContent = '立即注册';
            }
        };
    }
})();
