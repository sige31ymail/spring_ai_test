# Testing

このメモは、このプロジェクトで追加したテストの目的、構成、実行方法を整理したものです。

---

## 目的

Spring AI / RAG アプリのうち、AIモデルやOllama本体を呼び出さずに確認できる範囲をテストする。

特に、以下を確認する。

- RAG APIの入力値バリデーション
- JSON不正リクエスト時のエラーレスポンス
- VectorStore未読み込み時のエラーレスポンス
- AIサービス接続失敗時のエラーレスポンス
- 正常時のControllerレスポンス
- `GlobalExceptionHandler` による共通エラーレスポンス
- `ApiErrorResponse` の `code` / `message` が期待通り返ること

---

## テスト対象

現在追加している主なテストファイル。

```text
src/test/java/com/example/spring_ai_test/RagControllerErrorHandlingTest.java
src/test/java/com/example/spring_ai_test/RagControllerTest.java
```

`RagControllerErrorHandlingTest` では、エラー系レスポンスを確認する。

`RagControllerTest` では、正常系レスポンスを確認する。

どちらも `@WebMvcTest` と `MockMvc` を使って、Controller層とエラーハンドリングを確認する。

```java
@WebMvcTest(RagController.class)
@Import(GlobalExceptionHandler.class)
class RagControllerErrorHandlingTest {
    // ...
}
```

---

## なぜ @WebMvcTest を使うのか

`@WebMvcTest` は、Spring MVCのController周辺だけを軽く起動するテスト。

このプロジェクトでは、RAG回答生成時にOllamaやVectorStoreが関係するが、ControllerテストではAIモデル本体を呼ぶ必要はない。

そのため、`RagService` はモックにする。

```java
@MockitoBean
private RagService ragService;
```

これにより、以下のメリットがある。

- Ollamaが起動していなくてもテストできる
- EmbeddingModelを呼ばない
- ChatModelを呼ばない
- VectorStore本体に依存しない
- ControllerとExceptionHandlerの動作だけを確認できる

---

## 使用している主なテスト機能

| 機能 | 役割 |
|---|---|
| `MockMvc` | HTTPリクエストを擬似的に実行する |
| `@WebMvcTest` | Controller周辺だけを起動する |
| `@Import(GlobalExceptionHandler.class)` | 共通エラーハンドラをテスト対象に含める |
| `@MockitoBean` | `RagService` をモック化する |
| `jsonPath` | JSONレスポンスの中身を検証する |
| `status()` | HTTPステータスを検証する |
| `content()` | 文字列レスポンスを検証する |
| `when(...).thenReturn(...)` | モックServiceの戻り値を設定する |
| `doThrow(...)` | モックServiceから例外を投げる |

---

# エラー系Controllerテスト

対象ファイル。

```text
src/test/java/com/example/spring_ai_test/RagControllerErrorHandlingTest.java
```

現在の `RagControllerErrorHandlingTest` では、以下を確認している。

```text
- topK不正 GET
- threshold不正 GET
- fileName不正 GET
- JSON不正 POST
- VectorStore未読み込み
- message空 POST
- fileName不正 POST
- AIサービス接続失敗 503
```

---

## topK不正

`topK=0` の場合、400 Bad Requestになることを確認する。

```java
mockMvc.perform(get("/ai/rag/ask-md-dir")
                .param("message", "ToolContext")
                .param("topK", "0")
                .param("threshold", "0.0"))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.code").value("INVALID_RAG_REQUEST"))
        .andExpect(jsonPath("$.message").value("topKは1以上20以下で指定してください。"));
```

期待するレスポンス。

```json
{
  "code": "INVALID_RAG_REQUEST",
  "message": "topKは1以上20以下で指定してください。"
}
```

---

## threshold不正

`threshold=1.5` の場合、400 Bad Requestになることを確認する。

```java
mockMvc.perform(get("/ai/rag/ask-md-dir")
                .param("message", "ToolContext")
                .param("topK", "10")
                .param("threshold", "1.5"))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.code").value("INVALID_RAG_REQUEST"))
        .andExpect(jsonPath("$.message").value("thresholdは0.0以上1.0以下で指定してください。"));
```

