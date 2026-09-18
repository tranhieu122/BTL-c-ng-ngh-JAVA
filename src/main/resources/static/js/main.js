import { initCoreUi, ready, setupCardReveal } from "./modules/core.js";
import { initForms } from "./modules/forms.js";
import { initNavigation } from "./modules/navigation.js";

const loadFeature = (selector, modulePath, initializer) => {
    if (!document.querySelector(selector)) return Promise.resolve();
    return import(modulePath).then((module) => module[initializer]());
};

const initRealtimeFeatures = async () => {
    if (!document.querySelector("[data-realtime]")) return;

    // Register snapshot consumers before opening the SSE connection so the
    // first snapshot cannot arrive before a page-specific listener is ready.
    const [{ initRealtimeRegions }, { initNotifications }, { initRealtime }] = await Promise.all([
        import("./modules/realtime-regions.js"),
        import("./modules/notifications.js"),
        import("./modules/realtime.js")
    ]);
    const refreshReviewQueue = () => import("./modules/review-queue.js")
        .then(({ initReviewQueue }) => initReviewQueue());
    initRealtimeRegions(refreshReviewQueue);
    initNotifications();
    initRealtime();
};

ready(() => {
    document.documentElement.classList.add("js-ready");

    initCoreUi();
    initNavigation();
    initForms();
    setupCardReveal();

    // Most pages only need the shared shell. Loading feature modules on demand
    // avoids parsing the whole frontend bundle again after every navigation.
    void Promise.allSettled([
        loadFeature("[data-password-toggle], [data-login-form]", "./modules/auth.js", "initAuth"),
        loadFeature("[data-document-grid], [data-search-input]", "./modules/catalog.js", "initCatalog"),
        loadFeature("[data-share-link]", "./modules/document-actions.js", "initDocumentActions"),
        loadFeature("[data-document-submission]", "./modules/document-submission.js", "initDocumentSubmission"),
        loadFeature("[data-review-list]", "./modules/review-queue.js", "initReviewQueue"),
        loadFeature("[data-admin-dashboard]", "./modules/admin-dashboard.js", "initAdminDashboard"),
        loadFeature("[data-profile-page]", "./modules/profile.js", "initProfile"),
        loadFeature("[data-document-assistant]", "./modules/document-assistant.js", "initDocumentAssistant"),
        initRealtimeFeatures()
    ]);
});
