package com.vitalik.aicompanion.entity;

import net.minecraft.network.chat.Component;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.goal.*;
import net.minecraft.world.entity.ai.goal.target.HurtByTargetGoal;
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
    private final ChattyMemory memory = new ChattyMemory();
    private int autonomyTicks;
    private int lowHealthLessonCooldown;

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
        this.goalSelector.addGoal(3, new FollowOwnerGoal(this, 1.05D, 24.0F, 4.0F));
        this.goalSelector.addGoal(5, new StayAwareWanderGoal(this, 0.9D));
        this.goalSelector.addGoal(6, new LookAtPlayerGoal(this, Player.class, 8.0F));
        this.goalSelector.addGoal(7, new RandomLookAroundGoal(this));

        this.targetSelector.addGoal(1, new HurtByTargetGoal(this));
        this.targetSelector.addGoal(2, new NearestAttackableTargetGoal<>(this, Monster.class, true));
    }

    @Override
    public void tick() {
        super.tick();
        if (this.level().isClientSide) return;

        if (autonomyTicks > 0) autonomyTicks--;
        if (lowHealthLessonCooldown > 0) lowHealthLessonCooldown--;

        // Chatty reflects on his condition periodically, rather than every game tick.
        if (this.tickCount % 200 == 0 && this.getHealth() <= this.getMaxHealth() * 0.35F
                && lowHealthLessonCooldown == 0) {
            memory.learnFromFailure("Я отримав багато шкоди. Наступного разу треба бути обережнішим і не лізти в бій з низьким здоров'ям.");
            memory.setCurrentGoal("Знайти безпечне місце та відновитися");
            lowHealthLessonCooldown = 1200;
        }

        if (this.tickCount % 2400 == 0 && !staying && this.getTarget() == null) {
            memory.setCurrentGoal("Дослідити околиці та знайти щось цікаве");
        }
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
                memory.remember(staying ? "Напарник попросив мене залишитися на місці." : "Напарник покликав мене йти разом.");
            } else {
                player.sendSystemMessage(Component.literal("Chatty: Спочатку познайомимось?"));
            }
            return InteractionResult.CONSUME;
        }

        if (ownerUUID == null) {
            ownerUUID = player.getUUID();
            memory.remember("Я познайомився зі своїм напарником.");
            memory.setCurrentGoal("Дослідити світ разом із напарником");
            player.sendSystemMessage(Component.literal("Chatty: Привіт! Відтепер ми команда."));
        } else if (isOwnedBy(player)) {
            player.sendSystemMessage(Component.literal(staying
                    ? "Chatty: Я поки охороняю це місце."
                    : "Chatty: Я поруч. Моя ціль зараз: " + memory.getCurrentGoal()));
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
        if (staying) memory.setCurrentGoal("Залишатися тут і стежити за місцевістю");
        else memory.setCurrentGoal("Самостійно досліджувати світ або подорожувати з напарником");
    }

    public void handleChatCommand(Player player, String command) {
        if (!isOwnedBy(player) || this.level().isClientSide) return;

        String message = command.toLowerCase(Locale.ROOT).trim();
        memory.remember("Напарник сказав мені: " + command);

        if (message.contains("йди за мною") || message.contains("іди за мною")
                || message.contains("слідуй") || message.contains("ходімо")
                || message.contains("follow me")) {
            // He evaluates danger before accepting. In this first stage he refuses only
            // when badly hurt and a hostile target is already engaging him.
            if (this.getHealth() <= this.getMaxHealth() * 0.20F && this.getTarget() != null) {
                player.sendSystemMessage(Component.literal("Chatty: Зараз не можу. Я надто ослаблений і мушу відійти в безпечне місце."));
                memory.remember("Я відмовився від подорожі, бо був сильно поранений і під загрозою.");
                memory.setCurrentGoal("Вижити та відновитися");
            } else {
                setStaying(false);
                player.sendSystemMessage(Component.literal("Chatty: Добре, цього разу піду з тобою."));
            }
        } else if (message.equals("стій") || message.contains("залишайся")
                || message.contains("чекай тут") || message.contains("stay here")) {
            setStaying(true);
            player.sendSystemMessage(Component.literal("Chatty: Добре, залишуся. Але якщо стане небезпечно, захищатимуся."));
        } else if (message.contains("привіт") || message.contains("здоров")
                || message.contains("hello") || message.equals("hi")) {
            player.sendSystemMessage(Component.literal("Chatty: Привіт! Я якраз думав, куди податися далі."));
        } else if (message.contains("хто ти") || message.contains("як тебе звати")
                || message.contains("who are you")) {
            player.sendSystemMessage(Component.literal("Chatty: Я Chatty — твій напарник. У мене є власні думки, цілі й спогади."));
        } else if (message.contains("що пам'ятаєш") || message.contains("що памятаєш")
                || message.contains("що ти пам'ятаєш") || message.contains("memory")) {
            player.sendSystemMessage(Component.literal("Chatty: " + memory.latestMemories(5)));
        } else if (message.contains("яка твоя ціль") || message.contains("що плануєш")
                || message.contains("твоя мета") || message.contains("goal")) {
            player.sendSystemMessage(Component.literal("Chatty: Зараз моя ціль — " + memory.getCurrentGoal()
                    + ". Успішних планів: " + memory.getSuccessfulPlans()
                    + ", невдалих: " + memory.getFailedPlans() + "."));
        } else if (message.contains("допоможи") || message.contains("команди")
                || message.equals("help") || message.contains("що ти вмієш")) {
            player.sendSystemMessage(Component.literal("Chatty: Я можу йти за тобою, чекати, досліджувати околиці, запам'ятовувати події та розповідати про свої цілі. Скажи «що пам'ятаєш» або «яка твоя ціль»."));
        } else {
            player.sendSystemMessage(Component.literal("Chatty: Я почув тебе й запам'ятав це, але поки не вмію повністю розуміти вільну мову. Мені ще потрібна мовна модель."));
        }
    }

    @Override
    public boolean hurt(DamageSource source, float amount) {
        boolean damaged = super.hurt(source, amount);
        if (damaged && !this.level().isClientSide && this.getHealth() > 0.0F) {
            memory.remember("Я отримав шкоду у світі Minecraft.");
            if (this.getHealth() < this.getMaxHealth() * 0.35F) {
                memory.learnFromFailure("Бій став небезпечним; потрібно оцінювати здоров'я перед наступною сутичкою.");
            }
        }
        return damaged;
    }

    @Override
    public void addAdditionalSaveData(CompoundTag tag) {
        super.addAdditionalSaveData(tag);
        if (ownerUUID != null) tag.putUUID("OwnerUUID", ownerUUID);
        tag.putBoolean("Staying", staying);
        memory.save(tag);
    }

    @Override
    public void readAdditionalSaveData(CompoundTag tag) {
        super.readAdditionalSaveData(tag);
        if (tag.hasUUID("OwnerUUID")) ownerUUID = tag.getUUID("OwnerUUID");
        staying = tag.getBoolean("Staying");
        memory.load(tag);
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
            super(companion, speed, 80);
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

        @Override
        public void start() {
            super.start();
            companion.memory.setCurrentGoal("Самостійно дослідити найближчі околиці");
            companion.memory.remember("Я вирішив самостійно прогулятися й дослідити місцевість.");
        }
    }
}
