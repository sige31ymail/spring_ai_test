# Spring AI Observability

このメモは、このプロジェクトで確認した Spring AI / Actuator の Observability 設定と、確認コマンドを整理したものです。

---

## 目的

Spring AI アプリケーションで、以下を確認できるようにする。

- Actuator が有効になっているか
- Spring AI のメトリクスが取得できるか
- ChatClient の処理時間を確認する
- Advisor の処理時間を確認する
- Ollama の chat / embedding 呼び出しを確認する
- トークン使用量を確認する
- エラー時に `error` タグがどう記録されるか確認する

---

## 前提

このプロジェクトでは、以下を使用している。

| 項目 | 内容 |
|---|---|
| Spring Boot | 3.5.14 |
| Spring AI | 1.1.5 |
| Java | 17 |
| Chat model | qwen3.5:4b |
| Embedding model | nomic-embed-text |
| LLM backend | Ollama |
| VectorStore | SimpleVectorStore |

---

## 追加した依存関係

`pom.xml` に Actuator を追加する。

```xml
<dependency>
    <groupId>org.springframework.boot</groupId>
    <artifactId>spring-boot-starter-actuator</artifactId>
</dependency>
```

---

## Actuator設定

`src/main/resources/application.yaml` に以下を追加する。

```yaml
management:
  endpoints:
    web:
      exposure:
        include: health,info,metrics
  endpoint:
    health:
      show-details: always
```

現在の主な設定は以下。

```yaml
spring:
  application:
    name: spring_ai_test
  ai:
    ollama:
      base-url: http://localhost:11434
      chat:
        options:
          model: qwen3.5:4b
          temperature: 0.7
          num-predict: 128
      embedding:
        options:
          model: nomic-embed-text

management:
  endpoints:
    web:
      exposure:
        include: health,info,metrics
  endpoint:
    health:
      show-details: always

logging:
  level:
    org.springframework.ai.chat.client.advisor: DEBUG
    org.springframework.web.servlet.mvc.method.annotation.RequestMappingHandlerMapping: TRACE
```

---

## 起動方法

```powershell
.\mvnw.cmd clean spring-boot:run
```

---

## Actuator基本確認

別のPowerShellで確認する。

```powershell
curl.exe http://localhost:8080/actuator
curl.exe http://localhost:8080/actuator/health
curl.exe http://localhost:8080/actuator/metrics
```

`health` で `UP` が返ればActuatorは有効。

```json
{
  "status": "UP"
}
```

---

## メトリクス一覧の確認

PowerShellでJSONとして取得する。

```powershell
$json = curl.exe http://localhost:8080/actuator/metrics | ConvertFrom-Json
$json.names
```

Spring AI 関連だけを見る。

```powershell
$json.names | Where-Object { $_ -like "*gen_ai*" }
$json.names | Where-Object { $_ -like "*spring*" }
$json.names | Where-Object { $_ -like "*vector*" }
$json.names | Where-Object { $_ -like "*db*" }
```

---

## この環境で確認できたSpring AI系メトリクス

実際に確認できたメトリクスは以下。

```text
gen_ai.client.operation
gen_ai.client.operation.active
gen_ai.client.token.usage
spring.ai.advisor
spring.ai.advisor.active
spring.ai.chat.client
spring.ai.chat.client.active
```

---

## ChatClientメトリクス

確認コマンド。

```powershell
curl.exe http://localhost:8080/actuator/metrics/spring.ai.chat.client
```

例。

```json
{
  "name": "spring.ai.chat.client",
  "baseUnit": "seconds",
  "measurements": [
    {
      "statistic": "COUNT",
      "value": 2.0
    },
    {
      "statistic": "TOTAL_TIME",
      "value": 34.0040763
    },
    {
      "statistic": "MAX",
      "value": 9.3195245
    }
  ],
  "availableTags": [
    {
      "tag": "gen_ai.operation.name",
      "values": [
        "framework"
      ]
    },
    {
      "tag": "spring.ai.kind",
      "values": [
        "chat_client"
      ]
    },
    {
      "tag": "error",
      "values": [
        "none"
      ]
    },
    {
      "tag": "spring.ai.chat.client.stream",
      "values": [
        "false"
      ]
    },
    {
      "tag": "gen_ai.system",
      "values": [
        "spring_ai"
      ]
    }
  ]
}
```

