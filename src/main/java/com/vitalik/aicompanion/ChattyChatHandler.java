package com.vitalik.aicompanion;

import com.vitalik.aicompanion.entity.CompanionEntity;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.event.ServerChatEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.Comparator;
import java.util.Locale;

@Mod.EventBusSubscriber(modid = AICompanionMod.MODID)
public class ChattyChatHandler {

    @SubscribeEvent
    public static void onPlayerChat(ServerChatEvent event) {
        ServerPlayer player = event.getPlayer();
        String message = event.getMessage().getString().trim();
        String lower = message.toLowerCase(Locale.ROOT);

        String command;
        if (lower.startsWith("чатті")) {
            command = message.substring("чатті".length()).replaceFirst("^[,:!\\s-]+", "").trim();
        } else if (lower.startsWith("chatty")) {
            command = message.substring("chatty".length()).replaceFirst("^[,:!\\s-]+", "").trim();
        } else {
            return;
        }

        if (command.isBlank()) {
            command = "привіт";
        }

        CompanionEntity companion = player.level()
                .getEntitiesOfClass(
                        CompanionEntity.class,
                        player.getBoundingBox().inflate(32.0D),
                        entity -> entity.isOwnedBy(player)
                )
                .stream()
                .min(Comparator.comparingDouble(entity -> entity.distanceToSqr(player)))
                .orElse(null);

        if (companion != null) {
            companion.handleChatCommand(player, command);
        } else {
            player.sendSystemMessage(net.minecraft.network.chat.Component.literal(
                    "Chatty: Я не бачу тебе поруч. Поклич мене ближче!"
            ));
        }
    }
}
