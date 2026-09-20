const setupPasswordToggle = () => {
    document.querySelectorAll("[data-password-toggle]").forEach((toggle) => {
        const inputId = toggle.getAttribute("aria-controls");
        const input = inputId ? document.getElementById(inputId) : null;
        if (!(input instanceof HTMLInputElement)) return;

        toggle.addEventListener("click", () => {
            const show = input.type === "password";
            input.type = show ? "text" : "password";
            toggle.setAttribute("aria-pressed", String(show));
            toggle.textContent = show ? "Ẩn" : "Hiện";
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
        button.setAttribute("aria-label", "Đang xử lý...");

        window.requestAnimationFrame(() => {
            button.disabled = true;
        });
    });
};

const setupOtpInputs = () => {
    const groups = document.querySelectorAll("[data-otp-group]");
    groups.forEach((group) => {
        const digits = Array.from(group.querySelectorAll(".otp-digit"));
        const targetInput = group.querySelector("[data-otp-target]");
        if (!digits.length || !targetInput) return;

        const syncToTarget = () => {
            const val = digits.map((d) => d.value.trim()).join("");
            targetInput.value = val;
            digits.forEach((d) => {
                if (d.value.trim()) {
                    d.classList.add("is-filled");
                } else {
                    d.classList.remove("is-filled");
                }
            });
        };

        if (targetInput.value) {
            const chars = targetInput.value.slice(0, digits.length).split("");
            chars.forEach((ch, idx) => {
                if (digits[idx]) digits[idx].value = ch;
            });
            syncToTarget();
        }

        digits.forEach((digit, index) => {
            digit.addEventListener("input", (e) => {
                const val = e.target.value.replace(/[^0-9]/g, "");
                e.target.value = val ? val.slice(-1) : "";
                syncToTarget();

                if (val && index < digits.length - 1) {
                    digits[index + 1].focus();
                    digits[index + 1].select();
                }
            });

            digit.addEventListener("keydown", (e) => {
                if (e.key === "Backspace" && !digit.value && index > 0) {
                    digits[index - 1].focus();
                    digits[index - 1].select();
                } else if (e.key === "ArrowLeft" && index > 0) {
                    digits[index - 1].focus();
                } else if (e.key === "ArrowRight" && index < digits.length - 1) {
                    digits[index + 1].focus();
                }
            });

            digit.addEventListener("paste", (e) => {
                e.preventDefault();
                const paste = (e.clipboardData || window.clipboardData).getData("text");
                const cleanDigits = paste.replace(/[^0-9]/g, "").slice(0, digits.length);
                if (!cleanDigits) return;

                cleanDigits.split("").forEach((ch, i) => {
                    if (digits[i]) digits[i].value = ch;
                });
                syncToTarget();

                const nextFocus = Math.min(cleanDigits.length, digits.length - 1);
                digits[nextFocus].focus();
            });

            digit.addEventListener("focus", () => {
                digit.select();
            });
        });
    });
};

const setupOtpCountdown = () => {
    const countdownEl = document.querySelector("[data-otp-countdown]");
    if (!countdownEl) return;

    let seconds = parseInt(countdownEl.getAttribute("data-seconds") || "0", 10);
    const button = countdownEl.closest("form") ? countdownEl.closest("form").querySelector("button") : null;
    const initialText = countdownEl.getAttribute("data-original-text") || "Gửi lại mã OTP";

    if (seconds > 0) {
        if (button) button.disabled = true;

        const timer = setInterval(() => {
            seconds -= 1;
            if (seconds <= 0) {
                clearInterval(timer);
                countdownEl.textContent = initialText;
                if (button) button.disabled = false;
            } else {
                countdownEl.textContent = `${initialText} (${seconds}s)`;
            }
        }, 1000);
        countdownEl.textContent = `${initialText} (${seconds}s)`;
    }
};

export const initAuth = () => {
    setupPasswordToggle();
    setupCapsLockWarning();
    setupLoginForm();
    setupOtpInputs();
    setupOtpCountdown();
};
