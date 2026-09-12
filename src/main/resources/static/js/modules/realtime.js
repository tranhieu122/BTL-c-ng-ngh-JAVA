const labels = {DRAFT: "Bản nháp", SUBMITTED: "Chờ duyệt", REVISION_REQUIRED: "Cần chỉnh sửa", APPROVED: "Đã duyệt", PUBLISHED: "Đã xuất bản", REJECTED: "Từ chối"};

// Injectable browser primitives make lifecycle/reconnect behavior testable without a server mock UI.
export function connectRealtime({url, snapshotUrl, onSnapshot, onStatus, onExpired,
    EventSourceClass = window.EventSource, fetchSnapshot = (...args) => window.fetch(...args),
    schedule = (fn, ms) => window.setTimeout(fn, ms), cancel = id => window.clearTimeout(id)}) {
    let source, timer, attempts = 0, stopped = true, generation = 0;
    const disconnect = () => { generation++; if (source) source.close(); source = null; if (timer != null) cancel(timer); timer = null; };
    const expired = () => { stopped = true; disconnect(); onExpired(); };
    const open = () => {
        if (stopped || source) return;
        const ticket = ++generation;
        onStatus("Đang kết nối");
        source = new EventSourceClass(url);
        source.addEventListener("snapshot", event => {
            if (stopped || ticket !== generation) return;
            try { onSnapshot(JSON.parse(event.data)); attempts = 0; onStatus("Trực tuyến"); }
            catch { source.onerror(); }
        });
        source.addEventListener("session-expired", () => { if (ticket === generation) expired(); });
        source.onerror = async () => {
            if (stopped || ticket !== generation) return;
            disconnect();
            const failedGeneration = generation;
            onStatus("Mất kết nối");
            // Also distinguishes an expired cookie from a temporary SSE/proxy interruption.
            try {
                const response = await fetchSnapshot(snapshotUrl, {credentials: "same-origin", cache: "no-store", headers: {Accept: "application/json"}, signal: AbortSignal.timeout(8000)});
                if (stopped || failedGeneration !== generation) return;
                if (response.status === 401 || response.status === 403) { expired(); return; }
                if (response.ok) {
                    const snapshot = await response.json();
                    if (!stopped && failedGeneration === generation) onSnapshot(snapshot);
                }
            } catch { /* Ordinary form/navigation actions remain independent of realtime. */ }
            if (!stopped && failedGeneration === generation) {
                timer = schedule(() => { timer = null; open(); }, Math.min(30000, 1000 * 2 ** Math.min(attempts++, 5)));
            }
        };
    };
    return {
        start() { if (!stopped) return; stopped = false; open(); },
        stop() { stopped = true; disconnect(); }
    };
}

