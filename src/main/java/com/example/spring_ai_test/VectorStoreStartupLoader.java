package com.example.spring_ai_test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

@Component
public class VectorStoreStartupLoader implements ApplicationRunner {

    private static final Logger logger = LoggerFactory.getLogger(VectorStoreStartupLoader.class);

    private final RagService ragService;

    public VectorStoreStartupLoader(RagService ragService) {
        this.ragService = ragService;
    }

    @Override
    public void run(ApplicationArguments args) throws IOException {

        Path path = Path.of("data", "simple-vector-store.json");

        if (!Files.exists(path)) {
            logger.info("VectorStore file not found. Skip auto load: {}", path.toAbsolutePath());
            return;
        }

        ragService.loadStore();
    }
}
