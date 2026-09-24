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

    const params = new URLSearchParams();
    if (source.pageNumber !== null && source.pageNumber !== undefined) {
        params.set("page", String(source.pageNumber));
    }
    if (source.chunkId) {
        params.set("chunkId", String(source.chunkId));
    }
    const citationIndex = source.sourceId || source.citationIndex;
    if (citationIndex) {
        params.set("citation", String(citationIndex));
    }
    const snippetText = source.snippet || source.content || source.excerpt || "";
    const phrase = extractSearchPhrase(snippetText);
    if (phrase) {
        params.set("search", phrase);
    }
    if (snippetText) {
        params.set("highlight", snippetText.slice(0, 300));
    }

    return `/repository/${source.documentId}?${params.toString()}#reader-title`;
}

// Quản lý phần tử Popover/Tooltip trích dẫn đang hiển thị trên màn hình
let activeCitationPopover = null;
let activeSetOpenFn = null;
let urlHighlightExecuted = false;

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
 * Trích xuất một cụm từ tìm kiếm ngắn gọn (3-6 từ) từ đoạn trích dẫn để truyền vào trình xem PDF (#search=...).
 * Giúp PDF viewer tự động cuộn đến đúng vị trí và bôi vàng (highlight) từ khóa trong tệp PDF.
 */
function extractSearchPhrase(text) {
    if (!text) return "";
    const clean = text
        .replace(/[*_#`~=]+/g, " ")
        .replace(/^[>\s*-]+/gm, "")
        .replace(/\b(Description|Action|Note|Lưu ý|Mô tả|Ví dụ)\s*:\s*/gi, "");
    const sentences = clean.split(/[.?!;\n\r]+/).map((s) => s.trim()).filter(Boolean);
    for (const s of sentences) {
        const words = s.split(/\s+/).filter((w) => w.length > 1 && !/^[0-9]+$/.test(w));
        if (words.length >= 4) {
            return words.slice(0, 6).join(" ");
        }
    }
    for (const s of sentences) {
        const words = s.split(/\s+/).filter((w) => w.length > 1 && !/^[0-9]+$/.test(w));
        if (words.length >= 2) {
            return words.slice(0, 5).join(" ");
        }
    }
    return clean.slice(0, 40).trim();
}

/**
 * Tải trực tiếp phiên bản PDF được bôi vàng (highlight) từ máy chủ (/view/{id}?highlight=...#page=...),
 * hiển thị Banner đối chiếu trích dẫn màu vàng nổi bật và cuộn mượt đoạn văn bản vào giữa khung nhìn (center).
 *
 * @param {Object} source - Nguồn trích dẫn gồm { snippet, content, pageNumber, sourceId, documentId, searchPhrase, sectionTitle, chunkId }
 */
async function highlightDocumentSnippetOnPage(source) {
    if (!source) return;
    const snippetText = (source.content || source.snippet || source.excerpt || "").trim();
    if (!snippetText) return;

    // Xóa bất kỳ banner cũ nếu có trước đây
    const existingBanner = document.getElementById("document-citation-highlight-banner");
    if (existingBanner) existingBanner.remove();

    const searchPhrase = source.searchPhrase || extractSearchPhrase(snippetText);
    const iframe = document.querySelector(".document-reader-frame");

    const pageDocEl = document.querySelector("[data-page-doc-id]");
    const targetDocId = source.documentId != null
        ? String(source.documentId)
        : (pageDocEl ? pageDocEl.dataset.pageDocId : null);

    let targetPage = source.pageNumber;

    // 1. Chỉ gọi locate nếu chưa xác định được targetPage từ metadata chunk
    if (!targetPage && targetDocId && searchPhrase) {
        try {
            const resp = await fetch(`/view/${targetDocId}/locate?phrase=${encodeURIComponent(searchPhrase)}`);
            if (resp.ok) {
                const data = await resp.json();
                if (data && data.page) {
                    targetPage = data.page;
                }
            }
        } catch (e) {
            console.warn("Could not locate snippet page", e);
        }
    }

    const citationIndex = source.sourceId || source.citationIndex || 1;

    // 2. Tạo Banner đối chiếu trích dẫn hiển thị ngay phía trên trình đọc PDF
    const banner = element("div", "document-citation-highlight-banner");
    banner.id = "document-citation-highlight-banner";

    const bannerHeader = element("div", "citation-highlight-header");
    const badge = element("div", "citation-highlight-badge");
    badge.innerHTML = `<strong>[Nguồn ${citationIndex}]</strong> Đoạn trích dẫn AI tham chiếu:`;

    const actions = element("div", "citation-highlight-actions");
    if (targetPage) {
        const pagePill = element("span", "citation-highlight-page", `Trang ${targetPage}`);
        actions.append(pagePill);
    }
    if (source.chunkId) {
        const chunkPill = element("span", "citation-highlight-pill", `Chunk ${source.chunkId}`);
        actions.append(chunkPill);
    }
    if (source.sectionTitle) {
        const sectionPill = element("span", "citation-highlight-section", `Mục: ${source.sectionTitle}`);
        actions.append(sectionPill);
    }

    const copyBtn = element("button", "citation-highlight-copy-btn", "Sao chép");
    copyBtn.type = "button";
    copyBtn.title = "Sao chép đoạn trích dẫn";
    copyBtn.addEventListener("click", () => {
        navigator.clipboard.writeText(snippetText);
        copyBtn.textContent = "Đã chép ✓";
        setTimeout(() => copyBtn.textContent = "Sao chép", 2000);
    });
    actions.append(copyBtn);

    const closeBtn = element("button", "citation-highlight-close", "✕");
    closeBtn.type = "button";
    closeBtn.setAttribute("aria-label", "Đóng đoạn trích dẫn");
    closeBtn.addEventListener("click", () => banner.remove());
    actions.append(closeBtn);

    bannerHeader.append(badge, actions);

    // Format snippet trong ngoặc kép và bôi vàng từ khóa tham chiếu
    const bannerBody = element("div", "citation-highlight-body");
    const cleanSnippet = snippetText.replace(/^[“"']+|[”"']+$/g, "").trim();
    let textMatched = false;

    if (searchPhrase && cleanSnippet.toLowerCase().includes(searchPhrase.toLowerCase())) {
        const idx = cleanSnippet.toLowerCase().indexOf(searchPhrase.toLowerCase());
        const before = cleanSnippet.slice(0, idx);
        const matched = cleanSnippet.slice(idx, idx + searchPhrase.length);
        const after = cleanSnippet.slice(idx + searchPhrase.length);
        bannerBody.innerHTML = `“${escapeHtml(before)}<mark class="citation-yellow-mark">${escapeHtml(matched)}</mark>${escapeHtml(after)}”`;
        textMatched = true;
    } else {
        // Thử tìm theo 3 từ khóa liên tiếp
        const words = searchPhrase ? searchPhrase.split(/\s+/).filter(w => w.length > 2) : [];
        if (words.length >= 2) {
            const sub = words.slice(0, 3).join(" ");
            const subIdx = cleanSnippet.toLowerCase().indexOf(sub.toLowerCase());
            if (subIdx !== -1) {
                const before = cleanSnippet.slice(0, subIdx);
                const matched = cleanSnippet.slice(subIdx, subIdx + sub.length);
                const after = cleanSnippet.slice(subIdx + sub.length);
                bannerBody.innerHTML = `“${escapeHtml(before)}<mark class="citation-yellow-mark">${escapeHtml(matched)}</mark>${escapeHtml(after)}”`;
                textMatched = true;
            }
        }
        if (!textMatched) {
            bannerBody.innerHTML = `“${escapeHtml(cleanSnippet)}”`;
            const warn = element("div", "citation-highlight-warning");
            warn.style.marginTop = "0.5rem";
            warn.style.fontSize = "0.75rem";
            warn.style.color = "#9a3412";
            warn.textContent = "Không thể xác định chính xác vị trí đoạn trích trong tài liệu.";
            bannerBody.append(warn);
        }
    }

    banner.append(bannerHeader, bannerBody);

    // Chèn banner vào ngay phía trên iframe reader
    if (iframe && iframe.parentNode) {
        iframe.parentNode.insertBefore(banner, iframe);
    } else {
        const reader = document.querySelector(".document-reader");
        if (reader) reader.prepend(banner);
    }

    // 3. Cập nhật iframe tải trực tiếp phiên bản PDF đã được bôi vàng bên trong tài liệu
    if (iframe) {
        const rawSrc = iframe.getAttribute("src") || iframe.src;
        const baseUrl = rawSrc.split("?")[0].split("#")[0];
        const queryParts = [];
        if (searchPhrase) {
            const cleanPhrase = searchPhrase.replace(/["']/g, "").trim();
            if (cleanPhrase) {
                queryParts.push(`highlight=${encodeURIComponent(cleanPhrase)}`);
            }
        }
        if (targetPage) {
            queryParts.push(`page=${targetPage}`);
        }
        const queryString = queryParts.length > 0 ? `?${queryParts.join("&")}` : "";
        const hashString = targetPage ? `#page=${targetPage}&search=${encodeURIComponent(searchPhrase || "")}` : "";
        const newSrc = `${baseUrl}${queryString}${hashString}`;
        if (iframe.src !== newSrc) {
            iframe.src = newSrc;
        }
    }

    // 4. Cuộn mượt đưa đoạn trích dẫn và trình đọc PDF vào chính giữa khung nhìn (center)
    const reader = document.querySelector(".document-reader");
    if (reader) {
        reader.classList.add("is-citation-active");
        setTimeout(() => reader.classList.remove("is-citation-active"), 4500);
    }

    banner.scrollIntoView({ behavior: "smooth", block: "center" });

    // 5. Nếu trên trang có xuất hiện tóm tắt / mô tả trùng khớp thì bôi vàng phụ trợ
    if (searchPhrase && searchPhrase.length > 3) {
        highlightMatchingTextInElements(searchPhrase);
    }
}

/**
 * Tìm và bôi vàng (highlight) các đoạn văn bản trên trang chi tiết nếu trùng khớp với cụm từ trích dẫn.
 */
function highlightMatchingTextInElements(phrase) {
    if (!phrase || phrase.length < 3) return;
    const targets = document.querySelectorAll(".document-abstract, .academic-summary p");
    targets.forEach((target) => {
        if (!target.dataset.originalText) {
            target.dataset.originalText = target.innerHTML;
        }
        const text = target.textContent;
        const index = text.toLowerCase().indexOf(phrase.toLowerCase());
        if (index !== -1) {
            const matched = text.substring(index, index + phrase.length);
            target.innerHTML = text.substring(0, index)
                + `<mark class="citation-yellow-mark">${matched}</mark>`
                + text.substring(index + phrase.length);
        } else {
            const words = phrase.split(/\s+/).filter((w) => w.length > 2);
            if (words.length >= 3) {
                const subPhrase = words.slice(0, 3).join(" ");
                const subIdx = text.toLowerCase().indexOf(subPhrase.toLowerCase());
                if (subIdx !== -1) {
                    const matched = text.substring(subIdx, subIdx + subPhrase.length);
                    target.innerHTML = text.substring(0, subIdx)
                        + `<mark class="citation-yellow-mark">${matched}</mark>`
                        + text.substring(subIdx + subPhrase.length);
                }
            }
        }
    });
}

/**
 * Tự động kiểm tra URL query parameters khi người dùng mở trang từ liên kết "Xem tài liệu →".
 * Nếu có tham số ?highlight=... hoặc ?search=... thì tự động kích hoạt bôi vàng và cuộn tới vị trí trích dẫn.
 */
function highlightFromUrlParams() {
    if (urlHighlightExecuted) return;
    try {
        const params = new URLSearchParams(window.location.search);
        const highlightText = params.get("highlight");
        const searchPhrase = params.get("search");
        const pageNumber = params.get("page");
        const chunkId = params.get("chunkId");
        const citationNumber = params.get("citation");
        const sectionTitle = params.get("section");

        if (highlightText || searchPhrase || pageNumber || chunkId) {
            urlHighlightExecuted = true;
            const pageDocEl = document.querySelector("[data-page-doc-id]");
            const docId = pageDocEl ? Number(pageDocEl.dataset.pageDocId) : null;

            const triggerHighlight = (fullContent) => {
                highlightDocumentSnippetOnPage({
                    documentId: docId,
                    chunkId: chunkId ? Number(chunkId) : null,
                    content: fullContent,
                    snippet: fullContent || highlightText || searchPhrase || "Nội dung trích dẫn được tham khảo.",
                    searchPhrase: searchPhrase,
                    pageNumber: pageNumber ? Number(pageNumber) : null,
                    sourceId: citationNumber ? Number(citationNumber) : 1,
                    sectionTitle: sectionTitle || null
                });
            };

            if (chunkId) {
                const chunkEndpoint = "/api/document-assistant/chunks/" + chunkId + "?citationIndex=" + (citationNumber || 1) + "&documentId=" + (docId || "");
                fetch(chunkEndpoint)
                    .then(r => r.ok ? r.json() : null)
                    .then(data => {
                        triggerHighlight(data ? (data.content || data.snippet) : highlightText);
                    })
                    .catch(() => triggerHighlight(highlightText));
            } else {
                setTimeout(() => triggerHighlight(highlightText), 300);
            }
        }
    } catch (e) {
        console.warn("Could not parse citation highlight URL params", e);
    }
}

/**
 * Điều hướng người dùng tới tài liệu nguồn và kích hoạt highlight đoạn trích dẫn.
 * Nếu đang ở sẵn trên trang tài liệu: Cuộn mượt và highlight trực tiếp.
 * Nếu ở trang khác: Mở trang tài liệu kèm tham số highlight.
 */
async function navigateToCitation(source) {
    hideCitationTooltip();
    if (!source) return;

    const citationIndex = source.sourceId || source.citationIndex || 1;
    console.log(`[CITATION NAVIGATE] citationIndex=${citationIndex}, documentId=${source.documentId}, chunkId=${source.chunkId}, pageNumber=${source.pageNumber}`);

    const pageDocEl = document.querySelector("[data-page-doc-id]");
    const currentPageDocId = pageDocEl ? pageDocEl.dataset.pageDocId : null;

    const targetDocId = source.documentId != null 
        ? String(source.documentId) 
        : (source.detailUrl ? (source.detailUrl.match(/\/repository\/(\d+)/)?.[1] || null) : null);

    const isSamePage = (currentPageDocId && targetDocId && String(currentPageDocId) === String(targetDocId))
        || (targetDocId && window.location.pathname.startsWith(`/repository/${targetDocId}`));

    if (isSamePage) {
        if (window.innerWidth <= 768 && activeSetOpenFn) {
            activeSetOpenFn(false);
        }
        await highlightDocumentSnippetOnPage(source);
    } else {
        const url = citationUrl(source);
        window.location.href = url;
    }
}

/**
 * Hiển thị Tooltip/Popover xem trước nội dung trích dẫn (snippet) khi người dùng di chuột (hover) vào [1], [2].
 * Hiển thị chuẩn xác: [Nguồn X] title, Trang X, Chunk Y và nội dung chunk trong dấu ngoặc kép.
 *
 * @param {HTMLElement} targetElement - Thẻ citation được tương tác
 * @param {Object} source - Dữ liệu nguồn tham khảo (gồm title, content, snippet, documentId, detailUrl...)
 */
function showCitationTooltip(targetElement, source) {
    hideCitationTooltip();
    if (!source) {
        console.warn("[CITATION] Source is missing or undefined");
        return;
    }

    const citationIndex = source.sourceId || source.citationIndex || 1;
    console.log(`[CITATION HOVER] citationIndex=${citationIndex}, documentId=${source.documentId}, chunkId=${source.chunkId}, pageNumber=${source.pageNumber}`);

    // Tạo phần tử popover dạng tooltip
    const popover = element("div", "assistant-citation-popover");
    popover.setAttribute("role", "tooltip");

    // Header của popover: icon tài liệu + badge nguồn [Nguồn X] + tên tài liệu + trang + chunk
    const header = element("div", "assistant-citation-popover-header");
    const docIcon = element("span", "assistant-citation-popover-icon", "📄");

    const metaWrap = element("div", "assistant-citation-popover-meta");
    metaWrap.style.flex = "1";
    metaWrap.style.minWidth = "0";

    const topRow = element("div", "assistant-citation-popover-top-row");
    topRow.style.display = "flex";
    topRow.style.alignItems = "center";
    topRow.style.gap = "0.4rem";

    const sourceBadge = element("span", "assistant-citation-popover-badge", `[Nguồn ${citationIndex}]`);
    sourceBadge.style.fontWeight = "bold";
    sourceBadge.style.color = "var(--primary-color, #4338ca)";
    sourceBadge.style.flexShrink = "0";

    const titleText = element("span", "assistant-citation-popover-title", source.title || "Tài liệu EduRepo");
    titleText.title = source.title || "Tài liệu EduRepo";
    topRow.append(sourceBadge, titleText);

    const subRow = element("div", "assistant-citation-popover-subtags");
    subRow.style.display = "flex";
    subRow.style.alignItems = "center";
    subRow.style.gap = "0.35rem";
    subRow.style.marginTop = "0.2rem";
    subRow.style.fontSize = "0.72rem";
    subRow.style.color = "#64748b";

    const pageBadge = element("span", "assistant-citation-popover-page", source.pageNumber ? `Trang ${source.pageNumber}` : "Trang --");
    pageBadge.style.fontWeight = "600";
    pageBadge.style.color = "#475569";
    subRow.append(pageBadge);

    const chunkBadge = element("span", "assistant-citation-popover-chunk", source.chunkId ? `• Chunk ${source.chunkId}` : "");
    if (source.chunkId) {
        subRow.append(chunkBadge);
    }

    if (source.sectionTitle) {
        const sectionBadge = element("span", "assistant-citation-popover-section", `• ${source.sectionTitle}`);
        sectionBadge.style.overflow = "hidden";
        sectionBadge.style.textOverflow = "ellipsis";
        sectionBadge.style.whiteSpace = "nowrap";
        sectionBadge.title = source.sectionTitle;
        subRow.append(sectionBadge);
    }

    metaWrap.append(topRow, subRow);
    header.append(docIcon, metaWrap);

    // Body của popover: CHÍNH XÁC nội dung chunk từ source (không dùng ảnh thumbnail trang đầu, không dùng mục lục)
    let snippetText = (source.content || source.snippet || source.excerpt || "").trim();
    if (!snippetText) {
        snippetText = "Đang tải trích dẫn...";
    }
    const cleanSnippet = snippetText.replace(/^[“"']+|[”"']+$/g, "").trim();
    const body = element("div", "assistant-citation-popover-snippet", `“${cleanSnippet}”`);

    // Footer của popover: Nút "Xem tài liệu →"
    const footer = element("div", "assistant-citation-popover-footer");
    const viewLink = element("a", "assistant-citation-popover-link", "Xem tài liệu →");
    viewLink.href = citationUrl(source);
    viewLink.target = "_blank";
    viewLink.rel = "noopener";

    viewLink.addEventListener("click", (e) => {
        e.preventDefault();
        navigateToCitation(source);
    });

    footer.append(viewLink);
    popover.append(header, body, footer);

    // Truy vấn ngầm chi tiết chính xác từ backend theo chunkId để làm giàu dữ liệu
    if (source.chunkId) {
        const targetDocId = source.documentId != null 
            ? String(source.documentId) 
            : (source.detailUrl ? (source.detailUrl.match(/\/repository\/(\d+)/)?.[1] || null) : null);
        const chunkApiUrl = "/api/document-assistant/chunks/" + source.chunkId + "?citationIndex=" + citationIndex + "&documentId=" + (targetDocId || "");
        fetch(chunkApiUrl)
            .then(async (resp) => {
                if (resp.ok) {
                    const chunkData = await resp.json();
                    if (chunkData) {
                        const exactContent = chunkData.content || chunkData.snippet;
                        if (exactContent) {
                            const clean = exactContent.replace(/^[“"']+|[”"']+$/g, "").trim();
                            body.textContent = `“${clean}”`;
                            source.content = exactContent;
                            source.snippet = chunkData.snippet || exactContent.slice(0, 300);
                        }
                        if (chunkData.pageNumber) {
                            source.pageNumber = chunkData.pageNumber;
                            pageBadge.textContent = `Trang ${chunkData.pageNumber}`;
                        }
                        if (chunkData.chunkId) {
                            chunkBadge.textContent = `• Chunk ${chunkData.chunkId}`;
                            if (!chunkBadge.parentNode) subRow.append(chunkBadge);
                        }
                        if (chunkData.sectionTitle && !source.sectionTitle) {
                            source.sectionTitle = chunkData.sectionTitle;
                        }
                        viewLink.href = citationUrl(source);
                    }
                } else if (resp.status === 404) {
                    console.warn(`[CITATION] Chunk ${source.chunkId} not found in database (404)`);
                    body.textContent = "Không tìm thấy đoạn trích dẫn nguồn cho citation này trong cơ sở dữ liệu.";
                    body.style.color = "#dc2626";
                }
            })
            .catch((err) => {
                console.warn("[CITATION] Error fetching chunk detail:", err);
            });
    } else if (!source.content && !source.snippet && !source.excerpt) {
        body.textContent = "Không tìm thấy đoạn trích dẫn nguồn cho citation này.";
        body.style.color = "#dc2626";
    }

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
    if (top < 10) {
        top = rect.bottom + margin;
    }

    let left = rect.left + (rect.width / 2) - (popoverRect.width / 2);
    if (left < 10) left = 10;
    if (left + popoverRect.width > window.innerWidth - 10) {
        left = window.innerWidth - popoverRect.width - 10;
    }

    popover.style.top = `${window.scrollY + top}px`;
    popover.style.left = `${window.scrollX + left}px`;
}

/**
 * Gắn các sự kiện tương tác chuột (hover) và cảm ứng/bàn phím (click/touch) cho thẻ Citation.
 * Hover: Xem trước nội dung chunk và trang.
 * Click: Lập tức mở tài liệu, nhảy đến đúng trang, bôi vàng và cuộn vào giữa màn hình.
 *
 * @param {HTMLElement} citation - Thẻ citation DOM node
 * @param {number} sourceId - Số thứ tự nguồn tham khảo
 * @param {Object} source - Dữ liệu chi tiết của nguồn
 */
function bindCitationEvents(citation, sourceId, source) {
    // Hover: Hiển thị preview popup chính xác
    let leaveTimer = null;
    citation.addEventListener("mouseenter", () => {
        if (leaveTimer) clearTimeout(leaveTimer);
        console.log(`[CITATION HOVER] citationIndex=${sourceId}, documentId=${source?.documentId}, chunkId=${source?.chunkId}, pageNumber=${source?.pageNumber}`);
        showCitationTooltip(citation, source);
    });
    citation.addEventListener("mouseleave", () => {
        leaveTimer = setTimeout(() => {
            if (activeCitationPopover && !activeCitationPopover.matches(":hover")) {
                hideCitationTooltip();
            }
        }, 220);
    });

    // Click: Mở ngay document viewer, chuyển tới đúng trang, bôi vàng và cuộn vào giữa vùng nhìn
    citation.addEventListener("click", (e) => {
        e.preventDefault();
        e.stopPropagation();
        console.log(`[CITATION CLICK] citationIndex=${sourceId}, documentId=${source?.documentId}, chunkId=${source?.chunkId}, pageNumber=${source?.pageNumber}`);
        navigateToCitation(source);
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
    formatted = formatted.replace(/(?<=^|[\s\p{P}])__([^_]+)__(?=$|[\s\p{P}])/gu, '<strong>$1</strong>');

    // Italic: *text* hoặc _text_ (chỉ kích hoạt _ khi có ranh giới từ/dấu câu, tránh xung đột snake_case)
    formatted = formatted.replace(/\*([^*]+)\*/g, '<em>$1</em>');
    formatted = formatted.replace(/(?<=^|[\s\p{P}])_([^\s_]+(?:\s+[^\s_]+)*)_(?=$|[\s\p{P}])/gu, '<em>$1</em>');

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
        return `§§CODEBLOCK:${codeBlocks.length - 1}§§`;
    });

    const inlineCodes = [];
    text = text.replace(/`[^`]+`/g, (match) => {
        inlineCodes.push(match);
        return `§§INLINECODE:${inlineCodes.length - 1}§§`;
    });

    // 2. Trích xuất Math Block: $$...$$ hoặc \[...\]
    text = text.replace(/\$\$([\s\S]+?)\$\$/g, (match, formula) => {
        mathBlocks.push(formula.trim());
        return `§§MATHBLOCK:${mathBlocks.length - 1}§§`;
    });
    text = text.replace(/\\\[([\s\S]+?)\\\]/g, (match, formula) => {
        mathBlocks.push(formula.trim());
        return `§§MATHBLOCK:${mathBlocks.length - 1}§§`;
    });

    // 3. Trích xuất Math Inline: $...$ hoặc \(...\)
    text = text.replace(/(?<!\\)\$([^\$\n\r]+?)(?<!\\)\$/g, (match, formula) => {
        const trimmed = formula.trim();
        // Bỏ qua nếu là số tiền thông thường (ví dụ: $100, $50k, $2.5)
        if (/^\d+(?:[.,]\d+)?\s*(?:k|m|usd|vnđ)?$/i.test(trimmed)) {
            return match;
        }
        mathInlines.push(trimmed);
        return `§§MATHINLINE:${mathInlines.length - 1}§§`;
    });
    text = text.replace(/\\\(([\s\S]+?)\\\)/g, (match, formula) => {
        mathInlines.push(formula.trim());
        return `§§MATHINLINE:${mathInlines.length - 1}§§`;
    });

    // 3.1 Bổ trợ phát hiện các ký hiệu toán/logic LaTeX rời rạc khi AI quên kẹp dấu $...$ (ví dụ \neg, \forall, \exists, \land, \lor, \sigma, ...)
    const loneMathPattern = /(?<![\\a-zA-Z0-9])\\(forall|exists|land|lor|neg|vee|wedge|sigma|pi|rho|bowtie|times|cap|cup|in|notin|subset|subseteq|supset|supseteq|emptyset|setminus|rightarrow|leftarrow|Rightarrow|Leftarrow|iff|to|le|ge|neq|approx|pm|div|cdot|infty|partial|nabla)(?![a-zA-Z])/g;
    text = text.replace(loneMathPattern, (match) => {
        mathInlines.push(match);
        return `§§MATHINLINE:${mathInlines.length - 1}§§`;
    });

    // 4. Khôi phục lại khối mã nguồn và inline code
    text = text.replace(/§§INLINECODE:(\d+)§§/g, (m, idx) => inlineCodes[idx]);
    text = text.replace(/§§CODEBLOCK:(\d+)§§/g, (m, idx) => codeBlocks[idx]);

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
    html = html.replace(/§§MATH_?BLOCK:(\d+)§§/g, (m, idx) => {
        const formula = mathBlocks[idx] || "";
        return `<div class="katex-display" data-katex-math="${escapeHtml(formula)}"></div>`;
    });
    html = html.replace(/§§MATH_?INLINE:(\d+)§§/g, (m, idx) => {
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

    // Ánh xạ nguồn theo sourceId hoặc citationIndex (1, 2, ...)
    const sourceMap = new Map();
    sources.forEach((src, index) => {
        const id = src.sourceId != null ? Number(src.sourceId) : (src.citationIndex != null ? Number(src.citationIndex) : index + 1);
        sourceMap.set(id, src);
        if (src.citationIndex != null) {
            sourceMap.set(Number(src.citationIndex), src);
        }
        if (src.sourceId != null) {
            sourceMap.set(Number(src.sourceId), src);
        }
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
    sessionId,
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
    if (sessionId) {
        streamUrl.searchParams.set("sessionId", String(sessionId));
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
                    let startData = null;
                    try {
                        startData = JSON.parse(dataStr);
                    } catch {}
                    onStart && onStart(startData);
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

    // Các thành phần Quản lý đa phiên hội thoại (Multi-Session Drawer)
    const sessionsUrl = root.dataset.sessionsUrl || "/api/chat-sessions";
    const isAuthenticated = root.dataset.authenticated === "true";

    const sidebar = root.querySelector("[data-assistant-sidebar]");
    const sidebarToggleBtn = root.querySelector("[data-assistant-sidebar-toggle]");
    const sidebarCloseBtn = root.querySelector("[data-assistant-sidebar-close]");
    const newChatHeaderBtn = root.querySelector("[data-assistant-new-session]");
    const newChatSidebarBtn = root.querySelector("[data-assistant-sidebar-new]");
    const sessionListContainer = root.querySelector("[data-session-list]");
    const guestSyncBanner = root.querySelector("[data-guest-sync-banner]");
    const guestSyncCount = root.querySelector("[data-guest-sync-count]");
    const guestSyncConfirmBtn = root.querySelector("[data-guest-sync-confirm]");
    const guestSyncDismissBtn = root.querySelector("[data-guest-sync-dismiss]");
    const guestHint = root.querySelector("[data-guest-hint]");

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

    // Multi-Session Storage Keys & State
    const ACTIVE_SESSION_KEY = "edurepo_active_session_id";
    const GUEST_SESSIONS_KEY = "edurepo_guest_sessions";
    const GUEST_MIGRATED_KEY = "edurepo_guest_migrated";

    let currentSessions = [];
    let activeSessionId = sessionStorage.getItem(ACTIVE_SESSION_KEY) || null;

    const getGuestSessions = () => {
        try {
            const raw = localStorage.getItem(GUEST_SESSIONS_KEY);
            if (raw) return JSON.parse(raw);
        } catch {}
        try {
            const oldHistory = localStorage.getItem(STORAGE_HISTORY_KEY);
            if (oldHistory) {
                const parsed = JSON.parse(oldHistory);
                if (Array.isArray(parsed) && parsed.length > 0) {
                    const firstUser = parsed.find(m => m.type === "user");
                    const converted = [{
                        id: "guest_" + Date.now(),
                        title: firstUser ? firstUser.content.slice(0, 40) : "Cuộc trò chuyện đã lưu",
                        scopeType: "GLOBAL",
                        scopedDocumentId: null,
                        messages: parsed.map(m => ({
                            senderType: m.type === "user" ? "USER" : "ASSISTANT",
                            content: m.type === "user" ? m.content : (m.payload?.answer || m.payload?.message || ""),
                            citations: m.payload?.sources || [],
                            rating: m.payload?.rating || null,
                            clientMessageId: m.payload?.messageId || ("client_" + Date.now())
                        })),
                        createdAt: new Date().toISOString(),
                        updatedAt: new Date().toISOString()
                    }];
                    localStorage.setItem(GUEST_SESSIONS_KEY, JSON.stringify(converted));
                    localStorage.removeItem(STORAGE_HISTORY_KEY);
                    return converted;
                }
            }
        } catch {}
        return [];
    };

    const saveGuestSessions = (sessions) => {
        try {
            localStorage.setItem(GUEST_SESSIONS_KEY, JSON.stringify(sessions));
        } catch (e) {
            console.warn("Could not save guest sessions", e);
        }
    };

    const groupSessionsByDate = (sessions) => {
        const today = new Date();
        today.setHours(0, 0, 0, 0);

        const sevenDaysAgo = new Date(today);
        sevenDaysAgo.setDate(sevenDaysAgo.getDate() - 7);

        const groups = {
            today: [],
            last7Days: [],
            older: []
        };

        sessions.forEach(session => {
            const sessionDate = new Date(session.updatedAt || session.createdAt || Date.now());
            if (sessionDate >= today) {
                groups.today.push(session);
            } else if (sessionDate >= sevenDaysAgo) {
                groups.last7Days.push(session);
            } else {
                groups.older.push(session);
            }
        });

        return groups;
    };

    const renderSessionList = (sessions) => {
        if (!sessionListContainer) return;
        sessionListContainer.innerHTML = "";

        if (!sessions || sessions.length === 0) {
            const empty = element("div", "session-empty-state");
            empty.innerHTML = `
                <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.5">
                    <path d="M8 12h.01M12 12h.01M16 12h.01M21 12c0 4.418-4.03 8-9 8a9.863 9.863 0 01-4.255-.949L3 20l1.395-3.72C3.512 15.042 3 13.574 3 12c0-4.418 4.03-8 9-8s9 3.582 9 8z"/>
                </svg>
                <p>Chưa có cuộc trò chuyện nào</p>
            `;
            sessionListContainer.append(empty);
            return;
        }

        const groups = groupSessionsByDate(sessions);
        const renderGroup = (title, items) => {
            if (!items || items.length === 0) return;
            const groupHeader = element("div", "session-group-title", title);
            sessionListContainer.append(groupHeader);

            items.forEach(session => {
                const itemEl = createSessionItemElement(session);
                sessionListContainer.append(itemEl);
            });
        };

        renderGroup("Hôm nay", groups.today);
        renderGroup("7 ngày qua", groups.last7Days);
        renderGroup("Cũ hơn", groups.older);
    };

    const createSessionItemElement = (session) => {
        const item = element("div", "session-item");
        if (String(session.id) === String(activeSessionId)) {
            item.classList.add("is-active");
        }
        item.dataset.sessionId = session.id;

        const content = element("div", "session-item-content");
        const icon = element("span", "session-icon");
        icon.innerHTML = session.scopeType === "DOCUMENT"
            ? `<svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"><path d="M14 2H6a2 2 0 0 0-2 2v16a2 2 0 0 0 2 2h12a2 2 0 0 0 2-2V8z"/><polyline points="14 2 14 8 20 8"/></svg>`
            : `<svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"><path d="M21 15a2 2 0 0 1-2 2H7l-4 4V5a2 2 0 0 1 2-2h14a2 2 0 0 1 2 2z"/></svg>`;

        const titleSpan = element("span", "session-title", session.title || "Cuộc trò chuyện mới");
        content.append(icon, titleSpan);

        if (session.scopeType === "DOCUMENT") {
            const badge = element("span", "session-scope-badge", "Tài liệu");
            content.append(badge);
        }

        const actions = element("div", "session-actions");
        const renameBtn = element("button", "session-action-btn", "");
        renameBtn.title = "Đổi tên";
        renameBtn.innerHTML = `<svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"><path d="M11 4H4a2 2 0 0 0-2 2v14a2 2 0 0 0 2 2h14a2 2 0 0 0 2-2v-7"/><path d="M18.5 2.5a2.121 2.121 0 0 1 3 3L12 15l-4 1 1-4 9.5-9.5z"/></svg>`;

        const deleteBtn = element("button", "session-action-btn delete-btn", "");
        deleteBtn.title = "Xóa cuộc trò chuyện";
        deleteBtn.innerHTML = `<svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"><polyline points="3 6 5 6 21 6"/><path d="M19 6v14a2 2 0 0 1-2 2H7a2 2 0 0 1-2-2V6m3 0V4a2 2 0 0 1 2-2h4a2 2 0 0 1 2 2v2"/></svg>`;

        actions.append(renameBtn, deleteBtn);
        item.append(content, actions);

        item.addEventListener("click", (e) => {
            if (e.target.closest(".session-actions") || e.target.closest(".session-rename-form")) {
                return;
            }
            selectSession(session);
        });

        renameBtn.addEventListener("click", (e) => {
            e.stopPropagation();
            startRenameSession(item, session, titleSpan);
        });

        deleteBtn.addEventListener("click", (e) => {
            e.stopPropagation();
            confirmDeleteSession(session);
        });

        return item;
    };

    const startRenameSession = (item, session, titleSpan) => {
        const originalTitle = session.title;
        const form = element("form", "session-rename-form");
        const input = element("input", "session-rename-input");
        input.type = "text";
        input.value = originalTitle;
        input.maxLength = 100;

        const saveBtn = element("button", "session-rename-btn", "");
        saveBtn.type = "submit";
        saveBtn.title = "Lưu";
        saveBtn.innerHTML = `<svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"><polyline points="20 6 9 17 4 12"/></svg>`;

        const cancelBtn = element("button", "session-rename-btn cancel-btn", "");
        cancelBtn.type = "button";
        cancelBtn.title = "Hủy";
        cancelBtn.innerHTML = `<svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"><line x1="18" y1="6" x2="6" y2="18"/><line x1="6" y1="6" x2="18" y2="18"/></svg>`;

        form.append(input, saveBtn, cancelBtn);

        const contentEl = item.querySelector(".session-item-content");
        const actionsEl = item.querySelector(".session-actions");
        contentEl.style.display = "none";
        if (actionsEl) actionsEl.style.display = "none";
        item.append(form);
        input.focus();
        input.select();

        const cleanup = () => {
            form.remove();
            contentEl.style.display = "";
            if (actionsEl) actionsEl.style.display = "";
        };

        cancelBtn.addEventListener("click", (e) => {
            e.stopPropagation();
            cleanup();
        });

        form.addEventListener("submit", async (e) => {
            e.preventDefault();
            e.stopPropagation();
            const newTitle = input.value.trim();
            if (!newTitle || newTitle === originalTitle) {
                cleanup();
                return;
            }

            if (isAuthenticated) {
                try {
                    const patchUrl = `${sessionsUrl}/${session.id}/title`;
                    const res = await fetch(patchUrl, {
                        method: "PATCH",
                        headers: {
                            "Content-Type": "application/json",
                            "Accept": "application/json"
                        },
                        credentials: "same-origin",
                        body: JSON.stringify({ title: newTitle })
                    });
                    if (res.ok) {
                        session.title = newTitle;
                        titleSpan.textContent = newTitle;
                    }
                } catch (err) {
                    console.warn("Could not rename session", err);
                }
            } else {
                const guestSessions = getGuestSessions();
                const target = guestSessions.find(s => s.id === session.id);
                if (target) {
                    target.title = newTitle;
                    target.updatedAt = new Date().toISOString();
                    saveGuestSessions(guestSessions);
                }
                session.title = newTitle;
                titleSpan.textContent = newTitle;
            }
            cleanup();
        });
    };

    const confirmDeleteSession = async (session) => {
        if (!confirm(`Bạn có chắc muốn xóa cuộc trò chuyện "${session.title}"?`)) {
            return;
        }

        if (isAuthenticated) {
            try {
                const delUrl = `${sessionsUrl}/${session.id}`;
                const res = await fetch(delUrl, {
                    method: "DELETE",
                    headers: { Accept: "application/json" },
                    credentials: "same-origin"
                });
                if (!res.ok) {
                    alert("Không thể xóa cuộc trò chuyện. Vui lòng thử lại.");
                    return;
                }
            } catch (e) {
                console.warn("Delete session error", e);
                alert("Có lỗi xảy ra khi xóa.");
                return;
            }
        } else {
            const guestSessions = getGuestSessions().filter(s => s.id !== session.id);
            saveGuestSessions(guestSessions);
        }

        currentSessions = currentSessions.filter(s => s.id !== session.id);
        renderSessionList(currentSessions);

        if (String(activeSessionId) === String(session.id)) {
            startNewChatSession();
        }
    };

    const selectSession = async (session) => {
        activeSessionId = session.id;
        sessionStorage.setItem(ACTIVE_SESSION_KEY, String(session.id));

        const allItems = sessionListContainer ? sessionListContainer.querySelectorAll(".session-item") : [];
        allItems.forEach(el => {
            el.classList.toggle("is-active", String(el.dataset.sessionId) === String(session.id));
        });

        if (sidebar) sidebar.hidden = true;

        messages.innerHTML = "";

        if (session.scopeType === "DOCUMENT" && session.scopedDocumentId) {
            setScopedDocument(session.scopedDocumentId, session.scopedDocumentTitle || "Tài liệu", null, null, false);
        } else {
            setScopedDocument(null, null);
        }

        loading.hidden = false;

        try {
            let messageList = [];
            if (isAuthenticated) {
                const msgUrl = `${sessionsUrl}/${session.id}/messages`;
                const res = await fetch(msgUrl, {
                    headers: { Accept: "application/json" },
                    credentials: "same-origin"
                });
                if (res.ok) {
                    messageList = await res.json();
                }
            } else {
                const guestSessions = getGuestSessions();
                const target = guestSessions.find(s => s.id === session.id);
                messageList = target ? target.messages : [];
            }

            loading.hidden = true;

            if (messageList.length === 0) {
                messages.replaceChildren(...initialConversation.map((node) => node.cloneNode(true)));
                renderSuggestionsUI(DEFAULT_SUGGESTIONS);
            } else {
                root.querySelector("[data-assistant-suggestions]")?.remove();
                messageList.forEach(msg => {
                    const timeStr = msg.createdAt ? formatDate(msg.createdAt) + " " + currentTime() : currentTime();
                    if (msg.senderType === "USER") {
                        appendUserMessage(messages, msg.content, timeStr, null);
                    } else if (msg.senderType === "ASSISTANT") {
                        const payload = {
                            answer: msg.content,
                            sources: msg.citations || [],
                            messageId: msg.clientMessageId || `msg-${msg.id}`
                        };
                        appendAssistantResponse(messages, payload, timeStr, payload.messageId, msg.feedbackRating, handleFeedbackClick);
                    }
                });
                messages.scrollTo({ top: messages.scrollHeight, behavior: "auto" });
            }
        } catch (e) {
            console.warn("Could not load session messages", e);
            loading.hidden = true;
        }
    };

    const startNewChatSession = () => {
        activeSessionId = null;
        sessionStorage.removeItem(ACTIVE_SESSION_KEY);

        const allItems = sessionListContainer ? sessionListContainer.querySelectorAll(".session-item") : [];
        allItems.forEach(el => el.classList.remove("is-active"));

        messages.replaceChildren(...initialConversation.map((node) => node.cloneNode(true)));
        if (pageDocEl && pageDocEl.dataset.pageDocId) {
            setScopedDocument(pageDocEl.dataset.pageDocId, pageDocEl.dataset.pageDocTitle, pageDocEl.dataset.pageDocCategory, pageDocEl.dataset.pageDocKeywords, false);
        } else {
            setScopedDocument(null, null);
            renderSuggestionsUI(DEFAULT_SUGGESTIONS);
        }

        clearAttachment();
        input.value = "";
        updateComposer();
        if (sidebar) sidebar.hidden = true;
        input.focus();
    };

    const fetchUserSessions = async () => {
        if (isAuthenticated) {
            try {
                const res = await fetch(sessionsUrl, {
                    headers: { Accept: "application/json" },
                    credentials: "same-origin"
                });
                if (res.ok) {
                    currentSessions = await res.json();
                } else {
                    currentSessions = [];
                }
            } catch (e) {
                console.warn("Could not fetch sessions", e);
                currentSessions = [];
            }
        } else {
            currentSessions = getGuestSessions();
            if (guestHint) guestHint.hidden = false;
        }
        renderSessionList(currentSessions);
    };

    const checkGuestMigration = () => {
        if (!isAuthenticated || !guestSyncBanner) return;
        const isMigrated = localStorage.getItem(GUEST_MIGRATED_KEY) === "true";
        if (isMigrated) return;

        const guestSessions = getGuestSessions();
        if (!guestSessions || guestSessions.length === 0) return;

        if (guestSyncCount) guestSyncCount.textContent = String(guestSessions.length);
        guestSyncBanner.hidden = false;

        if (guestSyncConfirmBtn) {
            guestSyncConfirmBtn.addEventListener("click", async () => {
                try {
                    guestSyncConfirmBtn.disabled = true;
                    guestSyncConfirmBtn.textContent = "Đang đồng bộ...";

                    const importPayload = {
                        sessions: guestSessions.map(s => ({
                            guestSessionId: s.id,
                            title: s.title,
                            scopeType: s.scopeType || "GLOBAL",
                            scopedDocumentId: s.scopedDocumentId || null,
                            messages: (s.messages || []).map(m => ({
                                senderType: m.senderType || "USER",
                                content: m.content || "",
                                citations: m.citations || [],
                                rating: m.rating || null,
                                clientMessageId: m.clientMessageId || null
                            }))
                        }))
                    };

                    const res = await fetch(`${sessionsUrl}/import-guest`, {
                        method: "POST",
                        headers: {
                            "Content-Type": "application/json",
                            "Accept": "application/json"
                        },
                        credentials: "same-origin",
                        body: JSON.stringify(importPayload)
                    });

                    if (res.ok) {
                        localStorage.setItem(GUEST_MIGRATED_KEY, "true");
                        localStorage.removeItem(GUEST_SESSIONS_KEY);
                        localStorage.removeItem(STORAGE_HISTORY_KEY);
                        guestSyncBanner.hidden = true;
                        await fetchUserSessions();
                    } else {
                        alert("Không thể đồng bộ dữ liệu. Vui lòng thử lại.");
                        guestSyncConfirmBtn.disabled = false;
                        guestSyncConfirmBtn.textContent = "Đồng ý";
                    }
                } catch (err) {
                    console.warn("Guest migration failed", err);
                    guestSyncConfirmBtn.disabled = false;
                    guestSyncConfirmBtn.textContent = "Đồng ý";
                }
            });
        }

        if (guestSyncDismissBtn) {
            guestSyncDismissBtn.addEventListener("click", () => {
                localStorage.setItem(GUEST_MIGRATED_KEY, "true");
                guestSyncBanner.hidden = true;
            });
        }
    };

    const handleFeedbackClick = async (messageId, rating, upBtn, downBtn) => {
        const docId = activeScopedDocument?.id || null;
        const success = await sendFeedbackRating(messageId, rating, docId, upBtn, downBtn);
        if (success) {
            const histItem = history.find(item => item.payload && item.payload.messageId === messageId);
            if (histItem && histItem.payload) {
                histItem.payload.rating = rating;
                saveHistory(history);
            }
            if (activeSessionId && isAuthenticated) {
                try {
                    const numericMsgId = String(messageId).replace(/^msg-/, "");
                    if (/^\d+$/.test(numericMsgId)) {
                        await fetch(`${sessionsUrl}/${activeSessionId}/messages/${numericMsgId}/feedback`, {
                            method: "POST",
                            headers: { "Content-Type": "application/json", "Accept": "application/json" },
                            credentials: "same-origin",
                            body: JSON.stringify({ rating: rating === "UP" ? 1 : -1 })
                        });
                    }
                } catch (e) {
                    console.warn("Could not save session message feedback", e);
                }
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
        const newHeight = Math.min(input.scrollHeight, 104);
        input.style.height = `${newHeight}px`;
        input.style.overflowY = input.scrollHeight > 104 ? "auto" : "hidden";
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
    activeSetOpenFn = setOpen;

    const DEFAULT_SUGGESTIONS = [
        {
            label: "Tìm tài liệu Spring Boot & Java",
            title: "Tìm kiếm tài liệu về Spring Boot & Java",
            query: "Tìm tài liệu về Spring Boot và Java"
        },
        {
            label: "Quy trình bảo mật OWASP",
            title: "Quy trình kiểm thử bảo mật web theo chuẩn OWASP",
            query: "Quy trình kiểm thử bảo mật web theo chuẩn OWASP gồm những bước nào?"
        },
        {
            label: "Tổng quan Trí tuệ nhân tạo",
            title: "Tổng quan về Trí tuệ nhân tạo",
            query: "Tổng quan về Trí tuệ nhân tạo và các ứng dụng phổ biến hiện nay"
        },
        {
            label: "Thiết kế cơ sở dữ liệu",
            title: "Hướng dẫn thiết kế cơ sở dữ liệu quan hệ",
            query: "Tìm tài liệu về thiết kế cơ sở dữ liệu quan hệ"
        }
    ];

    // Tạo 4 câu hỏi gợi ý thông minh bám sát tài liệu (0đ API, không dùng icon để giữ phong cách chuyên nghiệp)
    function generateDocumentSuggestions(docTitle, category, keywords) {
        if (!docTitle) return DEFAULT_SUGGESTIONS;
        const cleanTitle = docTitle.replace(/["'“”]/g, "").trim();

        return [
            {
                label: "Tóm tắt nội dung chính",
                title: "Tóm tắt nội dung cốt lõi của tài liệu",
                query: `Tóm tắt nội dung chính và các điểm cốt lõi quan trọng nhất trong tài liệu "${cleanTitle}".`
            },
            {
                label: "Các khái niệm then chốt",
                title: "Các khái niệm kỹ thuật và nguyên lý then chốt",
                query: `Các khái niệm kỹ thuật và nguyên lý then chốt được trình bày trong tài liệu "${cleanTitle}" là gì?`
            },
            {
                label: "Ứng dụng vào thực tế",
                title: "Khả năng áp dụng vào thực tế",
                query: `Kiến thức và giải pháp trong tài liệu "${cleanTitle}" được ứng dụng vào thực tế hoặc dự án như thế nào?`
            },
            {
                label: "Lưu ý khi triển khai",
                title: "Các lưu ý và thách thức khi áp dụng",
                query: `Những điểm cần lưu ý, thách thức hoặc sai lầm thường gặp khi áp dụng nội dung trong "${cleanTitle}" là gì?`
            }
        ];
    }

    // Render danh sách nút gợi ý câu hỏi (thuần text chuyên nghiệp, không icon, không chữ tiêu đề)
    const renderSuggestionsUI = (suggestions) => {
        if (!Array.isArray(suggestions) || suggestions.length === 0) return;
        let container = root.querySelector("[data-assistant-suggestions]");
        if (!container) {
            container = element("div", "assistant-suggestions");
            container.setAttribute("data-assistant-suggestions", "");
            container.setAttribute("aria-label", "Gợi ý câu hỏi");
            messages.append(container);
        }

        container.innerHTML = "";
        const grid = element("div", "assistant-suggestions-grid");
        suggestions.forEach((item) => {
            const btn = element("button", "assistant-suggestion-btn");
            btn.type = "button";
            btn.setAttribute("data-assistant-suggestion", item.query);
            if (item.title) btn.title = item.title;
            btn.textContent = item.label;
            grid.append(btn);
        });
        container.append(grid);
        messages.scrollTo({ top: messages.scrollHeight, behavior: "smooth" });
    };

    // Thiết lập chế độ Scoped Document Q&A
    const setScopedDocument = (docId, docTitle, category = null, keywords = null, openChat = true) => {
        if (!docId) {
            activeScopedDocument = null;
            if (scopedBanner) scopedBanner.hidden = true;
            input.placeholder = "Dán ảnh (Ctrl+V) hoặc hỏi bài tập, Java, AI…";
            if (history.length === 0) {
                renderSuggestionsUI(DEFAULT_SUGGESTIONS);
            } else {
                root.querySelector("[data-assistant-suggestions]")?.remove();
            }
            return;
        }
        activeScopedDocument = {
            id: Number(docId),
            title: docTitle || "Tài liệu đang mở",
            category: category,
            keywords: keywords
        };
        if (scopedTitle) scopedTitle.textContent = activeScopedDocument.title;
        if (scopedBanner) scopedBanner.hidden = false;
        const shortDocTitle = activeScopedDocument.title.length > 28
            ? activeScopedDocument.title.substring(0, 25) + "…"
            : activeScopedDocument.title;
        input.placeholder = `Hỏi về "${shortDocTitle}"…`;

        if (history.length === 0) {
            const scopedSuggestions = generateDocumentSuggestions(activeScopedDocument.title, category, keywords);
            renderSuggestionsUI(scopedSuggestions);
        } else {
            root.querySelector("[data-assistant-suggestions]")?.remove();
        }

        if (openChat) {
            setOpen(true);
        }
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
            const category = btn.dataset.chatDocumentCategory;
            const keywords = btn.dataset.chatDocumentKeywords;
            setScopedDocument(docId, docTitle, category, keywords, true);
        }
    });

    // Tự động phát hiện nếu người dùng đang ở trang chi tiết tài liệu
    const pageDocEl = document.querySelector("[data-page-doc-id]");
    if (pageDocEl) {
        const pageDocId = pageDocEl.dataset.pageDocId;
        const pageDocTitle = pageDocEl.dataset.pageDocTitle;
        const pageCategory = pageDocEl.dataset.pageDocCategory;
        const pageKeywords = pageDocEl.dataset.pageDocKeywords;
        if (pageDocId && pageDocTitle) {
            setScopedDocument(pageDocId, pageDocTitle, pageCategory, pageKeywords, false);
        }
    } else if (history.length === 0) {
        renderSuggestionsUI(DEFAULT_SUGGESTIONS);
    }

    // Khởi tạo các phiên hội thoại và khôi phục phiên đang mở
    fetchUserSessions().then(() => {
        if (isAuthenticated) {
            checkGuestMigration();
        }
        if (activeSessionId) {
            selectSession({ id: activeSessionId });
        } else if (!isAuthenticated && Array.isArray(history) && history.length > 0) {
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
    });

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
                sessionId: activeSessionId,
                context: conversationContext,
                signal: currentAbortController.signal,
                onStart: (startData) => {
                    if (startData && startData.sessionId) {
                        const isNew = !activeSessionId;
                        activeSessionId = startData.sessionId;
                        sessionStorage.setItem(ACTIVE_SESSION_KEY, String(activeSessionId));
                        if (isNew) {
                            const newSessionItem = {
                                id: startData.sessionId,
                                title: startData.sessionTitle || (message ? message.slice(0, 30) : "Cuộc trò chuyện mới"),
                                scopeType: activeScopedDocument ? "DOCUMENT" : "GLOBAL",
                                scopedDocumentId: activeScopedDocument?.id || null,
                                updatedAt: new Date().toISOString()
                            };
                            currentSessions.unshift(newSessionItem);
                            renderSessionList(currentSessions);
                        }
                    }
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

            if (!isAuthenticated) {
                let guestSessions = getGuestSessions();
                let currentGuest = guestSessions.find(s => String(s.id) === String(activeSessionId));
                if (!currentGuest) {
                    const titleWords = message ? message.trim().split(/\s+/).slice(0, 8).join(" ") : "Cuộc trò chuyện mới";
                    currentGuest = {
                        id: activeSessionId || ("guest_" + Date.now()),
                        title: titleWords,
                        scopeType: activeScopedDocument ? "DOCUMENT" : "GLOBAL",
                        scopedDocumentId: activeScopedDocument?.id || null,
                        messages: [],
                        createdAt: new Date().toISOString(),
                        updatedAt: new Date().toISOString()
                    };
                    activeSessionId = currentGuest.id;
                    sessionStorage.setItem(ACTIVE_SESSION_KEY, String(currentGuest.id));
                    guestSessions.unshift(currentGuest);
                }
                currentGuest.messages.push({
                    senderType: "USER",
                    content: message,
                    clientMessageId: `msg-user-${Date.now()}`
                });
                currentGuest.messages.push({
                    senderType: "ASSISTANT",
                    content: accumulatedText,
                    citations: collectedSources,
                    clientMessageId: messageId || `msg-asst-${Date.now()}`
                });
                currentGuest.updatedAt = new Date().toISOString();
                saveGuestSessions(guestSessions);
                renderSessionList(guestSessions);
            }

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
        startNewChatSession();
    });
    if (newChatHeaderBtn) {
        newChatHeaderBtn.addEventListener("click", () => {
            if (pending) return;
            startNewChatSession();
        });
    }
    if (newChatSidebarBtn) {
        newChatSidebarBtn.addEventListener("click", () => {
            if (pending) return;
            startNewChatSession();
        });
    }
    if (sidebarToggleBtn) {
        sidebarToggleBtn.addEventListener("click", () => {
            if (sidebar) sidebar.hidden = !sidebar.hidden;
        });
    }
    if (sidebarCloseBtn) {
        sidebarCloseBtn.addEventListener("click", () => {
            if (sidebar) sidebar.hidden = true;
        });
    }
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
    highlightFromUrlParams();
}

// Tự động kiểm tra URL query parameters khi người dùng truy cập trang có kèm thông số bôi vàng trích dẫn
if (typeof window !== "undefined" && window.location) {
    if (document.readyState === "loading") {
        document.addEventListener("DOMContentLoaded", highlightFromUrlParams);
    } else {
        highlightFromUrlParams();
    }
}
