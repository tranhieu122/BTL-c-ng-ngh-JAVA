/**
 * Module xử lý các thao tác nhanh trên tài liệu (Document Actions Script).
 * Thao tác bật/tắt yêu thích (Bookmark), sao chép liên kết chia sẻ, và hiển thị hộp thoại báo cáo vi phạm.
 */

import { showToast } from "./core.js";

const copyText = async (value) => {
    if (navigator.clipboard && window.isSecureContext) {
        await navigator.clipboard.writeText(value);
        return;
    }

    const helper = document.createElement("textarea");
    helper.value = value;
    helper.style.position = "fixed";
    helper.style.opacity = "0";
    document.body.appendChild(helper);
    helper.focus();
    helper.select();
    document.execCommand("copy");
    helper.remove();
};

const setupSharing = () => {
    document.querySelectorAll("[data-share-link]").forEach((button) => {
        button.addEventListener("click", async () => {
            const title = button.dataset.shareTitle || document.title;
            const shareData = {
                title,
                text: `Xem tài liệu “${title}” trên EduRepo`,
                url: window.location.href
            };

            try {
                if (navigator.share) {
                    await navigator.share(shareData);
                    return;
                }

                await copyText(window.location.href);
                showToast("Đã sao chép liên kết tài liệu");
            } catch (error) {
                if (error && error.name === "AbortError") return;
                showToast("Chưa thể chia sẻ liên kết. Vui lòng thử lại.", "error");
            }
        });
    });
};

export const initDocumentActions = () => {
    setupSharing();
};
