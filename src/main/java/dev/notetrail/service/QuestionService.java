package dev.notetrail.service;

import dev.notetrail.ai.AiUpstreamException;
import dev.notetrail.ai.OpenAiCompatibleClient;
import dev.notetrail.api.Answer;
import dev.notetrail.api.Hit;
import dev.notetrail.store.QuestionRepository;
import java.util.List;
import java.util.Locale;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

@Service
public class QuestionService {
  private static final String NO_EVIDENCE = "未找到与问题相关的资料。";

  private final SearchService searchService;
  private final QuestionRepository questionRepository;
  private final OpenAiCompatibleClient openAiCompatibleClient;

  @Value("${notetrail.ai.mode:local}")
  private String configuredMode;

  public QuestionService(
      SearchService searchService,
      QuestionRepository questionRepository,
      OpenAiCompatibleClient openAiCompatibleClient) {
    this.searchService = searchService;
    this.questionRepository = questionRepository;
    this.openAiCompatibleClient = openAiCompatibleClient;
  }

  public Answer answer(String question, int topK) {
    List<Hit> citations = searchService.search(question, topK);
    if (citations.isEmpty()) {
      return questionRepository.save(question, NO_EVIDENCE, "EXTRACTIVE", List.of());
    }
    String mode = configuredMode == null ? "" : configuredMode.strip().toLowerCase(Locale.ROOT);
    if (mode.equals("local")) {
      return questionRepository.save(question, extract(citations), "EXTRACTIVE", citations);
    }
    if (mode.equals("openai")) {
      String generated = openAiCompatibleClient.generate(question, citations);
      return questionRepository.save(question, generated, "OPENAI_COMPATIBLE", citations);
    }
    throw new AiUpstreamException("模型模式配置无效。");
  }

  public List<Answer> history() {
    return questionRepository.findLatest();
  }

  private String extract(List<Hit> citations) {
    StringBuilder answer = new StringBuilder();
    for (int index = 0; index < citations.size(); index++) {
      if (!answer.isEmpty()) {
        answer.append("\n\n");
      }
      answer.append('[').append(index + 1).append("] ").append(citations.get(index).text());
    }
    return answer.toString();
  }
}
