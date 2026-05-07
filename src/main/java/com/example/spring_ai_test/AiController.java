package com.example.spring_ai_test;

import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.client.advisor.MessageChatMemoryAdvisor;
import org.springframework.ai.chat.client.advisor.SimpleLoggerAdvisor;
import org.springframework.ai.chat.memory.ChatMemory;
import org.springframework.ai.ollama.api.OllamaChatOptions;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import reactor.core.publisher.Flux;
import java.util.List;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.ai.document.Document;
import org.springframework.ai.chat.client.advisor.vectorstore.QuestionAnswerAdvisor;
import org.springframework.ai.rag.advisor.RetrievalAugmentationAdvisor;
import org.springframework.ai.rag.generation.augmentation.ContextualQueryAugmenter;
import org.springframework.ai.rag.retrieval.search.VectorStoreDocumentRetriever;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.reader.markdown.MarkdownDocumentReader;
import org.springframework.ai.transformer.splitter.TokenTextSplitter;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import org.springframework.core.io.ClassPathResource;

@RestController
public class AiController {

    private final ChatClient chatClient;
    private final ChatMemory chatMemory;
    private static final Logger logger = LoggerFactory.getLogger(TenantProductSearchTools.class);
    private final VectorStore vectorStore;

    public AiController(
            ChatClient.Builder builder,
            ChatMemory chatMemory,
            VectorStore vectorStore) {

        this.chatClient = builder.build();
        this.chatMemory = chatMemory;
        this.vectorStore = vectorStore;
    }

    private OllamaChatOptions fastOptions() {
        return OllamaChatOptions.builder()
                .temperature(0.3)
                .numPredict(128)
                .disableThinking()
                .build();
    }