期待するレスポンス。

```json
{
  "code": "INVALID_RAG_REQUEST",
  "message": "thresholdは0.0以上1.0以下で指定してください。"
}
```

---

## fileName不正 GET

GET `/ai/rag/ask-md-file` で、許可されていない `fileName=unknown.md` の場合、400 Bad Requestになることを確認する。

```java
mockMvc.perform(get("/ai/rag/ask-md-file")
                .param("fileName", "unknown.md")
                .param("message", "ToolContext")
                .param("topK", "5")
                .param("threshold", "0.0"))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.code").value("INVALID_RAG_REQUEST"))
        .andExpect(jsonPath("$.message")
                .value("fileNameは spring-ai-notes.md, spring-ai-tools.md, spring-ai-rag.md のいずれかを指定してください。"));
```

期待するレスポンス。

```json
{
  "code": "INVALID_RAG_REQUEST",
  "message": "fileNameは spring-ai-notes.md, spring-ai-tools.md, spring-ai-rag.md のいずれかを指定してください。"
}
```

---

## JSON不正 POST

壊れたJSONをPOSTした場合、400 Bad Requestになることを確認する。

```java
String invalidJson = "{ \"message\": \"ToolContext\", \"topK\": 10,";

mockMvc.perform(post("/ai/rag/ask-md-dir")
                .contentType(MediaType.APPLICATION_JSON)
                .content(invalidJson))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.code").value("INVALID_REQUEST_BODY"))
        .andExpect(jsonPath("$.message").value("リクエストBodyのJSON形式が不正です。"));
```

期待するレスポンス。

```json
{
  "code": "INVALID_REQUEST_BODY",
  "message": "リクエストBodyのJSON形式が不正です。"
}
```

---

## VectorStore未読み込み

`RagService` が `VectorStoreNotLoadedException` を投げた場合、409 Conflictになることを確認する。

```java
doThrow(new VectorStoreNotLoadedException(
        "VectorStoreが読み込まれていません。Markdownを読み込むか、保存済みVectorStoreを読み込んでください。"))
        .when(ragService)
        .searchAll(anyString(), anyInt(), anyDouble());

mockMvc.perform(get("/ai/rag/search-md-dir-simple")
                .param("message", "ToolContext")
                .param("topK", "10")
                .param("threshold", "0.0"))
        .andExpect(status().isConflict())
        .andExpect(jsonPath("$.code").value("VECTOR_STORE_NOT_LOADED"))
        .andExpect(jsonPath("$.message")
                .value("VectorStoreが読み込まれていません。Markdownを読み込むか、保存済みVectorStoreを読み込んでください。"));
```

期待するレスポンス。

```json
{
  "code": "VECTOR_STORE_NOT_LOADED",
  "message": "VectorStoreが読み込まれていません。Markdownを読み込むか、保存済みVectorStoreを読み込んでください。"
}
```

---

## message空 POST

POST `/ai/rag/ask-md-dir` で `message=""` の場合、400 Bad Requestになることを確認する。

```java
String requestBody = """
        {
          "message": "",
          "topK": 10,
          "threshold": 0.0
        }
        """;

mockMvc.perform(post("/ai/rag/ask-md-dir")
                .contentType(MediaType.APPLICATION_JSON)
                .content(requestBody))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.code").value("INVALID_RAG_REQUEST"))
        .andExpect(jsonPath("$.message").value("messageは必須です。"));
```

期待するレスポンス。

```json
{
  "code": "INVALID_RAG_REQUEST",
  "message": "messageは必須です。"
}
```

---

## fileName不正 POST

POST `/ai/rag/ask-md-file` で `fileName=unknown.md` の場合、400 Bad Requestになることを確認する。

```java
String requestBody = """
        {
          "fileName": "unknown.md",
          "message": "ToolContext",
          "topK": 5,
          "threshold": 0.0
        }
        """;

mockMvc.perform(post("/ai/rag/ask-md-file")
                .contentType(MediaType.APPLICATION_JSON)
                .content(requestBody))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.code").value("INVALID_RAG_REQUEST"))
        .andExpect(jsonPath("$.message")
                .value("fileNameは spring-ai-notes.md, spring-ai-tools.md, spring-ai-rag.md のいずれかを指定してください。"));
```

