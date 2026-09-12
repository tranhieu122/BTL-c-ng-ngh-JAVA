package com.hieu.edurepo.controller;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;

import org.junit.jupiter.api.Test;
import org.springframework.ui.ConcurrentModel;

class GlobalModelAttributesTest {

    @Test
    void addsVietnamTimeAndConfiguredTextToEveryControllerModel() {
        Clock fixedClock = Clock.fixed(Instant.parse("2026-09-12T03:45:00Z"), ZoneOffset.UTC);
        GlobalModelAttributes attributes = new GlobalModelAttributes(
                fixedClock, "Asia/Ho_Chi_Minh", "dd/MM/yyyy HH:mm");
        ConcurrentModel model = new ConcurrentModel();

        attributes.addGlobalAttributes(model);

        assertThat(model.getAttribute("appZoneId")).isEqualTo("Asia/Ho_Chi_Minh");
        assertThat(model.getAttribute("currentDateTimeText")).isEqualTo("12/09/2026 10:45");
        assertThat(model.getAttribute("currentDateTime").toString())
                .contains("2026-09-12T10:45+07:00[Asia/Ho_Chi_Minh]");
    }
}
