package fr.lordfinn.steveparty.items.custom.parachute;

import fr.lordfinn.steveparty.Steveparty;
import fr.lordfinn.steveparty.advancement.ModCriteria;
import fr.lordfinn.steveparty.items.ModItems;
import net.fabricmc.fabric.api.attachment.v1.AttachmentRegistry;
import net.fabricmc.fabric.api.attachment.v1.AttachmentSyncPredicate;
import net.fabricmc.fabric.api.attachment.v1.AttachmentType;
import net.fabricmc.fabric.api.event.player.UseEntityCallback;
import net.fabricmc.fabric.api.tag.convention.v2.ConventionalEntityTypeTags;
import net.minecraft.entity.Entity;
import net.minecraft.entity.ItemEntity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvent;
import net.minecraft.sound.SoundEvents;
import net.minecraft.util.ActionResult;
import net.minecraft.util.Hand;
import net.minecraft.util.math.Vec3d;
import org.jetbrains.annotations.Nullable;

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

/**
 * The parachute's rules ({@link ParachuteItem}), on the server (the open state is synced to every client, which draw
 * it and brake their own player's fall with it).
 * <ul>
 *     <li><b>A player</b> holding one opens it once he has fallen {@link #AUTO_OPEN_FALL} blocks, or at once with a use
 *     in the air; another use folds it (it stays folded until he lands). Open, he comes down slowly
 *     ({@link #DESCENT}), steering with his keys as in any fall, and takes no fall damage. It folds when he lands,
 *     swims, gets into lava or a vehicle, glides with an elytra (the elytra wins) or stops holding it. Each opening
 *     wears it by one.</li>
 *     <li><b>A mob</b> (not a player, not a boss) gets one by a use on it: packed on its back until it falls
 *     {@link #MOB_OPEN_FALL} blocks, then open the same way; landed, the parachute drops as an item, worn by one. A
 *     player takes a packed or open one back by sneaking with an empty hand on the mob. Removed (killed, despawned, a
 *     board space's mob at the end of its sequence), the mob drops it as it was.</li>
 * </ul>
 */
public final class Parachutes {
    /** The fall (blocks) after which a held parachute opens by itself; a mob's packed one. */
    public static final float AUTO_OPEN_FALL = 4, MOB_OPEN_FALL = 1.5f;
    /**
     * The speed an open parachute brakes a fall toward (blocks a tick), and the share of the excess it takes off a tick:
     * with gravity, it comes down at about 0.12 block a tick (2.4 blocks a second).
     */
    public static final double DESCENT = 0.1, BRAKE = 0.8;
    /** Its uses before it breaks (openings and landings). */
    public static final int MAX_DAMAGE = 128;
    /** The ticks between two flaps of the canopy, open. */
    private static final int FLAP_TICKS = 30;

    /** The parachute an entity carries: synced to every client (they draw it), saved with a mob. */
    public static final AttachmentType<ParachuteState> TYPE = AttachmentRegistry.<ParachuteState>builder()
            .persistent(ParachuteState.CODEC)
            .syncWith(ParachuteState.PACKET_CODEC, AttachmentSyncPredicate.all())
            .buildAndRegister(Steveparty.id("parachute"));

    /** Players who folded theirs by hand in the air: it doesn't open again by itself before they land. */
    private static final Set<UUID> FOLDED = new HashSet<>();

    private Parachutes() {
    }

    public static void initialize() {
        TYPE.identifier();
        UseEntityCallback.EVENT.register((player, world, hand, entity, hit) -> {
            if (hand != Hand.MAIN_HAND || !player.isSneaking() || !player.getMainHandStack().isEmpty()) return ActionResult.PASS;
            if (!(entity instanceof LivingEntity mob) || mob instanceof PlayerEntity || get(mob) == null) return ActionResult.PASS;
            if (world instanceof ServerWorld) takeBack(player, mob);
            return ActionResult.success(world.isClient);
        });
    }

    public static @Nullable ParachuteState get(Entity entity) {
        return entity.getAttached(TYPE);
    }

    /** Whether {@code entity} is under an open parachute (client or server). */
    public static boolean isOpen(Entity entity) {
        ParachuteState state = get(entity);
        return state != null && state.open();
    }

