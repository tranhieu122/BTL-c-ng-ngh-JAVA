package com.hieu.edurepo.service;

/**
 * Interface cung cấp thông tin thời tiết cho một thành phố.
 */
public interface WeatherService {

    /**
     * Lấy thông tin thời tiết hiện tại cho tên thành phố cụ thể.
     *
     * @param city Tên thành phố (vd: "Hanoi", "Ho Chi Minh City", "Da Nang", "Tokyo")
     * @return Chuỗi JSON mô tả thời tiết (nhiệt độ, tình trạng mây/mưa, độ ẩm...)
     */
    String getWeather(String city);
}
