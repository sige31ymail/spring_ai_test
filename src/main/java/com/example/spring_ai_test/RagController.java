package com.example.spring_ai_test;

import java.io.IOException;
import java.util.List;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class RagController {

    private final RagService ragService;

    public RagController(RagService ragService) {
        this.ragService = ragService;
    }

    @GetMapping("/ai/rag/load-md-dir")
    public String loadMarkdownDirectory() throws IOException {
        return ragService.loadMarkdownDirectory();
    }

    @GetMapping("/ai/rag/save-store")
    public String saveVectorStore() throws IOException {
        return ragService.saveStore();
    }

    @GetMapping("/ai/rag/load-store")
    public String loadVectorStore() throws IOException {
        return ragService.loadStore();
    }

    @GetMapping("/ai/rag/search-md-file-simple")
    public List<RagFileSearchResult> searchMarkdownFileSimple(
            @RequestParam(defaultValue = "spring-ai-tools.md") String fileName,
            @RequestParam(defaultValue = "ToolContextとは何ですか？") String message,
            @RequestParam(defaultValue = "5") int topK,
            @RequestParam(defaultValue = "0.0") double threshold) {

        return ragService.searchByFile(fileName, message, topK, threshold);
    }

    @GetMapping("/ai/rag/ask-md-file")
    public RagAnswerWithSources askMarkdownFile(
            @RequestParam(defaultValue = "spring-ai-tools.md") String fileName,
            @RequestParam(defaultValue = "ToolContextとは何ですか？") String message,
            @RequestParam(defaultValue = "5") int topK,
            @RequestParam(defaultValue = "0.0") double threshold) {

        return ragService.askByFile(fileName, message, topK, threshold);
    }
}