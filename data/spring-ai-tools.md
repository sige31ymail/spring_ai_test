# Spring AI Tools

## Tool Calling

Tool Callingは、AIモデルが必要に応じてJavaメソッドを呼び出す仕組みです。
DB検索、外部API呼び出し、計算処理などをAIから利用できます。

## ToolContext

ToolContextは、AIモデルには直接渡さず、Tool実行時だけJava側に渡す内部情報です。
tenantId、loginUserId、roleなどをTool側で利用できます。

## Return Direct

Return Directは、Toolの結果をAIに再解釈させず、そのまま呼び出し元へ返す仕組みです。
JSONや検索結果を正確に返したい場合に使います。