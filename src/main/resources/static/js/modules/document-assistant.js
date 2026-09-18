const ERROR_MESSAGE = "Hiện chưa thể tải danh sách tài liệu. Bạn vui lòng thử lại sau.";

function element(tag, className, text) {
    const node = document.createElement(tag);
    if (className) node.className = className;
    if (text !== undefined) node.textContent = text;
    return node;
}

function formatDate(value) {
    if (!value) return "Chưa cập nhật";
    const date = new Date(value);
    if (Number.isNaN(date.getTime())) return "Chưa cập nhật";
    return new Intl.DateTimeFormat("vi-VN", { day: "2-digit", month: "2-digit", year: "numeric" }).format(date);
}

function currentTime() {
    return new Intl.DateTimeFormat("vi-VN", { hour: "2-digit", minute: "2-digit" }).format(new Date());
}

function appendMessage(container, message, type) {
    const row = element("div", `assistant-message-row assistant-message-row-${type}`);
    const content = element("div");
    const bubble = element("div", `assistant-message assistant-message-${type}`, message);
    const time = element("time", "", currentTime());
    if (type === "system") row.append(element("span", "assistant-message-avatar", "E"));
    content.append(bubble, time);
    row.append(content);
    container.append(row);
}

function appendUserMessage(container, message) {
    appendMessage(container, message, "user");
}

function appendAssistantResponse(container, response) {
    const group = element("div", "assistant-response");
    const row = element("div", "assistant-message-row assistant-message-row-system");
    const content = element("div");
    content.append(
        element("div", "assistant-message assistant-message-system", response.message || ERROR_MESSAGE),
        element("time", "", currentTime())
    );
    row.append(element("span", "assistant-message-avatar", "E"), content);
    group.append(row);

    if (Array.isArray(response.documents) && response.documents.length > 0) {
        const list = element("div", "assistant-result-list");
        response.documents.forEach((item) => {
            const card = element("article", "assistant-result-card");
            const heading = element("h3", "", item.title || "Tài liệu");
            const description = element("p", "", item.description || "Chưa có mô tả cho tài liệu này.");
            const metadata = element("dl", "assistant-result-meta");
            [["Danh mục", item.category], ["Tác giả", item.author], ["Xuất bản", formatDate(item.publishedAt)]]
                .forEach(([label, value]) => {
                    const rowItem = element("div");
                    rowItem.append(element("dt", "", label), element("dd", "", value || "Chưa cập nhật"));
                    metadata.append(rowItem);
                });
            const link = element("a", "assistant-result-link", "Xem tài liệu");
            link.href = item.detailUrl;
            link.setAttribute("aria-label", `Xem tài liệu ${item.title || ""}`.trim());
            link.append(element("span", "", " →"));
            const footer = element("div", "assistant-result-footer");
            footer.append(link);
            if (Array.isArray(item.actions) && item.actions.length > 0) {
                const quickActions = element("div", "assistant-result-actions");
                item.actions.forEach((action) => {
                    if (!action?.label || !action?.message) return;
                    const button = element("button", "", action.label);
                    button.type = "button";
                    button.dataset.assistantSuggestion = action.message;
                    quickActions.append(button);
                });
                footer.append(quickActions);
            }
            card.append(heading, description, metadata, footer);
            list.append(card);
        });
        group.append(list);
    }

    if (response.hasMore && response.allResultsUrl) {
        const allResults = element("a", "assistant-all-results", "Xem tất cả kết quả →");
        allResults.href = response.allResultsUrl;
        group.append(allResults);
    }

    if (Array.isArray(response.suggestions) && response.suggestions.length > 0) {
        const suggestions = element("div", "assistant-suggestions assistant-suggestions-response");
        const label = element("p", "", "Bạn có thể thử");
        const actions = element("div");
        response.suggestions.slice(0, 4).forEach((text) => {
            const button = element("button", "", text);
            button.type = "button";
            button.dataset.assistantSuggestion = text;
            actions.append(button);
        });
        suggestions.append(label, actions);
        group.append(suggestions);
    }
    container.append(group);
    container.scrollTo({ top: container.scrollHeight, behavior: "smooth" });
}

