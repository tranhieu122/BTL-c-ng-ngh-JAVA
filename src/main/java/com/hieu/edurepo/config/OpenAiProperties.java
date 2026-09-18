package com.hieu.edurepo.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Component
@ConfigurationProperties(prefix = "openai")
public class OpenAiProperties {

    private String apiKey = "";
    private String model = "gpt-5.6-luna";
    private String embeddingModel = "text-embedding-3-small";
    private String baseUrl = "https://api.openai.com/v1";
    private int timeoutSeconds = 45;

    public String getApiKey() {
        return apiKey;
    }

    public void setApiKey(String apiKey) {
        this.apiKey = apiKey != null ? apiKey.trim() : "";
    }

    public String getModel() {
        return model;
    }

    public void setModel(String model) {
        this.model = model != null && !model.isBlank() ? model.trim() : "gpt-5.6-luna";
    }

    public String getEmbeddingModel() {
        return embeddingModel;
    }

    public void setEmbeddingModel(String embeddingModel) {
        this.embeddingModel = embeddingModel != null && !embeddingModel.isBlank() ? embeddingModel.trim() : "text-embedding-3-small";
    }

    public String getBaseUrl() {
        return baseUrl;
    }

    public void setBaseUrl(String baseUrl) {
        this.baseUrl = baseUrl != null && !baseUrl.isBlank() ? baseUrl.trim() : "https://api.openai.com/v1";
    }

    public int getTimeoutSeconds() {
        return timeoutSeconds;
    }

    public void setTimeoutSeconds(int timeoutSeconds) {
        this.timeoutSeconds = timeoutSeconds > 0 ? timeoutSeconds : 45;
    }

    public boolean isConfigured() {
        return apiKey != null && !apiKey.isBlank();
    }
}
