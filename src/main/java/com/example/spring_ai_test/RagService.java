package com.example.spring_ai_test;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.document.Document;
import org.springframework.ai.ollama.api.OllamaChatOptions;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.SimpleVectorStore;
import org.springframework.stereotype.Service;

@Service
public class RagService {

    private final ChatClient chatClient;
    private final SimpleVectorStore vectorStore;

    public RagService(ChatClient.Builder builder, SimpleVectorStore vectorStore) {
        this.chatClient = builder.build();
        this.vectorStore = vectorStore;
    }

    private OllamaChatOptions ragOptions() {
        return OllamaChatOptions.builder()
                .temperature(0.1)
                .numPredict(512)
                .disableThinking()
                .build();
    }

    public List<RagFileSearchResult> searchByFile(
            String fileName,
            String message,
            int topK,
            double threshold) {

        String safeFileName = normalizeDocFileName(fileName);

        List<Document> documents = vectorStore.similaritySearch(
                SearchRequest.builder()
                        .query(message)
                        .topK(topK)
                        .similarityThreshold(threshold)
                        .filterExpression("source == 'docs-dir' && fileName == '" + safeFileName + "'")
                        .build());

        return documents.stream()
                .map(doc -> new RagFileSearchResult(
                        String.valueOf(doc.getMetadata().getOrDefault("fileName", "")),
                        String.valueOf(doc.getMetadata().getOrDefault("title", "")),
                        doc.getScore(),
                        doc.getMetadata().get("distance"),
                        doc.getText()))
                .toList();
    }

    public RagAnswerWithSources askByFile(
            String fileName,
            String message,
            int topK,
            double threshold) {

        String safeFileName = normalizeDocFileName(fileName);

        List<Document> documents = vectorStore.similaritySearch(
                SearchRequest.builder()
                        .query(message)
                        .topK(topK)
                        .similarityThreshold(threshold)
                        .filterExpression("source == 'docs-dir' && fileName == '" + safeFileName + "'")
                        .build());

        List<RagSearchResult> sources = documents.stream()
                .map(doc -> new RagSearchResult(
                        String.valueOf(doc.getMetadata().getOrDefault("title", "")),
                        doc.getScore(),
                        doc.getMetadata().get("distance"),
                        doc.getText()))
                .toList();

        if (documents.isEmpty()) {
            return new RagAnswerWithSources("参考情報にはありません。", sources);
        }

        String context = documents.stream()
                .map(doc -> {
                    String docFileName = String.valueOf(doc.getMetadata().getOrDefault("fileName", ""));
                    String title = String.valueOf(doc.getMetadata().getOrDefault("title", ""));

                    return "ファイル: " + docFileName
                            + "\nタイトル: " + title
                            + "\n本文:\n" + doc.getText();
                })
                .collect(Collectors.joining("\n\n---\n\n"));

        String answer = chatClient.prompt()
                .options(ragOptions())
                .system("""
                        あなたはSpring AIの学習アシスタントです。
                        必ず参考情報だけを根拠に回答してください。
                        参考情報にない内容は、推測せず「参考情報にはありません」と答えてください。
                        回答は日本語で簡潔にしてください。
                        """)
                .user(u -> u
                        .text("""
                                質問:
                                {message}

                                参考情報:
                                {context}
                                """)
                        .param("message", message)
                        .param("context", context))
                .call()
                .content();

        return new RagAnswerWithSources(answer, sources);
    }

    private String normalizeDocFileName(String fileName) {
        return switch (fileName) {
            case "spring-ai-notes.md",
                 "spring-ai-tools.md",
                 "spring-ai-rag.md" -> fileName;
            default -> "spring-ai-notes.md";
        };
    }
}