import { initAuth } from "./modules/auth.js";
import { initCatalog } from "./modules/catalog.js";
import { initCoreUi, ready, setupCardReveal } from "./modules/core.js";
import { initDocumentActions } from "./modules/document-actions.js";
import { initForms } from "./modules/forms.js";
import { initNavigation } from "./modules/navigation.js";

ready(() => {
    document.documentElement.classList.add("js-ready");

    initCoreUi();
    initNavigation();
    initCatalog();
    initDocumentActions();
    initForms();
    initAuth();
    setupCardReveal();
});
