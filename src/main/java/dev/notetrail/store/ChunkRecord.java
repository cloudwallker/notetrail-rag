package dev.notetrail.store;

public record ChunkRecord(long id, long documentId, String title, int position, String text) {}
