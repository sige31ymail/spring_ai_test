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

---

## 現在対応しているエラー

| 例外 | HTTPステータス | code | message |
|---|---:|---|---|
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

### GlobalExceptionHandler

`@RestControllerAdvice` を使って、Controller内で発生した例外を共通的に処理する。

```java
package com.example.spring_ai_test;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.client.ResourceAccessException;

@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger logger = LoggerFactory.getLogger(GlobalExceptionHandler.class);

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

そのため、Ollama停止時などは画面に次のように表示される。

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

### 1. Ollama起動中の正常系

```powershell
curl.exe "http://localhost:8080/ai/rag/ask-md-dir?message=ToolContext&topK=10&threshold=0.0"
```

### 2. Ollama停止確認

```powershell
curl.exe http://localhost:11434/api/tags
```

接続できなければOllama停止状態。

### 3. Ollama停止中にRAG回答を呼ぶ

```powershell
curl.exe "http://localhost:8080/ai/rag/ask-md-dir?message=ToolContext&topK=10&threshold=0.0"
```

### 4. エラーレスポンスを確認

期待値。

```json
{
  "code": "AI_SERVICE_UNAVAILABLE",
  "message": "AIサービスに接続できません。Ollamaが起動しているか確認してください。"
}
```

### 5. メトリクスを確認

```powershell
curl.exe http://localhost:8080/actuator/metrics/gen_ai.client.operation
curl.exe http://localhost:8080/actuator/metrics/spring.ai.chat.client
curl.exe http://localhost:8080/actuator/metrics/spring.ai.advisor
```

---

## 今後の改善案

現在は、`ResourceAccessException` とその他の `Exception` を扱っている。

今後は、以下のようにエラー種別を増やすとよい。

| 追加候補 | 目的 |
|---|---|
| JSON不正 | リクエストBodyの形式誤りを400で返す |
| 入力値不正 | `topK` や `threshold` の不正値を400で返す |
| モデル名不正 | Ollamaにモデルがない場合のエラーを分かりやすく返す |
| VectorStore未読み込み | Markdown未読み込み時に専用エラーを返す |
| タイムアウト | AI応答が遅すぎる場合に504相当で返す |

---

## まとめ

エラーハンドリングを追加することで、以下が分かりやすくなる。

```text
サーバ側
  → 例外を共通的にJSONへ変換

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