期待するレスポンス。

```json
{
  "code": "INVALID_RAG_REQUEST",
  "message": "fileNameは spring-ai-notes.md, spring-ai-tools.md, spring-ai-rag.md のいずれかを指定してください。"
}
```

---

## AIサービス接続失敗

`RagService` が `ResourceAccessException` を投げた場合、503 Service Unavailableになることを確認する。

```java
doThrow(new ResourceAccessException("Connection refused"))
        .when(ragService)
        .askAll(anyString(), anyInt(), anyDouble());

mockMvc.perform(get("/ai/rag/ask-md-dir")
                .param("message", "ToolContext")
                .param("topK", "10")
                .param("threshold", "0.0"))
        .andExpect(status().isServiceUnavailable())
        .andExpect(jsonPath("$.code").value("AI_SERVICE_UNAVAILABLE"))
        .andExpect(jsonPath("$.message")
                .value("AIサービスに接続できません。Ollamaが起動しているか確認してください。"));
```

期待するレスポンス。

```json
{
  "code": "AI_SERVICE_UNAVAILABLE",
  "message": "AIサービスに接続できません。Ollamaが起動しているか確認してください。"
}
```

---

# 正常系Controllerテスト

対象ファイル。

```text
src/test/java/com/example/spring_ai_test/RagControllerTest.java
```

現在の `RagControllerTest` では、Serviceをモック化して、Controllerが正常レスポンスを返すことを確認している。

確認している内容。

```text
- ask-md-dir が answer + sources を返す
- ask-md-file POST が answer + sources を返す
- search-md-dir-simple が sources配列を返す
- load-md-dir がServiceの文字列メッセージを返す
```

---

## ask-md-dir 正常系

`RagService.askAll()` が `RagFileAnswerWithSources` を返した場合、Controllerが200 OKで `answer` と `sources` を返すことを確認する。

```java
RagFileSearchResult source = new RagFileSearchResult(
        "spring-ai-tools.md",
        "ToolContext",
        0.95,
        0.05,
        "ToolContextはTool Callingで追加情報を渡す仕組みです。");

RagFileAnswerWithSources response = new RagFileAnswerWithSources(
        "ToolContextは、Tool Callingで追加情報を渡す仕組みです。",
        List.of(source));

when(ragService.askAll(anyString(), anyInt(), anyDouble()))
        .thenReturn(response);

mockMvc.perform(get("/ai/rag/ask-md-dir")
                .param("message", "ToolContext")
                .param("topK", "10")
                .param("threshold", "0.0"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.answer")
                .value("ToolContextは、Tool Callingで追加情報を渡す仕組みです。"))
        .andExpect(jsonPath("$.sources[0].fileName").value("spring-ai-tools.md"))
        .andExpect(jsonPath("$.sources[0].title").value("ToolContext"))
        .andExpect(jsonPath("$.sources[0].score").value(0.95))
        .andExpect(jsonPath("$.sources[0].distance").value(0.05))
        .andExpect(jsonPath("$.sources[0].text")
                .value("ToolContextはTool Callingで追加情報を渡す仕組みです。"));
```

---

## ask-md-file POST 正常系

`RagService.askByFile()` が `RagFileAnswerWithSources` を返した場合、Controllerが200 OKで返すことを確認する。

```java
RagFileSearchResult source = new RagFileSearchResult(
        "spring-ai-tools.md",
        "ToolContext",
        0.90,
        0.10,
        "ToolContextはツール実行時の補足情報です。");

RagFileAnswerWithSources response = new RagFileAnswerWithSources(
        "ToolContextはツール実行時の補足情報です。",
        List.of(source));

when(ragService.askByFile(anyString(), anyString(), anyInt(), anyDouble()))
        .thenReturn(response);

String requestBody = """
        {
          "fileName": "spring-ai-tools.md",
          "message": "ToolContext",
          "topK": 5,
          "threshold": 0.0
        }
        """;

mockMvc.perform(post("/ai/rag/ask-md-file")
                .contentType(MediaType.APPLICATION_JSON)
                .content(requestBody))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.answer")
                .value("ToolContextはツール実行時の補足情報です。"))
        .andExpect(jsonPath("$.sources[0].fileName").value("spring-ai-tools.md"))
        .andExpect(jsonPath("$.sources[0].title").value("ToolContext"));
```

