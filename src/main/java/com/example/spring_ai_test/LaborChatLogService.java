package com.example.spring_ai_test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import com.fasterxml.jackson.databind.ObjectMapper;

@Service
public class LaborChatLogService {

    private static final Logger logger = LoggerFactory.getLogger(LaborChatLogService.class);
    private static final String LOG_PATH = "data/labor-chat-log.jsonl";

    private final ObjectMapper objectMapper;

    public LaborChatLogService(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    /**
     * ログエントリを JSONL ファイルに1行追記する（スレッドセーフ）。
     */
    public synchronized void append(LaborChatLogEntry entry) throws IOException {
        Path path = Path.of(LOG_PATH);
        Files.createDirectories(path.getParent());
        String line = objectMapper.writeValueAsString(entry) + System.lineSeparator();
        Files.writeString(path, line, StandardOpenOption.CREATE, StandardOpenOption.APPEND);
        logger.debug("Chat log appended: conversationId={}, elapsedMs={}", entry.conversationId(), entry.elapsedMs());
    }

    /**
     * 最新の {@code limit} 件を新しい順で返す。
     */
    public List<LaborChatLogEntry> getLogs(int limit) throws IOException {
        Path path = Path.of(LOG_PATH);
        if (!Files.exists(path)) {
            return Collections.emptyList();
        }

        List<String> lines = Files.readAllLines(path);
        List<LaborChatLogEntry> result = new ArrayList<>();
        int start = Math.max(0, lines.size() - limit);

        for (int i = lines.size() - 1; i >= start; i--) {
            String line = lines.get(i).trim();
            if (!line.isEmpty()) {
                try {
                    result.add(objectMapper.readValue(line, LaborChatLogEntry.class));
                } catch (Exception e) {
                    logger.warn("Skipping malformed log line at index {}: {}", i, e.getMessage());
                }
            }
        }

        return result;
    }

    /**
     * ログファイルをすべて削除する。
     */
    public void clearLogs() throws IOException {
        Files.deleteIfExists(Path.of(LOG_PATH));
        logger.info("Chat log cleared.");
    }
}
