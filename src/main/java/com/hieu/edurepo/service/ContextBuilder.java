package com.hieu.edurepo.service;

import com.hieu.edurepo.config.RagProperties;
import com.hieu.edurepo.dto.RagSearchResult;
import com.hieu.edurepo.dto.RagSource;
import com.hieu.edurepo.entity.Document;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Component
public class ContextBuilder {

    private final RagProperties ragProperties;

    public ContextBuilder(RagProperties ragProperties) {
        this.ragProperties = ragProperties;
    }

    public record BuiltContext(String systemPrompt, String userPrompt, List<RagSource> sources) {
    }

    public BuiltContext buildContext(String userQuestion, List<RagSearchResult> searchResults) {
        String systemPrompt = """
                Bạn là trợ lý AI của hệ thống EduRepo.
                
                Nhiệm vụ:
                - Trả lời câu hỏi của người dùng dựa trên thông tin trong phần CONTEXT dưới đây.
                - Không tự bịa đặt thông tin (tuyệt đối không hallucination).
                - Nếu CONTEXT không chứa thông tin hoặc không đủ dữ liệu để trả lời câu hỏi, hãy trả lời chính xác: "Xin lỗi, tôi không tìm thấy thông tin phù hợp trong kho học liệu EduRepo để trả lời câu hỏi này."
                - Ưu tiên thông tin chính xác, trung thực trong tài liệu học tập của EduRepo.
                - Trả lời bằng tiếng Việt lịch sự, súc tích và mạch lạc.
                """;

        if (searchResults == null || searchResults.isEmpty()) {
            String emptyUserPrompt = "CONTEXT:\n(Không tìm thấy tài liệu phù hợp trong EduRepo)\n\nUSER QUESTION:\n" + userQuestion;
            return new BuiltContext(systemPrompt, emptyUserPrompt, List.of());
        }

        StringBuilder contextBuilder = new StringBuilder();
        contextBuilder.append("CONTEXT:\n\n");

        int maxChars = ragProperties.getMaxContextTokens() * 4;
        int currentLength = 0;
        int docIndex = 1;

        // Gom các nguồn và tránh trùng lặp tài liệu
        Map<Long, RagSource> uniqueSources = new LinkedHashMap<>();

        for (RagSearchResult result : searchResults) {
            Document doc = result.document();
            if (doc == null) continue;

            String chunkContent = result.chunk() != null ? result.chunk().getContent() : "";
            if (chunkContent.isBlank()) continue;

            String docBlock = String.format("[Document %d]\nTitle: %s\nDocument ID: %d\nContent:\n%s\n\n",
                    docIndex,
                    doc.getTitle() != null ? doc.getTitle() : "Tài liệu",
                    doc.getId(),
                    chunkContent);

            if (currentLength + docBlock.length() > maxChars && docIndex > 1) {
                break;
            }

            contextBuilder.append(docBlock);
            currentLength += docBlock.length();
            docIndex++;

            if (!uniqueSources.containsKey(doc.getId())) {
                uniqueSources.put(doc.getId(), new RagSource(
                        doc.getId(),
                        doc.getTitle(),
                        Math.round(result.similarity() * 100.0) / 100.0,
                        "/repository/" + doc.getId()
                ));
            }
        }

        contextBuilder.append("USER QUESTION:\n").append(userQuestion);

        return new BuiltContext(systemPrompt, contextBuilder.toString(), new ArrayList<>(uniqueSources.values()));
    }
}
