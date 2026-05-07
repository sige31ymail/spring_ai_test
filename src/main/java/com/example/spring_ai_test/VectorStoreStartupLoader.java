package com.example.spring_ai_test;

import java.nio.file.Files;
import java.nio.file.Path;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.vectorstore.SimpleVectorStore;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

@Component
public class VectorStoreStartupLoader implements ApplicationRunner {

    private static final Logger logger = LoggerFactory.getLogger(VectorStoreStartupLoader.class);

    private final SimpleVectorStore vectorStore;

    public VectorStoreStartupLoader(SimpleVectorStore vectorStore) {
        this.vectorStore = vectorStore;
    }

    @Override
    public void run(ApplicationArguments args) {

        Path path = Path.of("data", "simple-vector-store.json");

        if (!Files.exists(path)) {
            logger.info("VectorStore file not found. Skip auto load: {}", path.toAbsolutePath());
            return;
        }

        vectorStore.load(path.toFile());

        logger.info("VectorStore auto loaded from: {}", path.toAbsolutePath());
    }
}
