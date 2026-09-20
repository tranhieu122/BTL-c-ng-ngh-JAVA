const ERROR_MESSAGE = "Hiện chưa thể tải danh sách tài liệu. Bạn vui lòng thử lại sau.";
const STORAGE_HISTORY_KEY = "edurepo_assistant_history";
const STORAGE_CONTEXT_KEY = "edurepo_assistant_context";
const STORAGE_OPEN_KEY = "edurepo_assistant_open";

function loadHistory() {
    try {
        const raw = localStorage.getItem(STORAGE_HISTORY_KEY);
        return raw ? JSON.parse(raw) : [];
    } catch {
        return [];
    }
}

function saveHistory(history) {
    try {
        const trimmed = history.slice(-50);
        localStorage.setItem(STORAGE_HISTORY_KEY, JSON.stringify(trimmed));
    } catch (e) {
        console.warn("Could not save assistant history", e);
    }
}

function loadContext() {
    try {
        const raw = localStorage.getItem(STORAGE_CONTEXT_KEY);
        return raw ? JSON.parse(raw) : null;
    } catch {
        return null;
    }
}

function saveContext(context) {
    try {
        if (context) {
            localStorage.setItem(STORAGE_CONTEXT_KEY, JSON.stringify(context));
        } else {
            localStorage.removeItem(STORAGE_CONTEXT_KEY);
        }
    } catch (e) {
        console.warn("Could not save assistant context", e);
    }
}

function loadOpenState() {
    try {
        return sessionStorage.getItem(STORAGE_OPEN_KEY) === "true";
    } catch {
        return false;
    }
}

function saveOpenState(open) {
    try {
        sessionStorage.setItem(STORAGE_OPEN_KEY, String(open));
    } catch {}
}

function clearStorage() {
    try {
        localStorage.removeItem(STORAGE_HISTORY_KEY);
        localStorage.removeItem(STORAGE_CONTEXT_KEY);
        sessionStorage.removeItem(STORAGE_OPEN_KEY);
    } catch {}
}

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

/**
 * Tạo icon avatar hệ thống đồng nhất với giao diện (SVG Sparkle gradient).
 */
function createSystemAvatar() {
    const avatar = element("span", "assistant-message-avatar");
    avatar.setAttribute("aria-hidden", "true");
    avatar.innerHTML = `<svg viewBox="0 0 24 24" fill="none"><path d="M12 2L14.4 7.6L20 10L14.4 12.4L12 18L9.6 12.4L4 10L9.6 7.6L12 2Z" fill="currentColor"/></svg>`;
    return avatar;
}

/**
 * Nút sao chép câu trả lời vào clipboard với hiệu ứng phản hồi trực quan.
 */
function createCopyButton(textToCopy) {
    const btn = element("button", "assistant-action-copy");
    btn.type = "button";
    btn.title = "Sao chép câu trả lời";
    btn.setAttribute("aria-label", "Sao chép câu trả lời");
    btn.innerHTML = `<svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"><rect x="9" y="9" width="13" height="13" rx="2" ry="2"></rect><path d="M5 15H4a2 2 0 0 1-2-2V4a2 2 0 0 1 2-2h9a2 2 0 0 1 2 2v1"></path></svg><span>Sao chép</span>`;

    btn.addEventListener("click", async () => {
        try {
            await navigator.clipboard.writeText(textToCopy);
            btn.classList.add("is-copied");
            btn.innerHTML = `<svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"><polyline points="20 6 9 17 4 12"></polyline></svg><span>Đã chép</span>`;
            setTimeout(() => {
                btn.classList.remove("is-copied");
                btn.innerHTML = `<svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"><rect x="9" y="9" width="13" height="13" rx="2" ry="2"></rect><path d="M5 15H4a2 2 0 0 1-2-2V4a2 2 0 0 1 2-2h9a2 2 0 0 1 2 2v1"></path></svg><span>Sao chép</span>`;
            }, 2000);
        } catch (err) {
            console.warn("Could not copy to clipboard", err);
        }
    });

    return btn;
}

