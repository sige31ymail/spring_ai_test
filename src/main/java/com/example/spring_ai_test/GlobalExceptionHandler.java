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
