package com.hieu.edurepo.service;

/**
 * Interface cung cấp thông tin ngày giờ hiện tại theo múi giờ.
 */
public interface TimeService {

    /**
     * Lấy ngày và giờ hiện tại theo định dạng dễ đọc cho một múi giờ cụ thể.
     *
     * @param timezone Chuỗi múi giờ IANA (vd: "Asia/Ho_Chi_Minh", "UTC"). Nếu null/trống, mặc định Asia/Ho_Chi_Minh.
     * @return Chuỗi JSON hoặc thông tin thời gian hiện tại
     */
    String getCurrentTime(String timezone);
}
