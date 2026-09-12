import { normalizeText, safeStorage } from "./core.js";

const setupCategoryFilters = () => {
    const filters = Array.from(document.querySelectorAll("[data-category-filter]"));
    const cards = Array.from(document.querySelectorAll("[data-document-card]"));
    const status = document.querySelector("[data-filter-status]");
    const visibleCount = document.querySelector("[data-visible-count]");
    const emptyState = document.querySelector("[data-filter-empty]");
    const resetButton = document.querySelector("[data-reset-filter]");
    const searchInput = document.querySelector("[data-search-input]");

    if (filters.length === 0 || cards.length === 0) return;

    const state = {
        category: "",
        categoryLabel: "Tất cả",
        // Text search is performed by the server across all metadata and pages.
        query: ""
    };

    const getSearchText = (card) => normalizeText([
        card.dataset.title,
        card.dataset.category,
        card.textContent
    ].join(" "));

    const applyFilter = () => {
        const normalizedFilter = normalizeText(state.category);
        const normalizedQuery = normalizeText(state.query);
        let count = 0;

        cards.forEach((card) => {
            const categoryMatches = !normalizedFilter
                || normalizeText(card.dataset.category) === normalizedFilter;
            const searchMatches = !normalizedQuery
                || getSearchText(card).includes(normalizedQuery);
            const matches = categoryMatches && searchMatches;

            card.hidden = !matches;
            if (matches) count += 1;
        });

        filters.forEach((button) => {
            const active = normalizeText(button.dataset.categoryFilter) === normalizedFilter;
            button.classList.toggle("active", active);
            button.setAttribute("aria-pressed", String(active));
        });

        if (visibleCount) visibleCount.textContent = String(count);
        if (status) {
            const details = [];
            if (normalizedFilter) details.push(`danh mục: ${state.categoryLabel}`);
            if (normalizedQuery) details.push(`từ khóa: ${state.query}`);

            status.textContent = details.length > 0
                ? `Đang lọc ${details.join(", ")} (${count} tài liệu trong trang)`
                : "Đang hiển thị tất cả tài liệu";
        }
        if (emptyState) emptyState.hidden = count !== 0;
    };

    filters.forEach((button) => {
        button.addEventListener("click", () => {
            state.category = button.dataset.categoryFilter || "";
            state.categoryLabel = button.textContent.trim() || "Tất cả";
            applyFilter();
        });
    });

    if (resetButton) {
        resetButton.addEventListener("click", () => {
            state.category = "";
            state.categoryLabel = "Tất cả";
            state.query = "";
            if (searchInput) searchInput.value = "";
            applyFilter();
            filters[0].focus();
        });
    }

    applyFilter();
};

const setupDocumentSorting = () => {
    const select = document.querySelector("[data-document-sort]");
    const grid = document.querySelector("[data-document-grid]");
    if (!select || !grid) return;

    const cards = Array.from(grid.querySelectorAll("[data-document-card]"));
    cards.forEach((card, index) => {
        card.dataset.originalOrder = String(index);
    });

    const compareTitle = (first, second) => first.dataset.title.localeCompare(
        second.dataset.title,
        "vi",
        { sensitivity: "base" }
    );

    select.addEventListener("change", () => {
        const sorted = [...cards];

        if (select.value === "title-asc") sorted.sort(compareTitle);
        if (select.value === "title-desc") {
            sorted.sort((first, second) => compareTitle(second, first));
        }
        if (select.value === "newest") {
            sorted.sort((first, second) => {
                const firstTime = Date.parse(first.dataset.date || "") || 0;
                const secondTime = Date.parse(second.dataset.date || "") || 0;
                const dateDifference = secondTime - firstTime;

                return dateDifference
                    || Number(first.dataset.originalOrder) - Number(second.dataset.originalOrder);
            });
        }

        sorted.forEach((card) => grid.appendChild(card));
    });
};

const setupViewSwitcher = () => {
    const grid = document.querySelector("[data-document-grid]");
    const buttons = Array.from(document.querySelectorAll("[data-view]"));
    if (!grid || buttons.length === 0) return;

    const applyView = (view, persist = true) => {
        const nextView = view === "list" ? "list" : "grid";
        grid.classList.toggle("is-list-view", nextView === "list");

        buttons.forEach((button) => {
            const active = button.dataset.view === nextView;
            button.classList.toggle("active", active);
            button.setAttribute("aria-pressed", String(active));
        });

        if (persist) safeStorage.set("edurepo-document-view", nextView);
    };

    buttons.forEach((button) => {
        button.addEventListener("click", () => applyView(button.dataset.view));
    });

    applyView(safeStorage.get("edurepo-document-view") || "grid", false);
};

const setupSearchShortcut = () => {
    const input = document.querySelector("[data-search-input]");
    if (!input) return;

    document.addEventListener("keydown", (event) => {
        const target = event.target;
        const editing = target instanceof HTMLInputElement
            || target instanceof HTMLTextAreaElement
            || target instanceof HTMLSelectElement
            || target?.isContentEditable;

        if (event.key !== "/" || editing || event.ctrlKey || event.metaKey || event.altKey) {
            return;
        }

        event.preventDefault();
        input.focus();
    });
};

export const initCatalog = () => {
    setupCategoryFilters();
    setupDocumentSorting();
    setupViewSwitcher();
    setupSearchShortcut();
};
