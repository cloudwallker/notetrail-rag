package dev.notetrail.api;

public class DocumentNotFoundException extends RuntimeException {
  public DocumentNotFoundException() {
    super("文档不存在。");
  }
}