function appendMessage(container, message, type, timeStr) {
    const row = element("div", `assistant-message-row assistant-message-row-${type}`);
    const content = element("div");
    const bubble = element("div", `assistant-message assistant-message-${type}`, message);
    const time = element("time", "", timeStr || currentTime());
    if (type === "system") {
        row.append(createSystemAvatar());
    }
    content.append(bubble, time);
    row.append(content);
    container.append(row);
}

function appendUserMessage(container, message, timeStr) {
    appendMessage(container, message, "user", timeStr);
}

function citationUrl(source) {
    if (!source?.documentId) return source?.detailUrl || "#";

    const fragment = [];
    if (source.pageNumber !== null && source.pageNumber !== undefined) {
        fragment.push(`page=${encodeURIComponent(String(source.pageNumber))}`);
    }
    if (source.excerpt) {
        const searchText = source.excerpt.replace(/[“”"]/g, "").slice(0, 90).trim();
        if (searchText) fragment.push(`search=${encodeURIComponent(searchText)}`);
    }

    return `/view/${source.documentId}${fragment.length > 0 ? `#${fragment.join("&")}` : ""}`;
}

// Quản lý phần tử Popover/Tooltip trích dẫn đang hiển thị trên màn hình
let activeCitationPopover = null;

/**
 * Ẩn và xóa popover trích dẫn khỏi DOM nếu đang mở.
 */
function hideCitationTooltip() {
    if (activeCitationPopover) {
        activeCitationPopover.remove();
        activeCitationPopover = null;
    }
}

/**
 * Hiển thị Tooltip/Popover xem trước nội dung trích dẫn (snippet) khi người dùng di chuột hoặc chạm vào [1], [2].
 * @param {HTMLElement} targetElement - Thẻ citation được tương tác
 * @param {Object} source - Dữ liệu nguồn tham khảo (gồm title, snippet, documentId, detailUrl...)
 */
function showCitationTooltip(targetElement, source) {
    hideCitationTooltip();
    if (!source) return;

    // Tạo phần tử popover dạng tooltip
    const popover = element("div", "assistant-citation-popover");
    popover.setAttribute("role", "tooltip");

    // Header của popover: icon tài liệu + tên tài liệu
    const header = element("div", "assistant-citation-popover-header");
    const docIcon = element("span", "assistant-citation-popover-icon", "📄");
    const titleText = element("span", "assistant-citation-popover-title", source.title || "Tài liệu EduRepo");
    titleText.title = source.title || "Tài liệu EduRepo";
    if (source.sectionTitle) {
        const sectionBadge = element("span", "assistant-citation-popover-section", ` • ${source.sectionTitle}`);
        sectionBadge.style.fontSize = "0.75rem";
        sectionBadge.style.color = "#64748b";
        sectionBadge.style.fontWeight = "normal";
        titleText.append(sectionBadge);
    }
    header.append(docIcon, titleText);

    // Body của popover: đoạn trích dẫn (snippet) từ chunk thực tế mà AI đã tham khảo
    const snippetText = source.snippet || source.excerpt || "Không có đoạn trích dẫn.";
    const body = element("div", "assistant-citation-popover-snippet", snippetText);

    // Footer của popover: Nút "Xem tài liệu →" dẫn tới trang chi tiết tài liệu /repository/{documentId}
    const footer = element("div", "assistant-citation-popover-footer");
    const viewLink = element("a", "assistant-citation-popover-link", "Xem tài liệu →");
    const docUrl = source.documentId ? `/repository/${source.documentId}` : (source.detailUrl || "#");
    viewLink.href = docUrl;
    viewLink.target = "_blank";
    viewLink.rel = "noopener";
    footer.append(viewLink);

    popover.append(header, body, footer);

    // Giữ popover không bị tắt khi người dùng di chuột từ citation vào bên trong popover
    let hoverTimeout = null;
    popover.addEventListener("mouseenter", () => {
        if (hoverTimeout) clearTimeout(hoverTimeout);
    });
    popover.addEventListener("mouseleave", () => {
        hideCitationTooltip();
    });

    document.body.appendChild(popover);
    activeCitationPopover = popover;

    // Tính toán tọa độ hiển thị thông minh (phía trên mục citation, tránh tràn màn hình)
    const rect = targetElement.getBoundingClientRect();
    const popoverRect = popover.getBoundingClientRect();
    const margin = 8;

    let top = rect.top - popoverRect.height - margin;
    // Nếu phía trên không đủ chỗ, hiển thị xuống phía dưới
    if (top < 10) {
        top = rect.bottom + margin;
    }

    let left = rect.left + (rect.width / 2) - (popoverRect.width / 2);
    // Đảm bảo không bị tràn sang mép trái hoặc mép phải màn hình
    if (left < 10) left = 10;
    if (left + popoverRect.width > window.innerWidth - 10) {
        left = window.innerWidth - popoverRect.width - 10;
    }

    popover.style.top = `${window.scrollY + top}px`;
    popover.style.left = `${window.scrollX + left}px`;
}

/**
 * Gắn các sự kiện tương tác chuột (hover) và cảm ứng/bàn phím (click/touch) cho thẻ Citation.
 * @param {HTMLElement} citation - Thẻ citation DOM node
 * @param {number} sourceId - Số thứ tự nguồn tham khảo
 * @param {Object} source - Dữ liệu chi tiết của nguồn
 */
function bindCitationEvents(citation, sourceId, source) {
    // Xử lý hover trên máy tính (Desktop)
    let leaveTimer = null;
    citation.addEventListener("mouseenter", () => {
        if (leaveTimer) clearTimeout(leaveTimer);
        showCitationTooltip(citation, source);
    });
    citation.addEventListener("mouseleave", () => {
        leaveTimer = setTimeout(() => {
            if (activeCitationPopover && !activeCitationPopover.matches(":hover")) {
                hideCitationTooltip();
            }
        }, 220);
    });

    // Xử lý chạm/bấm trên điện thoại (Mobile) hoặc phím Enter
    citation.addEventListener("click", (e) => {
        e.preventDefault();
        e.stopPropagation();
        if (activeCitationPopover && activeCitationPopover.dataset.sourceId === String(sourceId)) {
            hideCitationTooltip();
        } else {
            showCitationTooltip(citation, source);
            if (activeCitationPopover) {
                activeCitationPopover.dataset.sourceId = String(sourceId);
            }
        }
    });
}

/**
 * Tạo một thẻ Citation tương tác (vd: [1], [2]) có gắn sự kiện hover (desktop) và click (mobile).
 * @param {number} sourceId - Số thứ tự nguồn tham khảo
 * @param {Object} source - Dữ liệu chi tiết của nguồn
 */
function createCitationNode(sourceId, source) {
    const citation = element("span", "assistant-inline-citation", `[${sourceId}]`);
    citation.dataset.sourceId = String(sourceId);
    citation.setAttribute("role", "button");
    citation.setAttribute("tabindex", "0");
    citation.setAttribute("aria-label", `Trích dẫn nguồn ${sourceId}: ${source?.title || "Tài liệu"}`);
    bindCitationEvents(citation, sourceId, source);
    return citation;
}

/**
 * Thoát các ký tự đặc biệt trong HTML nhằm ngăn ngừa lỗ hổng XSS.
 * @param {string} text - Văn bản gốc
 * @returns {string} Chuỗi an toàn đã được mã hóa HTML entities
 */
function escapeHtml(text) {
    if (!text) return "";
    return text
        .replace(/&/g, "&amp;")
        .replace(/</g, "&lt;")
        .replace(/>/g, "&gt;")
        .replace(/"/g, "&quot;")
        .replace(/'/g, "&#039;");
}

/**
 * Định dạng các thành phần nội dòng (inline Markdown) bao gồm in đậm, in nghiêng, khối code và citation.
 * @param {string} text - Đoạn văn bản chứa cú pháp inline
 * @returns {string} Chuỗi HTML đã được format
 */
function formatInline(text) {
    if (!text) return "";
    let formatted = escapeHtml(text);

    // Citations placeholder §§CIT:1§§ -> thẻ span tương tác
    formatted = formatted.replace(/§§CIT:(\d+)§§/g, '<span class="assistant-inline-citation" data-source-id="$1" role="button" tabindex="0" aria-label="Trích dẫn nguồn $1">[$1]</span>');

    // Inline code: `code`
    formatted = formatted.replace(/`([^`]+)`/g, '<code class="assistant-inline-code">$1</code>');

    // Bold: **text** hoặc __text__
    formatted = formatted.replace(/\*\*([^*]+)\*\*/g, '<strong>$1</strong>');
    formatted = formatted.replace(/__([^_]+)__/g, '<strong>$1</strong>');

    // Italic: *text* hoặc _text_
    formatted = formatted.replace(/\*([^*]+)\*/g, '<em>$1</em>');
    formatted = formatted.replace(/_([^_]+)_/g, '<em>$1</em>');

    // Strikethrough: ~~text~~
    formatted = formatted.replace(/~~([^~]+)~~/g, '<del>$1</del>');

    return formatted;
}

/**
 * Bộ phân tích cú pháp Markdown gọn nhẹ sang HTML cấu trúc chuẩn:
 * - Hỗ trợ tiêu đề (#, ##, ###)
 * - Danh sách có thứ tự (1., 2.) và không có thứ tự (-, *)
 * - Khối mã nguồn (```code```)
 * - Đoạn trích dẫn (> quote)
 * - Tự động bảo toàn các vị trí trích dẫn [1], [2] để gắn sự kiện tương tác
 * @param {string} rawText - Nội dung phản hồi từ AI
 * @returns {string} HTML hoàn chỉnh bọc trong div.assistant-markdown
 */
function parseMarkdownToHtml(rawText) {
    if (!rawText) return "";

    // 1. Bảo vệ trích dẫn [1], [2] trước khi parse Markdown
    const textWithCitations = rawText.replace(/\[(\d+)\]/g, "§§CIT:$1§§");

    const lines = textWithCitations.replace(/\r\n/g, "\n").replace(/\r/g, "\n").split("\n");
    const blocks = [];
    let currentList = null;
    let currentQuote = [];
    let inCodeBlock = false;
    let codeLanguage = "";
    let codeLines = [];

    const flushList = () => {
        if (currentList) {
            blocks.push(currentList);
            currentList = null;
        }
    };

    const flushQuote = () => {
        if (currentQuote.length > 0) {
            blocks.push({ type: "quote", lines: [...currentQuote] });
            currentQuote = [];
        }
    };

    for (let i = 0; i < lines.length; i++) {
        const line = lines[i];
        const trimmed = line.trim();

        // Khối mã nguồn ```
        if (trimmed.startsWith("```")) {
            if (inCodeBlock) {
                inCodeBlock = false;
                blocks.push({ type: "code", lang: codeLanguage, content: codeLines.join("\n") });
                codeLines = [];
                codeLanguage = "";
            } else {
                flushList();
                flushQuote();
                inCodeBlock = true;
                codeLanguage = trimmed.slice(3).trim();
            }
            continue;
        }

        if (inCodeBlock) {
            codeLines.push(line);
            continue;
        }

        // Trích dẫn blockquote >
        if (line.startsWith(">")) {
            flushList();
            currentQuote.push(line.replace(/^>\s?/, ""));
            continue;
        } else {
            flushQuote();
        }

        // Tiêu đề ### hoặc ##
        const headingMatch = line.match(/^(#{1,4})\s+(.+)$/);
        if (headingMatch) {
            flushList();
            blocks.push({ type: "heading", level: headingMatch[1].length, text: headingMatch[2] });
            continue;
        }

        // Danh sách không thứ tự: - item hoặc * item (hỗ trợ thụt lề)
        const ulMatch = line.match(/^\s*[\*\-]\s+(.+)$/);
        if (ulMatch) {
            if (!currentList || currentList.type !== "ul") {
                flushList();
                currentList = { type: "ul", items: [] };
            }
            currentList.items.push(ulMatch[1]);
            continue;
        }

        // Danh sách có thứ tự: 1. item (hỗ trợ thụt lề)
        const olMatch = line.match(/^\s*(\d+)\.\s+(.+)$/);
        if (olMatch) {
            if (!currentList || currentList.type !== "ol") {
                flushList();
                currentList = { type: "ol", items: [] };
            }
            currentList.items.push(olMatch[2]);
            continue;
        }

        // Dòng trống kết thúc danh sách
        if (!trimmed) {
            flushList();
            continue;
        }

        // Dòng văn bản thông thường
        flushList();
        blocks.push({ type: "paragraph", text: line });
    }

    flushList();
    flushQuote();
    if (inCodeBlock) {
        blocks.push({ type: "code", lang: codeLanguage, content: codeLines.join("\n") });
    }

    // Nhóm các dòng văn bản liền kề thành một đoạn paragraph
    const groupedBlocks = [];
    let paraBuffer = [];

    blocks.forEach((b) => {
        if (b.type === "paragraph") {
            paraBuffer.push(b.text);
        } else {
            if (paraBuffer.length > 0) {
                groupedBlocks.push({ type: "paragraph", text: paraBuffer.join("\n") });
                paraBuffer = [];
            }
            groupedBlocks.push(b);
        }
    });
    if (paraBuffer.length > 0) {
        groupedBlocks.push({ type: "paragraph", text: paraBuffer.join("\n") });
    }

    // Render HTML cho từng block
    const htmlParts = groupedBlocks.map((b) => {
        switch (b.type) {
            case "heading": {
                const tag = b.level <= 2 ? "h3" : "h4";
                return `<${tag} class="assistant-md-heading">${formatInline(b.text)}</${tag}>`;
            }
            case "ul": {
                const items = b.items.map((it) => `<li>${formatInline(it)}</li>`).join("");
                return `<ul class="assistant-markdown-list">${items}</ul>`;
            }
            case "ol": {
                const items = b.items.map((it) => `<li>${formatInline(it)}</li>`).join("");
                return `<ol class="assistant-markdown-list">${items}</ol>`;
            }
            case "quote": {
                return `<blockquote>${formatInline(b.lines.join("\n")).replace(/\n/g, "<br>")}</blockquote>`;
            }
            case "code": {
                return `<pre class="assistant-code-block"><code>${escapeHtml(b.content)}</code></pre>`;
            }
            case "paragraph":
            default: {
                return `<p>${formatInline(b.text).replace(/\n/g, "<br>")}</p>`;
            }
        }
    });

    return `<div class="assistant-markdown">${htmlParts.join("")}</div>`;
}

/**
 * Làm sạch câu trả lời từ LLM, triệt tiêu các cụm từ máy móc như "Theo Context,".
 */
function sanitizeAnswerText(text) {
    if (!text) return "";
    let cleaned = text.replace(/^STATUS:\s*(ANSWERED|INSUFFICIENT)\s*/i, "").trim();

    // Chuyển đổi "Theo Context [1]" -> "Theo tài liệu [1]"
    cleaned = cleaned.replace(/^(?:\*\*)?(?:theo|dựa\s+(?:trên|vào))\s+(?:retrieved\s+)?context\s*(\[\d+\])/i, "Theo tài liệu $1");

    // Gọt bỏ "Theo Context,", "Dựa trên Context,", "**Theo Context:**", "Trong Context,"
    cleaned = cleaned.replace(/^(?:\*\*)?(?:theo|dựa\s+(?:trên|vào)|căn\s+cứ\s+(?:vào)?|trong)\s+(?:retrieved\s+)?context(?:\s+được\s+cung\s+cấp)?[,:]?(?:\*\*)?[\s,:—–-]+\s*/i, "");

    if (cleaned && cleaned[0] === cleaned[0].toLowerCase()) {
        cleaned = cleaned[0].toUpperCase() + cleaned.slice(1);
    }

    // Thay thế các từ context máy móc còn sót lại trong nội dung thành "tài liệu"
    cleaned = cleaned.replace(/\btheo\s+(?:retrieved\s+)?context\b/gi, "theo tài liệu");
    cleaned = cleaned.replace(/\btrong\s+(?:retrieved\s+)?context\b/gi, "trong tài liệu");
    cleaned = cleaned.replace(/\btừ\s+(?:retrieved\s+)?context\b/gi, "từ tài liệu");
    cleaned = cleaned.replace(/\bdựa\s+trên\s+(?:retrieved\s+)?context\b/gi, "dựa trên tài liệu");

    return cleaned.trim();
}

/**
 * Phân tích và render nội dung câu trả lời của AI:
 * - Định dạng Markdown hoàn chỉnh (tiêu đề, in đậm, danh sách, khối mã, trích dẫn).
 * - Tự động phát hiện các chuỗi trích dẫn [1], [2] và gắn sự kiện Popover preview.
 * - Hiển thị khay nguồn tham khảo nếu không có inline citations.
 */
function appendAnswerContent(bubble, response) {
    const rawAnswer = sanitizeAnswerText(response.answer || response.message || ERROR_MESSAGE);
    const sources = Array.isArray(response.sources) ? response.sources : [];

    // Ánh xạ nguồn theo sourceId (1, 2, ...)
    const sourceMap = new Map();
    sources.forEach((src, index) => {
        const id = src.sourceId != null ? Number(src.sourceId) : index + 1;
        sourceMap.set(id, src);
    });

    // Render Markdown sang HTML
    bubble.innerHTML = parseMarkdownToHtml(rawAnswer);

    // Gắn sự kiện hover và click cho tất cả các thẻ citation inline [1], [2]
    bubble.querySelectorAll(".assistant-inline-citation").forEach((citationNode) => {
        const sourceId = parseInt(citationNode.dataset.sourceId, 10);
        const source = sourceMap.get(sourceId) || sources[sourceId - 1];
        bindCitationEvents(citationNode, sourceId, source);
    });

    // Nếu không có inline citations trong văn bản nhưng có danh sách sources, hiển thị khay tài liệu bên dưới
    const hasInlineCitation = /\[\d+\]/.test(rawAnswer);
    if (!hasInlineCitation && sources.length > 0) {
        const tray = element("div", "assistant-sources-tray");
        const trayLabel = element("span", "assistant-sources-tray-label", "Tài liệu trích dẫn:");
        const citations = element("span", "assistant-inline-citations");
        const visibleSources = sources.slice(0, 4);
        visibleSources.forEach((src, index) => {
            const id = src.sourceId != null ? Number(src.sourceId) : index + 1;
            citations.append(createCitationNode(id, src));
        });

        if (sources.length > visibleSources.length) {
            const more = element("span", "assistant-inline-citation assistant-inline-citation-more", "…");
            more.title = `${sources.length - visibleSources.length} nguồn khác`;
            citations.append(more);
        }

        tray.append(trayLabel, citations);
        bubble.append(tray);
    }
}

function appendAssistantResponse(container, response, timeStr) {
    const group = element("div", "assistant-response");
    const row = element("div", "assistant-message-row assistant-message-row-system");
    const content = element("div");
    const bubble = element("div", "assistant-message assistant-message-system");
    appendAnswerContent(bubble, response);

    // Footer chứa thời gian và nút sao chép câu trả lời
    const footer = element("div", "assistant-message-footer");
    const timeNode = element("time", "", timeStr || currentTime());
    const rawAnswerText = sanitizeAnswerText(response.answer || response.message || "");
    const copyBtn = createCopyButton(rawAnswerText);
    footer.append(timeNode, copyBtn);

    content.append(bubble, footer);
    row.append(createSystemAvatar(), content);
    group.append(row);

    if (Array.isArray(response.documents) && response.documents.length > 0) {
        const list = element("div", "assistant-result-list");
        response.documents.forEach((item) => {
            const card = element("article", "assistant-result-card");
            const heading = element("h3", "", item.title || "Tài liệu");
            const description = element("p", "", item.description || "Chưa có mô tả cho tài liệu này.");
            const metadata = element("dl", "assistant-result-meta");
            [["Danh mục", item.category], ["Tác giả", item.author], ["Xuất bản", formatDate(item.publishedAt)],
                ["Đánh giá", item.averageRating ? `${Number(item.averageRating).toFixed(1)}/5 (${item.reviewCount || 0})` : "Chưa có"],
                ["Lượt xem", item.viewCount], ["Lượt tải", item.downloadCount]]
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
    let conversationContext = loadContext();
    let history = loadHistory();

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

    const setOpen = (open, shouldFocus = true) => {
        panel.hidden = !open;
        root.classList.toggle("is-open", open);
        openButton.setAttribute("aria-expanded", String(open));
        openButton.setAttribute("aria-label", open ? "Đóng trợ lý tra cứu tài liệu" : "Mở trợ lý tra cứu tài liệu");
        saveOpenState(open);
        if (open) {
            messages.scrollTo({ top: messages.scrollHeight, behavior: "auto" });
            if (shouldFocus) window.setTimeout(() => input.focus(), 80);
        } else if (shouldFocus) {
            openButton.focus();
        }
    };

    // Khôi phục lịch sử chat từ bộ nhớ lưu trữ khi chuyển trang
    if (Array.isArray(history) && history.length > 0) {
        root.querySelector("[data-assistant-suggestions]")?.remove();
        history.forEach((item) => {
            if (item.type === "user") {
                appendUserMessage(messages, item.content, item.time);
            } else if (item.type === "assistant" && item.payload) {
                appendAssistantResponse(messages, item.payload, item.time);
            }
        });
        messages.scrollTo({ top: messages.scrollHeight, behavior: "auto" });
    }

    // Nếu người dùng đang mở chatbot ở trang trước, giữ nguyên trạng thái mở ở trang mới
    if (loadOpenState()) {
        setOpen(true, false);
    }

    const sendMessage = async (message) => {
        if (pending || !message) return;
        root.querySelector("[data-assistant-suggestions]")?.remove();
        const userTime = currentTime();
        appendUserMessage(messages, message, userTime);
        history.push({ type: "user", content: message, time: userTime });
        saveHistory(history);

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
            let payload;
            if (response.status === 429) {
                try {
                    payload = await response.json();
                } catch {
                    payload = { message: "Bạn đang gửi câu hỏi quá nhanh. Vui lòng đợi trong giây lát rồi thử lại nhé.", documents: [] };
                }
            } else if (!response.ok) {
                throw new Error("Assistant request failed");
            } else {
                payload = await response.json();
            }

            if (payload.context && payload.type !== "ERROR" && payload.type !== "RATE_LIMITED") {
                conversationContext = payload.context;
                saveContext(conversationContext);
            }
            const assistantTime = currentTime();
            appendAssistantResponse(messages, payload, assistantTime);
            history.push({ type: "assistant", payload: payload, time: assistantTime });
            saveHistory(history);
        } catch (_error) {
            const errPayload = { message: ERROR_MESSAGE, documents: [] };
            const assistantTime = currentTime();
            appendAssistantResponse(messages, errPayload, assistantTime);
            history.push({ type: "assistant", payload: errPayload, time: assistantTime });
            saveHistory(history);
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
        history = [];
        clearStorage();
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
    document.addEventListener("click", (event) => {
        if (activeCitationPopover && !event.target.closest(".assistant-inline-citation") && !event.target.closest(".assistant-citation-popover")) {
            hideCitationTooltip();
        }
    });
    messages.addEventListener("scroll", () => {
        hideCitationTooltip();
    }, { passive: true });
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
