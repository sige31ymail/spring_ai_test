# Spring AI RAG

## RAG

RAGはRetrieval Augmented Generationの略です。
ユーザー質問に関連する文書をVectorStoreから検索し、その文書をプロンプトに追加して回答します。

## Embedding

Embeddingは、文章を数値ベクトルに変換する処理です。
RAGでは、文書とユーザー質問の両方をEmbeddingし、近い文書を検索します。

## VectorStore

VectorStoreは、Embeddingされた文書を保存し、質問に近い文書を検索するための保存先です。