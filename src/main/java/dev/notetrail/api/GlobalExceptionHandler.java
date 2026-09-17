package dev.notetrail.api;

import dev.notetrail.ai.AiUpstreamException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.HttpMediaTypeNotSupportedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

@RestControllerAdvice
public class GlobalExceptionHandler {
  private static final Logger LOG = LoggerFactory.getLogger(GlobalExceptionHandler.class);

  @ExceptionHandler(ApiValidationException.class)
  public ResponseEntity<ErrorResponse> validation(ApiValidationException exception) {
    return response(HttpStatus.BAD_REQUEST, "VALIDATION_ERROR", exception.getMessage());
  }

  @ExceptionHandler({
    HttpMessageNotReadableException.class,
    MethodArgumentNotValidException.class,
    MethodArgumentTypeMismatchException.class,
    MissingServletRequestParameterException.class,
    HttpMediaTypeNotSupportedException.class
  })
  public ResponseEntity<ErrorResponse> invalidRequest(Exception exception) {
    return response(HttpStatus.BAD_REQUEST, "VALIDATION_ERROR", "请求参数或 JSON 格式无效。");
  }

  @ExceptionHandler(DocumentNotFoundException.class)
  public ResponseEntity<ErrorResponse> notFound(DocumentNotFoundException exception) {
    return response(HttpStatus.NOT_FOUND, "NOT_FOUND", exception.getMessage());
  }

  @ExceptionHandler(AiUpstreamException.class)
  public ResponseEntity<ErrorResponse> aiFailure(AiUpstreamException exception) {
    return response(HttpStatus.BAD_GATEWAY, "AI_UPSTREAM_ERROR", exception.getMessage());
  }

  @ExceptionHandler(Exception.class)
  public ResponseEntity<ErrorResponse> internal(Exception exception) {
    LOG.error("Unhandled API failure", exception);
    return response(HttpStatus.INTERNAL_SERVER_ERROR, "INTERNAL_ERROR", "服务内部错误。");
  }

  private ResponseEntity<ErrorResponse> response(HttpStatus status, String code, String message) {
    return ResponseEntity.status(status).body(new ErrorResponse(code, message));
  }
}