---

## search-md-dir-simple 正常系

`RagService.searchAll()` が検索結果リストを返した場合、ControllerがJSON配列として返すことを確認する。

```java
RagFileSearchResult source = new RagFileSearchResult(
        "spring-ai-rag.md",
        "RAG",
        0.88,
        0.12,
        "RAGは検索結果をもとに回答する仕組みです。");

when(ragService.searchAll(anyString(), anyInt(), anyDouble()))
        .thenReturn(List.of(source));

mockMvc.perform(get("/ai/rag/search-md-dir-simple")
                .param("message", "RAG")
                .param("topK", "10")
                .param("threshold", "0.0"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$[0].fileName").value("spring-ai-rag.md"))
        .andExpect(jsonPath("$[0].title").value("RAG"))
        .andExpect(jsonPath("$[0].score").value(0.88))
        .andExpect(jsonPath("$[0].distance").value(0.12))
        .andExpect(jsonPath("$[0].text")
                .value("RAGは検索結果をもとに回答する仕組みです。"));
```

---

## load-md-dir 正常系

`RagService.loadMarkdownDirectory()` の戻り値を、Controllerが文字列として返すことを確認する。

```java
when(ragService.loadMarkdownDirectory())
        .thenReturn("Markdown directory loaded: 10");

mockMvc.perform(get("/ai/rag/load-md-dir"))
        .andExpect(status().isOk())
        .andExpect(content().string("Markdown directory loaded: 10"));
```

---

## テスト実行方法

PowerShellでプロジェクトルートに移動して実行する。

```powershell
.\mvnw.cmd test
```

テストが成功すると、概ね以下のような結果になる。

```text
BUILD SUCCESS
```

---

## このテストで確認していないこと

このテストはControllerとエラーハンドリングを中心にした軽量テスト。

そのため、以下は確認していない。

- Ollamaとの実通信
- EmbeddingModelの動作
- ChatModelの回答生成
- SimpleVectorStoreの実際の検索精度
- Markdown分割処理の詳細
- RAG回答内容の正しさ

これらは、別途Serviceテストや手動確認で扱う。

---

## テスト対象を分ける考え方

このプロジェクトでは、以下のように分けると分かりやすい。

| 種類 | 対象 | Ollama必要 |
|---|---|---|
| Controllerテスト | 入力値、HTTPステータス、エラーJSON、正常レスポンス | 不要 |
| Serviceテスト | Markdown分割、検索結果変換、状態管理 | できれば不要にしたい |
| 手動確認 | 実際のRAG回答、Ollama連携 | 必要 |
| E2E確認 | 画面からRAG回答まで | 必要 |

---

## @MockitoBean について

現在のテストでは、`RagService` を以下でモック化している。

```java
@MockitoBean
private RagService ragService;
```

もし環境によって `@MockitoBean` でコンパイルエラーになる場合は、以下の旧方式に置き換える。

```java
import org.springframework.boot.test.mock.mockito.MockBean;
```

```java
@MockBean
private RagService ragService;
```

ただし、Spring Bootのバージョンによっては `@MockBean` が非推奨になる場合がある。

---

## 今後追加するとよいテスト

### load-store / save-store の正常系Controllerテスト

VectorStore保存・読み込み系のエンドポイントが、Serviceの戻り値を返すことを確認する。

### Service層の単体テスト

Markdown分割、検索結果の重複除去、VectorStore読み込み状態の管理などを、可能な範囲でOllamaなしで確認する。

---

## まとめ

現在のテストでは、AIモデルを呼ばずにRAG APIのエラーハンドリングと正常系Controllerレスポンスを確認している。

```text
HTTPリクエスト
  ↓
RagController
  ↓
RagService mock
  ↓
GlobalExceptionHandler
  ↓
JSON / Text response
```

この構成により、OllamaやVectorStoreに依存せず、APIとしての応答を安定して検証できる。
