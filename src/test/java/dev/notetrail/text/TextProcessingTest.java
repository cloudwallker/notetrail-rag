package dev.notetrail.text;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import org.junit.jupiter.api.Test;

class TextProcessingTest {
  private final TextChunker chunker = new TextChunker();
  private final SearchTokenizer tokenizer = new SearchTokenizer();

  @Test
  void chunksByUnicodeCodePointsWithoutSplittingSurrogatePairs() {
    String text = "😀".repeat(501);

    List<String> chunks = chunker.chunk(text);

    assertThat(chunks).hasSize(2);
    assertThat(chunks.get(0).codePointCount(0, chunks.get(0).length())).isEqualTo(500);
    assertThat(chunks.get(1).codePointCount(0, chunks.get(1).length())).isEqualTo(81);
    assertThat(chunks.get(1)).isEqualTo(text.substring(text.offsetByCodePoints(0, 420)));
    assertThat(chunks.get(0).substring(chunks.get(0).offsetByCodePoints(0, 420)))
        .isEqualTo(chunks.get(1).substring(0, chunks.get(1).offsetByCodePoints(0, 80)));
  }

  @Test
  void tokenizesEnglishWordsAndChineseSinglesAndBigrams() {
    assertThat(tokenizer.tokenize("Java，中国AI中国"))
        .containsExactly("java", "中", "国", "中国", "ai", "中", "国", "中国");
  }
}
