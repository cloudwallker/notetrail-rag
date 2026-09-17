package dev.notetrail.api;

import com.fasterxml.jackson.databind.JsonNode;

public record QuestionRequest(String question, JsonNode topK) {}