    /** Whether {@code entity} is in the air where a parachute holds: not on the ground, not in water or lava, not riding, gliding or flying. */
    public static boolean canFloat(LivingEntity entity) {
        if (entity.isOnGround() || entity.isTouchingWater() || entity.isInLava() || entity.hasVehicle()) return false;
        if (entity.isSpectator() || entity.isFallFlying() || entity.isClimbing() || entity.hasNoGravity()) return false;
        return !(entity instanceof PlayerEntity player) || !player.getAbilities().flying;
    }

    /** Whether {@code stack} still opens: like an elytra, not at its last use. */
    public static boolean usable(ItemStack stack) {
        return stack.isOf(ModItems.PARACHUTE) && stack.getDamage() < stack.getMaxDamage() - 1;
    }

    /** The parachute {@code player} holds (main hand first), and in which hand; null if none. */
    public static @Nullable Hand heldHand(PlayerEntity player) {
        if (player.getMainHandStack().isOf(ModItems.PARACHUTE)) return Hand.MAIN_HAND;
        if (player.getOffHandStack().isOf(ModItems.PARACHUTE)) return Hand.OFF_HAND;
        return null;
    }

    // ---------------------------------------------------------------- ticks

    /** Each tick, at its end, on the server: a player's or a mob's parachute opens, folds, lands. */
    public static void tick(LivingEntity entity) {
        if (entity instanceof ServerPlayerEntity player) tickPlayer(player);
        else {
            ParachuteState state = get(entity);
            if (state != null) tickMob(entity, state);
        }
    }

    private static void tickPlayer(ServerPlayerEntity player) {
        boolean air = canFloat(player);
        if (!air && !player.isFallFlying()) FOLDED.remove(player.getUuid());
        ParachuteState state = get(player);
        Hand hand = heldHand(player);
        if (state != null) {
            if (hand == null || !air) {
                fold(player, player.isOnGround());
                return;
            }
            player.fallDistance = 0;
            ItemStack held = player.getStackInHand(hand);
            if (ParachuteItem.color(held) != state.color()) player.setAttached(TYPE, new ParachuteState(held.copyWithCount(1), state.since(), true));
            flap(player, state);
        } else if (hand != null && air && !FOLDED.contains(player.getUuid()) && player.fallDistance >= AUTO_OPEN_FALL) {
            open(player, hand);
        }
    }

    private static void tickMob(LivingEntity mob, ParachuteState state) {
        if (!state.open()) {
            if (canFloat(mob) && mob.fallDistance >= MOB_OPEN_FALL) {
                mob.setAttached(TYPE, new ParachuteState(state.stack(), mob.getWorld().getTime(), true));
                mob.fallDistance = 0;
                play(mob, SoundEvents.ITEM_ARMOR_EQUIP_ELYTRA.value(), 0.8f, 1.2f);
            }
            return;
        }
        if (mob.isOnGround() || mob.isTouchingWater() || mob.isInLava()) {
            land(mob, state);
            return;
        }
        if (mob.hasVehicle()) { // taken into a bubble: packed again
            mob.setAttached(TYPE, new ParachuteState(state.stack(), mob.getWorld().getTime(), false));
            return;
        }
        mob.fallDistance = 0;
        flap(mob, state);
    }

    /** Now and then, open: the canopy flaps in the wind. */
    private static void flap(LivingEntity entity, ParachuteState state) {
        long open = entity.getWorld().getTime() - state.since();
        if (open > 0 && open % FLAP_TICKS == 0) play(entity, SoundEvents.BLOCK_WOOL_STEP, 0.35f, 0.7f + entity.getRandom().nextFloat() * 0.2f);
    }

    /**
     * Its brake (in its travel, wherever it moves: a client its own player, the server its mobs): open, a fall faster
     * than {@link #DESCENT} eases toward it.
     */
    public static void brake(LivingEntity entity) {
        if (!isOpen(entity)) return;
        Vec3d speed = entity.getVelocity();
        if (speed.y < -DESCENT) entity.setVelocity(speed.x, speed.y + (-DESCENT - speed.y) * BRAKE, speed.z);
    }

    // ---------------------------------------------------------------- a player's

    /** A use of the held parachute in the air (server): open, or fold it if it is. @return whether it did something */
    public static boolean toggle(ServerPlayerEntity player, Hand hand) {
        if (!canFloat(player)) return false;
        if (isOpen(player)) {
            FOLDED.add(player.getUuid());
            fold(player, false);
            return true;
        }
        FOLDED.remove(player.getUuid());
        return open(player, hand);
    }

