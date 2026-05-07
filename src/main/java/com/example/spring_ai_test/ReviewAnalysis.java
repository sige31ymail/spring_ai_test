package com.example.spring_ai_test;

public record ReviewAnalysis(
        String summary,
        String sentiment,
        int score,
        boolean needsFollowUp
        ) {

}
