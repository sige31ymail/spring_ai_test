package com.example.spring_ai_test;

import java.io.IOException;
import java.util.List;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/labor")
public class LaborController {

    private final LaborDocumentService laborDocumentService;
    private final LaborRagService laborRagService;

    public LaborController(LaborDocumentService laborDocumentService, LaborRagService laborRagService) {
        this.laborDocumentService = laborDocumentService;
        this.laborRagService = laborRagService;
    }

    @PostMapping("/load")
    public ResponseEntity<String> load() throws IOException {
        String result = laborDocumentService.loadPdfFromUrl();
        return ResponseEntity.ok(result);
    }

    @GetMapping("/save-store")
    public String saveStore() throws IOException {
        return laborDocumentService.saveStore();
    }

    @GetMapping("/load-store")
    public String loadStore() throws IOException {
        return laborDocumentService.loadStore();
    }

    @PostMapping("/ask")
    public RagFileAnswerWithSources ask(@RequestBody LaborAskRequest request) {

        String message = request.message() != null ? request.message() : "";
        int topK = request.topK() != null ? request.topK() : 5;
        double threshold = request.threshold() != null ? request.threshold() : 0.0;

        validateRequest(message, topK, threshold);

        return laborRagService.ask(message, topK, threshold);
    }

    @GetMapping("/search")
    public List<RagFileSearchResult> search(
            @RequestParam String message,
            @RequestParam(defaultValue = "5") int topK,
            @RequestParam(defaultValue = "0.0") double threshold) {

        validateRequest(message, topK, threshold);

        return laborRagService.search(message, topK, threshold);
    }

    @GetMapping("/status")
    public java.util.Map<String, Object> status() {
        return java.util.Map.of(
                "loaded", laborDocumentService.isLoaded()
        );
    }

    private void validateRequest(String message, int topK, double threshold) {

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
}
