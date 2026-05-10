# Error Handling

このメモは、このプロジェクトで追加した Spring AI / RAG 向けのエラーハンドリングを整理したものです。

---

## 目的

Spring AI / RAG 処理で例外が発生したときに、Spring Boot標準の500レスポンスではなく、利用者に分かりやすいJSONを返す。

これにより、以下が分かりやすくなる。

- API利用者に原因を伝えやすい
- RAGチャット画面でエラー内容を表示しやすい
- Observabilityの `error` タグと突き合わせやすい
- Ollama停止時などの障害原因を切り分けやすい
- JSON不正や入力値不正を400 Bad Requestとして明確に返せる
- VectorStore未読み込み状態を専用エラーとして返せる

---

## 現在対応しているエラー

| 例外 | HTTPステータス | code | message |
|---|---:|---|---|
| `HttpMessageNotReadableException` | 400 | `INVALID_REQUEST_BODY` | リクエストBodyのJSON形式が不正です。 |
| `InvalidRagRequestException` | 400 | `INVALID_RAG_REQUEST` | 入力値に応じたエラーメッセージ |
| `VectorStoreNotLoadedException` | 409 | `VECTOR_STORE_NOT_LOADED` | VectorStoreが読み込まれていません。Markdownを読み込むか、保存済みVectorStoreを読み込んでください。 |
| `ResourceAccessException` | 503 | `AI_SERVICE_UNAVAILABLE` | AIサービスに接続できません。Ollamaが起動しているか確認してください。 |
| `Exception` | 500 | `INTERNAL_SERVER_ERROR` | 予期しないエラーが発生しました。ログを確認してください。 |

---

## サーバ側の構成

### ApiErrorResponse

APIエラー時に返す共通レスポンス。

```java
package com.example.spring_ai_test;

public record ApiErrorResponse(
        String code,
        String message
) {
}
```

レスポンス例。

```json
{
  "code": "AI_SERVICE_UNAVAILABLE",
  "message": "AIサービスに接続できません。Ollamaが起動しているか確認してください。"
}
```

---

### InvalidRagRequestException

RAGリクエストの入力値が不正な場合に投げる例外。

```java
package com.example.spring_ai_test;

public class InvalidRagRequestException extends RuntimeException {

    public InvalidRagRequestException(String message) {
        super(message);
    }
}
```

主に以下の場合に使う。

| 入力値 | 条件 |
|---|---|
| `message` | 空文字、null、空白のみは不可 |
| `topK` | 1以上20以下 |
| `threshold` | 0.0以上1.0以下 |
| `fileName` | `spring-ai-notes.md`, `spring-ai-tools.md`, `spring-ai-rag.md` のいずれか |

---

### VectorStoreNotLoadedException

VectorStoreが未読み込みの状態で、検索やRAG回答を実行しようとした場合に投げる例外。

```java
package com.example.spring_ai_test;

public class VectorStoreNotLoadedException extends RuntimeException {

    public VectorStoreNotLoadedException(String message) {
        super(message);
    }
}
```

この例外は、リクエスト形式は正しいが、現在のアプリ状態では処理できないことを表す。

そのため、HTTPステータスは `409 Conflict` とする。

---

### GlobalExceptionHandler

`@RestControllerAdvice` を使って、Controller内で発生した例外を共通的に処理する。

```java
package com.example.spring_ai_test;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.client.ResourceAccessException;

@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger logger = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    @ExceptionHandler(InvalidRagRequestException.class)
    public ResponseEntity<ApiErrorResponse> handleInvalidRagRequestException(
            InvalidRagRequestException exception) {

        logger.warn("Invalid RAG request: {}", exception.getMessage());

        return ResponseEntity
                .status(HttpStatus.BAD_REQUEST)
                .body(new ApiErrorResponse(
                        "INVALID_RAG_REQUEST",
                        exception.getMessage()));
    }

    @ExceptionHandler(VectorStoreNotLoadedException.class)
    public ResponseEntity<ApiErrorResponse> handleVectorStoreNotLoadedException(
            VectorStoreNotLoadedException exception) {

        logger.warn("VectorStore not loaded: {}", exception.getMessage());

        return ResponseEntity
                .status(HttpStatus.CONFLICT)
                .body(new ApiErrorResponse(
                        "VECTOR_STORE_NOT_LOADED",
                        exception.getMessage()));
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<ApiErrorResponse> handleHttpMessageNotReadableException(
            HttpMessageNotReadableException exception) {

        logger.warn("Invalid request body", exception);

        return ResponseEntity
                .status(HttpStatus.BAD_REQUEST)
                .body(new ApiErrorResponse(
                        "INVALID_REQUEST_BODY",
                        "リクエストBodyのJSON形式が不正です。"));
    }

    @ExceptionHandler(ResourceAccessException.class)
    public ResponseEntity<ApiErrorResponse> handleResourceAccessException(
            ResourceAccessException exception) {

        logger.warn("AI service access failed", exception);

        return ResponseEntity
                .status(HttpStatus.SERVICE_UNAVAILABLE)
                .body(new ApiErrorResponse(
                        "AI_SERVICE_UNAVAILABLE",
                        "AIサービスに接続できません。Ollamaが起動しているか確認してください。"));
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiErrorResponse> handleException(Exception exception) {

        logger.error("Unexpected API error", exception);

        return ResponseEntity
                .status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(new ApiErrorResponse(
                        "INTERNAL_SERVER_ERROR",
                        "予期しないエラーが発生しました。ログを確認してください。"));
    }
}
```

