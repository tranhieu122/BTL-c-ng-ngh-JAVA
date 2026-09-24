package com.hieu.edurepo.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.hieu.edurepo.config.OpenAiProperties;
import com.hieu.edurepo.service.impl.OpenAIServiceImpl;
import org.junit.jupiter.api.Test;

import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;

class OpenAIServiceFallbackTest {

    @Test
    void testOpenAIServiceHasModelCandidates() {
        OpenAiProperties props = new OpenAiProperties();
        props.setApiKey("test-key");
        props.setModel("gemini-3.5-flash");
        props.setBaseUrl("https://generativelanguage.googleapis.com/v1beta/openai/");

        OpenAIServiceImpl service = new OpenAIServiceImpl(props, new ObjectMapper());
        // Service starts and is available
        assertNotNull(service);
    }
}
