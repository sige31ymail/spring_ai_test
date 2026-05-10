package com.example.spring_ai_test;

import java.util.List;

import org.junit.jupiter.api.Test;
import static org.mockito.ArgumentMatchers.anyDouble;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(RagController.class)
@Import(GlobalExceptionHandler.class)
class RagControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private RagService ragService;

    @Test
    void askMarkdownDirectoryReturnsAnswerWithSources() throws Exception {

        RagFileSearchResult source = new RagFileSearchResult(
                "spring-ai-tools.md",
                "ToolContext",
                0.95,
                0.05,
                "ToolContextはTool Callingで追加情報を渡す仕組みです。");

        RagFileAnswerWithSources response = new RagFileAnswerWithSources(
                "ToolContextは、Tool Callingで追加情報を渡す仕組みです。",
                List.of(source));

        when(ragService.askAll(anyString(), anyInt(), anyDouble()))
                .thenReturn(response);

        mockMvc.perform(get("/ai/rag/ask-md-dir")
                        .param("message", "ToolContext")
                        .param("topK", "10")
                        .param("threshold", "0.0"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.answer")
                        .value("ToolContextは、Tool Callingで追加情報を渡す仕組みです。"))
                .andExpect(jsonPath("$.sources[0].fileName").value("spring-ai-tools.md"))
                .andExpect(jsonPath("$.sources[0].title").value("ToolContext"))
                .andExpect(jsonPath("$.sources[0].score").value(0.95))
                .andExpect(jsonPath("$.sources[0].distance").value(0.05))
                .andExpect(jsonPath("$.sources[0].text")
                        .value("ToolContextはTool Callingで追加情報を渡す仕組みです。"));
    }

    @Test
    void askMarkdownFilePostReturnsAnswerWithSources() throws Exception {

        RagFileSearchResult source = new RagFileSearchResult(
                "spring-ai-tools.md",
                "ToolContext",
                0.90,
                0.10,
                "ToolContextはツール実行時の補足情報です。");

        RagFileAnswerWithSources response = new RagFileAnswerWithSources(
                "ToolContextはツール実行時の補足情報です。",
                List.of(source));

        when(ragService.askByFile(anyString(), anyString(), anyInt(), anyDouble()))
                .thenReturn(response);

        String requestBody = """
                {
                  "fileName": "spring-ai-tools.md",
                  "message": "ToolContext",
                  "topK": 5,
                  "threshold": 0.0
                }
                """;

        mockMvc.perform(post("/ai/rag/ask-md-file")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestBody))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.answer")
                        .value("ToolContextはツール実行時の補足情報です。"))
                .andExpect(jsonPath("$.sources[0].fileName").value("spring-ai-tools.md"))
                .andExpect(jsonPath("$.sources[0].title").value("ToolContext"));
    }

    @Test
    void searchMarkdownDirectoryReturnsSources() throws Exception {

        RagFileSearchResult source = new RagFileSearchResult(
                "spring-ai-rag.md",
                "RAG",
                0.88,
                0.12,
                "RAGは検索結果をもとに回答する仕組みです。");

        when(ragService.searchAll(anyString(), anyInt(), anyDouble()))
                .thenReturn(List.of(source));

        mockMvc.perform(get("/ai/rag/search-md-dir-simple")
                        .param("message", "RAG")
                        .param("topK", "10")
                        .param("threshold", "0.0"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].fileName").value("spring-ai-rag.md"))
                .andExpect(jsonPath("$[0].title").value("RAG"))
                .andExpect(jsonPath("$[0].score").value(0.88))
                .andExpect(jsonPath("$[0].distance").value(0.12))
                .andExpect(jsonPath("$[0].text")
                        .value("RAGは検索結果をもとに回答する仕組みです。"));
    }

    @Test
    void loadMarkdownDirectoryReturnsServiceMessage() throws Exception {

        when(ragService.loadMarkdownDirectory())
                .thenReturn("Markdown directory loaded: 10");

        mockMvc.perform(get("/ai/rag/load-md-dir"))
                .andExpect(status().isOk())
                .andExpect(content().string("Markdown directory loaded: 10"));
    }
}