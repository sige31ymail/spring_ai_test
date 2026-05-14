package com.example.spring_ai_test;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.document.Document;
import org.springframework.ai.reader.pdf.PagePdfDocumentReader;
import org.springframework.ai.reader.pdf.config.PdfDocumentReaderConfig;
import org.springframework.ai.transformer.splitter.TokenTextSplitter;
import org.springframework.ai.vectorstore.SimpleVectorStore;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.core.io.FileSystemResource;
import org.springframework.stereotype.Service;

@Service
public class LaborDocumentService {

    private static final Logger logger = LoggerFactory.getLogger(LaborDocumentService.class);
    private static final String PDF_URL = "https://www.mhlw.go.jp/content/001620507.pdf";
    private static final String STORE_PATH = "data/labor-vector-store.json";
    private static final String SOURCE_TAG = "labor-pdf";
    private static final String FILE_NAME = "就業規則モデル.pdf";

    private final SimpleVectorStore laborVectorStore;
    private volatile boolean loaded = false;

    public LaborDocumentService(@Qualifier("laborVectorStore") SimpleVectorStore laborVectorStore) {
        this.laborVectorStore = laborVectorStore;
    }

    public String loadPdfFromUrl() throws IOException {

        if (loaded) {
            logger.warn("Labor PDF already loaded. Skip loading.");
            return "Labor PDF already loaded. Skip loading.";
        }

        logger.info("Downloading Labor PDF from: {}", PDF_URL);

        HttpClient httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(60))
                .build();

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(PDF_URL))
                .timeout(Duration.ofSeconds(120))
                .header("User-Agent", "Mozilla/5.0")
                .GET()
                .build();

        Path tempPdf;
        try {
            HttpResponse<byte[]> response = httpClient.send(request, HttpResponse.BodyHandlers.ofByteArray());
            if (response.statusCode() != 200) {
                throw new IOException("PDF download failed: HTTP " + response.statusCode());
            }
            tempPdf = Files.createTempFile("labor-", ".pdf");
            Files.write(tempPdf, response.body());
            logger.info("Labor PDF downloaded: {} bytes", response.body().length);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IOException("PDF download interrupted", e);
        }

        try {
            var config = PdfDocumentReaderConfig.builder()
                    .withPageTopMargin(0)
                    .withPagesPerDocument(1)
                    .build();

            var reader = new PagePdfDocumentReader(new FileSystemResource(tempPdf.toFile()), config);
            List<Document> rawDocs = reader.get();
            int pageCount = rawDocs.size();

            logger.info("Labor PDF parsed: pages={}", pageCount);

            List<Document> enrichedDocs = new ArrayList<>();
            int skippedEmpty = 0;
            for (int i = 0; i < rawDocs.size(); i++) {
                Document doc = rawDocs.get(i);
                String text = doc.getText();
                if (text == null || text.isBlank()) {
                    skippedEmpty++;
                    continue;
                }
                Map<String, Object> meta = new HashMap<>(doc.getMetadata());
                meta.put("source", SOURCE_TAG);
                meta.put("pageNumber", String.valueOf(i + 1));
                meta.put("fileName", FILE_NAME);
                enrichedDocs.add(new Document(text, meta));
            }

            if (enrichedDocs.isEmpty()) {
                return String.format("Labor PDF parsed but all %d pages had empty text. PDF may be image-based or encrypted.", pageCount);
            }

            logger.info("Labor PDF enriched: validPages={}, skippedEmpty={}, sampleText={}",
                    enrichedDocs.size(), skippedEmpty,
                    enrichedDocs.get(0).getText().substring(0, Math.min(100, enrichedDocs.get(0).getText().length())));


            List<Document> chunks = new TokenTextSplitter()
                    .apply(enrichedDocs);

            int chunkCount = chunks.size();
            logger.info("Labor PDF chunked: chunks={}", chunkCount);

            laborVectorStore.add(chunks);

            saveStore();
            loaded = true;

            logger.info("Labor PDF loaded and saved: pages={}, chunks={}", pageCount, chunkCount);
            return String.format("Labor PDF loaded: pages=%d, chunks=%d", pageCount, chunkCount);

        } finally {
            Files.deleteIfExists(tempPdf);
        }
    }

    public String saveStore() throws IOException {
        Path path = Path.of(STORE_PATH);
        Files.createDirectories(path.getParent());
        laborVectorStore.save(path.toFile());
        logger.info("Labor VectorStore saved to: {}", path.toAbsolutePath());
        return "Labor VectorStore saved to: " + path.toAbsolutePath();
    }

    public String loadStore() throws IOException {
        Path path = Path.of(STORE_PATH);
        if (!Files.exists(path)) {
            return "Labor VectorStore file not found: " + path.toAbsolutePath();
        }
        laborVectorStore.load(path.toFile());
        loaded = true;
        logger.info("Labor VectorStore loaded: path={}", path.toAbsolutePath());
        return "Labor VectorStore loaded from: " + path.toAbsolutePath();
    }

    public boolean isLoaded() {
        return loaded;
    }

    void ensureLoaded() {
        if (!loaded) {
            throw new VectorStoreNotLoadedException(
                    "Labor VectorStoreが読み込まれていません。PDFを取り込むか、保存済みVectorStoreを読み込んでください。");
        }
    }
}
