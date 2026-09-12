export const initReviewQueue = () => {
    const list = document.querySelector("[data-review-list]");
    if (!list || list.dataset.initialized) return;
    list.dataset.initialized = "true";


    const search = document.querySelector("[data-review-search]");
    const status = document.querySelector("[data-review-status]");
    const count = document.querySelector("[data-review-count]");
    const empty = document.querySelector("[data-review-empty]");
    const reset = document.querySelector("[data-review-reset]");

    const normalize = (value) => value.trim().toLocaleLowerCase("vi");

    const applyFilters = () => {
        const keyword = normalize(search?.value ?? "");
        const selectedStatus = status?.value ?? "all";
        let visible = 0;

        list.querySelectorAll("[data-review-item]").forEach((card) => {
            const haystack = normalize(`${card.dataset.title ?? ""} ${card.dataset.owner ?? ""}`);
            const matchesText = !keyword || haystack.includes(keyword);
            const matchesStatus = selectedStatus === "all" || card.dataset.status === selectedStatus;
            card.hidden = !(matchesText && matchesStatus);
            if (!card.hidden) visible += 1;
        });

        if (count) count.textContent = `${visible} tài liệu`;
        if (empty) empty.hidden = visible !== 0;
        list.hidden = visible === 0;
    };

    list.addEventListener("realtime:updated", applyFilters);
    applyFilters();

    search?.addEventListener("input", applyFilters);
    status?.addEventListener("change", applyFilters);
    reset?.addEventListener("click", () => {
        if (search) search.value = "";
        if (status) status.value = "all";
        applyFilters();
        search?.focus();
    });
};
