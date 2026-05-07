package com.example.spring_ai_test;

public record RagSearchResult(
        String title,
        Double score,
        Object distance,
        String text
        ) {

}
