package com.vitalik.aicompanion.entity;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.StringTag;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Persistent short-form memory for Chatty.
 * Memories are stored with the companion entity in its Minecraft NBT data.
 */
public final class ChattyMemory {
    private static final int MAX_MEMORIES = 128;

    private final List<String> memories = new ArrayList<>();
    private String currentGoal = "Познайомитися зі світом";
    private int failedPlans;
    private int successfulPlans;

    public void remember(String event) {
        if (event == null || event.isBlank()) return;
        memories.add(event.length() > 240 ? event.substring(0, 240) : event);
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
        if (goal != null && !goal.isBlank()) currentGoal = goal;
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
        int from = Math.max(0, memories.size() - amount);
        return String.join(" | ", memories.subList(from, memories.size()));
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
