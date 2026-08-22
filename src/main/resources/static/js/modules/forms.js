import { formatBytes, showToast } from "./core.js";

const MAX_UPLOAD_SIZE = 200 * 1024 * 1024;

const getAcceptedExtensions = (input) => (input.accept || "")
    .split(",")
    .map((item) => item.trim().toLowerCase())
    .filter((item) => item.startsWith("."));

const createUploadPreview = (input) => {
    const preview = document.createElement("div");
    preview.className = "upload-preview";
    preview.hidden = true;
    preview.setAttribute("aria-live", "polite");

    const name = document.createElement("strong");
    name.className = "upload-preview-name";

    const meta = document.createElement("span");
    meta.className = "upload-preview-meta";

    preview.append(name, meta);
    input.insertAdjacentElement("afterend", preview);

    return { preview, name, meta };
};

const setupFilePreviews = () => {
    document.querySelectorAll('input[type="file"]').forEach((input) => {
        const acceptedExtensions = getAcceptedExtensions(input);
        const preview = createUploadPreview(input);

        input.addEventListener("change", () => {
            const file = input.files && input.files[0];
            input.setCustomValidity("");

            if (!file) {
                preview.preview.hidden = true;
                return;
            }

            const extension = `.${file.name.split(".").pop().toLowerCase()}`;
            const allowed = acceptedExtensions.length === 0
                || acceptedExtensions.includes(extension);

            if (!allowed) {
                input.setCustomValidity(`Chỉ nhận tệp ${acceptedExtensions.join(", ")}.`);
                input.reportValidity();
                showToast("Định dạng tệp chưa đúng.", "error");
            } else if (file.size > MAX_UPLOAD_SIZE) {
                input.setCustomValidity("Tệp vượt quá giới hạn 200 MB.");
                input.reportValidity();
                showToast("Tệp vượt quá giới hạn 200 MB.", "error");
            }

            preview.name.textContent = file.name;
            preview.meta.textContent = `${extension.toUpperCase().replace(".", "")} · ${formatBytes(file.size)}`;
            preview.preview.hidden = false;
        });
    });
};

const setupDirtyFormWarning = () => {
    const dirtyForms = new WeakSet();
    const submittedForms = new WeakSet();

    document.querySelectorAll('form[method="post"], form[enctype]').forEach((form) => {
        if (form.matches("[data-login-form]")) return;

        form.addEventListener("input", () => dirtyForms.add(form));
        form.addEventListener("change", () => dirtyForms.add(form));
        form.addEventListener("submit", () => submittedForms.add(form));
    });

    window.addEventListener("beforeunload", (event) => {
        const hasDirtyForm = Array.from(document.forms).some((form) => (
            dirtyForms.has(form) && !submittedForms.has(form)
        ));

        if (!hasDirtyForm) return;

        event.preventDefault();
        event.returnValue = "";
    });
};

const setupSubmitState = () => {
    document.addEventListener("submit", (event) => {
        if (event.defaultPrevented) return;

        const form = event.target;
        if (!(form instanceof HTMLFormElement)) return;
        if (form.matches("[data-login-form]")) return;

        if (!form.checkValidity()) {
            const invalidField = form.querySelector(":invalid");
            if (invalidField) invalidField.focus({ preventScroll: false });
            showToast("Vui lòng kiểm tra lại các trường bắt buộc.", "error");
            return;
        }

        const submitter = event.submitter;
        form.setAttribute("aria-busy", "true");
        form.classList.add("is-submitting");

        if (submitter instanceof HTMLButtonElement) {
            submitter.dataset.originalText = submitter.textContent;
            submitter.disabled = true;
            submitter.textContent = "Đang xử lý...";
        }
    });
};

const setupReviewDecision = () => {
    const variants = ["button-success", "button-warning", "button-danger"];
    const decisions = {
        APPROVED: {
            className: "button-success",
            label: "Phê duyệt tài liệu",
            confirmLabel: "Xác nhận phê duyệt",
            message: "Phê duyệt tài liệu này? Quyết định sẽ được ghi vào lịch sử kiểm duyệt."
        },
        REVISION_REQUESTED: {
            className: "button-warning",
            label: "Yêu cầu chỉnh sửa",
            confirmLabel: "Gửi yêu cầu sửa",
            message: "Gửi yêu cầu chỉnh sửa cho tác giả? Hãy kiểm tra nội dung góp ý trước khi tiếp tục."
        },
        REJECTED: {
            className: "button-danger",
            label: "Từ chối tài liệu",
            confirmLabel: "Xác nhận từ chối",
            message: "Từ chối tài liệu này? Quyết định sẽ được ghi vào lịch sử kiểm duyệt."
        },
        PUBLISHED: {
            className: "button-success",
            label: "Công bố tài liệu",
            confirmLabel: "Xác nhận công bố",
            message: "Công bố tài liệu này vào kho học liệu công khai?"
        }
    };

    document.querySelectorAll("[data-review-action]").forEach((select) => {
        const form = select.closest("form");
        const submitButton = form?.querySelector("[data-review-submit]");
        const submitLabel = submitButton?.querySelector("[data-review-submit-label]");
        if (!submitButton || !submitLabel) return;

        const updateDecision = () => {
            const decision = decisions[select.value];
            submitButton.classList.remove(...variants);

            if (!decision) {
                submitLabel.textContent = "Lưu kết quả";
                submitButton.dataset.confirmLabel = "Lưu quyết định";
                submitButton.dataset.confirmMessage = "Lưu quyết định kiểm duyệt này? Kết quả sẽ được ghi vào lịch sử tài liệu.";
                return;
            }

            submitButton.classList.add(decision.className);
            submitLabel.textContent = decision.label;
            submitButton.dataset.confirmLabel = decision.confirmLabel;
            submitButton.dataset.confirmMessage = decision.message;
        };

        select.addEventListener("change", updateDecision);
        updateDecision();
    });
};

const setupAutoTrim = () => {
    const selector = [
        'input[type="email"]',
        'input[type="search"]',
        'input[type="text"]',
        'input:not([type])',
        "textarea"
    ].join(",");

    document.querySelectorAll(selector).forEach((input) => {
        input.addEventListener("blur", () => {
            input.value = input.value.trim();
        });
    });
};

const setupFacultyDepartmentSelects = () => {
    document.querySelectorAll("[data-faculty-select]").forEach((facultySelect) => {
        const form = facultySelect.closest("form");
        const departmentSelect = form?.querySelector("[data-department-select]");
        if (!departmentSelect) return;

        const updateDepartments = () => {
            const facultyId = facultySelect.value;
            Array.from(departmentSelect.options).forEach((option, index) => {
                if (index === 0) return;
                option.hidden = Boolean(facultyId) && option.dataset.facultyId !== facultyId;
                option.disabled = Boolean(facultyId) && option.dataset.facultyId !== facultyId;
            });
            if (departmentSelect.selectedOptions[0]?.disabled) departmentSelect.value = "";
            departmentSelect.disabled = !facultyId;
        };

        facultySelect.addEventListener("change", updateDepartments);
        updateDepartments();
    });
};

export const initForms = () => {
    setupFilePreviews();
    setupDirtyFormWarning();
    setupReviewDecision();
    setupSubmitState();
    setupAutoTrim();
    setupFacultyDepartmentSelects();
};
