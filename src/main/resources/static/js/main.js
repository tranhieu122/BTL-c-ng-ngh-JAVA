import { initAuth } from "./modules/auth.js";
import { initCatalog } from "./modules/catalog.js";
import { initCoreUi, ready, setupCardReveal } from "./modules/core.js";
import { initDocumentActions } from "./modules/document-actions.js";
import { initDocumentSubmission } from "./modules/document-submission.js";
import { initForms } from "./modules/forms.js";
import { initNavigation } from "./modules/navigation.js";
import { initReviewQueue } from "./modules/review-queue.js";
import { initAdminDashboard } from "./modules/admin-dashboard.js";
import { initProfile } from "./modules/profile.js";
import { initRealtimeRegions } from "./modules/realtime-regions.js";
import { initRealtime } from "./modules/realtime.js";
import { initNotifications } from "./modules/notifications.js";

ready(() => {
    document.documentElement.classList.add("js-ready");

    initCoreUi();
    initNavigation();
    initReviewQueue();
    initAdminDashboard();
    initCatalog();
    initDocumentActions();
    initDocumentSubmission();
    initProfile();
    initForms();
    initRealtimeRegions(initReviewQueue);
    initRealtime();
    initNotifications();
    initAuth();
    setupCardReveal();
});