意味。

| 項目 | 意味 |
|---|---|
| COUNT | ChatClient呼び出し回数 |
| TOTAL_TIME | ChatClient処理時間の合計秒数 |
| MAX | 観測ウィンドウ内の最大処理時間 |
| spring.ai.chat.client.stream | stream利用有無 |
| error | エラー有無 |

---

## Advisorメトリクス

確認コマンド。

```powershell
curl.exe http://localhost:8080/actuator/metrics/spring.ai.advisor
```

例。

```json
{
  "name": "spring.ai.advisor",
  "baseUnit": "seconds",
  "measurements": [
    {
      "statistic": "COUNT",
      "value": 4.0
    },
    {
      "statistic": "TOTAL_TIME",
      "value": 67.9760735
    },
    {
      "statistic": "MAX",
      "value": 9.3192432
    }
  ],
  "availableTags": [
    {
      "tag": "spring.ai.advisor.name",
      "values": [
        "SimpleLoggerAdvisor",
        "call"
      ]
    }
  ]
}
```

意味。

| 項目 | 意味 |
|---|---|
| spring.ai.advisor | Advisor処理時間 |
| spring.ai.advisor.name | 実行されたAdvisor名 |
| SimpleLoggerAdvisor | ChatClientのログ出力用Advisor |
| call | ChatClient呼び出し全体に関係するAdvisor処理 |

---

## モデル呼び出しメトリクス

確認コマンド。

```powershell
curl.exe http://localhost:8080/actuator/metrics/gen_ai.client.operation
```

chatだけを見る。

```powershell
curl.exe "http://localhost:8080/actuator/metrics/gen_ai.client.operation?tag=gen_ai.operation.name:chat"
```

embeddingだけを見る。

```powershell
curl.exe "http://localhost:8080/actuator/metrics/gen_ai.client.operation?tag=gen_ai.operation.name:embedding"
```

chatの例。

```json
{
  "name": "gen_ai.client.operation",
  "baseUnit": "seconds",
  "measurements": [
    {
      "statistic": "COUNT",
      "value": 2.0
    },
    {
      "statistic": "TOTAL_TIME",
      "value": 33.9430723
    },
    {
      "statistic": "MAX",
      "value": 9.3123462
    }
  ],
  "availableTags": [
    {
      "tag": "gen_ai.response.model",
      "values": [
        "qwen3.5:4b"
      ]
    },
    {
      "tag": "gen_ai.request.model",
      "values": [
        "qwen3.5:4b"
      ]
    },
    {
      "tag": "error",
      "values": [
        "none"
      ]
    },
    {
      "tag": "gen_ai.system",
      "values": [
        "ollama"
      ]
    }
  ]
}
```

embeddingの例。

```json
{
  "name": "gen_ai.client.operation",
  "baseUnit": "seconds",
  "measurements": [
    {
      "statistic": "COUNT",
      "value": 3.0
    },
    {
      "statistic": "TOTAL_TIME",
      "value": 2.0226283
    },
    {
      "statistic": "MAX",
      "value": 0.0850299
    }
  ],
  "availableTags": [
    {
      "tag": "gen_ai.response.model",
      "values": [
        "nomic-embed-text"
      ]
    },
    {
      "tag": "gen_ai.request.model",
      "values": [
        "none"
      ]
    },
    {
      "tag": "error",
      "values": [
        "none"
      ]
    },
    {
      "tag": "gen_ai.system",
      "values": [
        "ollama"
      ]
    }
  ]
}
```

意味。