    private OllamaChatOptions structuredOptions() {
        return OllamaChatOptions.builder()
                .temperature(0.1)
                .numPredict(512)
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

    @GetMapping("/ai/structured/review")
    public ReviewAnalysis structuredReview(
            @RequestParam(defaultValue = "商品は便利ですが、配送が遅くて少し不満です。") String message) {

        return chatClient.prompt()
                .options(fastOptions())
                .system("""
                        あなたはレビュー分析アシスタントです。
                        レビュー内容を分析してください。
                        sentiment は positive, neutral, negative のいずれかにしてください。
                        score は1から5の整数にしてください。
                        needsFollowUp は人間の確認が必要なら true にしてください。
                        """)
                .user(u -> u
                .text("次のレビューを分析してください: {message}")
                .param("message", message))
                .call()
                .entity(ReviewAnalysis.class);
    }

    @GetMapping("/ai/structured/list")
    public List<LearningItem> structuredList(
            @RequestParam(defaultValue = "Spring AIの初心者が次に学ぶべき内容") String message) {

        return chatClient.prompt()
                .options(structuredOptions())
                .system("""
                    あなたはJava学習計画を作るアシスタントです。
                    学習項目を3件返してください。
                    priority は 1 が最重要、3 が低めです。
                    余計な説明文は出力しないでください。
                    """)
                .user(message)
                .call()
                .entity(new ParameterizedTypeReference<List<LearningItem>>() {
                });
    }

    @GetMapping("/ai/rag/load")
    public String loadRagDocuments() {

        List<Document> documents = List.of(
                new Document("""
                    Spring AIは、Spring BootアプリケーションからLLM、Embedding、VectorStore、Tool Callingなどを扱うためのフレームワークです。
                    ChatClientを使うことで、AIモデルとの会話を簡潔に実装できます。
                    """),
                new Document("""
                    Tool Callingは、AIモデルが必要に応じてJavaメソッドを呼び出す仕組みです。
                    外部API、DB検索、計算処理などをAIから利用できます。
                    """),
                new Document("""
                    RAGはRetrieval Augmented Generationの略です。
                    ユーザーの質問に関連する文書をVectorStoreから検索し、その検索結果をプロンプトに追加して回答します。
                    """)
        );

        vectorStore.add(documents);

        return "RAG documents loaded: " + documents.size();
    }

    @GetMapping("/ai/rag/ask")
    public String askRag(
            @RequestParam(defaultValue = "RAGとは何ですか？") String message) {

        return chatClient.prompt()
                .options(structuredOptions())
                .advisors(QuestionAnswerAdvisor.builder(vectorStore).build())
                .system("""
                    あなたはSpring AIの学習アシスタントです。
                    回答は、提供された参考情報を優先して日本語で簡潔に説明してください。
                    参考情報にない内容は、推測せず「参考情報にはありません」と答えてください。
                    """)
                .user(message)
                .call()
                .content();
    }

    @GetMapping("/ai/rag/strict")
    public String askRagStrict(
            @RequestParam(defaultValue = "RAGとは何ですか？") String message,
            @RequestParam(defaultValue = "0.50") double threshold) {

        var strictRagAdvisor = RetrievalAugmentationAdvisor.builder()
                .documentRetriever(VectorStoreDocumentRetriever.builder()
                        .vectorStore(vectorStore)
                        .similarityThreshold(threshold)
                        .topK(3)
                        .build())
                .queryAugmenter(ContextualQueryAugmenter.builder()
                        .allowEmptyContext(false)
                        .build())
                .build();

        return chatClient.prompt()
                .options(structuredOptions())
                .advisors(strictRagAdvisor)
                .system("""
                    あなたはSpring AIの学習アシスタントです。
                    提供された参考情報だけを根拠に回答してください。
                    参考情報にない内容は、推測せず「参考情報にはありません」と答えてください。
                    """)
                .user(message)
                .call()
                .content();
    }

    @GetMapping("/ai/rag/load-metadata")
    public String loadRagDocumentsWithMetadata() {

        List<Document> documents = List.of(
                new Document("""
                    Spring AIは、Spring BootアプリケーションからLLM、Embedding、VectorStore、Tool Callingなどを扱うためのフレームワークです。
                    ChatClientを使うことで、AIモデルとの会話を簡潔に実装できます。
                    """,
                        Map.of("category", "basic")),
                new Document("""
                    Tool Callingは、AIモデルが必要に応じてJavaメソッドを呼び出す仕組みです。
                    外部API、DB検索、計算処理などをAIから利用できます。
                    ToolContextを使うと、AIに直接見せない内部情報をToolへ渡せます。
                    """,
                        Map.of("category", "tool")),
                new Document("""
                    RAGはRetrieval Augmented Generationの略です。
                    ユーザーの質問に関連する文書をVectorStoreから検索し、その検索結果をプロンプトに追加して回答します。
                    Embeddingモデルは文書や質問をベクトル化するために使います。
                    """,
                        Map.of("category", "rag")),
                new Document("""
                    Structured Outputは、AIの回答をStringではなくJavaのrecordやDTOとして受け取る仕組みです。
                    entity(Class) や entity(ParameterizedTypeReference) を使います。
                    """,
                        Map.of("category", "structured"))
        );

        vectorStore.add(documents);

        return "metadata付きRAG documents loaded: " + documents.size();
    }

    @GetMapping("/ai/rag/ask-category")
    public String askRagByCategory(
            @RequestParam(defaultValue = "rag") String category,
            @RequestParam(defaultValue = "RAGとは何ですか？") String message) {

        var searchRequest = SearchRequest.builder()
                .similarityThreshold(0.50)
                .topK(3)
                .filterExpression("category == '" + category + "'")
                .build();

        var advisor = QuestionAnswerAdvisor.builder(vectorStore)
                .searchRequest(searchRequest)
                .build();

        return chatClient.prompt()
                .options(structuredOptions())
                .advisors(advisor)
                .system("""
                    あなたはSpring AIの学習アシスタントです。
                    提供された参考情報だけを根拠に、日本語で簡潔に回答してください。
                    参考情報にない内容は「参考情報にはありません」と答えてください。
                    """)
                .user(message)
                .call()
                .content();
    }

    @GetMapping("/ai/rag/load-md")
    public String loadMarkdownRagDocuments() {

        var reader = new MarkdownDocumentReader("classpath:/docs/spring-ai-notes.md");

        List<Document> documents = reader.get();

        List<Document> splitDocuments = new TokenTextSplitter()
                .apply(documents);

        vectorStore.add(splitDocuments);

        return "Markdown RAG documents loaded: original="
                + documents.size()
                + ", split="
                + splitDocuments.size();
    }

    @GetMapping("/ai/rag/ask-md")
    public String askMarkdownRag(
            @RequestParam(defaultValue = "ToolContextとは何ですか？") String message) {

        var searchRequest = SearchRequest.builder()
                .query(message)
                .topK(5)
                .similarityThreshold(0.0)
                .build();

        var advisor = QuestionAnswerAdvisor.builder(vectorStore)
                .searchRequest(searchRequest)
                .build();

        return chatClient.prompt()
                .options(structuredOptions())
                .advisors(advisor)
                .system("""
                    あなたはSpring AIの学習アシスタントです。
                    提供されたMarkdownの参考情報だけを根拠に、日本語で簡潔に回答してください。
                    参考情報にない内容は、推測せず「参考情報にはありません」と答えてください。
                    """)
                .user(message)
                .call()
                .content();
    }

    @GetMapping("/ai/rag/search-md")
    public List<Document> searchMarkdownRag(
            @RequestParam(defaultValue = "ToolContextとは何ですか？") String message) {

        return vectorStore.similaritySearch(
                SearchRequest.builder()
                        .query(message)
                        .topK(10)
                        .similarityThreshold(0.0)
                        .build());
    }

    @GetMapping("/ai/rag/search-md-simple")
    public List<RagSearchResult> searchMarkdownRagSimple(
            @RequestParam(defaultValue = "ToolContextとは何ですか？") String message) {

        List<Document> documents = vectorStore.similaritySearch(
                SearchRequest.builder()
                        .query(message)
                        .topK(5)
                        .similarityThreshold(0.0)
                        .build());

        return documents.stream()
                .map(doc -> new RagSearchResult(
                String.valueOf(doc.getMetadata().getOrDefault("title", "")),
                doc.getScore(),
                doc.getMetadata().get("distance"),
                doc.getText()))
                .toList();
    }

    @GetMapping("/ai/rag/load-md-utf8")
    public String loadMarkdownRagDocumentsUtf8() throws IOException {

        var resource = new ClassPathResource("docs/spring-ai-notes.md");

        String markdown;
        try (var inputStream = resource.getInputStream()) {
            markdown = new String(inputStream.readAllBytes(), StandardCharsets.UTF_8);
        }

        List<Document> documents = List.of(
                new Document(
                        markdown,
                        Map.of("source", "spring-ai-notes-md-utf8"))
        );

        List<Document> splitDocuments = new TokenTextSplitter()
                .apply(documents);

        vectorStore.add(splitDocuments);

        return "UTF-8 Markdown RAG documents loaded: original="
                + documents.size()
                + ", split="
                + splitDocuments.size();
    }

    @GetMapping("/ai/rag/search-md-utf8-simple")
    public List<RagSearchResult> searchMarkdownRagUtf8Simple(
            @RequestParam(defaultValue = "ToolContextとは何ですか？") String message) {

        List<Document> documents = vectorStore.similaritySearch(
                SearchRequest.builder()
                        .query(message)
                        .topK(5)
                        .similarityThreshold(0.0)
                        .filterExpression("source == 'spring-ai-notes-md-utf8'")
                        .build());

        return documents.stream()
                .map(doc -> new RagSearchResult(
                String.valueOf(doc.getMetadata().getOrDefault("title", "")),
                doc.getScore(),
                doc.getMetadata().get("distance"),
                doc.getText()))
                .toList();
    }

    @GetMapping("/ai/rag/ask-md-utf8")
    public String askMarkdownRagUtf8(
            @RequestParam(defaultValue = "ToolContextとは何ですか？") String message) {

        var searchRequest = SearchRequest.builder()
                .query(message)
                .topK(5)
                .similarityThreshold(0.0)
                .filterExpression("source == 'spring-ai-notes-md-utf8'")
                .build();

        var advisor = QuestionAnswerAdvisor.builder(vectorStore)
                .searchRequest(searchRequest)
                .build();

        return chatClient.prompt()
                .options(structuredOptions())
                .advisors(advisor)
                .system("""
                    あなたはSpring AIの学習アシスタントです。
                    提供されたMarkdownの参考情報だけを根拠に、日本語で簡潔に回答してください。
                    参考情報にない内容は、推測せず「参考情報にはありません」と答えてください。
                    """)
                .user(message)
                .call()
                .content();
    }
}
