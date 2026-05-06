package com.example.spring_ai_test;

import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.client.advisor.SimpleLoggerAdvisor;
import org.springframework.ai.ollama.api.OllamaChatOptions;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.ai.chat.memory.ChatMemory;
import org.springframework.ai.chat.model.ToolContext;
import org.springframework.ai.chat.client.advisor.MessageChatMemoryAdvisor;
import org.springframework.http.MediaType;
import reactor.core.publisher.Flux;
import java.util.Map;
import java.util.Objects;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@RestController
public class AiController {

    private final ChatClient chatClient;
    private final ChatMemory chatMemory;
    private static final Logger logger = LoggerFactory.getLogger(TenantProductSearchTools.class);

    public AiController(ChatClient.Builder builder, ChatMemory chatMemory) {
        this.chatClient = builder.build();
        this.chatMemory = chatMemory;
    }

    private OllamaChatOptions fastOptions() {
        return OllamaChatOptions.builder()
                .temperature(0.3)
                .numPredict(128)
                .disableThinking()
                .build();
    }

    @GetMapping("/ai")
    public String ai(@RequestParam(defaultValue = "Spring AIとは何ですか？") String message) {
        return chatClient.prompt()
                .options(fastOptions())
                .system("回答は日本語で、3行以内で簡潔に答えてください。")
                .user(message)
                .call()
                .content();
    }

    @GetMapping("/ai/teacher")
    public String teacher(@RequestParam String message) {
        return chatClient.prompt()
                .options(fastOptions())
                .system("あなたは初心者向けのJava講師です。専門用語は少なく、短く説明してください。回答は3行以内にしてください。")
                .user(message)
                .call()
                .content();
    }

    @GetMapping("/ai/template")
    public String template(@RequestParam String topic,
            @RequestParam(defaultValue = "初心者") String level) {
        return chatClient.prompt()
                .options(fastOptions())
                .user(u -> u
                        .text("{level}向けに、{topic}を3行で説明してください。")
                        .param("level", level)
                        .param("topic", topic))
                .call()
                .content();
    }

    @GetMapping("/ai/entity")
    public SummaryResponse entity(@RequestParam String message) {
        return chatClient.prompt()
                .options(fastOptions())
                .user(u -> u
                        .text("""
                                次の内容を要約してください。
                                title と summary を持つ形式で返してください。
                                summary は3行以内にしてください。
                                内容: {message}
                                """)
                        .param("message", message))
                .call()
                .entity(SummaryResponse.class);
    }

    @GetMapping("/ai/log")
    public String log(@RequestParam String message) {
        return chatClient.prompt()
                .options(fastOptions())
                .advisors(new SimpleLoggerAdvisor())
                .user(message)
                .call()
                .content();
    }

    @GetMapping("/ai/memory")
    public String memory(
            @RequestParam(defaultValue = "default") String conversationId,
            @RequestParam String message) {

        return chatClient.prompt()
                .options(fastOptions())
                .advisors(MessageChatMemoryAdvisor.builder(chatMemory).build())
                .advisors(a -> a.param(ChatMemory.CONVERSATION_ID, conversationId))
                .system("回答は日本語で、短く簡潔に答えてください。")
                .user(message)
                .call()
                .content();
    }

    @GetMapping(value = "/ai/stream", produces = "text/event-stream;charset=UTF-8")
    public Flux<String> stream(@RequestParam(defaultValue = "Spring AIとは何ですか？") String message) {
        return chatClient.prompt()
                .options(fastOptions())
                .system("回答は日本語で、3行以内で簡潔に答えてください。")
                .user(message)
                .stream()
                .content();
    }

    @GetMapping(value = "/ai/stream-text", produces = "text/plain;charset=UTF-8")
    public Flux<String> streamText(@RequestParam(defaultValue = "Spring AIとは何ですか？") String message) {
        return chatClient.prompt()
                .options(fastOptions())
                .system("回答は日本語で、3行以内で簡潔に答えてください。")
                .user(message)
                .stream()
                .content();
    }

    @GetMapping("/ai/tool/time")
    public String toolTime(@RequestParam(defaultValue = "今の日時を教えてください。") String message) {
        return chatClient.prompt()
                .options(fastOptions())
                .system("必要な場合は利用可能なツールを使って回答してください。回答は日本語で簡潔にしてください。")
                .user(message)
                .tools(new DateTimeTools())
                .call()
                .content();
    }

    @GetMapping("/ai/tool/calc")
    public String toolCalc(@RequestParam(defaultValue = "単価120円の商品を5個買った場合の税込金額を計算してください。税率は10%です。") String message) {
        return chatClient.prompt()
                .options(fastOptions())
                .system("""
                        必要な場合は利用可能なツールを使って回答してください。
                        金額計算は必ずツールを使ってください。
                        回答は日本語で、計算過程を短く説明してください。
                        """)
                .user(message)
                .tools(new PriceCalculatorTools())
                .call()
                .content();
    }

    @GetMapping("/ai/tool/product")
    public String toolProduct(
            @RequestParam(defaultValue = "在庫のあるノートPCで一番安いものを教えてください。") String message) {

        return chatClient.prompt()
                .options(fastOptions())
                .system("""
                        あなたは商品検索アシスタントです。
                        商品、価格、在庫、カテゴリ、商品コードについて聞かれた場合は、必ず提供されたツールを使ってください。
                        回答は日本語で簡潔にしてください。
                        金額は円で表示してください。
                        """)
                .user(message)
                .tools(new ProductSearchTools())
                .call()
                .content();
    }

    @GetMapping("/ai/tool/product-context")
    public String toolProductContext(
            @RequestParam(defaultValue = "tokyo") String tenantId,
            @RequestParam(defaultValue = "在庫のあるノートPCで一番安いものを教えてください。") String message) {

        return chatClient.prompt()
                .options(fastOptions())
                .advisors(new SimpleLoggerAdvisor())
                .system("""
                        あなたは商品検索アシスタントです。
                        商品、価格、在庫について聞かれた場合は、必ず提供されたツールを使ってください。
                        回答は日本語で簡潔にしてください。
                        """)
                .user(message)
                .tools(new TenantProductSearchTools())
                .toolContext(Map.of("tenantId", tenantId))
                .call()
                .content();
    }

    @GetMapping("/ai/tool/return-direct")
    public String returnDirect(
            @RequestParam(defaultValue = "在庫のあるノートPCで一番安いものを教えてください。") String message) {

        return chatClient.prompt()
                .options(fastOptions())
                .system("""
                        あなたは商品検索アシスタントです。
                        商品検索が必要な場合は、必ず提供されたツールを使ってください。
                        """)
                .user(message)
                .tools(new DirectProductTools())
                .call()
                .content();
    }
}