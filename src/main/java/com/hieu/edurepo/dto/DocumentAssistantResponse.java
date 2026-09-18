package com.hieu.edurepo.dto;

import java.util.List;
import java.util.Map;

public record DocumentAssistantResponse(String type, String message, String query,
                                        Map<String, String> filters, List<DocumentAssistantItem> documents,
                                        List<String> suggestions, boolean hasMore,
                                        String allResultsUrl, DocumentAssistantContext context) {

    public DocumentAssistantResponse {
        filters = filters == null ? Map.of() : Map.copyOf(filters);
        documents = documents == null ? List.of() : List.copyOf(documents);
        suggestions = suggestions == null ? List.of() : List.copyOf(suggestions);
        context = context == null ? DocumentAssistantContext.empty() : context;
    }

    public DocumentAssistantResponse(String type, String message, String query,
                                     List<DocumentAssistantItem> documents, boolean hasMore,
                                     String allResultsUrl) {
        this(type, message, query, Map.of(), documents, List.of(), hasMore, allResultsUrl,
                DocumentAssistantContext.empty());
    }

    public static DocumentAssistantResponse message(String type, String message) {
        return message(type, message, List.of());
    }

    public static DocumentAssistantResponse message(String type, String message, List<String> suggestions) {
        return new DocumentAssistantResponse(type, message, "", Map.of(), List.of(), suggestions, false, null,
                DocumentAssistantContext.empty());
    }
}
