package com.example.spring_ai_test;

import java.io.IOException;
import java.util.List;
import java.util.Set;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/ai/rag")
public class RagController {

    private static final Set<String> ALLOWED_FILE_NAMES = Set.of(
            "spring-ai-notes.md",
            "spring-ai-tools.md",
            "spring-ai-rag.md");

    private final RagService ragService;

    public RagController(RagService ragService) {
        this.ragService = ragService;
    }

    @GetMapping("/load-md-dir")
    public String loadMarkdownDirectory() throws IOException {
        return ragService.loadMarkdownDirectory();
    }

    @GetMapping("/save-store")
    public String saveVectorStore() throws IOException {
        return ragService.saveStore();
    }

    @GetMapping("/load-store")
    public String loadVectorStore() throws IOException {
        return ragService.loadStore();
    }

    @GetMapping("/search-md-file-simple")
    public List<RagFileSearchResult> searchMarkdownFileSimple(
            @RequestParam(defaultValue = "spring-ai-tools.md") String fileName,
            @RequestParam(defaultValue = "ToolContextとは何ですか？") String message,
            @RequestParam(defaultValue = "5") int topK,
            @RequestParam(defaultValue = "0.0") double threshold) {

        validateRagRequest(message, topK, threshold);
        validateRagFileName(fileName);
        return ragService.searchByFile(fileName, message, topK, threshold);
    }

    @GetMapping("/ask-md-file")
    public RagFileAnswerWithSources askMarkdownFile(
            @RequestParam(defaultValue = "spring-ai-tools.md") String fileName,
            @RequestParam(defaultValue = "ToolContextとは何ですか？") String message,
            @RequestParam(defaultValue = "5") int topK,
            @RequestParam(defaultValue = "0.0") double threshold) {

        validateRagRequest(message, topK, threshold);
        validateRagFileName(fileName);
        return ragService.askByFile(fileName, message, topK, threshold);
    }

    @GetMapping("/search-md-dir-simple")
    public List<RagFileSearchResult> searchMarkdownDirectorySimple(
            @RequestParam(defaultValue = "ToolContextとは何ですか？") String message,
            @RequestParam(defaultValue = "10") int topK,
            @RequestParam(defaultValue = "0.0") double threshold) {

        validateRagRequest(message, topK, threshold);
        return ragService.searchAll(message, topK, threshold);
    }

    @GetMapping("/ask-md-dir")
    public RagFileAnswerWithSources askMarkdownDirectory(
            @RequestParam(defaultValue = "ToolContextとは何ですか？") String message,
            @RequestParam(defaultValue = "10") int topK,
            @RequestParam(defaultValue = "0.0") double threshold) {

        validateRagRequest(message, topK, threshold);
        return ragService.askAll(message, topK, threshold);
    }

    @PostMapping("/ask-md-dir")
    public RagFileAnswerWithSources askMarkdownDirectoryPost(
            @RequestBody RagAskRequest request) {

        String message = request.message() != null
                ? request.message()
                : "ToolContextとは何ですか？";

        int topK = request.topK() != null
                ? request.topK()
                : 10;

        double threshold = request.threshold() != null
                ? request.threshold()
                : 0.0;

        validateRagRequest(message, topK, threshold);
        return ragService.askAll(message, topK, threshold);
    }

    @PostMapping("/ask-md-file")
    public RagFileAnswerWithSources askMarkdownFilePost(
            @RequestBody RagAskRequest request) {

        String fileName = request.fileName() != null
                ? request.fileName()
                : "spring-ai-tools.md";

        String message = request.message() != null
                ? request.message()
                : "ToolContextとは何ですか？";

        int topK = request.topK() != null
                ? request.topK()
                : 5;

        double threshold = request.threshold() != null
                ? request.threshold()
                : 0.0;

        validateRagRequest(message, topK, threshold);
        validateRagFileName(fileName);
        return ragService.askByFile(fileName, message, topK, threshold);
    }

    private void validateRagRequest(String message, int topK, double threshold) {

        if (message == null || message.isBlank()) {
            throw new InvalidRagRequestException("messageは必須です。");
        }

        if (topK < 1 || topK > 20) {
            throw new InvalidRagRequestException("topKは1以上20以下で指定してください。");
        }

        if (threshold < 0.0 || threshold > 1.0) {
            throw new InvalidRagRequestException("thresholdは0.0以上1.0以下で指定してください。");
        }
    }

    private void validateRagFileName(String fileName) {

        if (fileName == null || fileName.isBlank()) {
            throw new InvalidRagRequestException("fileNameは必須です。");
        }

        if (!ALLOWED_FILE_NAMES.contains(fileName)) {
            throw new InvalidRagRequestException(
                    "fileNameは spring-ai-notes.md, spring-ai-tools.md, spring-ai-rag.md のいずれかを指定してください。");
        }
    }
}