    private static boolean open(ServerPlayerEntity player, Hand hand) {
        ItemStack held = player.getStackInHand(hand);
        if (!usable(held)) return false;
        player.setAttached(TYPE, new ParachuteState(held.copyWithCount(1), player.getWorld().getTime(), true));
        held.damage(1, player, LivingEntity.getSlotForHand(hand));
        player.fallDistance = 0;
        play(player, SoundEvents.ITEM_ARMOR_EQUIP_ELYTRA.value(), 1.0f, 1.2f);
        play(player, SoundEvents.BLOCK_WOOL_PLACE, 0.8f, 0.8f);
        return true;
    }

    private static void fold(ServerPlayerEntity player, boolean landed) {
        player.removeAttached(TYPE);
        play(player, SoundEvents.BLOCK_WOOL_BREAK, 0.7f, landed ? 1.0f : 1.3f);
        if (landed) ModCriteria.fire(player, "parachute_landing");
    }

    // ---------------------------------------------------------------- a mob's

    /** Whether a parachute can be put on {@code entity}: a living mob, not a player, not a boss, none on it yet. */
    public static boolean canCarry(LivingEntity entity) {
        return entity.isAlive() && !(entity instanceof PlayerEntity) && !entity.getType().isIn(ConventionalEntityTypeTags.BOSSES)
                && get(entity) == null;
    }

    /** {@code player} puts one of {@code stack} on {@code mob} (server), packed. @return whether he could */
    public static boolean attach(PlayerEntity player, LivingEntity mob, ItemStack stack) {
        if (!canCarry(mob) || !stack.isOf(ModItems.PARACHUTE)) return false;
        mob.setAttached(TYPE, new ParachuteState(stack.copyWithCount(1), mob.getWorld().getTime(), false));
        stack.decrementUnlessCreative(1, player);
        play(mob, SoundEvents.BLOCK_WOOL_PLACE, 0.8f, 1.0f);
        ModCriteria.fire(player, "parachute_mob", mob);
        return true;
    }

    /** {@code player} takes {@code mob}'s parachute back, as it is (server). */
    private static void takeBack(PlayerEntity player, LivingEntity mob) {
        ParachuteState state = get(mob);
        if (state == null) return;
        mob.removeAttached(TYPE);
        ItemStack stack = state.stack().copy();
        if (!player.giveItemStack(stack)) player.dropItem(stack, false);
        play(mob, SoundEvents.BLOCK_WOOL_BREAK, 0.7f, 1.2f);
    }

    /** {@code mob} landed (server): its parachute drops, worn by one (or breaks at its last use). */
    private static void land(LivingEntity mob, ParachuteState state) {
        mob.removeAttached(TYPE);
        ItemStack stack = state.stack().copy();
        ServerWorld world = (ServerWorld) mob.getWorld();
        stack.damage(1, world, null, item -> play(mob, SoundEvents.ENTITY_ITEM_BREAK, 0.8f, 1.0f));
        play(mob, SoundEvents.BLOCK_WOOL_BREAK, 0.7f, 1.0f);
        if (!stack.isEmpty()) drop(mob, stack);
    }

    /** {@code mob} is removed for good (killed, despawned, discarded): its parachute drops as it was (server). */
    public static void removed(LivingEntity mob) {
        if (mob instanceof PlayerEntity) return;
        ParachuteState state = get(mob);
        if (state == null) return;
        mob.removeAttached(TYPE);
        drop(mob, state.stack().copy());
    }

    private static void drop(LivingEntity mob, ItemStack stack) {
        ItemEntity item = new ItemEntity(mob.getWorld(), mob.getX(), mob.getY() + mob.getHeight() / 2, mob.getZ(), stack);
        item.setToDefaultPickupDelay();
        mob.getWorld().spawnEntity(item);
    }

    private static void play(LivingEntity entity, SoundEvent sound, float volume, float pitch) {
        entity.getWorld().playSound(null, entity.getX(), entity.getY(), entity.getZ(), sound,
                entity instanceof PlayerEntity ? SoundCategory.PLAYERS : SoundCategory.NEUTRAL, volume, pitch);
    }
}
