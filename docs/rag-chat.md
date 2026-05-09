# RAG Chat Page

## 概要

`rag-chat.html` は、Spring AI の RAG 機能をブラウザから確認するための簡易チャット画面です。

画面URL:

```text
http://localhost:8080/rag-chat.html
```

---

## 主な機能

| 機能 | 内容 |
|---|---|
| docs全体検索 | `src/main/resources/docs` 配下全体を対象にRAG回答する |
| ファイル指定検索 | 特定Markdownファイルだけを対象にRAG回答する |
| topK指定 | VectorStoreから取得する最大件数を指定する |
| threshold指定 | 類似度しきい値を指定する |
| 検索のみ | AI回答を生成せず、VectorStore検索結果だけ確認する |
| 参照元表示 | 回答に使われた `fileName`, `title`, `score`, `distance` を確認する |
| 本文表示 | 参照元Documentの本文を確認する |
| 回答時間表示 | 回答生成にかかった秒数を表示する |
| VectorStore操作 | load / save / Markdown読み込みを画面から実行する |

---

## 通常の使い方

Spring Bootを起動します。

```powershell
.\mvnw.cmd clean spring-boot:run
```

画面を開きます。

```text
http://localhost:8080/rag-chat.html
```

質問を入力して「送信」を押します。

例:

```text
ToolContextとは何ですか？
```

---

## 検索対象

### docs全体

`src/main/resources/docs` 配下のMarkdown全体を検索します。

どのファイルに答えがあるか分からない場合はこちらを使います。

### ファイル指定

特定のMarkdownだけを検索対象にします。

対象例:

| fileName | 内容 |
|---|---|
| `spring-ai-tools.md` | Tool Calling / ToolContext / Return Direct |
| `spring-ai-rag.md` | RAG / Embedding / VectorStore |
| `spring-ai-notes.md` | Spring AI全体の基本メモ |

---

## topK

`topK` は、VectorStoreから最大何件のDocumentを取得するかを指定します。

学習中のおすすめ:

```text
topK = 10
```

検索対象をファイル指定にする場合は、少なめでも十分です。

```text
topK = 5
```

---

## threshold

`threshold` は、類似度のしきい値です。

学習中のおすすめ:

```text
threshold = 0.0
```

値を上げると、関連度が低いDocumentが除外されます。

例:

```text
threshold = 0.5
threshold = 0.7
```

ただし、高くしすぎると必要なDocumentも検索結果から落ちることがあります。

---

## 「送信」と「検索のみ」の違い

### 送信

```text
VectorStore検索
  ↓
検索結果をPromptに追加
  ↓
qwen3.5が回答生成
  ↓
answer + sources を表示
```

### 検索のみ

```text
VectorStore検索
  ↓
検索結果だけ表示
```

回答が期待通りでない場合は、まず「検索のみ」で正しいDocumentが取得できているか確認します。

---

## 切り分けの考え方

### 検索結果に正しいDocumentがない場合

原因候補:

```text
topKが小さい
thresholdが高すぎる
Markdown分割が悪い
質問文とDocumentの意味が近くない
VectorStoreが古い
```

### 検索結果は正しいが回答が悪い場合

原因候補:

```text
Promptが弱い
参考情報が多すぎる
LLMがうまく要約できていない
```

---

## VectorStore操作

### 保存済みVectorStoreを読み込む

```text
/ai/rag/load-store
```

通常は起動時に自動ロードされるため、毎回押す必要はありません。

### Markdownを読み込む

```text
/ai/rag/load-md-dir
```

注意:

これは追加処理です。  
同じ起動中に何度も押すと、VectorStoreに重複登録される可能性があります。

### VectorStoreを保存する

```text
/ai/rag/save-store
```

現在のVectorStoreを以下に保存します。

```text
data/simple-vector-store.json
```

---

## Markdown更新時の手順

Markdownを更新した場合は、VectorStoreを作り直します。

```powershell
Remove-Item .\data\simple-vector-store.json
.\mvnw.cmd clean spring-boot:run
```

画面で以下を実行します。

```text
Markdownを読み込む
VectorStoreを保存する
```

---

## 注意点

`SimpleVectorStore` は学習・デモ用途です。

本番用途では、以下のようなVector DBを使うことが多いです。

```text
PostgreSQL + pgvector
Redis
Elasticsearch
OpenSearch
Chroma
Qdrant
Milvus
```

---

## 現在の到達点

この画面により、以下をブラウザで確認できます。

```text
RAG回答
VectorStore検索結果
参照元Document
topK / threshold の影響
ファイル指定検索
docs全体検索
```

---

## READMEにリンクを追加する場合

`README.md` に以下を追記するとよいです。

```markdown
## RAG Chat

RAGチャット画面の使い方は以下に整理しています。

- [RAG Chat Page](docs/rag-chat.md)
```
