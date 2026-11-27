package com.vitalik.aicompanion.registry;

import com.vitalik.aicompanion.AICompanionMod;
import com.vitalik.aicompanion.entity.CompanionEntity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

public class ModEntities {
    public static final DeferredRegister<EntityType<?>> ENTITY_TYPES =
            DeferredRegister.create(ForgeRegistries.ENTITY_TYPES, AICompanionMod.MODID);

    public static final RegistryObject<EntityType<CompanionEntity>> COMPANION =
            ENTITY_TYPES.register("companion", () -> EntityType.Builder
                    .of(CompanionEntity::new, MobCategory.CREATURE)
                    .sized(0.6F, 1.8F)
                    .clientTrackingRange(10)
                    .build("companion"));
}
