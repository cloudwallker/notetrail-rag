package dev.notetrail.store;

import static org.assertj.core.api.Assertions.assertThat;

import dev.notetrail.api.Answer;
import java.time.Instant;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.embedded.EmbeddedDatabase;
import org.springframework.jdbc.datasource.embedded.EmbeddedDatabaseBuilder;
import org.springframework.jdbc.datasource.embedded.EmbeddedDatabaseType;

class QuestionRepositoryTest {
  private EmbeddedDatabase database;
  private JdbcTemplate jdbcTemplate;
  private QuestionRepository repository;

  @BeforeEach
  void setUp() {
    database =
        new EmbeddedDatabaseBuilder()
            .generateUniqueName(true)
            .setType(EmbeddedDatabaseType.H2)
            .addScript("classpath:schema.sql")
            .build();
    jdbcTemplate = new JdbcTemplate(database);
    repository = new QuestionRepository(jdbcTemplate);
  }

  @AfterEach
  void tearDown() {
    database.shutdown();
  }

  @Test
  void findLatestOrdersWholeSecondsAndFractionsByInstantWithoutLosingNanoseconds() {
    insertQuestion(90, "2026-10-20T08:00:00Z");
    insertQuestion(20, "2026-10-20T08:00:00.500Z");
    insertQuestion(10, "2026-10-20T08:00:00.500000001Z");
    insertQuestion(100, "2026-10-20T07:59:59.999999999Z");
    insertQuestion(5, "2026-10-20T08:00:01Z");

    var history = repository.findLatest();

    assertThat(history).extracting(Answer::id).containsExactly(5L, 10L, 20L, 90L, 100L);
    assertThat(history.get(1).createdAt())
        .isEqualTo(Instant.parse("2026-10-20T08:00:00.500000001Z"));
  }

  @Test
  void findLatestBreaksEqualInstantTiesByDescendingId() {
    insertQuestion(10, "2026-10-20T08:00:00.500Z");
    insertQuestion(20, "2026-10-20T08:00:00.500000000Z");

    assertThat(repository.findLatest()).extracting(Answer::id).containsExactly(20L, 10L);
  }

  @Test
  void findLatestReturnsThe50NewestInstants() {
    insertQuestion(999, "2026-10-20T08:00:00Z");
    for (int index = 1; index <= 50; index++) {
      insertQuestion(index, "2026-10-20T08:00:00.%09dZ".formatted(index));
    }

    var history = repository.findLatest();

    assertThat(history).hasSize(50);
    for (int index = 0; index < history.size(); index++) {
      assertThat(history.get(index).id()).isEqualTo(50L - index);
    }
  }

  private void insertQuestion(long id, String createdAt) {
    jdbcTemplate.update(
        "INSERT INTO questions(id, question, answer, mode, created_at) VALUES (?, ?, ?, ?, ?)",
        id,
        "Question " + id,
        "Answer " + id,
        "EXTRACTIVE",
        createdAt);
  }
}
