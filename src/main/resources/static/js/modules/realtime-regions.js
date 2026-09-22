/**
 * Module cập nhật từng phân vùng giao diện thời gian thực (Realtime Regions Script).
 * Tự động làm mới thẻ tài liệu hoặc hàng đợi kiểm duyệt khi nhận được sự kiện SSE tương ứng.
 */

// Refresh server-rendered regions using the current URL (including filters and pagination).
export function regionVersion(region, snapshot) {
    const items = snapshot[region.dataset.liveSource] || [];
    return JSON.stringify(region.dataset.liveId
        ? items.filter(item => String(item.id) === region.dataset.liveId) : items);
}

export function syncReviewActions(snapshot) {
    const select = document.querySelector('[data-review-action]');
    if (!select) return;
    const form = select.closest('form');
    const badge = document.querySelector('[data-live-document-status]');
    const item = snapshot.reviewQueue.find(item => String(item.id) === badge?.dataset.liveDocumentStatus);
    const status = item?.status || 'UNAVAILABLE';
    if (form.dataset.liveStatus === status) return;
    const previous = select.value;
    if (!form.dataset.canPublish) {
        form.dataset.canPublish = String(Boolean(select.querySelector('option[value="PUBLISHED"]')));
    }
    const canPublish = form.dataset.canPublish === 'true';
    const pendingDecision = ['SUBMITTED', 'RESUBMITTED', 'UNDER_REVIEW'].includes(status);
    const options = pendingDecision
        ? [...(canPublish ? [['PUBLISHED', 'Công khai']] : []), ['APPROVED', 'Phê duyệt'],
            ['REVISION_REQUESTED', 'Yêu cầu chỉnh sửa'], ['REJECTED', 'Từ chối']]
        : status === 'APPROVED' && canPublish ? [['PUBLISHED', 'Công khai']] : [];
    select.replaceChildren(...[['', 'Chọn quyết định'], ...options].map(([value, label]) => {
        const option = document.createElement('option');
        option.value = value;
        option.textContent = label;
        return option;
    }));
    select.value = options.some(([value]) => value === previous) ? previous : '';
    select.disabled = !options.length;
    form.querySelector('[data-review-submit]').disabled = !options.length;
    const rubric = form.querySelector('.rubric-fieldset');
    if (rubric) { rubric.hidden = status !== 'SUBMITTED'; rubric.disabled = status !== 'SUBMITTED'; }
    let notice = form.querySelector('[data-live-review-notice]');
    if (!notice) {
        notice = document.createElement('p');
        notice.dataset.liveReviewNotice = '';
        notice.setAttribute('role', 'status');
        form.prepend(notice);
    }
    notice.textContent = options.length
        ? 'Quyết định đã đồng bộ theo trạng thái mới nhất. Nội dung góp ý được giữ nguyên.'
        : 'Hồ sơ không còn trong hàng chờ. Nội dung góp ý được giữ nguyên để bạn sao chép.';
    if (!item && badge) badge.textContent = 'Đã rời hàng chờ';
    select.dispatchEvent(new Event('change', {bubbles: true}));
    form.dataset.liveStatus = status;
}

export function initRealtimeRegions(onUpdated) {
    const regions = [...document.querySelectorAll('[data-live-region]')];
    document.addEventListener('realtime:snapshot', event => syncReviewActions(event.detail));
    if (!regions.length) return;
    const versions = new Map();
    let latest, running = false, timer, controller, suspended = false, generation = 0;
    const report = text => {
        const message = document.querySelector('[data-live-message]');
        if (message) message.textContent = text;
    };
    const refresh = async () => {
        if (running || suspended || !latest) return;
        const pending = regions.filter(region => versions.get(region) !== regionVersion(region, latest));
        if (!pending.length) return;
        // A confirmation dialog may retain a reference to a form being submitted.
        if (document.querySelector('[aria-busy="true"], [data-confirm-dialog]:not([hidden])')
            || pending.some(region => region.contains(document.activeElement)
                && !document.activeElement.closest('.review-toolbar'))) {
            timer = window.setTimeout(refresh, 1000);
            return;
        }
        running = true;
        const target = latest;
        const ticket = generation;
        controller = new AbortController();
        const timeout = window.setTimeout(() => controller.abort(), 10000);
        try {
            const response = await fetch(window.location.href, {
                credentials: 'same-origin', cache: 'no-store', signal: controller.signal,
                headers: {Accept: 'text/html'}
            });
            if (suspended || ticket !== generation) return;
            if (response.status === 404 && pending.every(region => region.dataset.liveId)) {
                for (const region of pending) {
                    region.querySelectorAll('button, input, select, textarea').forEach(control => { control.disabled = true; });
                    region.querySelectorAll('a').forEach(link => {
                        if (!link.href.endsWith('/documents')) link.removeAttribute('href');
                    });
                    versions.set(region, regionVersion(region, target));
                }
                report('Tài liệu không còn khả dụng. Hãy quay lại danh sách tài liệu.');
                return;
            }
            if (!response.ok || response.redirected) throw new Error('Page unavailable');
            const page = new DOMParser().parseFromString(await response.text(), 'text/html');
            if (suspended || ticket !== generation) return;
            for (const region of pending) {
                const next = page.querySelector(`[data-live-region="${region.dataset.liveRegion}"]`);
                if (!next) throw new Error('Missing region');
                // Recheck after the request: the user may have started interacting meanwhile.
                if (document.querySelector('[aria-busy="true"], [data-confirm-dialog]:not([hidden])')) break;
                if (region.contains(document.activeElement) && !document.activeElement.closest('.review-toolbar')) continue;
                const list = region.querySelector('[data-review-list]');
                const nextList = next.querySelector('[data-review-list]');
                if (list && nextList) {
                    list.replaceChildren(...nextList.childNodes);
                    list.dispatchEvent(new Event('realtime:updated'));
                } else {
                    // Keep local search controls even when the server queue becomes empty.
                    if (list && !nextList) {
                        list.replaceChildren();
                        list.dispatchEvent(new Event('realtime:updated'));
                    } else region.replaceChildren(...next.childNodes);
                }
                versions.set(region, regionVersion(region, target));
            }
            onUpdated();
            if (regions.every(region => versions.get(region) === regionVersion(region, latest))) {
                report('Danh sách và thao tác đã được đồng bộ tự động.');
                const refreshLink = document.querySelector('[data-live-refresh]');
                if (refreshLink) refreshLink.hidden = true;
            }
        } catch (error) {
            if (!suspended) report('Chưa tải được danh sách mới. Hệ thống sẽ tự thử lại; bạn vẫn có thể tải lại trang.');
        } finally {
            window.clearTimeout(timeout);
            running = false;
            if (!suspended && regions.some(region => versions.get(region) !== regionVersion(region, latest)))
                timer = window.setTimeout(refresh, 3000);
        }
    };
    document.addEventListener('realtime:snapshot', event => {
        latest = event.detail;
        window.clearTimeout(timer);
        refresh();
    });
    window.addEventListener('pagehide', () => {
        generation++;
        suspended = true;
        window.clearTimeout(timer);
        controller?.abort();
    });
    window.addEventListener('pageshow', () => { suspended = false; refresh(); });
}
