package com.example.spring_ai_test;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Collections;
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
import org.springframework.web.multipart.MultipartFile;

@Service
public class LaborDocumentService {

    private static final Logger logger = LoggerFactory.getLogger(LaborDocumentService.class);
    private static final String PDF_URL = "https://www.mhlw.go.jp/content/001620507.pdf";
    private static final String STORE_PATH = "data/labor-vector-store.json";
    private static final String FILES_PATH = "data/labor-files.txt";
    private static final String SOURCE_TAG = "labor-pdf";
    private static final String DEFAULT_FILE_NAME = "就業規則モデル.pdf";

    private final SimpleVectorStore laborVectorStore;
    private volatile boolean loaded = false;
    private final List<String> loadedFileNames = new ArrayList<>();

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
            return processPdf(tempPdf, DEFAULT_FILE_NAME);
        } finally {
            Files.deleteIfExists(tempPdf);
        }
    }

    public String uploadPdf(MultipartFile file) throws IOException {

        if (file == null || file.isEmpty()) {
            throw new InvalidRagRequestException("アップロードするPDFファイルを選択してください。");
        }

        String originalFilename = file.getOriginalFilename() != null
                ? file.getOriginalFilename()
                : "uploaded.pdf";

        if (!originalFilename.toLowerCase().endsWith(".pdf")) {
            throw new InvalidRagRequestException("PDFファイルのみアップロードできます。");
        }

        logger.info("PDF upload received: fileName={}, size={} bytes", originalFilename, file.getSize());

        Path tempPdf = Files.createTempFile("upload-", ".pdf");
        try {
            file.transferTo(tempPdf);
            return processPdf(tempPdf, originalFilename);
        } finally {
            Files.deleteIfExists(tempPdf);
        }
    }

    private String processPdf(Path pdfPath, String fileName) throws IOException {

        var config = PdfDocumentReaderConfig.builder()
                .withPageTopMargin(0)
                .withPagesPerDocument(1)
                .build();

        var reader = new PagePdfDocumentReader(new FileSystemResource(pdfPath.toFile()), config);
        List<Document> rawDocs = reader.get();
        int pageCount = rawDocs.size();

        logger.info("PDF parsed: fileName={}, pages={}", fileName, pageCount);

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
            meta.put("fileName", fileName);
            enrichedDocs.add(new Document(text, meta));
        }

        if (enrichedDocs.isEmpty()) {
            return String.format("[%s] 全 %d ページのテキスト抽出が空でした。スキャンPDFまたは暗号化されている可能性があります。",
                    fileName, pageCount);
        }

        logger.info("PDF enriched: fileName={}, validPages={}, skippedEmpty={}",
                fileName, enrichedDocs.size(), skippedEmpty);

        List<Document> chunks = new TokenTextSplitter().apply(enrichedDocs);
        int chunkCount = chunks.size();
        logger.info("PDF chunked: fileName={}, chunks={}", fileName, chunkCount);

        laborVectorStore.add(chunks);
        loaded = true;

        loadedFileNames.add(fileName);
        saveFileNames();
        saveStore();

        logger.info("PDF loaded and saved: fileName={}, pages={}, chunks={}", fileName, pageCount, chunkCount);
        return String.format("[%s] 取り込み完了: %d ページ, %d チャンク", fileName, pageCount, chunkCount);
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
        loadFileNames();
        logger.info("Labor VectorStore loaded: path={}, files={}", path.toAbsolutePath(), loadedFileNames);
        return "Labor VectorStore loaded from: " + path.toAbsolutePath();
    }

    public String clearStore() throws IOException {

        // 空のJSONをロードしてメモリ上のVectorStoreをリセット
        Path storePath = Path.of(STORE_PATH);
        if (Files.exists(storePath)) {
            Path emptyTemp = Files.createTempFile("empty-store-", ".json");
            try {
                Files.writeString(emptyTemp, "{}");
                laborVectorStore.load(emptyTemp.toFile());
            } finally {
                Files.deleteIfExists(emptyTemp);
            }
            Files.deleteIfExists(storePath);
        }

        Files.deleteIfExists(Path.of(FILES_PATH));
        loadedFileNames.clear();
        loaded = false;

        logger.info("Labor VectorStore cleared.");
        return "VectorStoreをリセットしました。新しいPDFを取り込んでください。";
    }

    public boolean isLoaded() {
        return loaded;
    }

    public List<String> getLoadedFileNames() {
        return Collections.unmodifiableList(loadedFileNames);
    }

    void ensureLoaded() {
        if (!loaded) {
            throw new VectorStoreNotLoadedException(
                    "Labor VectorStoreが読み込まれていません。PDFをアップロードするか、保存済みVectorStoreを読み込んでください。");
        }
    }

    private void saveFileNames() throws IOException {
        Path path = Path.of(FILES_PATH);
        Files.createDirectories(path.getParent());
        Files.write(path, loadedFileNames,
                StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING);
    }

    private void loadFileNames() throws IOException {
        Path path = Path.of(FILES_PATH);
        if (Files.exists(path)) {
            loadedFileNames.clear();
            loadedFileNames.addAll(Files.readAllLines(path));
        }
    }
}
