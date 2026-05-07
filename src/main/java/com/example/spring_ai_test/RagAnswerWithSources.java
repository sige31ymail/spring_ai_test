package com.example.spring_ai_test;

import java.util.List;

public record RagAnswerWithSources(
        String answer,
        List<RagSearchResult> sources
        ) {

}
