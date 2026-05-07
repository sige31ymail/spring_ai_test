package com.example.spring_ai_test;

public record RagFileSearchResult(
        String fileName,
        String title,
        Double score,
        Object distance,
        String text
        ) {

}
