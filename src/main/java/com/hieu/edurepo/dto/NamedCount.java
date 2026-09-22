package com.hieu.edurepo.dto;

/**
 * Bản ghi DTO chứa cặp tên và số lượng thống kê phục vụ biểu đồ và phân nhóm.
 */
public record NamedCount(String name, long count) {
}
