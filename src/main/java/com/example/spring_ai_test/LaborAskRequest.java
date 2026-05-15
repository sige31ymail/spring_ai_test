package com.example.spring_ai_test;

public record LaborAskRequest(String message, Integer topK, Double threshold, String conversationId) {
}
