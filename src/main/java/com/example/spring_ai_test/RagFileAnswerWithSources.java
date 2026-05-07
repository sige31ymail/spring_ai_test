package com.example.spring_ai_test;

import java.util.List;

public record RagFileAnswerWithSources(
        String answer,
        List<RagFileSearchResult> sources
) {
}