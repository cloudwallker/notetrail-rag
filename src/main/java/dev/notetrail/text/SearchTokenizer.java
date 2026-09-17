package dev.notetrail.text;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import org.springframework.stereotype.Component;

@Component
public class SearchTokenizer {
  public List<String> tokenize(String text) {
    List<String> tokens = new ArrayList<>();
    StringBuilder word = new StringBuilder();
    List<Integer> hanSequence = new ArrayList<>();
    text.codePoints()
        .forEach(
            codePoint -> {
              if (Character.UnicodeScript.of(codePoint) == Character.UnicodeScript.HAN) {
                flushWord(word, tokens);
                hanSequence.add(codePoint);
              } else {
                flushHan(hanSequence, tokens);
                if (Character.isLetterOrDigit(codePoint)) {
                  word.appendCodePoint(codePoint);
                } else {
                  flushWord(word, tokens);
                }
              }
            });
    flushWord(word, tokens);
    flushHan(hanSequence, tokens);
    return List.copyOf(tokens);
  }

  private void flushWord(StringBuilder word, List<String> tokens) {
    if (!word.isEmpty()) {
      tokens.add(word.toString().toLowerCase(Locale.ROOT));
      word.setLength(0);
    }
  }

  private void flushHan(List<Integer> sequence, List<String> tokens) {
    if (sequence.isEmpty()) {
      return;
    }
    for (int codePoint : sequence) {
      tokens.add(new String(Character.toChars(codePoint)));
    }
    for (int index = 0; index + 1 < sequence.size(); index++) {
      tokens.add(
          new String(Character.toChars(sequence.get(index)))
              + new String(Character.toChars(sequence.get(index + 1))));
    }
    sequence.clear();
  }
}
