package com.hieu.edurepo.controller;

import java.time.Clock;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Locale;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ModelAttribute;

/**
 * Bộ bổ sung thuộc tính toàn cục cho mọi trang Thymeleaf (Global Model Attributes).
 * Tự động nạp tên hệ thống, năm hiện tại, số lượng thông báo chưa đọc và cấu hình giao diện.
 */
@ControllerAdvice(annotations = Controller.class)
public class GlobalModelAttributes {

    private static final Locale VIETNAMESE_LOCALE = Locale.forLanguageTag("vi-VN");

    private final Clock clock;
    private final ZoneId appZoneId;
    private final DateTimeFormatter displayFormatter;

    public GlobalModelAttributes(
            Clock clock,
            @Value("${app.time.zone:Asia/Ho_Chi_Minh}") String zoneId,
            @Value("${app.time.display-format:dd/MM/yyyy HH:mm}") String displayFormat) {
        this.clock = clock;
        this.appZoneId = ZoneId.of(zoneId);
        this.displayFormatter = DateTimeFormatter.ofPattern(displayFormat, VIETNAMESE_LOCALE);
    }

    @ModelAttribute
    public void addGlobalAttributes(Model model) {
        ZonedDateTime currentDateTime = ZonedDateTime.now(clock).withZoneSameInstant(appZoneId);
        model.addAttribute("currentDateTime", currentDateTime);
        model.addAttribute("currentDateTimeText", displayFormatter.format(currentDateTime));
        model.addAttribute("appZoneId", appZoneId.getId());
    }
}
