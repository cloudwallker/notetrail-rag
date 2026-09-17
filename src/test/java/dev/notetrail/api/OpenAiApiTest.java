package dev.notetrail.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import dev.notetrail.NoteTrailApplication;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest(
    classes = NoteTrailApplication.class,
    properties = {
      "spring.datasource.url=jdbc:h2:mem:notetrail-openai;DB_CLOSE_DELAY=-1",
      "notetrail.ai.mode=openai",
      "notetrail.ai.model=test-model",
      "notetrail.ai.api-key=test-secret"
    })
@AutoConfigureMockMvc
class OpenAiApiTest {
  private static final AtomicInteger CALLS = new AtomicInteger();
  private static HttpServer server;

  @Autowired private MockMvc mockMvc;
  @Autowired private JdbcTemplate jdbcTemplate;

  @DynamicPropertySource
  static void configureServer(DynamicPropertyRegistry registry) {
    try {
      server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
      server.createContext("/v1/chat/completions", OpenAiApiTest::handleRequest);
      server.start();
    } catch (IOException exception) {
      throw new IllegalStateException(exception);
    }
    registry.add(
        "notetrail.ai.base-url", () -> "http://127.0.0.1:" + server.getAddress().getPort() + "/v1");
  }

  @AfterAll
  static void stopServer() {
    server.stop(0);
  }

  @BeforeEach
  void clearDatabase() {
    CALLS.set(0);
    jdbcTemplate.update("DELETE FROM question_citations");
    jdbcTemplate.update("DELETE FROM questions");
    jdbcTemplate.update("DELETE FROM chunks");
    jdbcTemplate.update("DELETE FROM documents");
  }

  @Test
  void exposesOpenAiModeAndPersistsGeneratedAnswer() throws Exception {
    mockMvc
        .perform(
            post("/api/documents")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"title\":\"资料\",\"text\":\"火星是太阳系的行星。\"}"))
        .andExpect(status().isCreated());

    mockMvc
        .perform(
            post("/api/questions")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"question\":\"火星是什么？\"}"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.mode").value("OPENAI_COMPATIBLE"))
        .andExpect(jsonPath("$.answer").value("模型回答[1]"));

    mockMvc
        .perform(get("/api/questions"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$[0].mode").value("OPENAI_COMPATIBLE"))
        .andExpect(jsonPath("$[0].answer").value("模型回答[1]"));
  }

  @Test
  void skipsModelWhenSearchHasNoEvidence() throws Exception {
    mockMvc
        .perform(
            post("/api/questions")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"question\":\"没有资料\"}"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.mode").value("EXTRACTIVE"))
        .andExpect(jsonPath("$.citations").isEmpty());
    assertThat(CALLS.get()).isZero();
  }

  @Test
  void mapsModelFailureToSafeBadGatewayResponse() throws Exception {
    mockMvc
        .perform(
            post("/api/documents")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"title\":\"故障\",\"text\":\"triggerfailure 资料\"}"))
        .andExpect(status().isCreated());

    mockMvc
        .perform(
            post("/api/questions")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"question\":\"triggerfailure\"}"))
        .andExpect(status().isBadGateway())
        .andExpect(jsonPath("$.code").value("AI_UPSTREAM_ERROR"))
        .andExpect(jsonPath("$.message").value("模型服务调用失败。"))
        .andExpect(
            result ->
                assertThat(result.getResponse().getContentAsString())
                    .doesNotContain("test-secret", "provider-secret-error"));
  }

  private static void handleRequest(HttpExchange exchange) throws IOException {
    CALLS.incrementAndGet();
    String request = new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
    if (request.contains("triggerfailure")) {
      respond(exchange, 500, "provider-secret-error");
      return;
    }
    respond(exchange, 200, "{\"choices\":[{\"message\":{\"content\":\"模型回答[1]\"}}]}");
  }

  private static void respond(HttpExchange exchange, int status, String body) throws IOException {
    byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
    exchange.sendResponseHeaders(status, bytes.length);
    exchange.getResponseBody().write(bytes);
    exchange.close();
  }
}
