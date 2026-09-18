package com.hieu.edurepo.dto;

import java.util.Map;

public record DocumentAssistantQuery(String intent, String keyword, String topic, String author, String languageCode,
                                     Integer year, String sortMode, int page) {

    public DocumentAssistantQuery {
        intent = clean(intent);
        keyword = clean(keyword);
        topic = clean(topic);
        author = clean(author);
        languageCode = clean(languageCode);
        sortMode = clean(sortMode);
        page = Math.max(0, page);
    }

    public Map<String, String> filters() {
        java.util.LinkedHashMap<String, String> values = new java.util.LinkedHashMap<>();
        put(values, "intent", intent);
        put(values, "keyword", keyword);
        put(values, "topic", topic);
        put(values, "author", author);
        put(values, "languageCode", languageCode);
        if (year != null) values.put("year", String.valueOf(year));
        put(values, "sortMode", sortMode);
        if (page > 0) values.put("page", String.valueOf(page));
        return Map.copyOf(values);
    }

    private static void put(Map<String, String> values, String key, String value) {
        if (value != null && !value.isBlank()) values.put(key, value);
    }

    private static String clean(String value) {
        return value == null ? "" : value.strip().replaceAll("\\s+", " ");
    }
}
