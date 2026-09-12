export const initAdminDashboard = () => {
    const dashboard = document.querySelector("[data-admin-dashboard]");
    if (!dashboard) return;

    dashboard.querySelectorAll("[data-count-up]").forEach((element) => {
        const target = Number.parseInt(element.dataset.countUp ?? "0", 10);
        if (!Number.isFinite(target) || window.matchMedia("(prefers-reduced-motion: reduce)").matches) {
            element.textContent = String(target);
            return;
        }

        const startedAt = performance.now();
        const duration = 650;
        const tick = (now) => {
            const progress = Math.min((now - startedAt) / duration, 1);
            const eased = 1 - Math.pow(1 - progress, 3);
            element.textContent = String(Math.round(target * eased));
            if (progress < 1) requestAnimationFrame(tick);
        };
        requestAnimationFrame(tick);
    });

    const tabs = [...dashboard.querySelectorAll("[data-dashboard-tab]")];
    const panels = [...dashboard.querySelectorAll("[data-dashboard-panel]")];
    tabs.forEach((tab) => tab.addEventListener("click", () => {
        tabs.forEach((item) => {
            const active = item === tab;
            item.classList.toggle("is-active", active);
            item.setAttribute("aria-selected", String(active));
        });
        panels.forEach((panel) => {
            panel.hidden = panel.dataset.dashboardPanel !== tab.dataset.dashboardTab;
        });
    }));

};
