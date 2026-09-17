package dev.notetrail.api;

import com.fasterxml.jackson.databind.JsonNode;

public record SearchRequest(String query, JsonNode topK) {}
