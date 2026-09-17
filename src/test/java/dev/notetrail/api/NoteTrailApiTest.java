package dev.notetrail.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.startsWith;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import dev.notetrail.NoteTrailApplication;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest(
    classes = NoteTrailApplication.class,
    properties = {
      "spring.datasource.url=jdbc:h2:mem:notetrail-api;DB_CLOSE_DELAY=-1",
      "notetrail.ai.mode=local"
    })
@AutoConfigureMockMvc
class NoteTrailApiTest {
  @Autowired private MockMvc mockMvc;
  @Autowired private ObjectMapper objectMapper;
  @Autowired private JdbcTemplate jdbcTemplate;

  @BeforeEach
  void clearDatabase() {
    jdbcTemplate.update("DELETE FROM question_citations");
    jdbcTemplate.update("DELETE FROM questions");
    jdbcTemplate.update("DELETE FROM chunks");
    jdbcTemplate.update("DELETE FROM documents");
  }

  @Test
  void reportsHealth() throws Exception {
    mockMvc
        .perform(get("/api/health"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.status").value("UP"))
        .andExpect(jsonPath("$.service").value("notetrail-rag"));
  }

  @Test
  void importsRanksAnswersKeepsHistorySnapshotAndDeletesDocument() throws Exception {
    createDocument("重复", "spring spring spring");
    long documentId = createDocument("完整", "Spring Boot 可以使用 JDBC 保存数据。数据库支持持久化。");

    mockMvc
        .perform(
            post("/api/search")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"query\":\"spring boot\",\"topK\":2}"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.hits[0].title").value("完整"))
        .andExpect(jsonPath("$.hits[0].position").value(0))
        .andExpect(jsonPath("$.hits[0].score").isNumber());

    mockMvc
        .perform(
            post("/api/questions")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"question\":\"如何保存数据？\",\"topK\":2}"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.mode").value("EXTRACTIVE"))
        .andExpect(jsonPath("$.answer").value(startsWith("[1]")))
        .andExpect(jsonPath("$.citations[0].title").value("完整"));

    mockMvc.perform(delete("/api/documents/{id}", documentId)).andExpect(status().isNoContent());

    mockMvc
        .perform(
            post("/api/search")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"query\":\"数据库持久化\"}"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.hits").isEmpty());

    mockMvc
        .perform(get("/api/questions"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$[0].citations[0].documentId").value(documentId))
        .andExpect(jsonPath("$[0].citations[0].title").value("完整"))
        .andExpect(jsonPath("$[0].answer").value(startsWith("[1]")));
  }

  @Test
  void returnsExplicitExtractiveAnswerWhenNothingMatches() throws Exception {
    mockMvc
        .perform(
            post("/api/questions")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"question\":\"不存在的资料\"}"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.mode").value("EXTRACTIVE"))
        .andExpect(jsonPath("$.answer").value("未找到与问题相关的资料。"))
        .andExpect(jsonPath("$.citations").isEmpty());
  }

  @Test
  void rejectsBlankAndOversizedDocumentsWithUniformErrors() throws Exception {
    mockMvc
        .perform(
            post("/api/documents")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"title\":\"空\",\"text\":\" \\n \\t\"}"))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"))
        .andExpect(jsonPath("$.message").isString());

    String oversized = "😀".repeat(100_001);
    mockMvc
        .perform(
            post("/api/documents")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(new ImportBody("过长", oversized))))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));
  }

  @Test
  void rejectsFractionalStringAndOutOfRangeTopK() throws Exception {
    for (String topK : new String[] {"1.5", "\"2\"", "true", "0", "11"}) {
      mockMvc
          .perform(
              post("/api/search")
                  .contentType(MediaType.APPLICATION_JSON)
                  .content("{\"query\":\"spring\",\"topK\":" + topK + "}"))
          .andExpect(status().isBadRequest())
          .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));
    }
  }

  @Test
  void rejectsNonTextScalarsForAllTextFields() throws Exception {
    for (RequestCase requestCase :
        new RequestCase[] {
          new RequestCase("/api/documents", "{\"title\":7,\"text\":\"正文\"}"),
          new RequestCase("/api/documents", "{\"title\":\"标题\",\"text\":false}"),
          new RequestCase("/api/search", "{\"query\":42}"),
          new RequestCase("/api/questions", "{\"question\":true}")
        }) {
      mockMvc
          .perform(
              post(requestCase.path())
                  .contentType(MediaType.APPLICATION_JSON)
                  .content(requestCase.body()))
          .andExpect(status().isBadRequest())
          .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));
    }
  }

  @Test
  void rejectsMalformedDuplicateAndUnknownJsonFields() throws Exception {
    for (String body :
        new String[] {
          "{\"query\":",
          "null",
          "\"query\"",
          "{\"query\":\"spring\",\"query\":\"boot\"}",
          "{\"query\":\"spring\",\"unexpected\":true}"
        }) {
      mockMvc
          .perform(post("/api/search").contentType(MediaType.APPLICATION_JSON).content(body))
          .andExpect(status().isBadRequest())
          .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));
    }
  }

  @Test
  void returnsNotFoundWhenDeletingUnknownDocument() throws Exception {
    mockMvc
        .perform(delete("/api/documents/999999"))
        .andExpect(status().isNotFound())
        .andExpect(jsonPath("$.code").value("NOT_FOUND"));
  }

  private long createDocument(String title, String text) throws Exception {
    String response =
        mockMvc
            .perform(
                post("/api/documents")
                    .contentType(MediaType.APPLICATION_JSON)
                    .characterEncoding(StandardCharsets.UTF_8)
                    .content(objectMapper.writeValueAsString(new ImportBody(title, text))))
            .andExpect(status().isCreated())
            .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
            .andReturn()
            .getResponse()
            .getContentAsString(StandardCharsets.UTF_8);
    JsonNode body = objectMapper.readTree(response);
    assertThat(body.get("chunkCount").asInt()).isPositive();
    return body.get("id").asLong();
  }

  private record ImportBody(String title, String text) {}

  private record RequestCase(String path, String body) {}
}
