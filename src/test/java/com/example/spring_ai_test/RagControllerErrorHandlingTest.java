package com.example.spring_ai_test;

import static org.mockito.ArgumentMatchers.anyDouble;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doThrow;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(RagController.class)
@Import(GlobalExceptionHandler.class)
class RagControllerErrorHandlingTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private RagService ragService;

    @Test
    void askMarkdownDirectoryReturnsBadRequestWhenTopKIsInvalid() throws Exception {

        mockMvc.perform(get("/ai/rag/ask-md-dir")
                        .param("message", "ToolContext")
                        .param("topK", "0")
                        .param("threshold", "0.0"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_RAG_REQUEST"))
                .andExpect(jsonPath("$.message").value("topKは1以上20以下で指定してください。"));
    }

    @Test
    void askMarkdownDirectoryReturnsBadRequestWhenThresholdIsInvalid() throws Exception {

        mockMvc.perform(get("/ai/rag/ask-md-dir")
                        .param("message", "ToolContext")
                        .param("topK", "10")
                        .param("threshold", "1.5"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_RAG_REQUEST"))
                .andExpect(jsonPath("$.message").value("thresholdは0.0以上1.0以下で指定してください。"));
    }

    @Test
    void askMarkdownFileReturnsBadRequestWhenFileNameIsInvalid() throws Exception {

        mockMvc.perform(get("/ai/rag/ask-md-file")
                        .param("fileName", "unknown.md")
                        .param("message", "ToolContext")
                        .param("topK", "5")
                        .param("threshold", "0.0"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_RAG_REQUEST"))
                .andExpect(jsonPath("$.message")
                        .value("fileNameは spring-ai-notes.md, spring-ai-tools.md, spring-ai-rag.md のいずれかを指定してください。"));
    }

    @Test
    void askMarkdownDirectoryPostReturnsBadRequestWhenJsonIsInvalid() throws Exception {

        String invalidJson = "{ \"message\": \"ToolContext\", \"topK\": 10,";

        mockMvc.perform(post("/ai/rag/ask-md-dir")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(invalidJson))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_REQUEST_BODY"))
                .andExpect(jsonPath("$.message").value("リクエストBodyのJSON形式が不正です。"));
    }

    @Test
    void searchMarkdownDirectoryReturnsConflictWhenVectorStoreIsNotLoaded() throws Exception {

        doThrow(new VectorStoreNotLoadedException(
                "VectorStoreが読み込まれていません。Markdownを読み込むか、保存済みVectorStoreを読み込んでください。"))
                .when(ragService)
                .searchAll(anyString(), anyInt(), anyDouble());

        mockMvc.perform(get("/ai/rag/search-md-dir-simple")
                        .param("message", "ToolContext")
                        .param("topK", "10")
                        .param("threshold", "0.0"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("VECTOR_STORE_NOT_LOADED"))
                .andExpect(jsonPath("$.message")
                        .value("VectorStoreが読み込まれていません。Markdownを読み込むか、保存済みVectorStoreを読み込んでください。"));
    }
}