export function initDocumentAssistant() {
    const root = document.querySelector("[data-document-assistant]");
    if (!root || root.dataset.initialized === "true") return;
    root.dataset.initialized = "true";

    const openButton = root.querySelector("[data-assistant-open]");
    const closeButton = root.querySelector("[data-assistant-close]");
    const clearButton = root.querySelector("[data-assistant-clear]");
    const panel = root.querySelector("[data-assistant-panel]");
    const form = root.querySelector("[data-assistant-form]");
    const input = root.querySelector("[data-assistant-input]");
    const submitButton = root.querySelector("[data-assistant-submit]");
    const messages = root.querySelector("[data-assistant-messages]");
    const loading = root.querySelector("[data-assistant-loading]");
    const count = root.querySelector("[data-assistant-count]");
    if (!openButton || !closeButton || !clearButton || !panel || !form || !input || !submitButton || !messages || !loading || !count) return;

    const assistantUrl = root.dataset.assistantUrl || "/api/document-assistant";
    const initialConversation = Array.from(messages.childNodes).map((node) => node.cloneNode(true));
    let pending = false;
    let conversationContext = null;

    const appendContext = (url) => {
        if (!conversationContext) return;
        const fields = {
            contextKeyword: conversationContext.keyword,
            contextTopic: conversationContext.topic,
            contextAuthor: conversationContext.author,
            contextLanguageCode: conversationContext.languageCode,
            contextYear: conversationContext.year,
            contextSortMode: conversationContext.sortMode,
            contextPage: conversationContext.page,
            contextAnchorAuthor: conversationContext.anchorAuthor,
            contextAnchorTopic: conversationContext.anchorTopic
        };
        Object.entries(fields).forEach(([key, value]) => {
            if (value !== null && value !== undefined && value !== "") url.searchParams.set(key, String(value));
        });
    };

    const updateComposer = () => {
        const length = input.value.length;
        count.textContent = String(length);
        submitButton.disabled = pending || input.value.trim().length === 0;
        input.style.height = "auto";
        input.style.height = `${Math.min(input.scrollHeight, 104)}px`;
    };

    const setOpen = (open) => {
        panel.hidden = !open;
        root.classList.toggle("is-open", open);
        openButton.setAttribute("aria-expanded", String(open));
        openButton.setAttribute("aria-label", open ? "Đóng trợ lý tra cứu tài liệu" : "Mở trợ lý tra cứu tài liệu");
        if (open) window.setTimeout(() => input.focus(), 80);
        else openButton.focus();
    };

    const sendMessage = async (message) => {
        if (pending || !message) return;
        root.querySelector("[data-assistant-suggestions]")?.remove();
        appendUserMessage(messages, message);
        input.value = "";
        pending = true;
        input.disabled = true;
        submitButton.disabled = true;
        loading.hidden = false;
        messages.scrollTo({ top: messages.scrollHeight, behavior: "smooth" });
        updateComposer();
        try {
            const url = new URL(assistantUrl, window.location.origin);
            url.searchParams.set("message", message);
            appendContext(url);
            const response = await fetch(url.toString(), {
                method: "GET",
                credentials: "same-origin",
                headers: { Accept: "application/json" }
            });
            if (!response.ok) throw new Error("Assistant request failed");
            const payload = await response.json();
            if (payload.context && payload.type !== "ERROR") conversationContext = payload.context;
            appendAssistantResponse(messages, payload);
        } catch (_error) {
            appendAssistantResponse(messages, { message: ERROR_MESSAGE, documents: [] });
        } finally {
            pending = false;
            input.disabled = false;
            loading.hidden = true;
            updateComposer();
            input.focus();
        }
    };

    openButton.addEventListener("click", () => setOpen(panel.hidden));
    closeButton.addEventListener("click", () => setOpen(false));
    clearButton.addEventListener("click", () => {
        if (pending) return;
        messages.replaceChildren(...initialConversation.map((node) => node.cloneNode(true)));
        conversationContext = null;
        input.value = "";
        updateComposer();
        input.focus();
    });
    document.addEventListener("keydown", (event) => {
        if (event.key === "Escape" && !panel.hidden) setOpen(false);
    });
    input.addEventListener("input", updateComposer);
    input.addEventListener("keydown", (event) => {
        if (event.key === "Enter" && !event.shiftKey && !event.isComposing) {
            event.preventDefault();
            form.requestSubmit();
        }
    });
    messages.addEventListener("click", (event) => {
        const suggestion = event.target.closest("[data-assistant-suggestion]");
        if (suggestion) sendMessage(suggestion.dataset.assistantSuggestion.trim());
    });
    form.addEventListener("submit", (event) => {
        event.preventDefault();
        sendMessage(input.value.trim());
    });
    updateComposer();
}
