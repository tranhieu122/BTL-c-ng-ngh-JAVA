package com.hieu.edurepo.dto;

public record DocumentAssistantAction(String label, String message) {

    public DocumentAssistantAction {
        label = clean(label);
        message = clean(message);
    }

    private static String clean(String value) {
        return value == null ? "" : value.strip().replaceAll("\\s+", " ");
    }
}
