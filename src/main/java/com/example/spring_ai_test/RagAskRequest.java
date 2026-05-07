package com.example.spring_ai_test;

public record RagAskRequest(
        String fileName,
        String message,
        Integer topK,
        Double threshold
        ) {

}
