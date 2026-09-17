package dev.notetrail.api;

import dev.notetrail.service.DocumentService;
import dev.notetrail.service.QuestionService;
import dev.notetrail.service.RequestValues;
import dev.notetrail.service.SearchService;
import java.net.URI;
import java.util.List;
import java.util.Map;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api")
public class NoteTrailController {
  private final DocumentService documentService;
  private final SearchService searchService;
  private final QuestionService questionService;

  public NoteTrailController(
      DocumentService documentService,
      SearchService searchService,
      QuestionService questionService) {
    this.documentService = documentService;
    this.searchService = searchService;
    this.questionService = questionService;
  }

  @GetMapping("/health")
  public Map<String, String> health() {
    return Map.of("status", "UP", "service", "notetrail-rag");
  }

  @PostMapping("/documents")
  public ResponseEntity<DocumentView> importDocument(@RequestBody ImportDocumentRequest request) {
    requireBody(request);
    DocumentView document = documentService.importDocument(request.title(), request.text());
    return ResponseEntity.created(URI.create("/api/documents/" + document.id())).body(document);
  }

  @GetMapping("/documents")
  public List<DocumentView> listDocuments() {
    return documentService.listDocuments();
  }

  @DeleteMapping("/documents/{id}")
  public ResponseEntity<Void> deleteDocument(@PathVariable long id) {
    documentService.deleteDocument(id);
    return ResponseEntity.noContent().build();
  }

  @PostMapping("/search")
  public SearchResponse search(@RequestBody SearchRequest request) {
    requireBody(request);
    return new SearchResponse(
        searchService.search(request.query(), RequestValues.topK(request.topK())));
  }

  @PostMapping("/questions")
  public Answer ask(@RequestBody QuestionRequest request) {
    requireBody(request);
    return questionService.answer(request.question(), RequestValues.topK(request.topK()));
  }

  @GetMapping("/questions")
  public List<Answer> history() {
    return questionService.history();
  }

  private void requireBody(Object request) {
    if (request == null) {
      throw new ApiValidationException("请求体必须是 JSON 对象。");
    }
  }
}
