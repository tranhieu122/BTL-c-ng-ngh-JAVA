package com.hieu.edurepo.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * Cấu hình cho dịch vụ thời tiết bên ngoài (Weather Tool).
 * Hỗ trợ WeatherAPI hoặc Open-Meteo fallback khi không có API key.
 */
@Component
@ConfigurationProperties(prefix = "app.weather")
public class WeatherProperties {

    /** API key từ dịch vụ thời tiết bên ngoài (vd: WeatherAPI, OpenWeatherMap...) */
    private String apiKey = "";

    /** Endpoint gốc của API thời tiết (mặc định trỏ tới weatherapi.com hoặc open-meteo) */
    private String baseUrl = "https://api.weatherapi.com/v1";

    /** Thời gian chờ tối đa khi gọi Weather API (giây) */
    private int timeoutSeconds = 10;

    public String getApiKey() {
        return apiKey;
    }

    public void setApiKey(String apiKey) {
        this.apiKey = apiKey == null ? "" : apiKey.trim();
    }

    public String getBaseUrl() {
        return baseUrl;
    }

    public void setBaseUrl(String baseUrl) {
        this.baseUrl = baseUrl == null ? "https://api.weatherapi.com/v1" : baseUrl.trim();
    }

    public int getTimeoutSeconds() {
        return timeoutSeconds;
    }

    public void setTimeoutSeconds(int timeoutSeconds) {
        this.timeoutSeconds = timeoutSeconds > 0 ? timeoutSeconds : 10;
    }

    public boolean isConfigured() {
        return !apiKey.isBlank();
    }
}
