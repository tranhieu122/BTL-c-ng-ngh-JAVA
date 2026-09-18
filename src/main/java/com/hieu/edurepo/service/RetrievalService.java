package com.hieu.edurepo.service;

import com.hieu.edurepo.dto.RagSearchResult;

import java.util.List;

public interface RetrievalService {

    List<RagSearchResult> retrieve(String query);

    List<RagSearchResult> retrieve(String query, int topK, double minSimilarity);
}
