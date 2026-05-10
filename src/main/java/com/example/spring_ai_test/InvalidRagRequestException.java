package com.example.spring_ai_test;

public class InvalidRagRequestException extends RuntimeException {

    public InvalidRagRequestException(String message) {
        super(message);
    }
}