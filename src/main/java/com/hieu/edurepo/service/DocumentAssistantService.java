package com.hieu.edurepo.service;

import com.hieu.edurepo.dto.DocumentAssistantContext;
import com.hieu.edurepo.dto.DocumentAssistantResponse;

public interface DocumentAssistantService {
    int MAX_MESSAGE_LENGTH = 200;
    int MAX_RESULTS = 5;

    DocumentAssistantResponse respond(String message);

    DocumentAssistantResponse respond(String message, DocumentAssistantContext context);
}
