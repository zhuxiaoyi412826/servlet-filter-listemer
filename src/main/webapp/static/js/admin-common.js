/** 后台 Tab 切换框架：点击菜单切换区块，并按需触发各模块的数据加载 */
window.AdminTabs = (function () {
    const registry = {};
    let current = null;

    /** 注册一个 Tab 的进入回调，切换到该 Tab 时执行 */
    function register(name, enter) {
        registry[name] = enter;
    }

    function show(name, force) {
        const pane = document.getElementById('tab-' + name);
        if (!pane) return;
        document.querySelectorAll('#admin-tabs button').forEach(b => {
            b.classList.toggle('active', b.dataset.tab === name);
        });
        document.querySelectorAll('.tab-pane').forEach(s => { s.hidden = s.id !== 'tab-' + name; });
        current = name;
        const enter = registry[name];
        if (enter) enter(force);
        try { history.replaceState(null, '', '#tab=' + name); } catch (e) { /* 忽略 */ }
    }

    document.addEventListener('DOMContentLoaded', () => {
        document.querySelectorAll('#admin-tabs button').forEach(b => {
            b.onclick = () => show(b.dataset.tab, true);
        });
        // 延后到各模块注册完成再执行首次加载
        const hash = (location.hash || '').replace(/^#/, '');
        const tab = hash.startsWith('tab=') ? hash.substring(4) : '';
        setTimeout(() => show(tab && document.getElementById('tab-' + tab) ? tab : 'dish', true), 0);
    });

    return { register, show, current: () => current };
})();
