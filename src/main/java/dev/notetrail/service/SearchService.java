package dev.notetrail.service;

import dev.notetrail.api.ApiValidationException;
import dev.notetrail.api.Hit;
import dev.notetrail.store.ChunkRecord;
import dev.notetrail.store.DocumentRepository;
import dev.notetrail.text.SearchTokenizer;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.springframework.stereotype.Service;

@Service
public class SearchService {
  private static final double K1 = 1.2;
  private static final double B = 0.75;

  private final DocumentRepository documentRepository;
  private final SearchTokenizer tokenizer;

  public SearchService(DocumentRepository documentRepository, SearchTokenizer tokenizer) {
    this.documentRepository = documentRepository;
    this.tokenizer = tokenizer;
  }

  public List<Hit> search(String query, int topK) {
    validateQuery(query);
    if (topK < 1 || topK > 10) {
      throw new ApiValidationException("topK 必须在 1 到 10 之间。");
    }
    List<String> queryTokens = tokenizer.tokenize(query);
    if (queryTokens.isEmpty()) {
      return List.of();
    }
    List<ChunkRecord> chunks = documentRepository.findAllChunks();
    if (chunks.isEmpty()) {
      return List.of();
    }
    List<TokenizedChunk> corpus =
        chunks.stream()
            .map(chunk -> new TokenizedChunk(chunk, tokenizer.tokenize(chunk.text())))
            .toList();
    double averageLength =
        corpus.stream().mapToInt(chunk -> chunk.tokens().size()).average().orElse(0.0);
    if (averageLength == 0.0) {
      return List.of();
    }
    Set<String> uniqueTerms = new LinkedHashSet<>(queryTokens);
    Map<String, Integer> documentFrequencies = documentFrequencies(corpus, uniqueTerms);
    List<Hit> hits = new ArrayList<>();
    for (TokenizedChunk chunk : corpus) {
      double score =
          score(chunk.tokens(), uniqueTerms, documentFrequencies, corpus.size(), averageLength);
      if (score > 0.0) {
        ChunkRecord record = chunk.record();
        hits.add(
            new Hit(
                record.documentId(),
                record.title(),
                record.id(),
                record.position(),
                record.text(),
                score));
      }
    }
    return hits.stream()
        .sorted(
            Comparator.comparingDouble(Hit::score)
                .reversed()
                .thenComparingLong(Hit::documentId)
                .thenComparingInt(Hit::position))
        .limit(topK)
        .toList();
  }

  private void validateQuery(String query) {
    if (query == null || query.isBlank()) {
      throw new ApiValidationException("query 不能为空。");
    }
    if (query.codePointCount(0, query.length()) > 1000) {
      throw new ApiValidationException("query 最多 1000 个字符。");
    }
  }

  private Map<String, Integer> documentFrequencies(List<TokenizedChunk> corpus, Set<String> terms) {
    Map<String, Integer> frequencies = new HashMap<>();
    for (TokenizedChunk chunk : corpus) {
      Set<String> present = new HashSet<>(chunk.tokens());
      for (String term : terms) {
        if (present.contains(term)) {
          frequencies.merge(term, 1, Integer::sum);
        }
      }
    }
    return frequencies;
  }

  private double score(
      List<String> tokens,
      Set<String> queryTerms,
      Map<String, Integer> documentFrequencies,
      int corpusSize,
      double averageLength) {
    Map<String, Integer> termFrequencies = new HashMap<>();
    for (String token : tokens) {
      termFrequencies.merge(token, 1, Integer::sum);
    }
    double score = 0.0;
    for (String term : queryTerms) {
      int frequency = termFrequencies.getOrDefault(term, 0);
      if (frequency == 0) {
        continue;
      }
      int documentFrequency = documentFrequencies.getOrDefault(term, 0);
      double inverseDocumentFrequency =
          Math.log(1.0 + (corpusSize - documentFrequency + 0.5) / (documentFrequency + 0.5));
      double denominator = frequency + K1 * (1.0 - B + B * tokens.size() / averageLength);
      score += inverseDocumentFrequency * frequency * (K1 + 1.0) / denominator;
    }
    return score;
  }

  private record TokenizedChunk(ChunkRecord record, List<String> tokens) {}
}
