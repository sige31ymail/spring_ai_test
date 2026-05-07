# Spring AI 学習ノート

## ChatClient

ChatClientは、Spring AIでAIモデルと会話するための中心APIです。
prompt、system、user、call、contentなどを使って、AIへの入力と出力を扱います。

## Tool Calling

Tool Callingは、AIモデルが必要に応じてJavaメソッドを呼び出す仕組みです。
DB検索、外部API呼び出し、計算処理などをAIから利用できます。

## ToolContext

ToolContextは、AIモデルには直接渡さず、Tool実行時だけJava側に渡す内部情報です。
tenantId、loginUserId、roleなどをTool側で利用できます。

## RAG

RAGはRetrieval Augmented Generationの略です。
ユーザー質問に関連する文書をVectorStoreから検索し、その文書をプロンプトに追加して回答します。

## Structured Output

Structured Outputは、AIの回答をStringではなくJavaのrecordやDTOとして受け取る仕組みです。
entity(Class) や entity(ParameterizedTypeReference) を使います。