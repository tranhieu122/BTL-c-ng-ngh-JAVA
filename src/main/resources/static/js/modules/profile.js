/**
 * Module quản lý hồ sơ cá nhân và đổi mật khẩu (Profile Script).
 * Xử lý cắt chỉnh xem trước ảnh đại diện (Avatar Preview) và kiểm tra khớp mật khẩu mới.
 */

export function avatarFileError(file) {
    if (!file || !file.size || file.size > 2 * 1024 * 1024 || !/\.(png|jpe?g)$/i.test(file.name)
        || !["image/png", "image/jpeg"].includes(file.type)) return "Chọn ảnh PNG hoặc JPEG, dung lượng tối đa 2 MB.";
    return "";
}

export function initProfile() {
    const page = document.querySelector("[data-profile-page]");
    if (!page || page.dataset.initialized) return;
    page.dataset.initialized = "true";
    const input = page.querySelector("[data-avatar-input]");
    const preview = page.querySelector("[data-avatar-preview]");
    const error = page.querySelector("[data-avatar-error]");
    const original = preview.src;
    let objectUrl;
    let selection = 0;
    const resetPreview = () => { if (objectUrl) URL.revokeObjectURL(objectUrl); objectUrl = null; };
    input.addEventListener("change", () => {
        const ticket = ++selection;
        resetPreview();
        preview.src = original;
        const file = input.files[0];
        const message = file ? avatarFileError(file) : "";
        error.textContent = message; input.setCustomValidity(message);
        if (!file || message) return;
        input.setCustomValidity("Đang kiểm tra ảnh, vui lòng chờ.");
        objectUrl = URL.createObjectURL(file);
        const candidate = new Image();
        candidate.onload = () => {
            if (ticket !== selection) return;
            const oversized = candidate.naturalWidth > 4096 || candidate.naturalHeight > 4096 || candidate.naturalWidth * candidate.naturalHeight > 16_000_000;
            const validation = oversized ? "Ảnh vượt giới hạn kích thước cho phép." : "";
            error.textContent = validation; input.setCustomValidity(validation);
            if (!validation) preview.src = objectUrl;
            else resetPreview();
        };
        candidate.onerror = () => {
            if (ticket !== selection) return;
            error.textContent = "Không thể đọc ảnh này."; input.setCustomValidity(error.textContent); resetPreview();
        };
        candidate.src = objectUrl;
    });
    window.addEventListener("pagehide", resetPreview);
    const passwordForm = page.querySelector("[data-password-form]");
    const next = passwordForm.elements.newPassword, confirmation = passwordForm.elements.confirmPassword;
    const validatePasswords = () => {
        next.setCustomValidity(new TextEncoder().encode(next.value).length > 72 ? "Mật khẩu tối đa 72 byte UTF-8." : "");
        confirmation.setCustomValidity(confirmation.value && confirmation.value !== next.value ? "Xác nhận mật khẩu mới không khớp." : "");
    };
    next.addEventListener("input", validatePasswords); confirmation.addEventListener("input", validatePasswords);
    page.querySelectorAll("form").forEach(form => form.addEventListener("submit", event => {
        if (event.defaultPrevented || !form.checkValidity()) return;
        page.querySelector("[data-profile-loading]").hidden = false;
        form.closest(".profile-card").setAttribute("aria-busy", "true");
    }));
    window.addEventListener("pageshow", () => {
        page.querySelector("[data-profile-loading]").hidden = true;
        page.querySelectorAll(".profile-card").forEach(card => card.removeAttribute("aria-busy"));
    });
}
