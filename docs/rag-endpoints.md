# RAG Endpoints

このメモは、現在主に使うRAG関連エンドポイントを整理したものです。

---

## 現在使う主力エンドポイント

| Endpoint | 用途 |
|---|---|
| `/ai/rag/load-md-dir` | `src/main/resources/docs` 配下のMarkdownをまとめてVectorStoreへ登録する |
| `/ai/rag/search-md-file-simple` | 特定Markdownファイルだけを対象に検索結果を確認する |
| `/ai/rag/ask-md-file` | 特定Markdownファイルだけを対象にRAG回答を返す |
| `/ai/rag/search-md-dir-simple` | `docs` 配下全体を対象に検索結果を確認する |
| `/ai/rag/ask-md-dir` | `docs` 配下全体を対象にRAG回答を返す |
| `/ai/rag/save-store` | 現在のVectorStoreをJSONファイルに保存する |
| `/ai/rag/load-store` | 保存済みJSONファイルからVectorStoreを復元する |

---

## 初回またはMarkdown更新時の手順

VectorStoreの重複を避けるため、Markdownを更新した場合は一度保存済みJSONを削除する。

```powershell
Remove-Item .\data\simple-vector-store.json