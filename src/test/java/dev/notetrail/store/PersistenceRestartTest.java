package dev.notetrail.store;

import static org.assertj.core.api.Assertions.assertThat;

import dev.notetrail.NoteTrailApplication;
import dev.notetrail.service.DocumentService;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.boot.WebApplicationType;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.context.ConfigurableApplicationContext;

class PersistenceRestartTest {
  @TempDir Path temporaryDirectory;

  @Test
  void restoresDocumentsAfterDatabaseIsClosedAndReopened() {
    String databaseUrl =
        "jdbc:h2:file:"
            + temporaryDirectory.resolve("notetrail").toAbsolutePath().toString().replace('\\', '/')
            + ";DB_CLOSE_ON_EXIT=FALSE";

    try (ConfigurableApplicationContext first = start(databaseUrl)) {
      first.getBean(DocumentService.class).importDocument("持久化", "restartable evidence");
    }

    try (ConfigurableApplicationContext second = start(databaseUrl)) {
      assertThat(second.getBean(DocumentService.class).listDocuments())
          .singleElement()
          .satisfies(
              document -> {
                assertThat(document.title()).isEqualTo("持久化");
                assertThat(document.chunkCount()).isEqualTo(1);
              });
    }
  }

  private ConfigurableApplicationContext start(String databaseUrl) {
    return new SpringApplicationBuilder(NoteTrailApplication.class)
        .web(WebApplicationType.NONE)
        .run(
            "--spring.datasource.url=" + databaseUrl,
            "--spring.datasource.username=sa",
            "--spring.datasource.password=",
            "--spring.sql.init.mode=always",
            "--notetrail.ai.mode=local");
  }
}
