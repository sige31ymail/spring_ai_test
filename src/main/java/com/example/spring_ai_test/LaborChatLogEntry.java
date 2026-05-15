package com.example.spring_ai_test;

public record LaborChatLogEntry(
        String timestamp,
        String conversationId,
        String message,
        String answer,
        int topK,
        double threshold,
        int sourceCount,
        long elapsedMs) {
}
