package com.hieu.edurepo.service.impl;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.hieu.edurepo.config.WeatherProperties;
import com.hieu.edurepo.service.WeatherService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;

/**
 * Thực thi dịch vụ thời tiết với khả năng:
 * 1. Gọi WeatherAPI (khi có api-key).
 * 2. Tự động fallback sang Open-Meteo Geo & Weather API (khi chưa cấu hình api-key hoặc môi trường dev) để tool luôn chạy ổn định.
 */
@Service
public class WeatherServiceImpl implements WeatherService {

    private static final Logger LOGGER = LoggerFactory.getLogger(WeatherServiceImpl.class);

    private final WeatherProperties properties;
    private final ObjectMapper objectMapper;
    private final HttpClient httpClient;

    public WeatherServiceImpl(WeatherProperties properties, ObjectMapper objectMapper) {
        this.properties = properties;
        this.objectMapper = objectMapper;
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(10))
                .build();
    }

    @Override
    public String getWeather(String city) {
        if (city == null || city.isBlank()) {
            return "{\"status\": \"ERROR\", \"message\": \"Tên thành phố không được để trống.\"}";
        }

        String trimmedCity = city.trim();

        // 1. Nếu có cấu hình API key cho WeatherAPI
        if (properties.isConfigured()) {
            try {
                String encodedCity = URLEncoder.encode(trimmedCity, StandardCharsets.UTF_8);
                String url = String.format("%s/current.json?key=%s&q=%s&aqi=no&lang=vi",
                        properties.getBaseUrl().replaceAll("/+$", ""),
                        properties.getApiKey(),
                        encodedCity);

                HttpRequest request = HttpRequest.newBuilder()
                        .uri(URI.create(url))
                        .timeout(Duration.ofSeconds(properties.getTimeoutSeconds()))
                        .GET()
                        .build();

                HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());

                if (response.statusCode() == 200) {
                    JsonNode root = objectMapper.readTree(response.body());
                    String locationName = root.path("location").path("name").asText(trimmedCity);
                    String country = root.path("location").path("country").asText("");
                    double tempC = root.path("current").path("temp_c").asDouble();
                    String condition = root.path("current").path("condition").path("text").asText("");
                    int humidity = root.path("current").path("humidity").asInt();
                    double windKph = root.path("current").path("wind_kph").asDouble();

                    return String.format("""
                            {
                              "status": "SUCCESS",
                              "city": "%s",
                              "country": "%s",
                              "temperature_celsius": %.1f,
                              "condition": "%s",
                              "humidity_percent": %d,
                              "wind_kph": %.1f
                            }
                            """, locationName, country, tempC, condition, humidity, windKph).trim();
                } else if (response.statusCode() == 400) {
                    return String.format("{\"status\": \"NOT_FOUND\", \"message\": \"Không tìm thấy thông tin thời tiết cho thành phố '%s'.\"}", trimmedCity);
                }
            } catch (Exception e) {
                LOGGER.warn("Lỗi khi gọi WeatherAPI cho '{}': {}", trimmedCity, e.getMessage());
            }
        }

        // 2. Fallback miễn phí qua Open-Meteo (không cần API key, cực kỳ ổn định)
        return fetchFromOpenMeteo(trimmedCity);
    }

    private String fetchFromOpenMeteo(String city) {
        try {
            // Bước 1: Geocoding để tìm tọa độ (latitude, longitude)
            String geoUrl = "https://geocoding-api.open-meteo.com/v1/search?name="
                    + URLEncoder.encode(city, StandardCharsets.UTF_8) + "&count=1&language=vi&format=json";

            HttpRequest geoRequest = HttpRequest.newBuilder()
                    .uri(URI.create(geoUrl))
                    .timeout(Duration.ofSeconds(6))
                    .GET()
                    .build();

            HttpResponse<String> geoResponse = httpClient.send(geoRequest, HttpResponse.BodyHandlers.ofString());
            if (geoResponse.statusCode() != 200) {
                return String.format("{\"status\": \"ERROR\", \"message\": \"Không thể kết nối dịch vụ bản đồ thời tiết cho '%s'.\"}", city);
            }

            JsonNode geoRoot = objectMapper.readTree(geoResponse.body());
            JsonNode results = geoRoot.path("results");
            if (!results.isArray() || results.isEmpty()) {
                return String.format("{\"status\": \"NOT_FOUND\", \"message\": \"Không tìm thấy tọa độ địa lý cho thành phố '%s'.\"}", city);
            }

            JsonNode topLocation = results.get(0);
            String name = topLocation.path("name").asText(city);
            String country = topLocation.path("country").asText("");
            double lat = topLocation.path("latitude").asDouble();
            double lon = topLocation.path("longitude").asDouble();

            // Bước 2: Lấy thông tin thời tiết theo tọa độ
            String weatherUrl = String.format(java.util.Locale.US,
                    "https://api.open-meteo.com/v1/forecast?latitude=%.4f&longitude=%.4f&current=temperature_2m,relative_humidity_2m,weather_code,wind_speed_10m",
                    lat, lon);

            HttpRequest weatherRequest = HttpRequest.newBuilder()
                    .uri(URI.create(weatherUrl))
                    .timeout(Duration.ofSeconds(6))
                    .GET()
                    .build();

            HttpResponse<String> weatherResponse = httpClient.send(weatherRequest, HttpResponse.BodyHandlers.ofString());
            if (weatherResponse.statusCode() == 200) {
                JsonNode weatherRoot = objectMapper.readTree(weatherResponse.body());
                JsonNode current = weatherRoot.path("current");
                double tempC = current.path("temperature_2m").asDouble();
                int humidity = current.path("relative_humidity_2m").asInt();
                int weatherCode = current.path("weather_code").asInt();
                double windSpeed = current.path("wind_speed_10m").asDouble();

                String condition = decodeWeatherCode(weatherCode);

                return String.format(java.util.Locale.US, """
                        {
                          "status": "SUCCESS",
                          "city": "%s",
                          "country": "%s",
                          "temperature_celsius": %.1f,
                          "condition": "%s",
                          "humidity_percent": %d,
                          "wind_kph": %.1f,
                          "provider": "Open-Meteo"
                        }
                        """, name, country, tempC, condition, humidity, windSpeed).trim();
            }
        } catch (Exception e) {
            LOGGER.error("Lỗi khi lấy thời tiết Open-Meteo cho '{}': {}", city, e.getMessage());
        }

        return String.format("{\"status\": \"ERROR\", \"message\": \"Hiện chưa thể lấy dữ liệu thời tiết cho '%s'. Vui lòng thử lại sau.\"}", city);
    }

    private String decodeWeatherCode(int code) {
        return switch (code) {
            case 0 -> "Trời quang, nắng đẹp";
            case 1, 2, 3 -> "Nhiều mây, mây rải rác";
            case 45, 48 -> "Có sương mù";
            case 51, 53, 55 -> "Mưa phùn nhẹ";
            case 61, 63, 65 -> "Có mưa";
            case 71, 73, 75 -> "Có tuyết rơi";
            case 80, 81, 82 -> "Mưa rào";
            case 95, 96, 99 -> "Có dông sét";
            default -> "Thời tiết bình thường";
        };
    }
}
