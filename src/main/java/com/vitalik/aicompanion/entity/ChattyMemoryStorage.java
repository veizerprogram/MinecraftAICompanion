package com.vitalik.aicompanion.entity;

/**
 * Memory archive format documentation.
 *
 * Chatty's archive is stored under:
 *   <world>/chatty_memory/<companion-uuid>.jsonl
 *
 * Each UTF-8 line is one compact JSON object, for example:
 *   {"event":"Я познайомився зі своїм напарником."}
 *
 * JSON Lines makes the archive append-friendly and allows one damaged line
 * to be skipped without losing the rest of the history. The active cache is
 * bounded in ChattyMemory, while this file preserves the longer history.
 */
public final class ChattyMemoryStorage {
    private ChattyMemoryStorage() {
    }
}
