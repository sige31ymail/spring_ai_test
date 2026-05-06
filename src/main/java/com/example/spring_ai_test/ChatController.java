package com.example.spring_ai_test;

import org.springframework.ai.chat.client.ChatClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class ChatController {

    private final ChatClient chatClient;

    public ChatController(ChatClient.Builder builder) {
        this.chatClient = builder.build();
    }

    @GetMapping("/ai")
    public String ai() {
        return chatClient.prompt()
                .user("Spring AIとは何ですか？簡単に説明してください。")
                .call()
                .content();
    }
}