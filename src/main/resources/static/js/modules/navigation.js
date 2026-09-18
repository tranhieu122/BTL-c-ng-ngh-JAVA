const markBestRouteMatch = (selector) => {
    const currentPath = window.location.pathname.replace(/\/$/, "") || "/";
    const candidates = Array.from(document.querySelectorAll(selector))
        .map((link) => {
            const path = new URL(link.href, window.location.origin)
                .pathname
                .replace(/\/$/, "") || "/";
            const exact = currentPath === path;
            const section = path !== "/" && currentPath.startsWith(`${path}/`);

            return { link, path, matches: exact || section };
        })
        .filter((item) => item.matches)
        .sort((first, second) => second.path.length - first.path.length);

    if (candidates.length === 0) return;
    candidates[0].link.classList.add("active");
    candidates[0].link.setAttribute("aria-current", "page");
};

const markActiveNavigation = () => {
    markBestRouteMatch("[data-sidebar-link]");
    markBestRouteMatch("[data-nav-link]");
};

const setupMobileNavigation = () => {
    const toggle = document.querySelector("[data-nav-toggle]");
    const navigation = document.querySelector("[data-primary-nav]");
    const backdrop = document.querySelector("[data-nav-backdrop]");

    if (!toggle || !navigation || !backdrop) return;

    let previousFocus = null;
    const focusableElements = () => Array.from(navigation.querySelectorAll(
        'a[href]:not([hidden]), button:not([disabled]):not([hidden]), [tabindex]:not([tabindex="-1"])'
    ));

    const setOpen = (open, restoreFocus = true) => {
        if (open) previousFocus = document.activeElement;
        document.body.classList.toggle("nav-open", open);
        navigation.classList.toggle("is-open", open);
        toggle.setAttribute("aria-expanded", String(open));

        const label = toggle.querySelector(".sr-only");
        if (label) {
            label.textContent = open
                ? "Đóng menu điều hướng"
                : "Mở menu điều hướng";
        }

        if (open) {
            window.requestAnimationFrame(() => focusableElements()[0]?.focus());
        } else if (restoreFocus && previousFocus instanceof HTMLElement) {
            previousFocus.focus({ preventScroll: true });
            previousFocus = null;
        }
    };

    toggle.addEventListener("click", () => {
        setOpen(toggle.getAttribute("aria-expanded") !== "true");
    });

    backdrop.addEventListener("click", () => setOpen(false));
    navigation.querySelectorAll("a").forEach((link) => {
        link.addEventListener("click", () => setOpen(false, false));
    });

    document.addEventListener("keydown", (event) => {
        if (toggle.getAttribute("aria-expanded") !== "true") return;

        if (event.key === "Escape") {
            event.preventDefault();
            setOpen(false);
            return;
        }

        if (event.key !== "Tab") return;
        const focusable = focusableElements();
        if (focusable.length === 0) return;
        const first = focusable[0];
        const last = focusable[focusable.length - 1];
        if (event.shiftKey && document.activeElement === first) {
            event.preventDefault();
            last.focus();
        } else if (!event.shiftKey && document.activeElement === last) {
            event.preventDefault();
            first.focus();
        }
    });

    window.addEventListener("resize", () => {
        if (window.innerWidth > 900) setOpen(false, false);
    });
};

const setupAccountMenu = () => {
    const menu = document.querySelector("[data-account-menu]");
    const toggle = menu?.querySelector("[data-account-toggle]");
    const panel = menu?.querySelector("[data-account-panel]");
    if (!menu || !toggle || !panel) return;
    const close = (restoreFocus = false) => {
        panel.hidden = true;
        toggle.setAttribute("aria-expanded", "false");
        if (restoreFocus) toggle.focus({ preventScroll: true });
    };
    toggle.addEventListener("click", () => {
        const opening = panel.hidden;
        panel.hidden = !opening;
        toggle.setAttribute("aria-expanded", String(opening));
    });
    document.addEventListener("click", (event) => { if (!menu.contains(event.target)) close(); });
    document.addEventListener("keydown", (event) => {
        if (event.key === "Escape" && !panel.hidden) {
            event.preventDefault();
            close(true);
        }
    });
};

export const initNavigation = () => {
    markActiveNavigation();
    setupMobileNavigation();
    setupAccountMenu();
};
