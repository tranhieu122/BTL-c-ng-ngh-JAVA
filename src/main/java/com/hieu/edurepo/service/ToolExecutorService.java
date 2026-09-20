package com.hieu.edurepo.service;

import com.hieu.edurepo.dto.ToolExecutionResult;

import java.util.List;
import java.util.Map;

/**
 * Interface điều phối và thực thi các Tool được OpenAI gọi.
 */
public interface ToolExecutorService {

    /**
     * Lấy danh sách định nghĩa các Tool (JSON Schema) theo chuẩn OpenAI function calling.
     *
     * @return Danh sách Map mô tả tool definitions
     */
    List<Map<String, Object>> getToolDefinitions();

    /**
     * Thực thi một tool theo tên và chuỗi JSON arguments.
     *
     * @param toolName Tên tool (vd: search_documents, get_weather, get_current_time)
     * @param argumentsJson Chuỗi JSON chứa tham số truyền vào tool
     * @return Kết quả ToolExecutionResult
     */
    ToolExecutionResult executeTool(String toolName, String argumentsJson);
}
