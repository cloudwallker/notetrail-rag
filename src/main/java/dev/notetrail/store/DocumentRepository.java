package dev.notetrail.store;

import dev.notetrail.api.DocumentNotFoundException;
import dev.notetrail.api.DocumentView;
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
public class DocumentRepository {
  private final JdbcTemplate jdbcTemplate;

  public DocumentRepository(JdbcTemplate jdbcTemplate) {
    this.jdbcTemplate = jdbcTemplate;
  }

  @Transactional
  public DocumentView create(String title, List<String> chunks) {
    Instant createdAt = Instant.now();
    KeyHolder keyHolder = new GeneratedKeyHolder();
    jdbcTemplate.update(
        connection -> {
          PreparedStatement statement =
              connection.prepareStatement(
                  "INSERT INTO documents(title, chunk_count, created_at) VALUES (?, ?, ?)",
                  Statement.RETURN_GENERATED_KEYS);
          statement.setString(1, title);
          statement.setInt(2, chunks.size());
          statement.setString(3, createdAt.toString());
          return statement;
        },
        keyHolder);
    long documentId = keyHolder.getKey().longValue();
    for (int position = 0; position < chunks.size(); position++) {
      jdbcTemplate.update(
          "INSERT INTO chunks(document_id, chunk_position, text) VALUES (?, ?, ?)",
          documentId,
          position,
          chunks.get(position));
    }
    return new DocumentView(documentId, title, chunks.size(), createdAt);
  }

  public List<DocumentView> findAll() {
    return jdbcTemplate.query(
        "SELECT id, title, chunk_count, created_at FROM documents ORDER BY id",
        (resultSet, rowNumber) ->
            new DocumentView(
                resultSet.getLong("id"),
                resultSet.getString("title"),
                resultSet.getInt("chunk_count"),
                Instant.parse(resultSet.getString("created_at"))));
  }

  public List<ChunkRecord> findAllChunks() {
    return jdbcTemplate.query(
        """
        SELECT c.id, c.document_id, d.title, c.chunk_position, c.text
        FROM chunks c
        JOIN documents d ON d.id = c.document_id
        ORDER BY c.document_id, c.chunk_position
        """,
        (resultSet, rowNumber) ->
            new ChunkRecord(
                resultSet.getLong("id"),
                resultSet.getLong("document_id"),
                resultSet.getString("title"),
                resultSet.getInt("chunk_position"),
                resultSet.getString("text")));
  }

  @Transactional
  public void delete(long documentId) {
    int existing =
        jdbcTemplate.queryForObject(
            "SELECT COUNT(*) FROM documents WHERE id = ?", Integer.class, documentId);
    if (existing == 0) {
      throw new DocumentNotFoundException();
    }
    jdbcTemplate.update("DELETE FROM chunks WHERE document_id = ?", documentId);
    jdbcTemplate.update("DELETE FROM documents WHERE id = ?", documentId);
  }
}
