const setupPasswordToggle = () => {
    document.querySelectorAll("[data-password-toggle]").forEach((toggle) => {
        const inputId = toggle.getAttribute("aria-controls");
        const input = inputId ? document.getElementById(inputId) : null;
        const label = toggle.querySelector("[data-password-label]");
        const icon = toggle.querySelector("[data-password-icon]");
        if (!(input instanceof HTMLInputElement)) return;

        toggle.addEventListener("click", () => {
            const show = input.type === "password";
            input.type = show ? "text" : "password";
            toggle.setAttribute("aria-pressed", String(show));

            if (label) label.textContent = show ? "Ẩn mật khẩu" : "Hiện mật khẩu";
            if (icon) icon.textContent = show ? "◌" : "◉";
            input.focus({ preventScroll: true });
        });
    });
};

const setupCapsLockWarning = () => {
    const input = document.querySelector("[data-password-input]");
    const warning = document.querySelector("[data-caps-warning]");
    if (!input || !warning) return;

    const update = (event) => {
        warning.hidden = !event.getModifierState("CapsLock");
    };

    input.addEventListener("keydown", update);
    input.addEventListener("keyup", update);
    input.addEventListener("blur", () => {
        warning.hidden = true;
    });
};

const setupLoginForm = () => {
    const form = document.querySelector("[data-login-form]");
    const button = document.querySelector("[data-submit-button]");
    if (!form || !button) return;

    form.addEventListener("submit", () => {
        form.setAttribute("aria-busy", "true");
        button.classList.add("is-loading");
        button.setAttribute("aria-label", "Đang đăng nhập");

        window.requestAnimationFrame(() => {
            button.disabled = true;
        });
    });
};

export const initAuth = () => {
    setupPasswordToggle();
    setupCapsLockWarning();
    setupLoginForm();
};
