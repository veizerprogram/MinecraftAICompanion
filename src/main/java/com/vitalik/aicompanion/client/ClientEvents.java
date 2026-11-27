
package com.vitalik.aicompanion.client;

import com.vitalik.aicompanion.AICompanionMod;
import com.vitalik.aicompanion.registry.ModEntities;
import com.vitalik.aicompanion.entity.CompanionEntity;

import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.model.geom.ModelLayers;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.MobRenderer;
import net.minecraft.resources.ResourceLocation;

import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.EntityRenderersEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

@Mod.EventBusSubscriber(
        modid = AICompanionMod.MODID,
        bus = Mod.EventBusSubscriber.Bus.MOD,
        value = Dist.CLIENT
)
public class ClientEvents {

    @SubscribeEvent
    public static void registerRenderers(EntityRenderersEvent.RegisterRenderers event) {
        event.registerEntityRenderer(
                ModEntities.COMPANION.get(),
                CompanionRenderer::new
        );
    }

    private static class CompanionRenderer
            extends MobRenderer<CompanionEntity, HumanoidModel<CompanionEntity>> {

        private static final ResourceLocation TEXTURE =
                new ResourceLocation(AICompanionMod.MODID,
                        "textures/entity/chatty.png");

        public CompanionRenderer(EntityRendererProvider.Context context) {
            super(
                    context,
                    new HumanoidModel<>(context.bakeLayer(ModelLayers.PLAYER)),
                    0.5F
            );
        }

        @Override
        public ResourceLocation getTextureLocation(CompanionEntity entity) {
            return TEXTURE;
        }
    }
}
