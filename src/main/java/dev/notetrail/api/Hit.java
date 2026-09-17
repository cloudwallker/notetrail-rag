package dev.notetrail.api;

public record Hit(
    long documentId, String title, long chunkId, int position, String text, double score) {}