| メトリクス | 意味 |
|---|---|
| gen_ai.client.operation | モデル呼び出し時間 |
| gen_ai.operation.name:chat | ChatModel呼び出し |
| gen_ai.operation.name:embedding | EmbeddingModel呼び出し |
| gen_ai.system:ollama | Ollama経由の呼び出し |
| gen_ai.response.model | 実際に応答したモデル |

---

## トークン使用量メトリクス

確認コマンド。

```powershell
curl.exe http://localhost:8080/actuator/metrics/gen_ai.client.token.usage
```

例。

```json
{
  "name": "gen_ai.client.token.usage",
  "description": "Measures number of input and output tokens used",
  "measurements": [
    {
      "statistic": "COUNT",
      "value": 3264.0
    }
  ],
  "availableTags": [
    {
      "tag": "gen_ai.operation.name",
      "values": [
        "chat",
        "embedding"
      ]
    },
    {
      "tag": "gen_ai.token.type",
      "values": [
        "output",
        "input",
        "total"
      ]
    }
  ]
}
```

input / output / total 別に見る。

```powershell
curl.exe "http://localhost:8080/actuator/metrics/gen_ai.client.token.usage?tag=gen_ai.token.type:input"
curl.exe "http://localhost:8080/actuator/metrics/gen_ai.client.token.usage?tag=gen_ai.token.type:output"
curl.exe "http://localhost:8080/actuator/metrics/gen_ai.client.token.usage?tag=gen_ai.token.type:total"
```

---

## RAGでの見え方

このプロジェクトのRAG処理は、概ね以下の流れ。

```text
ユーザー質問
  ↓
VectorStore検索
  ↓
検索結果からcontext作成
  ↓
ChatClient呼び出し
  ↓
Ollama qwen3.5:4bで回答生成
```

現在の観測方法は以下。

| 処理 | 確認方法 |
|---|---|
| VectorStore検索結果 | 自前ログ |
| ChatClient全体 | spring.ai.chat.client |
| Advisor処理 | spring.ai.advisor |
| Ollama chat | gen_ai.client.operation + tag=chat |
| Ollama embedding | gen_ai.client.operation + tag=embedding |
| token使用量 | gen_ai.client.token.usage |

---

## VectorStoreメトリクスについて

公式上は `db.vector.client.operation` というVectorStore操作メトリクスが存在する。

ただし、この環境で以下を確認した限り、`vector` / `db` 系メトリクスは表示されなかった。

```powershell
$json.names | Where-Object { $_ -like "*vector*" }
$json.names | Where-Object { $_ -like "*db*" }
```

そのため、現時点ではVectorStore検索部分はActuatorではなく、`RagService` の自前ログで確認する。

例。

```text
RAG search all result[1]: fileName=spring-ai-tools.md, title=ToolContext, score=..., distance=...
```

---

## SimpleLoggerAdvisorとの違い

`SimpleLoggerAdvisor` は、ChatClientに渡すプロンプトやAIの応答を確認するためのログ。

一方、自前のRAG検索ログは、VectorStore検索結果を確認するためのログ。

| ログ | 見えるもの |
|---|---|
| SimpleLoggerAdvisor | ChatClientに渡したprompt / response |
| RAG検索ログ | fileName / title / score / distance |

---

## エラー時のObservability確認

### 確認の目的

Ollama停止時などに、Spring AIのメトリクスに `error` タグがどう記録されるか確認する。

---

### 正常系を確認する

Ollamaが起動している状態でRAG回答を呼び出す。

日本語をGETクエリに直接入れると `HTTP 400 Bad Request` になることがあるため、まずASCIIだけで確認する。

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

### Ollamaを停止する

Ollamaアプリ、サービス、または `ollama serve` を停止する。

確認する。

```powershell
curl.exe http://localhost:11434/api/tags
```

接続できなければ、Ollama停止状態。

---

### RAG回答を再実行する

Spring Bootは起動したまま、同じRAG回答を呼び出す。

```powershell
curl.exe "http://localhost:8080/ai/rag/ask-md-dir?message=ToolContext&topK=10&threshold=0.0"
```

