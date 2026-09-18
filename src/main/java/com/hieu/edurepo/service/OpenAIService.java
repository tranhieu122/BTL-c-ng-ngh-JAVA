package com.hieu.edurepo.service;

public interface OpenAIService {

    String generateChatCompletion(String systemPrompt, String userPrompt);

    boolean isAvailable();
}
