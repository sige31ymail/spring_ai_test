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
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;

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

    public String loadMarkdownDirectory() throws IOException {

        Path docsDir = Path.of("src", "main", "resources", "docs");

        if (!Files.exists(docsDir)) {
            return "docs directory not found: " + docsDir.toAbsolutePath();
        }

        List<Document> allDocuments = new ArrayList<>();

        try (DirectoryStream<Path> stream = Files.newDirectoryStream(docsDir, "*.md")) {
            for (Path mdPath : stream) {
                String markdown = Files.readString(mdPath, StandardCharsets.UTF_8);
                String fileName = mdPath.getFileName().toString();

                List<Document> documents = splitMarkdownByH2WithFileName(markdown, fileName);
                allDocuments.addAll(documents);
            }
        }

        vectorStore.add(allDocuments);

        return "Markdown directory loaded: " + allDocuments.size();
    }

    public String saveStore() throws IOException {

        Path path = Path.of("data", "simple-vector-store.json");
        Files.createDirectories(path.getParent());

        vectorStore.save(path.toFile());

        return "VectorStore saved to: " + path.toAbsolutePath();
    }

    public String loadStore() throws IOException {

        Path path = Path.of("data", "simple-vector-store.json");

        if (!Files.exists(path)) {
            return "VectorStore file not found: " + path.toAbsolutePath();
        }

        vectorStore.load(path.toFile());

        return "VectorStore loaded from: " + path.toAbsolutePath();
    }

    private List<Document> splitMarkdownByH2WithFileName(String markdown, String fileName) {

        List<Document> documents = new ArrayList<>();

        String currentTitle = null;
        StringBuilder currentText = new StringBuilder();

        for (String line : markdown.split("\\R")) {

            if (line.startsWith("## ")) {

                if (currentTitle != null && currentText.length() > 0) {
                    documents.add(new Document(
                            currentText.toString().trim(),
                            Map.of(
                                    "source", "docs-dir",
                                    "fileName", fileName,
                                    "title", currentTitle)));
                }

                currentTitle = line.substring(3).trim();
                currentText = new StringBuilder();

                currentText.append("file: ").append(fileName).append("\n");
                currentText.append(line).append("\n");
            } else {
                if (currentTitle != null) {
                    currentText.append(line).append("\n");
                }
            }
        }

        if (currentTitle != null && currentText.length() > 0) {
            documents.add(new Document(
                    currentText.toString().trim(),
                    Map.of(
                            "source", "docs-dir",
                            "fileName", fileName,
                            "title", currentTitle)));
        }

        return documents;
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
                .collect(Collectors.toMap(
                        source -> source.fileName() + "\n" + source.title() + "\n" + source.text(),
                        source -> source,
                        (first, duplicate) -> first,
                        LinkedHashMap::new))
                .values()
                .stream()
                .toList();
    }

    public RagFileAnswerWithSources askByFile(
            String fileName,
            String message,
            int topK,
            double threshold) {

        List<RagFileSearchResult> sources = searchByFile(fileName, message, topK, threshold);

        if (sources.isEmpty()) {
            return new RagFileAnswerWithSources("参考情報にはありません。", sources);
        }

        String context = sources.stream()
                .map(source -> "ファイル: " + source.fileName()
                + "\nタイトル: " + source.title()
                + "\n本文:\n" + source.text())
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

        return new RagFileAnswerWithSources(answer, sources);
    }

    private String normalizeDocFileName(String fileName) {
        return switch (fileName) {
            case "spring-ai-notes.md", "spring-ai-tools.md", "spring-ai-rag.md" ->
                fileName;
            default ->
                "spring-ai-notes.md";
        };
    }

    public List<RagFileSearchResult> searchAll(
            String message,
            int topK,
            double threshold) {

        List<Document> documents = vectorStore.similaritySearch(
                SearchRequest.builder()
                        .query(message)
                        .topK(topK)
                        .similarityThreshold(threshold)
                        .filterExpression("source == 'docs-dir'")
                        .build());

        return documents.stream()
                .map(doc -> new RagFileSearchResult(
                String.valueOf(doc.getMetadata().getOrDefault("fileName", "")),
                String.valueOf(doc.getMetadata().getOrDefault("title", "")),
                doc.getScore(),
                doc.getMetadata().get("distance"),
                doc.getText()))
                .collect(Collectors.toMap(
                        source -> source.fileName() + "\n" + source.title() + "\n" + source.text(),
                        source -> source,
                        (first, duplicate) -> first,
                        java.util.LinkedHashMap::new))
                .values()
                .stream()
                .toList();
    }

    public RagFileAnswerWithSources askAll(
            String message,
            int topK,
            double threshold) {

        List<RagFileSearchResult> sources = searchAll(message, topK, threshold);

        if (sources.isEmpty()) {
            return new RagFileAnswerWithSources("参考情報にはありません。", sources);
        }

        String context = sources.stream()
                .map(source -> "ファイル: " + source.fileName()
                + "\nタイトル: " + source.title()
                + "\n本文:\n" + source.text())
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

        return new RagFileAnswerWithSources(answer, sources);
    }
}
