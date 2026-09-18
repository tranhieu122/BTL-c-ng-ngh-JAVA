package com.hieu.edurepo.service;

import java.util.List;

public interface EmbeddingService {

    List<Double> embedText(String text);

    List<List<Double>> embedBatch(List<String> texts);

    boolean isAvailable();
}
