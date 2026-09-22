/**
 * Module quản lý trung tâm thông báo thời gian thực (Notifications Script).
 * Cập nhật số đếm trên biểu tượng chuông báo, hiển thị danh sách thông báo popup và đánh dấu đã đọc.
 */

function formatTime(value) {
    if (!value) return "";
    const date = new Date(value);
    return Number.isNaN(date.getTime()) ? "" : new Intl.DateTimeFormat("vi-VN", {
        day: "2-digit", month: "2-digit", hour: "2-digit", minute: "2-digit"
    }).format(date);
}

export function initNotifications() {
    const root = document.querySelector("[data-realtime][data-notifications-url]");
    if (!root) return;
    const list = root.querySelector("[data-notification-list]");
    const badge = root.querySelector("[data-notification-count]");
    const readAll = root.querySelector("[data-notification-read-all]");
    const baseUrl = root.dataset.notificationsUrl;
    const apiUrl = `${baseUrl}/api`;
    let unread = 0;
    let loading = false;

    const setUnread = count => {
        unread = Math.max(0, Number(count) || 0);
        badge.textContent = unread > 99 ? "99+" : String(unread);
        badge.hidden = unread === 0;
        readAll.hidden = unread === 0;
    };
    const request = (url, options = {}) => fetch(url, {
        credentials: "same-origin", cache: "no-store", ...options,
        headers: {...(options.headers || {}), Accept: "application/json",
            [root.dataset.csrfHeader]: root.dataset.csrfToken}
    });
    const refreshCount = async () => {
        try {
            const response = await request(`${apiUrl}/unread-count`);
            if (response.ok) setUnread((await response.json()).count);
        } catch { /* A reconnect or page refresh will try again. */ }
    };
    const openDocument = item => item.documentId
        ? `${item.type === "DOCUMENT_SUBMITTED" ? root.dataset.reviewDetailUrl : root.dataset.documentsUrl}/${item.documentId}`
        : null;
    const refreshList = async () => {
        if (loading) return;
        loading = true;
        list.classList.remove("is-empty", "is-error");
        list.classList.add("is-loading");
        list.setAttribute("aria-busy", "true");
        list.replaceChildren();
        try {
            const response = await request(`${apiUrl}?page=0&size=20`);
            if (!response.ok) throw new Error("notifications unavailable");
            const page = await response.json();
            list.replaceChildren();
            list.classList.remove("is-loading", "is-error");
            for (const item of page.items) {
                const row = document.createElement("li");
                if (!item.read) row.classList.add("is-unread");
                const button = document.createElement("button");
                button.type = "button";
                button.innerHTML = `<strong></strong><span></span><time></time>`;
                button.querySelector("strong").textContent = item.title;
                button.querySelector("span").textContent = item.message;
                button.querySelector("time").textContent = formatTime(item.createdAt);
                button.addEventListener("click", async () => {
                    if (!item.read) {
                        const marked = await request(`${apiUrl}/${item.id}/read`, {method: "POST"});
                        if (marked.ok) setUnread(unread - 1);
                    }
                    const destination = openDocument(item);
                    if (destination) window.location.assign(destination);
                    else window.location.assign(`${baseUrl}/${item.id}`);
                });
                row.append(button);
                list.append(row);
            }
            list.classList.toggle("is-empty", page.items.length === 0);
            await refreshCount();
        } catch {
            list.replaceChildren();
            list.classList.remove("is-loading", "is-empty");
            list.classList.add("is-error");
        } finally {
            loading = false;
            list.removeAttribute("aria-busy");
        }
    };

    root.addEventListener("toggle", () => { if (root.open) refreshList(); });
    readAll.addEventListener("click", async () => {
        const response = await request(`${apiUrl}/read-all`, {method: "POST"});
        if (response.ok) { setUnread(0); refreshList(); }
    });
    document.addEventListener("realtime:changed", refreshCount);
    refreshCount();
}
