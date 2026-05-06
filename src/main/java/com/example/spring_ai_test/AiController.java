package com.example.spring_ai_test;

import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.client.advisor.SimpleLoggerAdvisor;
import org.springframework.ai.ollama.api.OllamaChatOptions;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class AiController {

    private final ChatClient chatClient;

    public AiController(ChatClient.Builder builder) {
        this.chatClient = builder.build();
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
}