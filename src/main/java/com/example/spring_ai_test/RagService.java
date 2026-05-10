package com.example.spring_ai_test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.client.advisor.SimpleLoggerAdvisor;
import org.springframework.ai.document.Document;
import org.springframework.ai.ollama.api.OllamaChatOptions;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.SimpleVectorStore;
import org.springframework.stereotype.Service;

@Service
public class RagService {

    private static final Logger logger = LoggerFactory.getLogger(RagService.class);
    private final ChatClient chatClient;
    private final SimpleVectorStore vectorStore;
    private boolean markdownDirectoryLoaded = false;

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

        if (markdownDirectoryLoaded) {
            logger.warn("RAG markdown directory already loaded. Skip loading.");
            return "Markdown directory already loaded. Skip loading.";
        }

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
        markdownDirectoryLoaded = true;

        logger.info("RAG markdown directory loaded: documentCount={}", allDocuments.size());
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
        markdownDirectoryLoaded = true;

        logger.info("RAG VectorStore loaded: path={}", path.toAbsolutePath());
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

    private List<RagFileSearchResult> toRagFileSearchResults(List<Document> documents) {

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

    private String buildContext(List<RagFileSearchResult> sources) {

        return sources.stream()
                .map(source -> "ファイル: " + source.fileName()
                + "\nタイトル: " + source.title()
                + "\n本文:\n" + source.text())
                .collect(Collectors.joining("\n\n---\n\n"));
    }

    private RagFileAnswerWithSources answerFromSources(
            String message,
            List<RagFileSearchResult> sources) {

        if (sources.isEmpty()) {
            return new RagFileAnswerWithSources("参考情報にはありません。", sources);
        }

        String context = buildContext(sources);

        String answer = chatClient.prompt()
                .options(ragOptions())
                .advisors(new SimpleLoggerAdvisor())
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

    public List<RagFileSearchResult> searchByFile(
            String fileName,
            String message,
            int topK,
            double threshold) {

        String safeFileName = normalizeDocFileName(fileName);
        ensureVectorStoreLoaded();

        logger.info("RAG search by file start: fileName={}, safeFileName={}, message={}, topK={}, threshold={}",
                fileName, safeFileName, message, topK, threshold);

        List<Document> documents = vectorStore.similaritySearch(
                SearchRequest.builder()
                        .query(message)
                        .topK(topK)
                        .similarityThreshold(threshold)
                        .filterExpression("source == 'docs-dir' && fileName == '" + safeFileName + "'")
                        .build());

        List<RagFileSearchResult> results = toRagFileSearchResults(documents);

        logSearchResults("RAG search by file", results);

        return results;
    }

    public RagFileAnswerWithSources askByFile(
            String fileName,
            String message,
            int topK,
            double threshold) {

        List<RagFileSearchResult> sources = searchByFile(fileName, message, topK, threshold);

        return answerFromSources(message, sources);
    }

    private String normalizeDocFileName(String fileName) {
        return switch (fileName) {
            case "spring-ai-notes.md", "spring-ai-tools.md", "spring-ai-rag.md" ->
                fileName;
            default ->
                throw new InvalidRagRequestException(
                        "fileNameは spring-ai-notes.md, spring-ai-tools.md, spring-ai-rag.md のいずれかを指定してください。");
        };
    }

    private void ensureVectorStoreLoaded() {

        if (!markdownDirectoryLoaded) {
            throw new VectorStoreNotLoadedException(
                    "VectorStoreが読み込まれていません。Markdownを読み込むか、保存済みVectorStoreを読み込んでください。");
        }
    }

    public List<RagFileSearchResult> searchAll(
            String message,
            int topK,
            double threshold) {
        ensureVectorStoreLoaded();

        logger.info("RAG search all start: message={}, topK={}, threshold={}",
                message, topK, threshold);

        List<Document> documents = vectorStore.similaritySearch(
                SearchRequest.builder()
                        .query(message)
                        .topK(topK)
                        .similarityThreshold(threshold)
                        .filterExpression("source == 'docs-dir'")
                        .build());

        List<RagFileSearchResult> results = toRagFileSearchResults(documents);

        logSearchResults("RAG search all", results);

        return results;
    }

    private void logSearchResults(String label, List<RagFileSearchResult> results) {

        logger.info("{} completed: resultCount={}", label, results.size());

        for (int i = 0; i < results.size(); i++) {
            RagFileSearchResult result = results.get(i);

            logger.info("{} result[{}]: fileName={}, title={}, score={}, distance={}",
                    label,
                    i + 1,
                    result.fileName(),
                    result.title(),
                    result.score(),
                    result.distance());
        }
    }

    public RagFileAnswerWithSources askAll(
            String message,
            int topK,
            double threshold) {

        List<RagFileSearchResult> sources = searchAll(message, topK, threshold);

        return answerFromSources(message, sources);
    }
}
