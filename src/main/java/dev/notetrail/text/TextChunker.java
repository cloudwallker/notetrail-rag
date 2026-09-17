package dev.notetrail.text;

import java.util.ArrayList;
import java.util.List;
import org.springframework.stereotype.Component;

@Component
public class TextChunker {
  private static final int CHUNK_SIZE = 500;
  private static final int CHUNK_STEP = 420;

  public List<String> chunk(String text) {
    int codePoints = text.codePointCount(0, text.length());
    List<String> chunks = new ArrayList<>();
    for (int start = 0; start < codePoints; start += CHUNK_STEP) {
      int end = Math.min(start + CHUNK_SIZE, codePoints);
      int startOffset = text.offsetByCodePoints(0, start);
      int endOffset = text.offsetByCodePoints(0, end);
      chunks.add(text.substring(startOffset, endOffset));
      if (end == codePoints) {
        break;
      }
    }
    return List.copyOf(chunks);
  }
}
