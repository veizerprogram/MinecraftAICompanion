package com.vitalik.aicompanion;

import com.vitalik.aicompanion.entity.CompanionEntity;
import com.vitalik.aicompanion.registry.ModEntities;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;
import net.minecraftforge.event.entity.EntityAttributeCreationEvent;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;

@Mod(AICompanionMod.MODID)
public class AICompanionMod {
    public static final String MODID = "ai_companion";

    public AICompanionMod() {
        IEventBus bus = FMLJavaFMLLoadingContext.get().getModEventBus();
        ModEntities.ENTITY_TYPES.register(bus);
        bus.addListener(this::registerAttributes);
    }

    private void registerAttributes(EntityAttributeCreationEvent event) {
        event.put(ModEntities.COMPANION.get(), CompanionEntity.createAttributes().build());
    }
}
