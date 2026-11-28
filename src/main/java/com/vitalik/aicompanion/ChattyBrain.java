package com.vitalik.aicompanion;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.vitalik.aicompanion.entity.CompanionEntity;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;

public final class ChattyBrain {
    private static final HttpClient HTTP = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(8))
            .build();
    private static final Set<UUID> REQUESTS_IN_PROGRESS = ConcurrentHashMap.newKeySet();

    private ChattyBrain() {}

    public static boolean isConfigured() {
        return ChattyAIConfig.ENABLED.get()
                && !ChattyAIConfig.API_KEY.get().isBlank()
                && !ChattyAIConfig.API_URL.get().isBlank()
                && !ChattyAIConfig.MODEL.get().isBlank();
    }

    public static boolean answerPlayerMessage(CompanionEntity companion, ServerPlayer player, String message) {
        if (!isConfigured() || !REQUESTS_IN_PROGRESS.add(companion.getUUID())) return false;

        String prompt = "Повідомлення власника: " + limit(message, 500)
                + "\n\nСтан світу:\n" + worldSnapshot(companion, player)
                + "\n\nКоротка пам'ять попередньої розмови:\n" + companion.getConversationContext();

        sendDecision(companion, player, prompt, false);
        return true;
    }

    public static void thinkAutonomously(CompanionEntity companion, ServerPlayer player) {
        if (!isConfigured() || !REQUESTS_IN_PROGRESS.add(companion.getUUID())) return;

        String prompt = "Зараз немає нової команди від власника. Самостійно оціни ситуацію та виріши, "
                + "чи варто щось зробити або запропонувати. Не вигадуй побачених подій. "
                + "Якщо немає корисної ідеї, поверни порожню reply та action NONE.\n\n"
                + "Стан світу:\n" + worldSnapshot(companion, player)
                + "\n\nПам'ять розмови:\n" + companion.getConversationContext();
        sendDecision(companion, player, prompt, true);
    }

    private static void sendDecision(CompanionEntity companion, ServerPlayer player, String prompt, boolean autonomous) {
        UUID companionId = companion.getUUID();
        UUID ownerId = player.getUUID();
        MinecraftServer server = player.getServer();

        JsonObject body = new JsonObject();
        body.addProperty("model", ChattyAIConfig.MODEL.get());
        JsonArray messages = new JsonArray();

        JsonObject system = new JsonObject();
        system.addProperty("role", "system");
        system.addProperty("content",
                "Ти Chatty — живий ШІ-напарник гравця у Minecraft Survival. "
                + "Спілкуйся природною українською, дружньо, коротко й не шаблонно. "
                + "Пам'ятай контекст, аналізуй тільки надані факти про світ і рухи гравця. "
                + "Ти можеш сам пропонувати плани, але не стверджуй, що вже виконав дію, якщо це не так. "
                + "Відповідай СТРОГО одним JSON-об'єктом без Markdown: "
                + "{\"reply\":\"текст для чату або порожньо\","
                + "\"action\":\"FOLLOW|WAIT|EXPLORE|NONE\"}. "
                + "FOLLOW означає повернутися до власника; WAIT — залишитися на місці; "
                + "EXPLORE — дослідити найближчу безпечну ділянку; NONE — не змінювати поведінку. "
                + "Обирай лише одну дію. Не проси й не розкривай API-ключі, паролі чи особисті дані.");
        messages.add(system);

        JsonObject user = new JsonObject();
        user.addProperty("role", "user");
        user.addProperty("content", prompt);
        messages.add(user);
        body.add("messages", messages);

        HttpRequest request;
        try {
            request = HttpRequest.newBuilder()
                    .uri(URI.create(ChattyAIConfig.API_URL.get().trim()))
                    .timeout(Duration.ofSeconds(25))
                    .header("Content-Type", "application/json")
                    .header("Authorization", "Bearer " + ChattyAIConfig.API_KEY.get().trim())
                    .POST(HttpRequest.BodyPublishers.ofString(body.toString()))
                    .build();
        } catch (Exception exception) {
            REQUESTS_IN_PROGRESS.remove(companionId);
            return;
        }

        HTTP.sendAsync(request, HttpResponse.BodyHandlers.ofString())
                .orTimeout(28, java.util.concurrent.TimeUnit.SECONDS)
                .whenComplete((response, error) -> {
                    REQUESTS_IN_PROGRESS.remove(companionId);
                    if (error != null || response == null || response.statusCode() < 200 || response.statusCode() >= 300) {
                        return;
                    }

                    Decision decision = parseResponse(response.body());
                    if (decision == null || server == null) return;

                    server.execute(() -> {
                        ServerPlayer currentOwner = server.getPlayerList().getPlayer(ownerId);
                        if (currentOwner == null || !currentOwner.isAlive()) return;
                        if (!(currentOwner.level().getEntity(companionId) instanceof CompanionEntity current)
                                || !current.isAlive() || !current.isOwnedBy(currentOwner)) return;

                        applyAction(current, decision.action);
                        if (!decision.reply.isBlank()) {
                            String reply = limit(decision.reply, 240);
                            current.rememberAssistantMessage(reply);
                            currentOwner.sendSystemMessage(net.minecraft.network.chat.Component.literal("Chatty: " + reply));
                        } else if (!autonomous) {
                            currentOwner.sendSystemMessage(net.minecraft.network.chat.Component.literal(
                                    "Chatty: Я почув тебе. Дай секунду подумати."));
                        }
                    });
                });
    }

    private static void applyAction(CompanionEntity companion, String action) {
        switch (action) {
            case "FOLLOW" -> companion.followOwner();
            case "WAIT" -> companion.setStaying(true);
            case "EXPLORE" -> companion.exploreNearby();
            default -> { }
        }
    }

    private static Decision parseResponse(String responseBody) {
        try {
            JsonObject root = JsonParser.parseString(responseBody).getAsJsonObject();
            String content = root.getAsJsonArray("choices")
                    .get(0).getAsJsonObject()
                    .getAsJsonObject("message")
                    .get("content").getAsString().trim();

            String cleaned = content.replaceAll("(?s)^\\s*\u0060{3}(?:json)?\\s*", "")
                    .replaceAll("(?s)\\s*\u0060{3}\\s*$", "").trim();
            JsonObject result = JsonParser.parseString(cleaned).getAsJsonObject();
            String reply = result.has("reply") && !result.get("reply").isJsonNull()
                    ? result.get("reply").getAsString() : "";
            String action = result.has("action") && !result.get("action").isJsonNull()
                    ? result.get("action").getAsString().toUpperCase(Locale.ROOT) : "NONE";
            if (!Set.of("FOLLOW", "WAIT", "EXPLORE", "NONE").contains(action)) action = "NONE";
            return new Decision(reply, action);
        } catch (Exception ignored) {
            return null;
        }
    }

    private static String worldSnapshot(CompanionEntity companion, ServerPlayer player) {
        StringBuilder out = new StringBuilder();
        ItemStack held = player.getMainHandItem();
        out.append("Гравець: ").append(player.getName().getString())
                .append("; вимір: ").append(player.level().dimension().location())
                .append("; координати: ").append(player.blockPosition())
                .append("; здоров'я: ").append(Math.round(player.getHealth()))
                .append("; голод: ").append(player.getFoodData().getFoodLevel())
                .append("; предмет у руці: ").append(held.isEmpty() ? "порожньо" : held.getHoverName().getString())
                .append("; рухається: ").append(player.getDeltaMovement().horizontalDistanceSqr() > 0.0025D)
                .append("; біжить: ").append(player.isSprinting())
                .append("; присідає: ").append(player.isCrouching())
                .append("; Chatty відстань до власника: ").append(Math.round(companion.distanceTo(player)))
                .append(" блоків; Chatty режим очікування: ").append(companion.isStaying())
                .append(".\nПоруч живі істоти: ");

        int count = 0;
        for (LivingEntity entity : player.level().getEntitiesOfClass(
                LivingEntity.class, player.getBoundingBox().inflate(12.0D),
                entity -> entity != player && entity.isAlive())) {
            if (count++ >= 8) break;
            out.append(entity.getName().getString())
                    .append(" (").append(Math.round(entity.distanceTo(player))).append(" блоків), ");
        }
        if (count == 0) out.append("немає помічених істот");
        return out.toString();
    }

    private static String limit(String value, int max) {
        if (value == null) return "";
        return value.length() <= max ? value : value.substring(0, max);
    }

    private record Decision(String reply, String action) {}
}
