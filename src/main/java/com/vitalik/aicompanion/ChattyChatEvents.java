package com.vitalik.aicompanion;

import com.vitalik.aicompanion.entity.CompanionEntity;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.event.ServerChatEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.List;
import java.util.Locale;

@Mod.EventBusSubscriber(modid = AICompanionMod.MODID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public class ChattyChatEvents {

    @SubscribeEvent
    public static void onPlayerChat(ServerChatEvent event) {
        ServerPlayer player = event.getPlayer();
        String original = event.getMessage().getString();

        List<CompanionEntity> companions = player.serverLevel().getEntitiesOfClass(
                CompanionEntity.class,
                player.getBoundingBox().inflate(48.0D),
                companion -> companion.isOwnedBy(player)
        );

        for (CompanionEntity companion : companions) {
            companion.rememberChat(original);

            // When configured, the AI receives every message from its owner in range.
            // The local command parser remains available if the AI is not configured.
            if (ChattyBrain.answerPlayerMessage(companion, player, original)) {
                continue;
            }

            String reply = interpretMessage(companion, original.toLowerCase(Locale.ROOT));
            if (reply != null) {
                companion.rememberAssistantMessage(reply);
                player.sendSystemMessage(Component.literal("Chatty: " + reply));
            }
        }
    }

    private static String interpretMessage(CompanionEntity companion, String message) {
        if (containsAny(message, "стій", "стой", "почекай", "зачекай", "залишайся", "охороняй")) {
            companion.setStaying(true);
            return "Добре, залишаюся тут. Дай знати, коли вирушимо.";
        }

        if (containsAny(message, "ходімо", "ходім", "пішли", "пойдем", "за мною",
                "йди за мною", "іди за мною", "ходи сюди", "іди сюди", "йди сюди", "до мене", "вирушаймо")) {
            companion.followOwner();
            return "Я з тобою! Не відставаймо одне від одного.";
        }

        if (containsAny(message, "досліди", "дослідимо", "розвід", "оглянь околиц",
                "досліди околиц", "погуляй", "розвідай")) {
            return companion.exploreNearby()
                    ? "Гаразд, трохи розвідаю місцевість."
                    : "Не можу зараз знайти безпечний шлях для розвідки.";
        }

        if (containsAny(message, "chatty", "чатті", "чатти", "що робиш", "чим займ", "ти де", "ти тут")) {
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

        return null;
    }

    private static boolean containsAny(String message, String... phrases) {
        for (String phrase : phrases) {
            if (message.contains(phrase)) return true;
        }
        return false;
    }
}
