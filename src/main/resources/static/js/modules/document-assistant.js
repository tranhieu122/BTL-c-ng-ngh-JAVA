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

function appendUserMessage(container, message, timeStr, imageSrc) {
    const row = element("div", "assistant-message-row assistant-message-row-user");
    const content = element("div");
    const bubble = element("div", "assistant-message assistant-message-user");
    if (imageSrc) {
        const img = element("img", "user-message-attachment");
        img.src = imageSrc;
        img.alt = "Ảnh câu hỏi";
        bubble.append(img);
    }
    if (message) {
        const textSpan = element("span", "", message);
        bubble.append(textSpan);
    }
    const time = element("time", "", timeStr || currentTime());
    content.append(bubble, time);
    row.append(content);
    container.append(row);
}

function createThumbnailDataUrl(file, maxWidth = 120, maxHeight = 120) {
    return new Promise((resolve) => {
        if (!file || !file.type || !file.type.startsWith("image/")) {
            resolve(null);
            return;
        }
        const reader = new FileReader();
        reader.onload = (e) => {
            const img = new Image();
            img.onload = () => {
                try {
                    const canvas = document.createElement("canvas");
                    let w = img.width;
                    let h = img.height;
                    const ratio = Math.min(maxWidth / w, maxHeight / h, 1);
                    canvas.width = Math.max(1, Math.round(w * ratio));
                    canvas.height = Math.max(1, Math.round(h * ratio));
                    const ctx = canvas.getContext("2d");
                    ctx.drawImage(img, 0, 0, canvas.width, canvas.height);
                    resolve(canvas.toDataURL("image/jpeg", 0.65));
                } catch {
                    resolve(null);
                }
            };
            img.onerror = () => resolve(null);
            img.src = e.target.result;
        };
        reader.onerror = () => resolve(null);
        reader.readAsDataURL(file);
    });
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
 * Render tất cả công thức toán học KaTeX bên trong một container DOM.
 * An toàn, chống XSS, tự động fallback về text thuần nếu chưa tải xong thư viện.
 */
function renderKaTeX(container) {
    if (!container) return;
    if (typeof window.katex === "undefined") {
        container.querySelectorAll("[data-katex-math]").forEach((el) => {
            el.textContent = el.getAttribute("data-katex-math");
        });
        return;
    }

    container.querySelectorAll("[data-katex-math]").forEach((el) => {
        if (el.dataset.katexRendered === "true") return;
        const math = el.getAttribute("data-katex-math") || "";
        const isDisplay = el.classList.contains("katex-display");
        try {
            window.katex.render(math, el, {
                displayMode: isDisplay,
                throwOnError: false
            });
            el.dataset.katexRendered = "true";
        } catch (err) {
            console.warn("KaTeX rendering error:", err);
            el.textContent = math;
        }
    });
}

/**
 * Trích xuất an toàn các khối toán học KaTeX ($$...$$ và $...$) và trích dẫn ([1], [2])
 * trước khi xử lý Markdown để ngăn chặn xung đột ký tự và lỗi XSS.
 */
function extractMathAndCitations(rawText) {
    if (!rawText) return { text: "", mathBlocks: [], mathInlines: [] };

    const mathBlocks = [];
    const mathInlines = [];

    // 1. Tạm thời bảo vệ các khối mã nguồn ```...``` và inline code `...`
    const codeBlocks = [];
    let text = rawText.replace(/```[\s\S]*?```/g, (match) => {
        codeBlocks.push(match);
        return `§§CODE_BLOCK:${codeBlocks.length - 1}§§`;
    });

    const inlineCodes = [];
    text = text.replace(/`[^`]+`/g, (match) => {
        inlineCodes.push(match);
        return `§§INLINE_CODE:${inlineCodes.length - 1}§§`;
    });

    // 2. Trích xuất Math Block: $$...$$ hoặc \[...\]
    text = text.replace(/\$\$([\s\S]+?)\$\$/g, (match, formula) => {
        mathBlocks.push(formula.trim());
        return `§§MATH_BLOCK:${mathBlocks.length - 1}§§`;
    });
    text = text.replace(/\\\[([\s\S]+?)\\\]/g, (match, formula) => {
        mathBlocks.push(formula.trim());
        return `§§MATH_BLOCK:${mathBlocks.length - 1}§§`;
    });

    // 3. Trích xuất Math Inline: $...$ hoặc \(...\)
    text = text.replace(/(?<!\\)\$([^\$\n\r]+?)(?<!\\)\$/g, (match, formula) => {
        const trimmed = formula.trim();
        // Bỏ qua nếu là số tiền thông thường (ví dụ: $100, $50k, $2.5)
        if (/^\d+(?:[.,]\d+)?\s*(?:k|m|usd|vnđ)?$/i.test(trimmed)) {
            return match;
        }
        mathInlines.push(trimmed);
        return `§§MATH_INLINE:${mathInlines.length - 1}§§`;
    });
    text = text.replace(/\\\(([\s\S]+?)\\\)/g, (match, formula) => {
        mathInlines.push(formula.trim());
        return `§§MATH_INLINE:${mathInlines.length - 1}§§`;
    });

    // 4. Khôi phục lại khối mã nguồn và inline code
    text = text.replace(/§§INLINE_CODE:(\d+)§§/g, (m, idx) => inlineCodes[idx]);
    text = text.replace(/§§CODE_BLOCK:(\d+)§§/g, (m, idx) => codeBlocks[idx]);

    // 5. Trích xuất Citations [1], [2]
    text = text.replace(/\[(\d+)\]/g, "§§CIT:$1§§");

    return { text, mathBlocks, mathInlines };
}

/**
 * Phân tích cú pháp Markdown sang HTML cấu trúc chuẩn:
 * - Tích hợp bảo vệ và render KaTeX math block/inline
 * - Bảo toàn inline citations
 * - Hỗ trợ tiêu đề, danh sách, quote, code blocks
 */
function parseMarkdownToHtml(rawText) {
    if (!rawText) return "";

    const { text: processedText, mathBlocks, mathInlines } = extractMathAndCitations(rawText);

    const lines = processedText.replace(/\r\n/g, "\n").replace(/\r/g, "\n").split("\n");
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

        // Bảng dữ liệu Markdown | Cột 1 | Cột 2 |
        const isTableRow = (str) => {
            const t = str.trim();
            return t.includes("|") && (t.startsWith("|") || /\|.*\|/.test(t));
        };
        const isTableSeparator = (str) => {
            const t = str.trim();
            return /^\|?\s*:?-{2,}:?\s*(\|\s*:?-{2,}:?\s*)+\|?$/.test(t);
        };

        if (isTableRow(line) && i + 1 < lines.length && isTableSeparator(lines[i + 1])) {
            flushList();
            flushQuote();
            const parseRow = (rowLine) => {
                let clean = rowLine.trim();
                if (clean.startsWith("|")) clean = clean.slice(1);
                if (clean.endsWith("|")) clean = clean.slice(0, -1);
                return clean.split("|").map(cell => cell.trim());
            };
            const headers = parseRow(line);
            i++; // Bỏ qua dòng separator |---|---|
            const rows = [];
            while (i + 1 < lines.length && isTableRow(lines[i + 1]) && !isTableSeparator(lines[i + 1])) {
                i++;
                rows.push(parseRow(lines[i]));
            }
            blocks.push({ type: "table", headers, rows });
            continue;
        }

        // Tiêu đề ### hoặc ##
        const headingMatch = line.match(/^(#{1,4})\s+(.+)$/);
        if (headingMatch) {
            flushList();
            blocks.push({ type: "heading", level: headingMatch[1].length, text: headingMatch[2] });
            continue;
        }

        // Danh sách không thứ tự: - item hoặc * item
        const ulMatch = line.match(/^\s*[\*\-]\s+(.+)$/);
        if (ulMatch) {
            if (!currentList || currentList.type !== "ul") {
                flushList();
                currentList = { type: "ul", items: [] };
            }
            currentList.items.push(ulMatch[1]);
            continue;
        }

        // Danh sách có thứ tự: 1. item
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
            case "table": {
                const ths = b.headers.map((h) => `<th>${formatInline(h)}</th>`).join("");
                const trs = b.rows.map((row) => {
                    const tds = row.map((cell) => `<td>${formatInline(cell)}</td>`).join("");
                    return `<tr>${tds}</tr>`;
                }).join("");
                return `<div class="assistant-table-wrapper"><table class="assistant-table"><thead><tr>${ths}</tr></thead><tbody>${trs}</tbody></table></div>`;
            }
            case "paragraph":
            default: {
                return `<p>${formatInline(b.text).replace(/\n/g, "<br>")}</p>`;
            }
        }
    });

    let html = `<div class="assistant-markdown">${htmlParts.join("")}</div>`;

    // Khôi phục các thẻ KaTeX placeholder sang HTML DOM elements với data-katex-math
    html = html.replace(/§§MATH_BLOCK:(\d+)§§/g, (m, idx) => {
        const formula = mathBlocks[idx] || "";
        return `<div class="katex-display" data-katex-math="${escapeHtml(formula)}"></div>`;
    });
    html = html.replace(/§§MATH_INLINE:(\d+)§§/g, (m, idx) => {
        const formula = mathInlines[idx] || "";
        return `<span class="katex-inline" data-katex-math="${escapeHtml(formula)}"></span>`;
    });

    return html;
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
 * - Định dạng Markdown hoàn chỉnh kèm KaTeX.
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

    // Kích hoạt render công thức KaTeX
    renderKaTeX(bubble);

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

/**
 * Tạo footer tin nhắn gồm: thời gian, nút Sao chép và nút đánh giá Thumbs Up / Down.
 */
function createMessageFooter(rawAnswerText, timeStr, messageId, savedRating, onFeedback) {
    const footer = element("div", "assistant-message-footer");
    const timeNode = element("time", "", timeStr || currentTime());
    const actions = element("div", "assistant-message-actions");

    const copyBtn = createCopyButton(rawAnswerText);
    actions.append(copyBtn);

    if (messageId && typeof onFeedback === "function") {
        const feedbackWrap = element("div", "assistant-feedback-actions");

        const upBtn = element("button", "assistant-feedback-btn feedback-up");
        upBtn.type = "button";
        upBtn.title = "Hữu ích (Thumbs up)";
        upBtn.setAttribute("aria-label", "Đánh giá câu trả lời hữu ích");
        upBtn.innerHTML = `<svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"><path d="M14 9V5a3 3 0 0 0-3-3l-4 9v11h11.28a2 2 0 0 0 2-1.7l1.38-9a2 2 0 0 0-2-2.3zM7 22H4a2 2 0 0 1-2-2v-7a2 2 0 0 1 2-2h3"></path></svg>`;

        const downBtn = element("button", "assistant-feedback-btn feedback-down");
        downBtn.type = "button";
        downBtn.title = "Chưa tốt (Thumbs down)";
        downBtn.setAttribute("aria-label", "Đánh giá câu trả lời chưa tốt");
        downBtn.innerHTML = `<svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"><path d="M10 15v4a3 3 0 0 0 3 3l4-9V2H5.72a2 2 0 0 0-2 1.7l-1.38 9a2 2 0 0 0 2 2.3zm7-13h3a2 2 0 0 1 2 2v7a2 2 0 0 1-2 2h-3"></path></svg>`;

        if (savedRating === "UP") upBtn.classList.add("is-active");
        if (savedRating === "DOWN") downBtn.classList.add("is-active");

        upBtn.addEventListener("click", () => {
            const nextRating = upBtn.classList.contains("is-active") ? "NONE" : "UP";
            onFeedback(messageId, nextRating, upBtn, downBtn);
        });

        downBtn.addEventListener("click", () => {
            const nextRating = downBtn.classList.contains("is-active") ? "NONE" : "DOWN";
            onFeedback(messageId, nextRating, upBtn, downBtn);
        });

        feedbackWrap.append(upBtn, downBtn);
        actions.append(feedbackWrap);
    }

    footer.append(timeNode, actions);
    return footer;
}