export function initRealtime() {
    const root = document.querySelector("[data-realtime]");
    if (!root || root.dataset.initialized) return;
    root.dataset.initialized = "true";
    const status = root.querySelector("[data-live-status]");
    const message = root.querySelector("[data-live-message]");
    const setStatus = value => {
        status.textContent = value;
        root.dataset.connection = value === "Trực tuyến" ? "online" : value === "Đang kết nối" ? "connecting" : "offline";
    };
    document.addEventListener("click", event => { if (!root.contains(event.target)) root.open = false; });
    root.addEventListener("keydown", event => {
        if (event.key === "Escape" && root.open) { root.open = false; root.querySelector("summary").focus(); }
    });
    if (!window.EventSource) { status.textContent = "Chưa hỗ trợ"; message.textContent = "Tải lại trang để xem cập nhật. Các chức năng vẫn dùng bình thường."; return; }
    let previous;
    const profileForm = document.querySelector("[data-profile-form]");
    let profileDirty = profileForm?.dataset.preserveInput === "true";
    profileForm?.addEventListener("input", () => { profileDirty = true; });
    const onSnapshot = snapshot => {
        if (!snapshot.profile || !Array.isArray(snapshot.documents) || !Array.isArray(snapshot.reviewQueue)) throw new Error("Invalid snapshot");
        const changed = previous && JSON.stringify(previous) !== JSON.stringify(snapshot);
        document.querySelectorAll("[data-account-name]").forEach(element => { element.textContent = snapshot.profile.fullName; });
        if (!previous || previous.profile.avatarRevision !== snapshot.profile.avatarRevision) {
            document.querySelectorAll("[data-account-avatar]").forEach(element => {
                if (element.hasAttribute("data-avatar-preview") && document.querySelector("[data-avatar-input]")?.files.length) return;
                element.src = `${root.dataset.avatarUrl}?v=${encodeURIComponent(snapshot.profile.avatarRevision)}`;
            });
        }
        if (profileForm && (!previous || JSON.stringify(previous.profile) !== JSON.stringify(snapshot.profile))) {
            if (profileDirty) document.querySelector("[data-profile-remote]").hidden = false;
            else for (const key of ["fullName", "phoneNumber", "affiliation", "bio"]) profileForm.elements[key].value = snapshot.profile[key] || "";
        }
        const list = root.querySelector("[data-live-documents]");
        list.replaceChildren();
        for (const item of snapshot.documents.slice(0, 20)) {
            const li = document.createElement("li");
            li.textContent = `${item.title} — ${labels[item.status] || item.status}`;
            list.append(li);
        }
        const queue = root.querySelector("[data-live-queue]");
        queue.hidden = !snapshot.canReview;
        queue.textContent = `Hàng chờ: ${snapshot.reviewQueue.filter(item => item.status === "SUBMITTED").length} cần duyệt · ${snapshot.reviewQueue.filter(item => item.status === "APPROVED").length} cần xuất bản`;
        if (snapshot.canReview) {
            const counts = {total: snapshot.reviewQueue.length, submitted: snapshot.reviewQueue.filter(item => item.status === "SUBMITTED").length,
                approved: snapshot.reviewQueue.filter(item => item.status === "APPROVED").length};
            for (const [key, count] of Object.entries(counts)) document.querySelectorAll(`[data-live-queue-${key}]`).forEach(element => { element.textContent = String(count); });
        }
        // Existing forms are never replaced, preserving inputs and their event handlers.
        document.querySelectorAll("[data-live-document-status]").forEach(element => {
            const item = [...snapshot.documents, ...snapshot.reviewQueue].find(doc => String(doc.id) === element.dataset.liveDocumentStatus);
            if (item) { element.textContent = labels[item.status] || item.status; element.className = `status-pill status-${item.status.toLowerCase()}`; }
        });
        message.textContent = changed ? "Có cập nhật mới. Trạng thái dưới đây đã được đồng bộ; tải lại trang để cập nhật các thao tác." : "Đã đồng bộ trạng thái mới nhất. Các chức năng vẫn dùng được khi mất kết nối.";
        if (changed) root.querySelector("[data-live-refresh]").hidden = false;
        previous = snapshot;
        document.dispatchEvent(new CustomEvent("realtime:snapshot", {detail: snapshot}));
        if (changed) document.dispatchEvent(new CustomEvent("realtime:changed", {detail: snapshot}));
    };
    const client = connectRealtime({url: root.dataset.streamUrl, snapshotUrl: root.dataset.snapshotUrl, onSnapshot,
        onStatus: value => { setStatus(value); if (value === "Mất kết nối") message.textContent = "Đang thử kết nối lại. Bạn vẫn có thể thao tác hoặc tải lại trang để cập nhật."; },
        onExpired: () => { window.location.assign(root.dataset.loginUrl); }});
    window.addEventListener("pagehide", () => client.stop());
    window.addEventListener("offline", () => { client.stop(); setStatus("Ngoại tuyến"); });
    window.addEventListener("pageshow", () => { if (navigator.onLine) client.start(); });
    window.addEventListener("online", () => client.start());
    if (navigator.onLine) client.start();
}
