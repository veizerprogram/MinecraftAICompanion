package com.vitalik.aicompanion.entity;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.StringTag;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.nio.file.Path;
import java.io.IOException;

/**
 * Persistent memory for meaningful events in Chatty's life.
 * Memories are stored with the companion entity in its Minecraft NBT data.
 */
public final class ChattyMemory {
    // Keep a generous history while avoiding an endlessly growing entity save tag.
    private static final int MAX_MEMORIES = 512;

    private final List<String> memories = new ArrayList<>();
    private String currentGoal = "Познайомитися зі світом";

    private int failedPlans;
    private int successfulPlans;
    private Path persistenceFile;

    public void remember(String event) {
        if (event == null || event.isBlank()) return;

        String entry = event.length() > 240 ? event.substring(0, 240) : event;
        // Repeated identical events in a row add no useful information.
        if (!memories.isEmpty() && memories.get(memories.size() - 1).equals(entry)) return;

        memories.add(entry);
        appendToDisk(entry);
        while (memories.size() > MAX_MEMORIES) {
            memories.remove(0);
        }
    }

    public void learnFromFailure(String lesson) {
        failedPlans++;
        remember("Урок після невдачі: " + lesson);
    }

    public void recordSuccess(String note) {
        successfulPlans++;
        remember("Успіх: " + note);
    }

    public void setCurrentGoal(String goal) {
        if (goal == null || goal.isBlank() || goal.equals(currentGoal)) return;
        currentGoal = goal;
        remember("Нова ціль: " + goal);
    }

    public String getCurrentGoal() {
        return currentGoal;
    }

    public int getFailedPlans() {
        return failedPlans;
    }

    public int getSuccessfulPlans() {
        return successfulPlans;
    }

    public List<String> getMemories() {
        return Collections.unmodifiableList(memories);
    }

    public String latestMemories(int amount) {
        if (memories.isEmpty()) return "Поки що я майже нічого не пережив.";
        int from = Math.max(0, memories.size() - Math.max(1, amount));
        return String.join(" | ", memories.subList(from, memories.size()));
    }


    /**
     * Connects this memory to its per-world JSONL archive. The archive keeps the
     * complete event history on disk; only the most recent MAX_MEMORIES are kept
     * in the entity's fast in-memory/NBT cache.
     */
    public void connectDiskArchive(Path file) {
        if (file == null) return;
        this.persistenceFile = file;
        java.util.Set<String> archived = new java.util.HashSet<>();
        try {
            if (java.nio.file.Files.exists(file)) {
                for (String line : java.nio.file.Files.readAllLines(file, java.nio.charset.StandardCharsets.UTF_8)) {
                    try {
                        com.google.gson.JsonObject obj = com.google.gson.JsonParser.parseString(line).getAsJsonObject();
                        if (!obj.has("event")) continue;
                        String event = obj.get("event").getAsString();
                        archived.add(event);
                        rememberFromArchive(event);
                    } catch (RuntimeException ignored) {
                        // Skip a damaged line; one bad record must not break the world.
                    }
                }
            }
            Path parent = file.getParent();
            if (parent != null) java.nio.file.Files.createDirectories(parent);
            // Preserve any events already present in the entity save but not yet archived.
            for (String event : memories) {
                if (archived.add(event)) appendToDisk(event);
            }
        } catch (IOException ignored) {
            this.persistenceFile = null;
        }
    }

    private void rememberFromArchive(String event) {
        if (event == null || event.isBlank()) return;
        String entry = event.length() > 240 ? event.substring(0, 240) : event;
        if (memories.contains(entry)) return;
        memories.add(entry);
        while (memories.size() > MAX_MEMORIES) memories.remove(0);
    }

    private void appendToDisk(String event) {
        if (persistenceFile == null) return;
        try {
            Path parent = persistenceFile.getParent();
            if (parent != null) java.nio.file.Files.createDirectories(parent);
            com.google.gson.JsonObject obj = new com.google.gson.JsonObject();
            obj.addProperty("event", event);
            String line = new com.google.gson.Gson().toJson(obj) + System.lineSeparator();
            java.nio.file.Files.writeString(persistenceFile, line,
                    java.nio.charset.StandardCharsets.UTF_8,
                    java.nio.file.StandardOpenOption.CREATE,
                    java.nio.file.StandardOpenOption.APPEND);
        } catch (IOException ignored) {
            // NBT remains the fallback if the archive cannot be written.
        }
    }

    public void save(CompoundTag tag) {
        ListTag list = new ListTag();
        for (String memory : memories) {
            list.add(StringTag.valueOf(memory));
        }
        tag.put("ChattyMemories", list);
        tag.putString("ChattyCurrentGoal", currentGoal);
        tag.putInt("ChattyFailedPlans", failedPlans);
        tag.putInt("ChattySuccessfulPlans", successfulPlans);
    }

    public void load(CompoundTag tag) {
        memories.clear();
        ListTag list = tag.getList("ChattyMemories", 8);
        for (int i = 0; i < list.size(); i++) {
            remember(list.getString(i));
        }
        if (tag.contains("ChattyCurrentGoal")) {
            currentGoal = tag.getString("ChattyCurrentGoal");
        }
        failedPlans = tag.getInt("ChattyFailedPlans");
        successfulPlans = tag.getInt("ChattySuccessfulPlans");
    }
}
