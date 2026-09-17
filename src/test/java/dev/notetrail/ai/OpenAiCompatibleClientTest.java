package dev.notetrail.ai;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import dev.notetrail.api.Hit;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class OpenAiCompatibleClientTest {
  private final ObjectMapper objectMapper = new ObjectMapper();
  private HttpServer server;
  private String baseUrl;

  @BeforeEach
  void startServer() throws IOException {
    server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
    server.start();
    baseUrl = "http://127.0.0.1:" + server.getAddress().getPort() + "/v1";
  }

  @AfterEach
  void stopServer() {
    server.stop(0);
  }

  @Test
  void sendsCompatibleRequestWithEvidenceAndReturnsGeneratedContent() throws Exception {
    AtomicReference<String> authorization = new AtomicReference<>();
    AtomicReference<JsonNode> request = new AtomicReference<>();
    server.createContext(
        "/v1/chat/completions",
        exchange -> {
          authorization.set(exchange.getRequestHeaders().getFirst("Authorization"));
          request.set(objectMapper.readTree(exchange.getRequestBody()));
          respond(exchange, 200, "{\"choices\":[{\"message\":{\"content\":\"依据资料[1]。\"}}]}");
        });
    OpenAiCompatibleClient client =
        new OpenAiCompatibleClient(
            objectMapper, baseUrl, "demo-model", "top-secret", Duration.ofSeconds(2));

    String answer = client.generate("问题", List.of(hit("真实证据")));

    assertThat(answer).isEqualTo("依据资料[1]。");
    assertThat(authorization.get()).isEqualTo("Bearer top-secret");
    assertThat(request.get().get("model").asText()).isEqualTo("demo-model");
    assertThat(request.get().get("temperature").asInt()).isZero();
    assertThat(request.get().get("messages").toString())
        .contains("只依据所给材料", "使用[编号]引用", "真实证据", "问题");
  }

  @Test
  void rejectsNonSuccessMalformedOversizedAndSlowResponsesWithoutLeakingDetails() {
    server.createContext(
        "/v1/chat/completions", exchange -> respond(exchange, 503, "provider-internal top-secret"));
    OpenAiCompatibleClient nonSuccess =
        new OpenAiCompatibleClient(
            objectMapper, baseUrl, "model", "top-secret", Duration.ofSeconds(2));
    assertSafeFailure(nonSuccess);

    server.removeContext("/v1/chat/completions");
    server.createContext(
        "/v1/chat/completions", exchange -> respond(exchange, 200, "{\"choices\":[]}"));
    assertSafeFailure(nonSuccess);

    server.removeContext("/v1/chat/completions");
    server.createContext(
        "/v1/chat/completions",
        exchange -> respond(exchange, 200, "{\"choices\":[{\"message\":{\"content\":123}}]}"));
    assertSafeFailure(nonSuccess);

    server.removeContext("/v1/chat/completions");
    server.createContext(
        "/v1/chat/completions",
        exchange -> respond(exchange, 200, "{\"choices\":[{\"message\":{\"content\":true}}]}"));
    assertSafeFailure(nonSuccess);

    server.removeContext("/v1/chat/completions");
    server.createContext(
        "/v1/chat/completions", exchange -> respond(exchange, 200, "x".repeat(256 * 1024 + 1)));
    assertSafeFailure(nonSuccess);

    server.removeContext("/v1/chat/completions");
    server.createContext(
        "/v1/chat/completions",
        exchange -> {
          try {
            Thread.sleep(500);
            respond(exchange, 200, "{\"choices\":[{\"message\":{\"content\":\"late\"}}]}");
          } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
          }
        });
    OpenAiCompatibleClient slow =
        new OpenAiCompatibleClient(
            objectMapper, baseUrl, "model", "top-secret", Duration.ofMillis(100));
    assertSafeFailure(slow);
  }

  @Test
  void rejectsIncompleteConfigurationWithoutEchoingSecret() {
    OpenAiCompatibleClient client =
        new OpenAiCompatibleClient(objectMapper, "", "model", "top-secret", Duration.ofSeconds(1));

    assertSafeFailure(client);
  }

  private void assertSafeFailure(OpenAiCompatibleClient client) {
    assertThatThrownBy(() -> client.generate("question", List.of(hit("evidence"))))
        .isInstanceOf(AiUpstreamException.class)
        .hasMessageNotContaining("top-secret")
        .hasMessageNotContaining("provider-internal");
  }

  private Hit hit(String text) {
    return new Hit(1L, "文档", 2L, 0, text, 1.25);
  }

  private void respond(HttpExchange exchange, int status, String body) throws IOException {
    byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
    exchange.getResponseHeaders().set("Content-Type", "application/json; charset=utf-8");
    exchange.sendResponseHeaders(status, bytes.length);
    exchange.getResponseBody().write(bytes);
    exchange.close();
  }
}