このとき、アプリ側は `HTTP 500 Internal Server Error` になることがある。

例。

```json
{
  "timestamp": "2026-05-09T11:49:12.303+00:00",
  "status": 500,
  "error": "Internal Server Error",
  "path": "/ai/rag/ask-md-dir"
}
```

---

### errorタグを確認する

```powershell
curl.exe http://localhost:8080/actuator/metrics/gen_ai.client.operation
curl.exe http://localhost:8080/actuator/metrics/spring.ai.chat.client
curl.exe http://localhost:8080/actuator/metrics/spring.ai.advisor
```

実際に確認できた例。

```json
{
  "name": "gen_ai.client.operation",
  "baseUnit": "seconds",
  "measurements": [
    {
      "statistic": "COUNT",
      "value": 4.0
    },
    {
      "statistic": "TOTAL_TIME",
      "value": 26.312113
    },
    {
      "statistic": "MAX",
      "value": 23.0727395
    }
  ],
  "availableTags": [
    {
      "tag": "gen_ai.operation.name",
      "values": [
        "chat",
        "embedding"
      ]
    },
    {
      "tag": "gen_ai.response.model",
      "values": [
        "nomic-embed-text",
        "qwen3.5:4b",
        "none"
      ]
    },
    {
      "tag": "gen_ai.request.model",
      "values": [
        "qwen3.5:4b",
        "none"
      ]
    },
    {
      "tag": "error",
      "values": [
        "ResourceAccessException",
        "none"
      ]
    },
    {
      "tag": "gen_ai.system",
      "values": [
        "ollama"
      ]
    }
  ]
}
```

`error` タグに `ResourceAccessException` が出ているため、Ollama呼び出し層で接続エラーが発生したことが分かる。

---

### エラー種別で絞り込む

```powershell
curl.exe "http://localhost:8080/actuator/metrics/gen_ai.client.operation?tag=error:ResourceAccessException"
```

embedding側に出ているか確認する。

```powershell
curl.exe "http://localhost:8080/actuator/metrics/gen_ai.client.operation?tag=error:ResourceAccessException&tag=gen_ai.operation.name:embedding"
```

chat側に出ているか確認する。

```powershell
curl.exe "http://localhost:8080/actuator/metrics/gen_ai.client.operation?tag=error:ResourceAccessException&tag=gen_ai.operation.name:chat"
```

---

### ChatClient / Advisor 側の見え方

実際の確認では、`gen_ai.client.operation` には `ResourceAccessException` が出た。

一方で、以下は `error:none` のままだった。

```text
spring.ai.chat.client
spring.ai.advisor
```

これは、RAG処理がChatClientに到達する前に、VectorStore検索で使うEmbedding呼び出しで失敗したためと考えられる。

RAGの流れ。

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

Embedding呼び出しで失敗すると、`answerFromSources()` の `ChatClient` 呼び出しまで進まない。

そのため、`spring.ai.chat.client` と `spring.ai.advisor` にはエラーが出ないことがある。

---

## 注意点

`SimpleLoggerAdvisor` は、プロンプトや回答内容をログに出す。

そのため、本番環境や個人情報・秘密情報を扱う環境では注意が必要。

学習用途では便利だが、本番では以下を検討する。

- DEBUGログを常時有効にしない
- 個人情報をプロンプトに含めない
- ログ保存先や保存期間を管理する
- 必要に応じてログマスキングする

---

## まとめ

このプロジェクトでは、Actuatorにより以下を確認できる。

```text
spring.ai.chat.client
spring.ai.advisor
gen_ai.client.operation
gen_ai.client.token.usage
```

RAG検索結果は、Actuatorではなく `RagService` の自前ログで確認する。

この構成により、RAG処理を以下のように分けて観測できる。

```text
検索部分
  → RAG検索ログ

AI呼び出し部分
  → Spring AI / Actuator metrics

プロンプト・回答内容
  → SimpleLoggerAdvisor

エラー発生箇所
  → errorタグとメトリクス種別で切り分け
```
