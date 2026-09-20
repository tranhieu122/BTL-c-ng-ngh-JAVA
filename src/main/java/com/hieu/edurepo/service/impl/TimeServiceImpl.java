package com.hieu.edurepo.service.impl;

import com.hieu.edurepo.service.TimeService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Locale;

/**
 * Thực thi dịch vụ lấy ngày giờ hiện tại sử dụng Java Time API chuẩn.
 * Mặc định múi giờ hệ thống EduRepo là Asia/Ho_Chi_Minh.
 */
@Service
public class TimeServiceImpl implements TimeService {

    private static final Logger LOGGER = LoggerFactory.getLogger(TimeServiceImpl.class);
    private static final String DEFAULT_ZONE = "Asia/Ho_Chi_Minh";

    @Override
    public String getCurrentTime(String timezone) {
        String zoneIdStr = (timezone == null || timezone.isBlank()) ? DEFAULT_ZONE : timezone.trim();
        ZoneId zoneId;
        try {
            zoneId = ZoneId.of(zoneIdStr);
        } catch (Exception e) {
            LOGGER.warn("Timezone không hợp lệ '{}', sử dụng mặc định '{}'", zoneIdStr, DEFAULT_ZONE);
            zoneId = ZoneId.of(DEFAULT_ZONE);
            zoneIdStr = DEFAULT_ZONE;
        }

        ZonedDateTime now = ZonedDateTime.now(zoneId);
        DateTimeFormatter fullFormatter = DateTimeFormatter.ofPattern("EEEE, 'ngày' dd/MM/yyyy, HH:mm:ss", Locale.forLanguageTag("vi-VN"));
        DateTimeFormatter isoFormatter = DateTimeFormatter.ISO_OFFSET_DATE_TIME;

        return String.format("""
                {
                  "status": "SUCCESS",
                  "timezone": "%s",
                  "datetime_formatted": "%s",
                  "iso": "%s"
                }
                """, zoneIdStr, now.format(fullFormatter), now.format(isoFormatter)).trim();
    }
}
