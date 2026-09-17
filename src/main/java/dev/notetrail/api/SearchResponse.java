package dev.notetrail.api;

import java.util.List;

public record SearchResponse(List<Hit> hits) {}
