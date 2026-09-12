package com.hieu.edurepo.dto;

import java.util.List;

public record NotificationPage(List<NotificationView> items, int page, int size,
                               long totalElements, int totalPages) { }