function appendAssistantResponse(container, response, timeStr, messageId, savedRating, onFeedback) {
    const group = element("div", "assistant-response");
    const row = element("div", "assistant-message-row assistant-message-row-system");
    const content = element("div");
    const bubble = element("div", "assistant-message assistant-message-system");
    appendAnswerContent(bubble, response);

    // Footer chứa thời gian, nút sao chép và đánh giá Thumbs Up / Down
    const rawAnswerText = sanitizeAnswerText(response.answer || response.message || "");
    const effectiveMsgId = messageId || response.messageId;
    const effectiveRating = savedRating || response.rating;
    const footer = createMessageFooter(rawAnswerText, timeStr, effectiveMsgId, effectiveRating, onFeedback);

    content.append(bubble, footer);
    row.append(createSystemAvatar(), content);
    group.append(row);

    // Chỉ hiển thị thẻ tài liệu khi người dùng chủ động tìm kiếm danh mục (RESULTS), không hiển thị khi đang hỏi đáp hội thoại
    const isConversationalAnswer = response.type === "RAG_ANSWER" || response.type === "RAG_INSUFFICIENT";

    if (!isConversationalAnswer && Array.isArray(response.documents) && response.documents.length > 0) {
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

    if (!isConversationalAnswer && response.hasMore && response.allResultsUrl) {
        const allResults = element("a", "assistant-all-results", "Xem tất cả kết quả →");
        allResults.href = response.allResultsUrl;
        group.append(allResults);
    }

    if (!isConversationalAnswer && Array.isArray(response.suggestions) && response.suggestions.length > 0) {
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

/**
 * Gửi đánh giá phản hồi (Thumbs Up / Down) lên server.
 */
async function sendFeedbackRating(messageId, rating, scopedDocId, upBtn, downBtn) {
    if (!messageId) return false;
    try {
        const response = await fetch("/api/document-assistant/feedback", {
            method: "POST",
            headers: {
                "Content-Type": "application/json",
                "Accept": "application/json"
            },
            body: JSON.stringify({
                messageId: messageId,
                rating: rating,
                scopedDocumentId: scopedDocId ? Number(scopedDocId) : null
            })
        });

        if (response.ok) {
            upBtn.classList.toggle("is-active", rating === "UP");
            downBtn.classList.toggle("is-active", rating === "DOWN");
            return true;
        }
    } catch (err) {
        console.warn("Feedback submission error:", err);
    }
    return false;
}

/**
 * Đọc luồng dữ liệu Server-Sent Events (SSE) theo thời gian thực từ /api/document-assistant/stream.
 */
async function streamAssistantResponse({
    url,
    message,
    scopedDocumentId,
    context,
    signal,
    onStart,
    onCitation,
    onToken,
    onDone,
    onError
}) {
    const streamUrl = new URL(url.endsWith("/") ? `${url}stream` : `${url}/stream`, window.location.origin);
    streamUrl.searchParams.set("message", message);
    if (scopedDocumentId) {
        streamUrl.searchParams.set("scopedDocumentId", String(scopedDocumentId));
    }
    if (context) {
        if (context.keyword) streamUrl.searchParams.set("contextKeyword", context.keyword);
        if (context.topic) streamUrl.searchParams.set("contextTopic", context.topic);
    }

    const response = await fetch(streamUrl.toString(), {
        method: "GET",
        headers: { "Accept": "text/event-stream" },
        signal
    });

    if (response.status === 429) {
        onError("Bạn đang gửi yêu cầu quá nhanh. Vui lòng đợi trong giây lát rồi thử lại nhé.");
        return;
    }
    if (!response.ok) {
        throw new Error(`HTTP ${response.status}`);
    }

    const reader = response.body.getReader();
    const decoder = new TextDecoder("utf-8");
    let buffer = "";
    let currentEvent = "message";

    while (true) {
        const { done, value } = await reader.read();
        if (done) break;

        buffer += decoder.decode(value, { stream: true });
        const lines = buffer.split(/\r\n|\r|\n/);
        buffer = lines.pop() || "";

        for (const line of lines) {
            const trimmed = line.trim();
            if (!trimmed) {
                currentEvent = "message";
                continue;
            }
            if (trimmed.startsWith("event:")) {
                currentEvent = trimmed.slice(6).trim();
            } else if (trimmed.startsWith("data:")) {
                const dataStr = trimmed.slice(5).trim();
                if (currentEvent === "start") {
                    onStart && onStart();
                } else if (currentEvent === "citation") {
                    try {
                        const sources = JSON.parse(dataStr);
                        onCitation && onCitation(sources);
                    } catch (e) {
                        console.warn("Could not parse citations", e);
                    }
                } else if (currentEvent === "token") {
                    let token = dataStr;
                    try {
                        const parsed = JSON.parse(dataStr);
                        // Server có thể trả về: "text" (quoted string) hoặc {"content":"text"}
                        if (typeof parsed === "string") {
                            token = parsed;
                        } else if (parsed && typeof parsed.content === "string") {
                            token = parsed.content;
                        }
                    } catch {}
                    onToken && onToken(token);
                } else if (currentEvent === "done") {
                    try {
                        const donePayload = JSON.parse(dataStr);
                        onDone && onDone(donePayload);
                    } catch (e) {
                        onDone && onDone({ messageId: null });
                    }
                } else if (currentEvent === "error") {
                    onError && onError(dataStr);
                }
            }
        }
    }
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
    const attachmentPreview = root.querySelector("[data-assistant-attachment]");
    const attachmentImg = root.querySelector("[data-assistant-attachment-img]");
    const attachmentName = root.querySelector("[data-assistant-attachment-name]");
    const attachmentRemove = root.querySelector("[data-assistant-attachment-remove]");
    const fileInput = root.querySelector("[data-assistant-file-input]");
    const attachBtn = root.querySelector("[data-assistant-attach-btn]");

    // Các thành phần Scoped Document và Streaming Control mới
    const scopedBanner = root.querySelector("[data-assistant-scoped-banner]");
    const scopedTitle = root.querySelector("[data-scoped-title]");
    const scopedClearBtn = root.querySelector("[data-scoped-clear]");
    const streamingBar = root.querySelector("[data-assistant-streaming-bar]");
    const stopButton = root.querySelector("[data-assistant-stop]");

    if (!openButton || !closeButton || !clearButton || !panel || !form || !input || !submitButton || !messages || !loading || !count) return;

    const assistantUrl = root.dataset.assistantUrl || "/api/document-assistant";
    const initialConversation = Array.from(messages.childNodes).map((node) => node.cloneNode(true));
    let pending = false;
    let conversationContext = loadContext();
    let history = loadHistory();
    let attachedFile = null;
    let attachedPreviewUrl = null;

    // Trạng thái Scoped Q&A và AbortController để dừng stream
    let activeScopedDocument = null;
    let currentAbortController = null;

    const handleFeedbackClick = async (messageId, rating, upBtn, downBtn) => {
        const docId = activeScopedDocument?.id || null;
        const success = await sendFeedbackRating(messageId, rating, docId, upBtn, downBtn);
        if (success) {
            const histItem = history.find(item => item.payload && item.payload.messageId === messageId);
            if (histItem && histItem.payload) {
                histItem.payload.rating = rating;
                saveHistory(history);
            }
        }
    };

    const clearAttachment = () => {
        if (attachedPreviewUrl) {
            URL.revokeObjectURL(attachedPreviewUrl);
            attachedPreviewUrl = null;
        }
        attachedFile = null;
        if (fileInput) fileInput.value = "";
        if (attachmentPreview) attachmentPreview.hidden = true;
        if (attachmentImg) attachmentImg.src = "";
        updateComposer();
    };

    const setAttachment = (file) => {
        if (!file) return;
        const validTypes = ["image/png", "image/jpeg", "image/jpg", "image/webp"];
        if (!validTypes.includes(file.type.toLowerCase())) {
            alert("Chỉ hỗ trợ dán hoặc tải lên hình ảnh định dạng PNG, JPG hoặc WebP.");
            return;
        }
        if (file.size > 5 * 1024 * 1024) {
            alert("Dung lượng ảnh vượt quá giới hạn cho phép (tối đa 5MB).");
            return;
        }
        if (attachedPreviewUrl) {
            URL.revokeObjectURL(attachedPreviewUrl);
        }
        attachedFile = file;
        attachedPreviewUrl = URL.createObjectURL(file);
        if (attachmentImg) attachmentImg.src = attachedPreviewUrl;
        if (attachmentName) attachmentName.textContent = file.name || "anh-chup-man-hinh.png";
        if (attachmentPreview) attachmentPreview.hidden = false;
        updateComposer();
    };

    const updateComposer = () => {
        const length = input.value.length;
        count.textContent = String(length);
        const hasContent = input.value.trim().length > 0 || attachedFile !== null;
        submitButton.disabled = pending || !hasContent;
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

    // Thiết lập chế độ Scoped Document Q&A
    const setScopedDocument = (docId, docTitle) => {
        if (!docId) {
            activeScopedDocument = null;
            if (scopedBanner) scopedBanner.hidden = true;
            input.placeholder = "Dán ảnh (Ctrl+V) hoặc hỏi bài tập, Java, AI…";
            return;
        }
        activeScopedDocument = { id: Number(docId), title: docTitle || "Tài liệu đang mở" };
        if (scopedTitle) scopedTitle.textContent = activeScopedDocument.title;
        if (scopedBanner) scopedBanner.hidden = false;
        input.placeholder = `Hỏi về "${activeScopedDocument.title}"…`;
        setOpen(true);
    };

    if (scopedClearBtn) {
        scopedClearBtn.addEventListener("click", () => {
            setScopedDocument(null, null);
        });
    }

    // Lắng nghe click các nút "Chat với tài liệu này" trên trang chi tiết
    document.addEventListener("click", (event) => {
        const btn = event.target.closest(".assistant-scoped-chat-btn");
        if (btn) {
            event.preventDefault();
            const docId = btn.dataset.chatDocumentId;
            const docTitle = btn.dataset.chatDocumentTitle;
            setScopedDocument(docId, docTitle);
        }
    });

    // Khôi phục lịch sử chat từ bộ nhớ lưu trữ khi chuyển trang
    if (Array.isArray(history) && history.length > 0) {
        root.querySelector("[data-assistant-suggestions]")?.remove();
        history.forEach((item) => {
            if (item.type === "user") {
                appendUserMessage(messages, item.content, item.time, item.imageSrc);
            } else if (item.type === "assistant" && item.payload) {
                appendAssistantResponse(messages, item.payload, item.time, item.payload.messageId, item.payload.rating, handleFeedbackClick);
            }
        });
        messages.scrollTo({ top: messages.scrollHeight, behavior: "auto" });
    }

    // Nếu người dùng đang mở chatbot ở trang trước, giữ nguyên trạng thái mở ở trang mới
    if (loadOpenState()) {
        setOpen(true, false);
    }

    const sendMessage = async (message) => {
        const hasText = Boolean(message && message.trim().length > 0);
        const hasImage = Boolean(attachedFile);
        if (pending || (!hasText && !hasImage)) return;

        root.querySelector("[data-assistant-suggestions]")?.remove();
        const userTime = currentTime();
        const currentFile = attachedFile;
        const currentPreviewUrl = attachedPreviewUrl;

        let thumbDataUrl = null;
        if (currentFile) {
            try {
                thumbDataUrl = await createThumbnailDataUrl(currentFile);
            } catch {}
        }

        appendUserMessage(messages, message, userTime, currentPreviewUrl || thumbDataUrl);
        history.push({ type: "user", content: message, time: userTime, imageSrc: thumbDataUrl });
        saveHistory(history);

        input.value = "";
        clearAttachment();
        pending = true;
        input.disabled = true;
        submitButton.disabled = true;
        updateComposer();

        // 1. Trường hợp có ảnh đính kèm (Vision OCR): Gửi qua multipart POST
        if (currentFile) {
            loading.hidden = false;
            messages.scrollTo({ top: messages.scrollHeight, behavior: "smooth" });
            try {
                const visionUrl = assistantUrl.endsWith("/") ? `${assistantUrl}vision` : `${assistantUrl}/vision`;
                const formData = new FormData();
                formData.append("image", currentFile);
                if (message && message.trim().length > 0) {
                    formData.append("message", message.trim());
                }
                if (activeScopedDocument?.id) {
                    formData.append("scopedDocumentId", String(activeScopedDocument.id));
                }
                if (conversationContext) {
                    if (conversationContext.keyword) formData.append("contextKeyword", conversationContext.keyword);
                    if (conversationContext.topic) formData.append("contextTopic", conversationContext.topic);
                }

                const response = await fetch(visionUrl, {
                    method: "POST",
                    credentials: "same-origin",
                    headers: { Accept: "application/json" },
                    body: formData
                });

                let payload;
                if (response.status === 429) {
                    payload = { message: "Bạn đang gửi yêu cầu quá nhanh. Vui lòng đợi trong giây lát rồi thử lại nhé.", documents: [] };
                } else if (!response.ok) {
                    throw new Error("Vision assistant request failed");
                } else {
                    payload = await response.json();
                }

                const assistantTime = currentTime();
                appendAssistantResponse(messages, payload, assistantTime, payload.messageId, null, handleFeedbackClick);
                history.push({ type: "assistant", payload: payload, time: assistantTime });
                saveHistory(history);
            } catch (_error) {
                const errPayload = { message: ERROR_MESSAGE, documents: [] };
                const assistantTime = currentTime();
                appendAssistantResponse(messages, errPayload, assistantTime, null, null, handleFeedbackClick);
                history.push({ type: "assistant", payload: errPayload, time: assistantTime });
                saveHistory(history);
            } finally {
                pending = false;
                input.disabled = false;
                loading.hidden = true;
                updateComposer();
                input.focus();
            }
            return;
        }

        // 2. Trường hợp chỉ có văn bản: STREAMING REAL-TIME QUA SSE (/stream)
        loading.hidden = true;
        if (streamingBar) streamingBar.hidden = false;

        const group = element("div", "assistant-response");
        const row = element("div", "assistant-message-row assistant-message-row-system");
        const content = element("div");
        const bubble = element("div", "assistant-message assistant-message-system assistant-thinking-state");
        bubble.innerHTML = '<div class="assistant-thinking-indicator"><span class="thinking-dot"></span><span class="thinking-dot"></span><span class="thinking-dot"></span><span class="thinking-text">Đang suy nghĩ...</span></div>';
        content.append(bubble);
        row.append(createSystemAvatar(), content);
        group.append(row);
        messages.append(group);
        messages.scrollTo({ top: messages.scrollHeight, behavior: "smooth" });

        currentAbortController = new AbortController();
        let accumulatedText = "";
        let collectedSources = [];
        let messageId = null;

        try {
            await streamAssistantResponse({
                url: assistantUrl,
                message: message,
                scopedDocumentId: activeScopedDocument?.id || null,
                context: conversationContext,
                signal: currentAbortController.signal,
                onStart: () => {
                    // Bubble đã được khởi tạo
                },
                onCitation: (sources) => {
                    collectedSources = sources;
                },
                onToken: (token) => {
                    if (bubble.classList.contains("assistant-thinking-state")) {
                        bubble.classList.remove("assistant-thinking-state");
                        bubble.innerHTML = "";
                    }
                    accumulatedText += token;
                    bubble.textContent = accumulatedText;
                    const cursor = element("span", "assistant-streaming-cursor");
                    cursor.setAttribute("aria-hidden", "true");
                    bubble.append(cursor);
                    messages.scrollTo({ top: messages.scrollHeight, behavior: "auto" });
                },
                onDone: (donePayload) => {
                    messageId = donePayload?.messageId || `msg-${Date.now()}`;
                },
                onError: (errText) => {
                    throw new Error(errText);
                }
            });

            // Hoàn tất câu trả lời: Render Markdown + KaTeX + Citations + Footer
            const assistantTime = currentTime();
            const cleanPayload = {
                answer: accumulatedText,
                sources: collectedSources,
                messageId: messageId
            };
            bubble.innerHTML = "";
            appendAnswerContent(bubble, cleanPayload);
            const footer = createMessageFooter(accumulatedText, assistantTime, messageId, null, handleFeedbackClick);
            content.append(footer);

            history.push({ type: "assistant", payload: cleanPayload, time: assistantTime });
            saveHistory(history);

        } catch (err) {
            if (err.name === "AbortError") {
                // Người dùng chủ động dừng tạo câu trả lời
                const assistantTime = currentTime();
                if (accumulatedText.trim().length > 0) {
                    const cleanPayload = {
                        answer: accumulatedText,
                        sources: collectedSources,
                        messageId: messageId || `msg-stopped-${Date.now()}`
                    };
                    bubble.innerHTML = "";
                    appendAnswerContent(bubble, cleanPayload);
                    const stopNote = element("p", "assistant-stopped-note", "⏹ Bạn đã dừng tạo câu trả lời.");
                    stopNote.style.fontSize = "0.75rem";
                    stopNote.style.color = "#94a3b8";
                    stopNote.style.marginTop = "0.4rem";
                    stopNote.style.fontStyle = "italic";
                    bubble.append(stopNote);
                    const footer = createMessageFooter(accumulatedText, assistantTime, cleanPayload.messageId, null, handleFeedbackClick);
                    content.append(footer);

                    history.push({ type: "assistant", payload: cleanPayload, time: assistantTime });
                    saveHistory(history);
                } else {
                    group.remove();
                }
            } else {
                bubble.innerHTML = "";
                const errText = err.message && err.message.length < 150 ? err.message : ERROR_MESSAGE;
                const errPayload = { message: errText, sources: [] };
                appendAnswerContent(bubble, errPayload);
                const assistantTime = currentTime();
                const footer = createMessageFooter(errText, assistantTime, null, null, null);
                content.append(footer);
            }
        } finally {
            pending = false;
            currentAbortController = null;
            if (streamingBar) streamingBar.hidden = true;
            input.disabled = false;
            updateComposer();
            input.focus();
        }
    };

    // Xử lý nút Dừng phản hồi (Stop Generation)
    if (stopButton) {
        stopButton.addEventListener("click", () => {
            if (currentAbortController) {
                currentAbortController.abort();
            }
        });
    }

    // Dán ảnh từ Clipboard (PrtScn / Ctrl + V)
    const handlePaste = (event) => {
        const clipboardData = event.clipboardData || window.clipboardData;
        if (!clipboardData || !clipboardData.items) return;

        for (let i = 0; i < clipboardData.items.length; i++) {
            const item = clipboardData.items[i];
            if (item.type && item.type.startsWith("image/")) {
                const blob = item.getAsFile();
                if (blob) {
                    event.preventDefault();
                    setAttachment(blob);
                    input.focus();
                    break;
                }
            }
        }
    };

    input.addEventListener("paste", handlePaste);
    form.addEventListener("paste", handlePaste);

    if (attachBtn && fileInput) {
        attachBtn.addEventListener("click", () => fileInput.click());
        fileInput.addEventListener("change", () => {
            if (fileInput.files && fileInput.files[0]) {
                setAttachment(fileInput.files[0]);
            }
        });
    }

    if (attachmentRemove) {
        attachmentRemove.addEventListener("click", () => {
            clearAttachment();
            input.focus();
        });
    }

    openButton.addEventListener("click", () => setOpen(panel.hidden));
    closeButton.addEventListener("click", () => setOpen(false));
    clearButton.addEventListener("click", () => {
        if (pending) return;
        clearAttachment();
        messages.replaceChildren(...initialConversation.map((node) => node.cloneNode(true)));
        conversationContext = null;
        history = [];
        activeScopedDocument = null;
        if (scopedBanner) scopedBanner.hidden = true;
        input.placeholder = "Dán ảnh (Ctrl+V) hoặc hỏi bài tập, Java, AI…";
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
        } else if (event.key === "Backspace" && input.value === "" && attachedFile) {
            clearAttachment();
        } else if (event.key === "Escape" && attachedFile) {
            clearAttachment();
            event.stopPropagation();
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
