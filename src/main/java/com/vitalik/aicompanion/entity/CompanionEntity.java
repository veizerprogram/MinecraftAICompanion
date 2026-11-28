package com.vitalik.aicompanion.entity;

import net.minecraft.world.entity.ai.goal.target.HurtByTargetGoal;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.goal.*;
import net.minecraft.world.entity.ai.goal.target.NearestAttackableTargetGoal;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.entity.Mob;
import net.minecraft.nbt.CompoundTag;

import java.util.EnumSet;
import java.util.Locale;
import java.util.UUID;

public class CompanionEntity extends PathfinderMob {
    private UUID ownerUUID;
    private boolean staying = false;

    public CompanionEntity(EntityType<? extends PathfinderMob> type, Level level) {
        super(type, level);
        this.setCanPickUpLoot(true);
    }

    public static AttributeSupplier.Builder createAttributes() {
        return Mob.createMobAttributes()
                .add(Attributes.MAX_HEALTH, 30.0D)
                .add(Attributes.MOVEMENT_SPEED, 0.30D)
                .add(Attributes.ATTACK_DAMAGE, 5.0D)
                .add(Attributes.FOLLOW_RANGE, 32.0D)
                .add(Attributes.ARMOR, 2.0D);
    }

    @Override
    protected void registerGoals() {
        this.goalSelector.addGoal(1, new FloatGoal(this));
        this.goalSelector.addGoal(2, new MeleeAttackGoal(this, 1.15D, true));
        this.goalSelector.addGoal(3, new FollowOwnerGoal(this, 1.05D, 12.0F, 3.0F));
        this.goalSelector.addGoal(5, new StayAwareWanderGoal(this, 0.8D));
        this.goalSelector.addGoal(6, new LookAtPlayerGoal(this, Player.class, 8.0F));
        this.goalSelector.addGoal(7, new RandomLookAroundGoal(this));

        this.targetSelector.addGoal(1, new HurtByTargetGoal(this));
        this.targetSelector.addGoal(2, new NearestAttackableTargetGoal<>(this, Monster.class, true));
    }

    @Override
    public InteractionResult mobInteract(Player player, InteractionHand hand) {
        if (this.level().isClientSide) return InteractionResult.SUCCESS;

        if (player.isShiftKeyDown()) {
            if (isOwnedBy(player)) {
                setStaying(!staying);
                player.sendSystemMessage(Component.literal(staying
                        ? "Chatty: Добре, залишаюся тут."
                        : "Chatty: Гаразд, я з тобою!"));
            } else {
                player.sendSystemMessage(Component.literal("Chatty: Спочатку познайомимось?"));
            }
            return InteractionResult.CONSUME;
        }

        if (ownerUUID == null) {
            ownerUUID = player.getUUID();
            player.sendSystemMessage(Component.literal("Chatty: Привіт! Відтепер ми команда."));
        } else if (isOwnedBy(player)) {
            player.sendSystemMessage(Component.literal(staying
                    ? "Chatty: Я поки охороняю це місце."
                    : "Chatty: Я поруч. Куди вирушаємо?"));
        } else {
            player.sendSystemMessage(Component.literal("Chatty: Я вже маю напарника."));
        }
        return InteractionResult.CONSUME;
    }

    public boolean isOwnedBy(Player player) {
        return ownerUUID != null && ownerUUID.equals(player.getUUID());
    }

    public void setStaying(boolean staying) {
        this.staying = staying;
        this.getNavigation().stop();
    }

    public void handleChatCommand(Player player, String command) {
        if (!isOwnedBy(player) || this.level().isClientSide) return;

        String message = command.toLowerCase(Locale.ROOT).trim();

        if (message.contains("йди за мною") || message.contains("іди за мною")
                || message.contains("слідуй") || message.contains("ходімо")
                || message.contains("follow me")) {
            setStaying(false);
            player.sendSystemMessage(Component.literal("Chatty: Зрозумів! Тримаюся поруч."));
        } else if (message.equals("стій") || message.contains("залишайся")
                || message.contains("чекай тут") || message.contains("stay here")) {
            setStaying(true);
            player.sendSystemMessage(Component.literal("Chatty: Добре, чекатиму тут."));
        } else if (message.contains("привіт") || message.contains("здоров")
                || message.contains("hello") || message.contains("hi")) {
            player.sendSystemMessage(Component.literal("Chatty: Привіт! Радий пригодам. Куди сьогодні вирушаємо?"));
        } else if (message.contains("хто ти") || message.contains("як тебе звати")
                || message.contains("who are you")) {
            player.sendSystemMessage(Component.literal("Chatty: Я Chatty — твій напарник у цьому світі Minecraft!"));
        } else if (message.contains("допоможи") || message.contains("команди")
                || message.equals("help") || message.contains("що ти вмієш")) {
            player.sendSystemMessage(Component.literal("Chatty: Поки я вчуся! Спробуй: «йди за мною», «залишайся тут», «привіт» або «хто ти»."));
        } else {
            player.sendSystemMessage(Component.literal("Chatty: Я почув тебе, але поки не вмію відповісти на таке. Напиши «Chatty, допоможи» — покажу, що вже розумію."));
        }
    }

    @Override
    public void addAdditionalSaveData(CompoundTag tag) {
        super.addAdditionalSaveData(tag);
        if (ownerUUID != null) tag.putUUID("OwnerUUID", ownerUUID);
        tag.putBoolean("Staying", staying);
    }

    @Override
    public void readAdditionalSaveData(CompoundTag tag) {
        super.readAdditionalSaveData(tag);
        if (tag.hasUUID("OwnerUUID")) ownerUUID = tag.getUUID("OwnerUUID");
        staying = tag.getBoolean("Staying");
    }

    private static class FollowOwnerGoal extends Goal {
        private final CompanionEntity companion;
        private final double speed;
        private final float startDistance;
        private final float stopDistance;
        private Player owner;

        FollowOwnerGoal(CompanionEntity companion, double speed, float startDistance, float stopDistance) {
            this.companion = companion;
            this.speed = speed;
            this.startDistance = startDistance;
            this.stopDistance = stopDistance;
            this.setFlags(EnumSet.of(Flag.MOVE, Flag.LOOK));
        }

        @Override
        public boolean canUse() {
            if (companion.ownerUUID == null || companion.staying) return false;
            Player candidate = companion.level().getPlayerByUUID(companion.ownerUUID);
            if (candidate == null || candidate.isSpectator()) return false;
            if (companion.distanceToSqr(candidate) < startDistance * startDistance) return false;
            owner = candidate;
            return true;
        }

        @Override
        public boolean canContinueToUse() {
            return owner != null && owner.isAlive() && !companion.staying
                    && companion.distanceToSqr(owner) > stopDistance * stopDistance;
        }

        @Override
        public void tick() {
            if (owner != null) {
                companion.getLookControl().setLookAt(owner, 10.0F, companion.getMaxHeadXRot());
                companion.getNavigation().moveTo(owner, speed);
            }
        }

        @Override
        public void stop() {
            owner = null;
            companion.getNavigation().stop();
        }
    }

    private static class StayAwareWanderGoal extends WaterAvoidingRandomStrollGoal {
        private final CompanionEntity companion;

        StayAwareWanderGoal(CompanionEntity companion, double speed) {
            super(companion, speed);
            this.companion = companion;
        }

        @Override
        public boolean canUse() {
            return !companion.staying && super.canUse();
        }

        @Override
        public boolean canContinueToUse() {
            return !companion.staying && super.canContinueToUse();
        }
    }
}
