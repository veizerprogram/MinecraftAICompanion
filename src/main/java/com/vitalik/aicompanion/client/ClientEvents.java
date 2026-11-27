package com.vitalik.aicompanion.client;

import com.vitalik.aicompanion.AICompanionMod;
import com.vitalik.aicompanion.registry.ModEntities;
import net.minecraft.client.model.PlayerModel;
import net.minecraft.client.model.geom.ModelLayers;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.MobRenderer;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.EntityRenderersEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

@Mod.EventBusSubscriber(modid = AICompanionMod.MODID, bus = Mod.EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
public class ClientEvents {
    @SubscribeEvent
    public static void registerRenderers(EntityRenderersEvent.RegisterRenderers event) {
        event.registerEntityRenderer(ModEntities.COMPANION.get(), CompanionRenderer::new);
    }

    private static class CompanionRenderer extends MobRenderer<com.vitalik.aicompanion.entity.CompanionEntity, PlayerModel<com.vitalik.aicompanion.entity.CompanionEntity>> {
        private static final ResourceLocation TEXTURE =
                new ResourceLocation(AICompanionMod.MODID, "textures/entity/chatty.png");

        CompanionRenderer(EntityRendererProvider.Context context) {
            super(context, new PlayerModel<>(context.bakeLayer(ModelLayers.PLAYER), false), 0.5F);
        }

        @Override
        public ResourceLocation getTextureLocation(com.vitalik.aicompanion.entity.CompanionEntity entity) {
            return TEXTURE;
        }
    }
}