---

## JSON不正の確認

POSTのリクエストBodyが壊れている場合、Controllerの `@RequestBody` に変換する前に失敗する。

この場合は `HttpMessageNotReadableException` を `400 Bad Request` として返す。

```powershell
curl.exe -i `
  -X POST "http://localhost:8080/ai/rag/ask-md-dir" `
  -H "Content-Type: application/json; charset=utf-8" `
  -d "{ ""message"": ""ToolContext"", ""topK"": 10,"
```

レスポンス。

```json
{
  "code": "INVALID_REQUEST_BODY",
  "message": "リクエストBodyのJSON形式が不正です。"
}
```

---

## 入力値バリデーション

`RagController` では、RAGリクエストの入力値をチェックしている。

```java
private void validateRagRequest(String message, int topK, double threshold) {

    if (message == null || message.isBlank()) {
        throw new InvalidRagRequestException("messageは必須です。");
    }

    if (topK < 1 || topK > 20) {
        throw new InvalidRagRequestException("topKは1以上20以下で指定してください。");
    }

    if (threshold < 0.0 || threshold > 1.0) {
        throw new InvalidRagRequestException("thresholdは0.0以上1.0以下で指定してください。");
    }
}
```

ファイル指定RAGでは、`fileName` も許可リストでチェックする。

```java
private void validateRagFileName(String fileName) {

    if (fileName == null || fileName.isBlank()) {
        throw new InvalidRagRequestException("fileNameは必須です。");
    }

    if (!ALLOWED_FILE_NAMES.contains(fileName)) {
        throw new InvalidRagRequestException(
                "fileNameは spring-ai-notes.md, spring-ai-tools.md, spring-ai-rag.md のいずれかを指定してください。");
    }
}
```

許可するファイル名。

```java
private static final Set<String> ALLOWED_FILE_NAMES = Set.of(
        "spring-ai-notes.md",
        "spring-ai-tools.md",
        "spring-ai-rag.md");
```

---

## 入力値不正の確認

### topK不正

```powershell
curl.exe "http://localhost:8080/ai/rag/ask-md-dir?message=ToolContext&topK=0&threshold=0.0"
```

レスポンス。

```json
{
  "code": "INVALID_RAG_REQUEST",
  "message": "topKは1以上20以下で指定してください。"
}
```

### threshold不正

```powershell
curl.exe "http://localhost:8080/ai/rag/ask-md-dir?message=ToolContext&topK=10&threshold=1.5"
```

レスポンス。

```json
{
  "code": "INVALID_RAG_REQUEST",
  "message": "thresholdは0.0以上1.0以下で指定してください。"
}
```

### message空

```powershell
$body = @{
  message = ""
  topK = 10
  threshold = 0.0
} | ConvertTo-Json

curl.exe -i `
  -X POST "http://localhost:8080/ai/rag/ask-md-dir" `
  -H "Content-Type: application/json; charset=utf-8" `
  -d $body
```

レスポンス。

```json
{
  "code": "INVALID_RAG_REQUEST",
  "message": "messageは必須です。"
}
```

### fileName不正

```powershell
curl.exe "http://localhost:8080/ai/rag/search-md-file-simple?fileName=unknown.md&message=ToolContext&topK=5&threshold=0.0"
```

レスポンス。

```json
{
  "code": "INVALID_RAG_REQUEST",
  "message": "fileNameは spring-ai-notes.md, spring-ai-tools.md, spring-ai-rag.md のいずれかを指定してください。"
}
```

POSTで確認する場合。

```powershell
$body = @{
  fileName = "unknown.md"
  message = "ToolContext"
  topK = 5
  threshold = 0.0
} | ConvertTo-Json

curl.exe -i `
  -X POST "http://localhost:8080/ai/rag/ask-md-file" `
  -H "Content-Type: application/json; charset=utf-8" `
  -d $body
