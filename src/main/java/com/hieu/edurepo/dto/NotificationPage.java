package com.hieu.edurepo.dto;

import java.util.List;

/**
 * DTO phân trang danh sách thông báo gửi về client qua giao diện AJAX.
 */
public record NotificationPage(List<NotificationView> items, int page, int size,
                               long totalElements, int totalPages) { }

