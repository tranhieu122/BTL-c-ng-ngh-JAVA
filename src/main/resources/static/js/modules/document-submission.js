import { formatBytes, showToast } from "./core.js";

const DRAFT_KEY = "edurepo-document-submission-draft";
const MAX_FILE_SIZE = 200 * 1024 * 1024;
const acceptedExtensions = ["pdf", "doc", "docx"];

const extensionOf = (fileName) => fileName.split(".").pop()?.toLowerCase() || "";
const escapeCss = (value) => window.CSS?.escape ? CSS.escape(value) : value.replace(/[^a-zA-Z0-9_-]/g, "\\$&");

const setupTextCounters = (form) => {
    form.querySelectorAll("[data-count-field]").forEach((field) => {
        const counter = form.querySelector(`[data-counter-for="${escapeCss(field.name)}"]`);
        if (!counter) return;
        const limit = Math.max(0, Number(field.maxLength) || 0);
        const update = () => {
            const current = field.value.length;
            counter.textContent = limit ? `${current}/${limit}` : `${current}`;
            counter.classList.toggle("is-near-limit", limit > 0 && current >= limit * .85 && current <= limit);
            counter.classList.toggle("is-over-limit", limit > 0 && current > limit);
        };
        field.addEventListener("input", update);
        update();
    });
};

const setupDescriptionTools = (form) => {
    const description = form.querySelector("textarea[name='description']");
    if (!description) return;
    form.querySelectorAll("[data-description-template]").forEach((button) => {
        button.addEventListener("click", () => {
            const template = button.dataset.descriptionTemplate;
            if (!template) return;
            if (description.value.trim() && !window.confirm("Thay thế mô tả hiện tại bằng mẫu này?")) return;
            description.value = template;
            description.dispatchEvent(new Event("input", { bubbles: true }));
            description.focus();
        });
    });
};

const setupOrganizationSummary = (form) => {
    const faculty = form.querySelector("[data-faculty-select]");
    const department = form.querySelector("[data-department-select]");
    const summary = form.querySelector("[data-organization-summary]");
    if (!faculty || !department || !summary) return;
    const update = () => {
        const facultyText = faculty.selectedOptions[0]?.textContent?.trim();
        const departmentText = department.selectedOptions[0]?.textContent?.trim();
        const validFaculty = faculty.value && facultyText;
        const validDepartment = department.value && departmentText;
        summary.classList.toggle("is-visible", Boolean(validFaculty || validDepartment));
        summary.replaceChildren();
        if (validFaculty) {
            const label = document.createElement("strong");
            label.textContent = "Đơn vị tiếp nhận";
            const value = document.createElement("span");
            value.textContent = `${facultyText}${validDepartment ? ` · ${departmentText}` : ""}`;
            summary.append(label, value);
        }
    };
    faculty.addEventListener("change", () => window.setTimeout(update));
    department.addEventListener("change", update);
    update();
};

