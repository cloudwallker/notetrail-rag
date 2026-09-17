package dev.notetrail.api;

import java.time.Instant;

public record DocumentView(long id, String title, int chunkCount, Instant createdAt) {}
