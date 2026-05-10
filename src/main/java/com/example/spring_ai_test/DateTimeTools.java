package com.example.spring_ai_test;

import java.time.ZoneId;
import java.time.ZonedDateTime;

import org.springframework.ai.tool.annotation.Tool;
import org.springframework.stereotype.Component;

@Component
public class DateTimeTools {

    @Tool(description = "Get the current date and time in Tokyo, Japan.")
    public String getCurrentDateTime() {
        return ZonedDateTime.now(ZoneId.of("Asia/Tokyo")).toString();
    }
}