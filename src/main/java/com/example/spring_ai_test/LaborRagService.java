package com.example.spring_ai_test;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.stream.Collectors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.client.advisor.MessageChatMemoryAdvisor;
import org.springframework.ai.chat.client.advisor.SimpleLoggerAdvisor;
import org.springframework.ai.chat.memory.ChatMemory;
import org.springframework.ai.document.Document;
import org.springframework.ai.ollama.api.OllamaChatOptions;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.SimpleVectorStore;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;

@Service
public class LaborRagService {

    private static final Logger logger = LoggerFactory.getLogger(LaborRagService.class);

    private final ChatClient chatClient;
    private final SimpleVectorStore laborVectorStore;
    private final LaborDocumentService laborDocumentService;
    private final ChatMemory chatMemory;
    private final LaborChatLogService chatLogService;

    public LaborRagService(
            ChatClient.Builder builder,
            @Qualifier("laborVectorStore") SimpleVectorStore laborVectorStore,
            LaborDocumentService laborDocumentService,
            ChatMemory chatMemory,
            LaborChatLogService chatLogService) {
        this.chatClient = builder.build();
        this.laborVectorStore = laborVectorStore;
        this.laborDocumentService = laborDocumentService;
        this.chatMemory = chatMemory;
        this.chatLogService = chatLogService;
    }

    private OllamaChatOptions ragOptions() {
        return OllamaChatOptions.builder()
                .temperature(0.1)
                .numPredict(512)
                .disableThinking()
                .build();
    }

    public List<RagFileSearchResult> search(String message, int topK, double threshold) {

        laborDocumentService.ensureLoaded();

        logger.info("Labor RAG search start: message={}, topK={}, threshold={}", message, topK, threshold);

        List<Document> documents = laborVectorStore.similaritySearch(
                SearchRequest.builder()
                        .query(message)
                        .topK(topK)
                        .similarityThreshold(threshold)
                        .build());

        List<RagFileSearchResult> results = toResults(documents);

        logger.info("Labor RAG search completed: resultCount={}", results.size());

        return results;
    }

    public RagFileAnswerWithSources ask(String message, int topK, double threshold, String conversationId) {

        long startMs = System.currentTimeMillis();

        List<RagFileSearchResult> sources = search(message, topK, threshold);

        String answer;
        if (sources.isEmpty()) {
            answer = "就業規則モデルには該当する記載が見つかりませんでした。";
        } else {
            String context = buildContext(sources);
            answer = chatClient.prompt()
                    .options(ragOptions())
                    .advisors(
                            MessageChatMemoryAdvisor.builder(chatMemory)
                                    .conversationId(conversationId)
                                    .build(),
                            new SimpleLoggerAdvisor())
                    .system("""
                            あなたは就業規則・労務管理の専門アシスタントです。
                            必ず提供された参考情報（厚生労働省の就業規則モデル）だけを根拠に回答してください。
                            参考情報に記載のない事項は「就業規則モデルには記載がありません」と答えてください。
                            回答は日本語で、簡潔かつ正確に答えてください。
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
        }

        long elapsedMs = System.currentTimeMillis() - startMs;

        try {
            chatLogService.append(new LaborChatLogEntry(
                    Instant.now().toString(),
                    conversationId,
                    message,
                    answer,
                    topK,
                    threshold,
                    sources.size(),
                    elapsedMs));
        } catch (Exception e) {
            logger.warn("Failed to write chat log: {}", e.getMessage());
        }

        return new RagFileAnswerWithSources(answer, sources);
    }

    private String buildContext(List<RagFileSearchResult> sources) {
        return sources.stream()
                .map(s -> "ファイル: " + s.fileName()
                        + "\nページ: " + s.title()
                        + "\n本文:\n" + s.text())
                .collect(Collectors.joining("\n\n---\n\n"));
    }

    private List<RagFileSearchResult> toResults(List<Document> documents) {
        return documents.stream()
                .map(doc -> new RagFileSearchResult(
                        String.valueOf(doc.getMetadata().getOrDefault("fileName", "")),
                        "p." + doc.getMetadata().getOrDefault("pageNumber", ""),
                        doc.getScore(),
                        doc.getMetadata().get("distance"),
                        doc.getText()))
                .collect(Collectors.toMap(
                        s -> s.fileName() + "\n" + s.title() + "\n" + s.text(),
                        s -> s,
                        (first, duplicate) -> first,
                        LinkedHashMap::new))
                .values()
                .stream()
                .toList();
    }
}