```

`Invoke-RestMethod` はHTTP 400を例外扱いにするため、レスポンス本文を見たい場合は `curl.exe -i` を使うと分かりやすい。

---

## VectorStore未読み込みの確認

アプリを再起動し、まだ「Markdownを読み込む」または「保存済みVectorStoreを読み込む」を実行していない状態で検索する。

```powershell
curl.exe -i "http://localhost:8080/ai/rag/search-md-dir-simple?message=ToolContext&topK=10&threshold=0.0"
```

レスポンス。

```json
{
  "code": "VECTOR_STORE_NOT_LOADED",
  "message": "VectorStoreが読み込まれていません。Markdownを読み込むか、保存済みVectorStoreを読み込んでください。"
}
```

HTTPステータスは `409 Conflict`。

ファイル指定側でも同様。

```powershell
curl.exe -i "http://localhost:8080/ai/rag/search-md-file-simple?fileName=spring-ai-tools.md&message=ToolContext&topK=5&threshold=0.0"
```

その後、Markdownを読み込む。

```powershell
curl.exe "http://localhost:8080/ai/rag/load-md-dir"
```

または保存済みVectorStoreを読み込む。

```powershell
curl.exe "http://localhost:8080/ai/rag/load-store"
```

読み込み後に同じ検索を再実行し、通常通り検索結果が返ればOK。

---

## ResourceAccessExceptionを個別に扱う理由

Spring AIでOllamaに接続できない場合、`ResourceAccessException` が発生することがある。

例。

- Ollamaが起動していない
- `localhost:11434` に接続できない
- Ollama APIが応答しない
- embedding または chat 呼び出し時に接続失敗する

この場合、単なる `500 Internal Server Error` ではなく、AIサービス側の問題として `503 Service Unavailable` を返す。

---

## Ollama停止時の確認

Ollamaが停止している状態でRAG回答を呼び出す。

```powershell
curl.exe "http://localhost:8080/ai/rag/ask-md-dir?message=ToolContext&topK=10&threshold=0.0"
```

期待するレスポンス。

```json
{
  "code": "AI_SERVICE_UNAVAILABLE",
  "message": "AIサービスに接続できません。Ollamaが起動しているか確認してください。"
}
```

HTTPステータスは `503 Service Unavailable`。

---

## 日本語パラメータを使う場合の注意

GETクエリに日本語を直接入れると、環境によっては `HTTP 400 Bad Request` になることがある。

そのため、確認時はまずASCIIだけで試す。

```powershell
curl.exe "http://localhost:8080/ai/rag/ask-md-dir?message=ToolContext&topK=10&threshold=0.0"
```

日本語で確認したい場合はPOSTを使う。

```powershell
$body = @{
  message = "ToolContextとは何ですか？"
  topK = 10
  threshold = 0.0
} | ConvertTo-Json

Invoke-RestMethod `
  -Uri "http://localhost:8080/ai/rag/ask-md-dir" `
  -Method Post `
  -ContentType "application/json; charset=utf-8" `
  -Body $body
```

---

## RAGチャット画面での表示

`rag-chat.html` では、APIエラー時にレスポンスJSONを読み取る。

```javascript
const errorData = await response.json().catch(() => null);

if (errorData && errorData.message) {
    throw new Error(`${errorData.message} (${errorData.code || response.status})`);
}
```

そのため、JSON不正、入力値不正、VectorStore未読み込み、Ollama停止時は画面に次のように表示される。

```text
エラーが発生しました: リクエストBodyのJSON形式が不正です。 (INVALID_REQUEST_BODY)
```

```text
エラーが発生しました: topKは1以上20以下で指定してください。 (INVALID_RAG_REQUEST)
```

```text
エラーが発生しました: VectorStoreが読み込まれていません。Markdownを読み込むか、保存済みVectorStoreを読み込んでください。 (VECTOR_STORE_NOT_LOADED)
```

```text
エラーが発生しました: AIサービスに接続できません。Ollamaが起動しているか確認してください。 (AI_SERVICE_UNAVAILABLE)
```

---

## RAGチャット画面で対象にしている処理

以下の3か所で、APIエラーJSONを読み取る。

| 関数 | 用途 |
|---|---|
| `sendMessage()` | RAG回答生成 |
| `searchOnly()` | RAG検索のみ |
| `callStoreEndpoint()` | VectorStore読み込み・保存・Markdown読み込み |

これにより、通常のチャットだけでなく、検索やVectorStore操作でもエラー内容を表示できる。

---

## Observabilityとの関係

Ollama停止時は、Actuator metrics の `gen_ai.client.operation` に以下のような `error` タグが出ることがある。

```text
ResourceAccessException
```

確認コマンド。

```powershell
curl.exe http://localhost:8080/actuator/metrics/gen_ai.client.operation
```

例。

```json
{
  "name": "gen_ai.client.operation",
  "availableTags": [
    {
      "tag": "error",
      "values": [
        "ResourceAccessException",
        "none"
      ]
    }
  ]
}
```

JSON不正、入力値不正、VectorStore未読み込みはAIモデル呼び出し前に止まるため、通常は `gen_ai.client.operation` には記録されない。

---

## ChatClient / Advisor側にエラーが出ない場合

RAG処理では、ChatClientに到達する前に失敗することがある。

流れ。

```text
RagController
  ↓
