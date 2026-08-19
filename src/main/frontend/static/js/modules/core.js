export const ready = (callback) => {
    if (document.readyState === "loading") {
        document.addEventListener("DOMContentLoaded", callback, { once: true });
        return;
    }
    callback();
};

export const normalizeText = (value) => (value || "")
    .toLocaleLowerCase("vi")
    .normalize("NFD")
    .replace(/[\u0300-\u036f]/g, "")
    .trim();

export const safeStorage = {
    get(key) {
        try {
            return window.localStorage.getItem(key);
        } catch (error) {
            return null;
        }
    },

    set(key, value) {
        try {
            window.localStorage.setItem(key, value);
        } catch (error) {
            // Device preferences are optional when browser storage is unavailable.
        }
    }
};

export const debounce = (callback, delay = 180) => {
    let timeoutId;

    return (...args) => {
        window.clearTimeout(timeoutId);
        timeoutId = window.setTimeout(() => callback(...args), delay);
    };
};

export const showToast = (message, type = "success") => {
    const region = document.querySelector("[data-toast-region]");
    if (!region) return;

    const toast = document.createElement("div");
    toast.className = `toast toast-${type}`;
    toast.setAttribute("role", "status");

    const icon = document.createElement("span");
    icon.className = "toast-icon";
    icon.setAttribute("aria-hidden", "true");
    icon.textContent = type === "success" ? "✓" : "!";

    const copy = document.createElement("span");
    copy.textContent = message;

    toast.append(icon, copy);
    region.appendChild(toast);
    window.requestAnimationFrame(() => toast.classList.add("is-visible"));

    window.setTimeout(() => {
        toast.classList.remove("is-visible");
        window.setTimeout(() => toast.remove(), 220);
    }, 3200);
};

const setCurrentYear = () => {
    document.querySelectorAll("[data-current-year]").forEach((element) => {
        element.textContent = String(new Date().getFullYear());
    });
};

const ensureMainTarget = () => {
    if (document.getElementById("main-content")) return;
    const main = document.querySelector("main");
    if (main) main.id = "main-content";
};

const getFilePresentation = (rawType) => {
    const type = normalizeText(rawType);

    if (type.includes("pdf")) {
        return { label: "PDF", className: "type-pdf", name: "Tài liệu PDF" };
    }
    if (type.includes("word") || type.includes("doc")) {
        return { label: "DOCX", className: "type-doc", name: "Tài liệu Word" };
    }
    if (type.includes("presentation") || type.includes("ppt")) {
        return { label: "PPTX", className: "type-ppt", name: "Bài trình chiếu" };
    }
    if (type.includes("sheet") || type.includes("excel") || type.includes("xls")) {
        return { label: "XLSX", className: "type-xls", name: "Bảng tính" };
    }
    if (type.includes("text")) {
        return { label: "TXT", className: "type-text", name: "Tệp văn bản" };
    }
    if (type.includes("zip") || type.includes("rar")) {
        return { label: "ZIP", className: "type-zip", name: "Tệp nén" };
    }

    return { label: "FILE", className: "type-file", name: rawType || "Tài liệu" };
};

const enhanceFileTypes = () => {
    document.querySelectorAll("[data-file-type]").forEach((element) => {
        const presentation = getFilePresentation(element.dataset.fileType);

        if (element.classList.contains("js-file-type")) {
            element.textContent = presentation.name;
            return;
        }

        element.classList.add(presentation.className);
        const label = element.querySelector("[data-file-label]");
        if (label) label.textContent = presentation.label;
    });
};

export const formatBytes = (bytes) => {
    const value = Number(bytes);
    if (!Number.isFinite(value) || value < 0) return null;
    if (value === 0) return "0 B";

    const units = ["B", "KB", "MB", "GB", "TB"];
    const unitIndex = Math.min(
        Math.floor(Math.log(value) / Math.log(1024)),
        units.length - 1
    );
    const result = value / Math.pow(1024, unitIndex);
    const formatted = new Intl.NumberFormat("vi-VN", {
        maximumFractionDigits: result >= 10 || unitIndex === 0 ? 0 : 1
    }).format(result);

    return `${formatted} ${units[unitIndex]}`;
};

const enhanceFileSizes = () => {
    document.querySelectorAll(".js-file-size[data-bytes]").forEach((element) => {
        const formatted = formatBytes(element.dataset.bytes);
        if (formatted) element.textContent = formatted;
    });
};

const enhanceDates = () => {
    const formatter = new Intl.DateTimeFormat("vi-VN", {
        day: "2-digit",
        month: "2-digit",
        year: "numeric"
    });

    document.querySelectorAll("time.js-date[datetime]").forEach((element) => {
        const date = new Date(element.getAttribute("datetime"));
        if (Number.isNaN(date.getTime())) return;

        element.textContent = formatter.format(date);
        element.title = new Intl.DateTimeFormat("vi-VN", {
            dateStyle: "full",
            timeStyle: "short"
        }).format(date);
    });
};

const setupDismissibleAlerts = () => {
    document.querySelectorAll("[data-dismiss-alert]").forEach((button) => {
        button.addEventListener("click", () => {
            const alert = button.closest(".alert");
            if (!alert) return;

            alert.classList.add("is-dismissing");
            window.setTimeout(() => alert.remove(), 200);
        });
    });
};

const setupDangerConfirmations = () => {
    document.addEventListener("submit", (event) => {
        const submitter = event.submitter;
        if (!submitter || !submitter.classList.contains("danger")) return;

        const message = submitter.dataset.confirmMessage
            || "Bạn chắc chắn muốn thực hiện thao tác này?";
        if (!window.confirm(message)) event.preventDefault();
    });
};

const setupAutoResizeTextareas = () => {
    document.querySelectorAll("textarea").forEach((textarea) => {
        const resize = () => {
            textarea.style.height = "auto";
            textarea.style.height = `${textarea.scrollHeight}px`;
        };

        textarea.addEventListener("input", resize);
        resize();
    });
};

const mirrorServerAlertsToToasts = () => {
    document.querySelectorAll(".alert.success, .alert.error").forEach((alert) => {
        const message = alert.textContent.trim();
        if (!message) return;

        showToast(message, alert.classList.contains("error") ? "error" : "success");
    });
};

export const setupCardReveal = () => {
    const cards = Array.from(document.querySelectorAll(".product-card"));
    if (cards.length === 0 || !("IntersectionObserver" in window)) return;

    const reduceMotion = window.matchMedia("(prefers-reduced-motion: reduce)").matches;
    if (reduceMotion) return;

    cards.forEach((card, index) => {
        card.classList.add("reveal-card");
        card.style.setProperty("--reveal-delay", `${Math.min(index % 4, 3) * 55}ms`);
    });

    const observer = new IntersectionObserver((entries) => {
        entries.forEach((entry) => {
            if (!entry.isIntersecting) return;

            entry.target.classList.add("is-revealed");
            observer.unobserve(entry.target);
        });
    }, { threshold: 0.08, rootMargin: "0px 0px -20px" });

    cards.forEach((card) => observer.observe(card));
};

export const initCoreUi = () => {
    setCurrentYear();
    ensureMainTarget();
    enhanceFileTypes();
    enhanceFileSizes();
    enhanceDates();
    setupDismissibleAlerts();
    setupDangerConfirmations();
    setupAutoResizeTextareas();
    mirrorServerAlertsToToasts();
};
