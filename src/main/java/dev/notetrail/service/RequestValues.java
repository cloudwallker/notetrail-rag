package dev.notetrail.service;

import com.fasterxml.jackson.databind.JsonNode;
import dev.notetrail.api.ApiValidationException;

public final class RequestValues {
  private RequestValues() {}

  public static int topK(JsonNode value) {
    if (value == null || value.isNull()) {
      return 5;
    }
    if (!value.isIntegralNumber() || !value.canConvertToInt()) {
      throw new ApiValidationException("topK 必须是整数。");
    }
    int topK = value.intValue();
    if (topK < 1 || topK > 10) {
      throw new ApiValidationException("topK 必须在 1 到 10 之间。");
    }
    return topK;
  }
}
