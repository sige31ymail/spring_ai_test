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
public class LaborVectorStoreStartupLoader implements ApplicationRunner {

    private static final Logger logger = LoggerFactory.getLogger(LaborVectorStoreStartupLoader.class);

    private final LaborDocumentService laborDocumentService;

    public LaborVectorStoreStartupLoader(LaborDocumentService laborDocumentService) {
        this.laborDocumentService = laborDocumentService;
    }

    @Override
    public void run(ApplicationArguments args) throws IOException {

        Path path = Path.of("data", "labor-vector-store.json");

        if (!Files.exists(path)) {
            logger.info("Labor VectorStore file not found. Skip auto load: {}", path.toAbsolutePath());
            return;
        }

        logger.info("Auto-loading labor VectorStore from: {}", path.toAbsolutePath());
        laborDocumentService.loadStore();
    }
}
