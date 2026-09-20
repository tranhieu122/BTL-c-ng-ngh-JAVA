package com.hieu.edurepo.util;

import java.text.Normalizer;
import java.util.Locale;

/** Chuẩn hóa text dùng chung cho tìm kiếm metadata và lexical RAG fallback. */
public final class SearchTextNormalizer {

    private SearchTextNormalizer() {
    }

    public static String fold(String value) {
        if (value == null || value.isBlank()) return "";
        String folded = Normalizer.normalize(value, Normalizer.Form.NFD)
                .replaceAll("\\p{M}+", "")
                .replace('đ', 'd')
                .replace('Đ', 'D')
                .toLowerCase(Locale.ROOT)
                .replaceAll("[^a-z0-9]+", " ")
                .strip();
        return folded.replaceAll("\\s+", " ");
    }
}