RagService.askAll()
  ↓
searchAll()
  ↓
vectorStore.similaritySearch()
  ↓
EmbeddingModel呼び出し
  ↓
answerFromSources()
  ↓
ChatClient呼び出し
```

`vectorStore.similaritySearch()` の中でEmbedding呼び出しに失敗した場合、`answerFromSources()` の `ChatClient` 呼び出しまで進まない。

そのため、以下のように見えることがある。

| メトリクス | 状態 |
|---|---|
| `gen_ai.client.operation` | `ResourceAccessException` が出る |
| `spring.ai.chat.client` | `error:none` のまま |
| `spring.ai.advisor` | `error:none` のまま |

これは異常ではなく、エラーが発生した層がEmbedding側だったという意味。

---

## 確認手順まとめ

### 1. JSON不正を確認する

```powershell
curl.exe -i `
  -X POST "http://localhost:8080/ai/rag/ask-md-dir" `
  -H "Content-Type: application/json; charset=utf-8" `
  -d "{ ""message"": ""ToolContext"", ""topK"": 10,"
```

### 2. 入力値不正を確認する

```powershell
curl.exe "http://localhost:8080/ai/rag/ask-md-dir?message=ToolContext&topK=0&threshold=0.0"
curl.exe "http://localhost:8080/ai/rag/ask-md-dir?message=ToolContext&topK=10&threshold=1.5"
curl.exe "http://localhost:8080/ai/rag/search-md-file-simple?fileName=unknown.md&message=ToolContext&topK=5&threshold=0.0"
```

### 3. VectorStore未読み込みを確認する

アプリ再起動直後、Markdownや保存済みVectorStoreを読み込む前に実行する。

```powershell
curl.exe -i "http://localhost:8080/ai/rag/search-md-dir-simple?message=ToolContext&topK=10&threshold=0.0"
```

### 4. MarkdownまたはVectorStoreを読み込む

```powershell
curl.exe "http://localhost:8080/ai/rag/load-md-dir"
```

または、

```powershell
curl.exe "http://localhost:8080/ai/rag/load-store"
```

### 5. Ollama起動中の正常系

```powershell
curl.exe "http://localhost:8080/ai/rag/ask-md-dir?message=ToolContext&topK=10&threshold=0.0"
```

### 6. Ollama停止確認

```powershell
curl.exe http://localhost:11434/api/tags
```

接続できなければOllama停止状態。

### 7. Ollama停止中にRAG回答を呼ぶ

```powershell
curl.exe "http://localhost:8080/ai/rag/ask-md-dir?message=ToolContext&topK=10&threshold=0.0"
```

### 8. メトリクスを確認

```powershell
curl.exe http://localhost:8080/actuator/metrics/gen_ai.client.operation
curl.exe http://localhost:8080/actuator/metrics/spring.ai.chat.client
curl.exe http://localhost:8080/actuator/metrics/spring.ai.advisor
```

---

## 今後の改善案

現在は、`HttpMessageNotReadableException`、`InvalidRagRequestException`、`VectorStoreNotLoadedException`、`ResourceAccessException`、その他の `Exception` を扱っている。

今後は、以下のようにエラー種別を増やすとよい。

| 追加候補 | 目的 |
|---|---|
| モデル名不正 | Ollamaにモデルがない場合のエラーを分かりやすく返す |
| タイムアウト | AI応答が遅すぎる場合に504相当で返す |

---

## まとめ

エラーハンドリングを追加することで、以下が分かりやすくなる。

```text
サーバ側
  → 例外を共通的にJSONへ変換
  → JSON不正は400として返す
  → 入力値不正は400として返す
  → VectorStore未読み込みは409として返す
  → AIサービス接続失敗は503として返す

UI側
  → APIエラーのmessage/codeを画面表示

Observability側
  → errorタグと突き合わせて原因を確認
```

Spring AI / RAGでは、Embedding、VectorStore、ChatClient、Advisorなど複数の層がある。

そのため、エラー時は以下をセットで見ると切り分けやすい。

```text
APIレスポンス
Spring Bootログ
RAG検索ログ
Actuator metrics
```