const setupFileZone = (form) => {
    const input = form.querySelector("[data-submission-file]");
    const zone = form.querySelector("[data-upload-zone]");
    const card = form.querySelector("[data-upload-file-card]");
    if (!input || !zone || !card) return;
    const name = card.querySelector("[data-upload-file-name]");
    const meta = card.querySelector("[data-upload-file-meta]");
    const icon = card.querySelector("[data-upload-file-icon]");
    const remove = card.querySelector("[data-upload-file-remove]");
    const fileInputChanged = () => {
        const file = input.files?.[0];
        input.setCustomValidity("");
        if (!file) { zone.classList.remove("has-file", "is-invalid"); card.classList.remove("is-visible"); return; }
        const extension = extensionOf(file.name);
        const validType = acceptedExtensions.includes(extension);
        const validSize = file.size <= MAX_FILE_SIZE;
        if (!validType || !validSize) {
            const message = !validType ? "Chỉ chấp nhận tệp PDF, DOC hoặc DOCX." : "Tệp vượt giới hạn 200 MB.";
            input.setCustomValidity(message);
            zone.classList.add("is-invalid");
            showToast(message, "error");
        } else zone.classList.remove("is-invalid");
        zone.classList.add("has-file");
        card.classList.add("is-visible");
        name.textContent = file.name;
        meta.textContent = `${extension.toUpperCase()} · ${formatBytes(file.size)}${validType && validSize ? " · Sẵn sàng tải lên" : " · Cần chọn lại"}`;
        icon.textContent = extension.toUpperCase();
        icon.classList.toggle("is-doc", extension !== "pdf");
        form.dispatchEvent(new Event("submission:changed"));
    };
    zone.addEventListener("click", (event) => {
        if (event.target === input || event.target.closest("[data-upload-file-remove]")) return;
        event.preventDefault();
        input.click();
    });
    zone.addEventListener("keydown", (event) => { if ((event.key === "Enter" || event.key === " ") && !event.target.closest("button")) { event.preventDefault(); input.click(); } });
    ["dragenter", "dragover"].forEach((type) => zone.addEventListener(type, (event) => { event.preventDefault(); zone.classList.add("is-dragging"); }));
    ["dragleave", "drop"].forEach((type) => zone.addEventListener(type, (event) => { event.preventDefault(); zone.classList.remove("is-dragging"); }));
    zone.addEventListener("drop", (event) => { if (event.dataTransfer?.files?.length) { input.files = event.dataTransfer.files; fileInputChanged(); } });
    input.addEventListener("change", fileInputChanged);
    remove?.addEventListener("click", (event) => { event.preventDefault(); event.stopPropagation(); input.value = ""; fileInputChanged(); input.focus(); });
    fileInputChanged();
};

const setupReadiness = (form) => {
    const layout = form.closest(".submission-layout") || form;
    const score = layout.querySelector("[data-readiness-score]");
    const message = layout.querySelector("[data-readiness-message]");
    if (!score || !message) return;
    const rules = [
        ["title", (value) => value.trim().length >= 8],
        ["description", (value) => value.trim().length >= 30],
        ["categoryId", (value) => Boolean(value)],
        ["facultyId", (value) => Boolean(value)],
        ["departmentId", (value) => Boolean(value)],
        ["file", () => Boolean(form.querySelector("[data-submission-file]")?.files?.length) || Boolean(form.dataset.editing === "true")]
    ];
    const update = () => {
        let done = 0;
        rules.forEach(([name, validator]) => {
            const field = form.elements.namedItem(name);
            const completed = validator(field?.value || "");
            const item = layout.querySelector(`[data-check="${name}"]`);
            item?.classList.toggle("is-done", completed);
            done += Number(completed);
        });
        const percent = Math.round(done / rules.length * 100);
        score.style.setProperty("--score", percent);
        score.querySelector("strong").textContent = `${percent}%`;
        message.textContent = percent === 100 ? "Hồ sơ đã sẵn sàng để gửi duyệt." : `Hoàn thiện thêm ${rules.length - done} mục để gửi duyệt.`;
        form.querySelectorAll(".submission-field").forEach((wrapper) => {
            const field = wrapper.querySelector("input,select,textarea");
            if (!field || !field.value) return wrapper.classList.remove("is-valid");
            wrapper.classList.toggle("is-valid", field.checkValidity());
        });
    };
    form.addEventListener("input", update);
    form.addEventListener("change", update);
    form.addEventListener("submission:changed", update);
    update();
};

