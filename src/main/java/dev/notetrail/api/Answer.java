package dev.notetrail.api;

import java.time.Instant;
import java.util.List;

public record Answer(
    long id, String question, String answer, String mode, List<Hit> citations, Instant createdAt) {}
