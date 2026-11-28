package com.vitalik.aicompanion;

import com.vitalik.aicompanion.entity.CompanionEntity;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.event.ServerChatEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.Locale;

@Mod.EventBusSubscriber(modid = AICompanionMod.MODID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public class ChattyChatEvents {

    @SubscribeEvent
    public static void onPlayerChat(ServerChatEvent event) {
        ServerPlayer player = event.getPlayer();
        String original = event.getMessage().getString();

        // Chatty locally remembers messages visible in the loaded part of this dimension.
        // Only the owner's messages are forwarded to the external AI provider.
        for (var entity : player.serverLevel().getAllEntities()) {
            if (!(entity instanceof CompanionEntity companion)) continue;

            if (companion.isOwnedBy(player)) {
                companion.rememberChat(original);

                // Always execute deterministic companion commands before asking the remote AI.
                String reply = interpretMessage(companion, original.toLowerCase(Locale.ROOT));
                if (reply != null) {
                    companion.rememberAssistantMessage(reply);
                    player.sendSystemMessage(Component.literal("Chatty: " + reply));
                    continue;
                }

                // Free-form conversation is sent to the AI only after local commands were checked.
                if (!ChattyBrain.answerPlayerMessage(companion, player, original)
                        && addressesChatty(original)) {
                    String fallback = ChattyBrain.isConfigured()
                            ? "Я почув тебе, але зараз зайнятий попереднім запитом. Спробуй ще раз за мить."
                            : "Я тебе чую! Але мій AI-мозок ще не під'єднаний. Поки що можу виконувати команди: «йди за мною», «стій», «досліди околиці».";
                    companion.rememberAssistantMessage(fallback);
                    player.sendSystemMessage(Component.literal("Chatty: " + fallback));
                }
            } else {
                companion.rememberHeardMessage(player.getName().getString(), original);
            }
        }
    }

    private static String interpretMessage(CompanionEntity companion, String message) {
        if (containsAny(message, "стій", "стой", "почекай", "зачекай", "залишайся", "охороняй",
                "чекай тут", "не рухайся", "залишся тут", "постій тут")) {
            companion.setStaying(true);
            return "Добре, залишаюся тут. Дай знати, коли вирушимо.";
        }

        if (containsAny(message, "ходімо", "ходім", "пішли", "пойдем", "за мною",
                "йди за мною", "іди за мною", "ходи сюди", "іди сюди", "йди сюди", "до мене", "вирушаймо",
                "пішли зі мною", "супроводжуй", "ходім зі мною", "повернись", "повертайся")) {
            companion.followOwner();
            return "Я з тобою! Не відставаймо одне від одного.";
        }

        if (containsAny(message, "досліди", "дослідимо", "розвід", "оглянь околиц",
                "досліди околиц", "погуляй", "розвідай", "подивись навколо", "оглянься", "розвідай місцевість")) {
            return companion.exploreNearby()
                    ? "Гаразд, трохи розвідаю місцевість."
                    : "Не можу зараз знайти безпечний шлях для розвідки.";
        }

        if (containsAny(message, "що робиш", "чим займ", "ти де", "ти тут", "як справи", "що думаєш")) {
            if (containsAny(message, "що робиш", "чим займ")) {
                return companion.isStaying()
                        ? "Поки що стою на варті й чекаю на тебе."
                        : "Тримаюся поруч і стежу за тим, що відбувається навколо.";
            }
            return "Я тут! Чую тебе. Що плануємо робити?";
        }

        if (containsAny(message, "привіт", "вітаю", "здоров", "доброго ранку", "добрий вечір")) {
            return "Привіт! Радий тебе чути. Я готовий до пригод.";
        }

        if (addressesChatty(message)) {
            return "Я слухаю! Можеш попросити мене йти за тобою, зачекати або дослідити околиці.";
        }

        return null;
    }

    private static boolean addressesChatty(String message) {
        String normalized = message.toLowerCase(Locale.ROOT);
        return containsAny(normalized, "chatty", "чатті", "чатти", "чатік", "чатик");
    }

    private static boolean containsAny(String message, String... phrases) {
        for (String phrase : phrases) {
            if (message.contains(phrase)) return true;
        }
        return false;
    }
}
