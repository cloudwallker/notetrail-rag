package dev.notetrail.service;

import dev.notetrail.api.ApiValidationException;
import dev.notetrail.api.DocumentNotFoundException;
import dev.notetrail.api.DocumentView;
import dev.notetrail.store.DocumentRepository;
import dev.notetrail.text.TextChunker;
import java.util.List;
import org.springframework.stereotype.Service;

@Service
public class DocumentService {
  private final DocumentRepository documentRepository;
  private final TextChunker textChunker;

  public DocumentService(DocumentRepository documentRepository, TextChunker textChunker) {
    this.documentRepository = documentRepository;
    this.textChunker = textChunker;
  }

  public DocumentView importDocument(String title, String text) {
    String normalizedTitle = validateTitle(title);
    validateText(text);
    return documentRepository.create(normalizedTitle, textChunker.chunk(text));
  }

  public List<DocumentView> listDocuments() {
    return documentRepository.findAll();
  }

  public void deleteDocument(long documentId) {
    if (documentId <= 0) {
      throw new DocumentNotFoundException();
    }
    documentRepository.delete(documentId);
  }

  private String validateTitle(String title) {
    if (title == null || title.isBlank()) {
      throw new ApiValidationException("title 不能为空。");
    }
    String normalized = title.strip();
    if (normalized.codePointCount(0, normalized.length()) > 100) {
      throw new ApiValidationException("title 最多 100 个字符。");
    }
    return normalized;
  }

  private void validateText(String text) {
    if (text == null || text.isBlank()) {
      throw new ApiValidationException("text 不能为空。");
    }
    if (text.codePointCount(0, text.length()) > 100_000) {
      throw new ApiValidationException("text 最多 100000 个 Unicode 码点。");
    }
  }
}
