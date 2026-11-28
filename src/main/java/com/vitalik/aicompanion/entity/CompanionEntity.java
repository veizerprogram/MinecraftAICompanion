package com.vitalik.aicompanion.entity;

import com.vitalik.aicompanion.ChattyAIConfig;
import com.vitalik.aicompanion.ChattyBrain;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.goal.FloatGoal;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.ai.goal.LookAtPlayerGoal;
import net.minecraft.world.entity.ai.goal.MeleeAttackGoal;
import net.minecraft.world.entity.ai.goal.RandomLookAroundGoal;
import net.minecraft.world.entity.ai.goal.WaterAvoidingRandomStrollGoal;
import net.minecraft.world.entity.ai.goal.target.HurtByTargetGoal;
import net.minecraft.world.entity.ai.goal.target.NearestAttackableTargetGoal;
import net.minecraft.world.entity.ai.util.DefaultRandomPos;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.EnumSet;
import java.util.UUID;

public class CompanionEntity extends PathfinderMob {
    private UUID ownerUUID;
    private boolean staying = false;
    private String lastObservedChat = "";
    private final Deque<String> conversationMemory = new ArrayDeque<>();
    private int lastAutonomyCheckTick = 0;
    private boolean exploring = false;
    private Vec3 lastObservedOwnerPosition;

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
        this.goalSelector.addGoal(3, new FollowOwnerGoal(this, 1.08D, 5.5F, 2.7F));

        this.goalSelector.addGoal(5, new WaterAvoidingRandomStrollGoal(this, 0.65D) {
            @Override
            public boolean canUse() {
                if (ownerUUID == null || staying || exploring) return false;
                Player owner = CompanionEntity.this.level().getPlayerByUUID(ownerUUID);
                if (owner == null || !owner.isAlive()) return false;
                double distance = CompanionEntity.this.distanceToSqr(owner);
                return distance > 4.5D * 4.5D && distance < 9.0D * 9.0D
                        && CompanionEntity.this.getRandom().nextInt(4) == 0
                        && super.canUse();
            }
        });

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
                    : "Chatty: Я поруч. Можемо вирушати!"));
        } else {
            player.sendSystemMessage(Component.literal("Chatty: Я вже маю напарника."));
        }
        return InteractionResult.CONSUME;
    }

    public boolean isOwnedBy(Player player) {
        return ownerUUID != null && ownerUUID.equals(player.getUUID());
    }

    public UUID getOwnerUUID() {
        return ownerUUID;
    }

    public void rememberChat(String message) {
        if (message == null || message.isBlank()) return;
        lastObservedChat = message.length() > 240 ? message.substring(0, 240) : message;
        addMemory("Ти: " + lastObservedChat);
    }

    public void rememberHeardMessage(String speaker, String message) {
        if (speaker == null || message == null || message.isBlank()) return;
        String safeMessage = message.length() > 200 ? message.substring(0, 200) : message;
        addMemory(speaker + ": " + safeMessage);
    }

    public void rememberAssistantMessage(String message) {
        addMemory("Chatty: " + message);
    }

    private void addMemory(String entry) {
        conversationMemory.addLast(entry.length() > 260 ? entry.substring(0, 260) : entry);
        while (conversationMemory.size() > 12) conversationMemory.removeFirst();
    }

    public String getConversationContext() {
        return String.join("\n", conversationMemory);
    }

    public String observeOwnerMovement(Player owner) {
        Vec3 current = owner.position();
        if (lastObservedOwnerPosition == null) {
            lastObservedOwnerPosition = current;
            return "перше спостереження";
        }
        Vec3 delta = current.subtract(lastObservedOwnerPosition);
        lastObservedOwnerPosition = current;
        return String.format(java.util.Locale.ROOT, "ΔX %.1f, ΔY %.1f, ΔZ %.1f; пройдена відстань %.1f блоків",
                delta.x, delta.y, delta.z, Math.sqrt(delta.horizontalDistanceSqr() + delta.y * delta.y));
    }

    public String getLastObservedChat() {
        return lastObservedChat;
    }

    public boolean isStaying() {
        return staying;
    }

    public void setStaying(boolean staying) {
        this.staying = staying;
        this.exploring = false;
        this.getNavigation().stop();
    }

    public void followOwner() {
        this.staying = false;
        this.exploring = false;
        this.getNavigation().stop();
    }

    public boolean exploreNearby() {
        this.staying = false;
        this.getNavigation().stop();
        Vec3 target = DefaultRandomPos.getPos(this, 8, 5);
        if (target == null) return false;
        boolean started = this.getNavigation().moveTo(target.x, target.y, target.z, 0.9D);
        this.exploring = started;
        return started;
    }

    @Override
    public void tick() {
        super.tick();
        if (this.level().isClientSide || ownerUUID == null || staying || this.tickCount % 20 != 0) return;
        if (exploring && !this.getNavigation().isInProgress()) exploring = false;

        int intervalTicks = ChattyAIConfig.AUTONOMY_INTERVAL_SECONDS.get() * 20;
        if (this.tickCount - lastAutonomyCheckTick < intervalTicks) return;
        lastAutonomyCheckTick = this.tickCount;

        Player owner = this.level().getPlayerByUUID(ownerUUID);
        if (owner instanceof ServerPlayer serverPlayer && owner.isAlive()
                && this.distanceToSqr(owner) <= 24.0D * 24.0D) {
            if (ChattyBrain.isConfigured()) {
                ChattyBrain.thinkAutonomously(this, serverPlayer);
            } else if (this.getRandom().nextInt(3) == 0) {
                String[] suggestions = {
                        "Може, дослідимо околиці? Раптом знайдемо щось цікаве.",
                        "Як щодо невеликої пригоди? Я готовий вирушати.",
                        "Можемо назбирати ресурсів або пошукати місце для бази."
                };
                serverPlayer.sendSystemMessage(Component.literal("Chatty: "
                        + suggestions[this.getRandom().nextInt(suggestions.length)]));
            }
        }
    }

    @Override
    public void addAdditionalSaveData(CompoundTag tag) {
        super.addAdditionalSaveData(tag);
        if (ownerUUID != null) tag.putUUID("OwnerUUID", ownerUUID);
        tag.putBoolean("Staying", staying);
        tag.putString("LastObservedChat", lastObservedChat);

        ListTag history = new ListTag();
        for (String entry : conversationMemory) history.add(StringTag.valueOf(entry));
        tag.put("ChatHistory", history);
    }

    @Override
    public void readAdditionalSaveData(CompoundTag tag) {
        super.readAdditionalSaveData(tag);
        if (tag.hasUUID("OwnerUUID")) ownerUUID = tag.getUUID("OwnerUUID");
        staying = tag.getBoolean("Staying");
        lastObservedChat = tag.getString("LastObservedChat");

        conversationMemory.clear();
        ListTag history = tag.getList("ChatHistory", 8);
        for (int i = Math.max(0, history.size() - 12); i < history.size(); i++) {
            conversationMemory.addLast(history.getString(i));
        }
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
            if (companion.ownerUUID == null || companion.staying || companion.exploring) return false;
            Player candidate = companion.level().getPlayerByUUID(companion.ownerUUID);
            if (candidate == null || candidate.isSpectator() || !candidate.isAlive()) return false;
            if (companion.distanceToSqr(candidate) < startDistance * startDistance) return false;
            owner = candidate;
            return true;
        }

        @Override
        public boolean canContinueToUse() {
            return owner != null && owner.isAlive() && !companion.staying && !companion.exploring
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
}
