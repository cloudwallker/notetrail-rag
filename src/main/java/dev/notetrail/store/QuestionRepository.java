package dev.notetrail.store;

import dev.notetrail.api.Answer;
import dev.notetrail.api.Hit;
import java.sql.PreparedStatement;
import java.sql.Statement;
import java.time.Instant;
import java.util.List;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.jdbc.support.KeyHolder;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

@Repository
public class QuestionRepository {
  private final JdbcTemplate jdbcTemplate;

  public QuestionRepository(JdbcTemplate jdbcTemplate) {
    this.jdbcTemplate = jdbcTemplate;
  }

  @Transactional
  public Answer save(String question, String answerText, String mode, List<Hit> citations) {
    Instant createdAt = Instant.now();
    KeyHolder keyHolder = new GeneratedKeyHolder();
    jdbcTemplate.update(
        connection -> {
          PreparedStatement statement =
              connection.prepareStatement(
                  "INSERT INTO questions(question, answer, mode, created_at) VALUES (?, ?, ?, ?)",
                  Statement.RETURN_GENERATED_KEYS);
          statement.setString(1, question);
          statement.setString(2, answerText);
          statement.setString(3, mode);
          statement.setString(4, createdAt.toString());
          return statement;
        },
        keyHolder);
    long questionId = keyHolder.getKey().longValue();
    for (int index = 0; index < citations.size(); index++) {
      Hit hit = citations.get(index);
      jdbcTemplate.update(
          """
          INSERT INTO question_citations(
            question_id, citation_order, document_id, title, chunk_id,
            chunk_position, text, score
          ) VALUES (?, ?, ?, ?, ?, ?, ?, ?)
          """,
          questionId,
          index,
          hit.documentId(),
          hit.title(),
          hit.chunkId(),
          hit.position(),
          hit.text(),
          hit.score());
    }
    return new Answer(questionId, question, answerText, mode, List.copyOf(citations), createdAt);
  }

  public List<Answer> findLatest() {
    return jdbcTemplate.query(
        """
        SELECT id, question, answer, mode, created_at
        FROM questions
        ORDER BY created_at DESC, id DESC
        LIMIT 50
        """,
        (resultSet, rowNumber) -> {
          long questionId = resultSet.getLong("id");
          return new Answer(
              questionId,
              resultSet.getString("question"),
              resultSet.getString("answer"),
              resultSet.getString("mode"),
              findCitations(questionId),
              Instant.parse(resultSet.getString("created_at")));
        });
  }

  private List<Hit> findCitations(long questionId) {
    return jdbcTemplate.query(
        """
        SELECT document_id, title, chunk_id, chunk_position, text, score
        FROM question_citations
        WHERE question_id = ?
        ORDER BY citation_order
        """,
        (resultSet, rowNumber) ->
            new Hit(
                resultSet.getLong("document_id"),
                resultSet.getString("title"),
                resultSet.getLong("chunk_id"),
                resultSet.getInt("chunk_position"),
                resultSet.getString("text"),
                resultSet.getDouble("score")),
        questionId);
  }
}
