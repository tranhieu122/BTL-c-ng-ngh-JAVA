package com.hieu.edurepo.dto;

import java.time.LocalDateTime;
import java.util.List;

public record DocumentAssistantItem(Long id, String title, String description, String category,
                                    String author, LocalDateTime publishedAt, String detailUrl,
                                    List<DocumentAssistantAction> actions,
                                    Double averageRating, Long reviewCount,
                                    Long viewCount, Long downloadCount) {

    public DocumentAssistantItem {
        actions = actions == null ? List.of() : List.copyOf(actions);
    }
}