const setupDraft = (form) => {
    if (form.dataset.editing === "true") return;
    const autosave = form.querySelector("[data-submission-autosave]");
    const key = `${DRAFT_KEY}:${form.dataset.draftOwner}`;
    const fields = ["title", "authorName", "description", "categoryId", "facultyId", "departmentId",
        "summary", "keywords", "learningResourceType", "educationLevel", "languageCode", "licenseType"];
    const unavailable = () => {
        if (autosave) autosave.textContent = "Không thể tự lưu trên trình duyệt. Hãy dùng nút Lưu bản nháp.";
    };
    try {
        // Discard the old shared draft; new drafts are scoped by account and browser tab.
        localStorage.removeItem(DRAFT_KEY);
        const saved = JSON.parse(sessionStorage.getItem(key) || "null");
        const hasSavedContent = saved && fields.some((name) => String(saved[name] || "").trim());
        if (hasSavedContent && autosave) {
            autosave.classList.add("has-restore-actions");
            autosave.replaceChildren(document.createTextNode("Có bản nháp chưa nộp. "));
            const restore = document.createElement("button");
            restore.type = "button";
            restore.textContent = "Khôi phục";
            restore.addEventListener("click", () => {
                fields.forEach((name) => {
                    if (saved[name] != null && form.elements.namedItem(name)) form.elements.namedItem(name).value = saved[name];
                });
                form.elements.namedItem("facultyId")?.dispatchEvent(new Event("change", { bubbles: true }));
                form.dispatchEvent(new Event("input", { bubbles: true }));
                form.dispatchEvent(new Event("change", { bubbles: true }));
                autosave.classList.remove("has-restore-actions");
                autosave.classList.add("is-saved");
                autosave.textContent = "Đã khôi phục bản nháp trên trình duyệt";
                showToast("Đã khôi phục bản nháp.", "success");
            });
            const discard = document.createElement("button");
            discard.type = "button";
            discard.textContent = "Bỏ";
            discard.addEventListener("click", () => {
                try { sessionStorage.removeItem(key); } catch { unavailable(); return; }
                autosave.classList.remove("has-restore-actions");
                autosave.textContent = "Bản nháp cũ đã được bỏ";
            });
            autosave.append(restore, document.createTextNode(" · "), discard);
        }
    } catch { unavailable(); }
    let timer;
    let retainDraft = true;
    document.querySelectorAll('form[action$="/logout"]').forEach((logout) => {
        logout.addEventListener("submit", () => { retainDraft = false; clearTimeout(timer); });
    });
    const persist = () => {
        if (!retainDraft) return;
        try {
            const draft = Object.fromEntries(fields.map((name) => [name, form.elements.namedItem(name)?.value || ""]));
            sessionStorage.setItem(key, JSON.stringify(draft));
            autosave?.classList.add("is-saved");
            if (autosave) autosave.textContent = "Đã lưu thông tin trong tab này; tệp cần chọn lại khi tải lại trang";
        } catch { unavailable(); }
    };
    const save = () => { clearTimeout(timer); timer = window.setTimeout(persist, 500); };
    form.addEventListener("input", save); form.addEventListener("change", save);
    form.addEventListener("submit", () => { clearTimeout(timer); persist(); });
    window.addEventListener("pagehide", () => { clearTimeout(timer); persist(); });
};

export const initDocumentSubmission = () => {
    try {
        const cleared = document.querySelector("[data-clear-submission-draft]");
        if (cleared) sessionStorage.removeItem(`${DRAFT_KEY}:${cleared.dataset.draftOwner}`);
    } catch { /* Storage is optional; server drafts remain available. */ }
    document.querySelectorAll('form[action$="/logout"]').forEach((form) => {
        form.addEventListener("submit", () => {
            try {
                Object.keys(sessionStorage).filter((key) => key.startsWith(DRAFT_KEY)).forEach((key) => sessionStorage.removeItem(key));
                localStorage.removeItem(DRAFT_KEY);
            } catch { /* Logout must still work if storage is blocked. */ }
        });
    });
    document.querySelectorAll("[data-document-submission]").forEach((form) => { setupTextCounters(form); setupDescriptionTools(form); setupOrganizationSummary(form); setupFileZone(form); setupReadiness(form); setupDraft(form); });
};
