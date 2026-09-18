package com.hieu.edurepo.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Component
@ConfigurationProperties(prefix = "rag")
public class RagProperties {

    private boolean enabled = true;
    private int topK = 5;
    private int chunkSize = 600;
    private int chunkOverlap = 100;
    private double similarityThreshold = 0.45;
    private int maxContextTokens = 3000;

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public int getTopK() {
        return topK;
    }

    public void setTopK(int topK) {
        this.topK = topK > 0 ? topK : 5;
    }

    public int getChunkSize() {
        return chunkSize;
    }

    public void setChunkSize(int chunkSize) {
        this.chunkSize = chunkSize > 50 ? chunkSize : 600;
    }

    public int getChunkOverlap() {
        return chunkOverlap;
    }

    public void setChunkOverlap(int chunkOverlap) {
        this.chunkOverlap = chunkOverlap >= 0 ? chunkOverlap : 100;
    }

    public double getSimilarityThreshold() {
        return similarityThreshold;
    }

    public void setSimilarityThreshold(double similarityThreshold) {
        this.similarityThreshold = similarityThreshold;
    }

    public int getMaxContextTokens() {
        return maxContextTokens;
    }

    public void setMaxContextTokens(int maxContextTokens) {
        this.maxContextTokens = maxContextTokens > 200 ? maxContextTokens : 3000;
    }
}
