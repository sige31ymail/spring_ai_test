# Testing

このメモは、このプロジェクトで追加したテストの目的、構成、実行方法を整理したものです。

---

## 目的

Spring AI / RAG アプリのうち、AIモデルやOllama本体を呼び出さずに確認できる範囲をテストする。

特に、以下を確認する。

- RAG APIの入力値バリデーション
- JSON不正リクエスト時のエラーレスポンス
- VectorStore未読み込み時のエラーレスポンス
- `GlobalExceptionHandler` による共通エラーレスポンス
- `ApiErrorResponse` の `code` / `message` が期待通り返ること

---

## テスト対象

現在追加しているテストファイル。

```text
src/test/java/com/example/spring_ai_test/RagControllerErrorHandlingTest.java
```

このテストでは、`@WebMvcTest` と `MockMvc` を使って、Controller層とエラーハンドリングを確認する。

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

このプロジェクトでは、RAG回答生成時にOllamaやVectorStoreが関係するが、エラーハンドリングのテストではAIモデル本体を呼ぶ必要はない。

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

---

## 現在のテスト内容

### topK不正

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

### threshold不正

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

### fileName不正

許可されていない `fileName=unknown.md` の場合、400 Bad Requestになることを確認する。

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

### JSON不正

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

### VectorStore未読み込み

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
| Controllerテスト | 入力値、HTTPステータス、エラーJSON | 不要 |
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

### message空のPOSTテスト

現在は手動確認中心のため、POSTで `message=""` のケースも自動テストに追加するとよい。

確認したいレスポンス。

```json
{
  "code": "INVALID_RAG_REQUEST",
  "message": "messageは必須です。"
}
```

---

### fileName不正のPOSTテスト

GETだけでなく、POST `/ai/rag/ask-md-file` でも `fileName` 不正を確認するとよい。

確認したいレスポンス。

```json
{
  "code": "INVALID_RAG_REQUEST",
  "message": "fileNameは spring-ai-notes.md, spring-ai-tools.md, spring-ai-rag.md のいずれかを指定してください。"
}
```

---

### AIサービス接続失敗のテスト

`RagService` が `ResourceAccessException` を投げた場合、503になることを確認する。

確認したいレスポンス。

```json
{
  "code": "AI_SERVICE_UNAVAILABLE",
  "message": "AIサービスに接続できません。Ollamaが起動しているか確認してください。"
}
```

---

## まとめ

現在のテストでは、AIモデルを呼ばずにRAG APIのエラーハンドリングを確認している。

```text
HTTPリクエスト
  ↓
RagController
  ↓
GlobalExceptionHandler
  ↓
ApiErrorResponse
```

この構成により、OllamaやVectorStoreに依存せず、APIとしてのエラー応答を安定して検証できる。
