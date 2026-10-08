package fr.lordfinn.steveparty.service;

import fr.lordfinn.steveparty.blocks.custom.PartyController.PartyControllerEntity;
import fr.lordfinn.steveparty.blocks.custom.PartyController.PartyCurrency;
import fr.lordfinn.steveparty.blocks.custom.boardspaces.BoardSpaces;
import fr.lordfinn.steveparty.dice.DicePrompts;
import fr.lordfinn.steveparty.entities.ModEntities;
import fr.lordfinn.steveparty.entities.TokenizedEntityInterface;
import fr.lordfinn.steveparty.entities.custom.frousseux.FrousseuxColor;
import fr.lordfinn.steveparty.entities.custom.frousseux.FrousseuxEntity;
import fr.lordfinn.steveparty.powerups.PowerUp;
import fr.lordfinn.steveparty.powerups.effects.ThiefBellEffect;
import fr.lordfinn.steveparty.sounds.ModSounds;
import fr.lordfinn.steveparty.utils.InventoryUtils;
import fr.lordfinn.steveparty.utils.MessageUtils;
import fr.lordfinn.steveparty.utils.ServerMemory;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.minecraft.entity.Entity;
import net.minecraft.entity.ItemEntity;
import net.minecraft.entity.mob.MobEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.particle.ItemStackParticleEffect;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static fr.lordfinn.steveparty.Steveparty.SCHEDULER;

/**
 * What a Frousseux space does (see FrousseuxTileBehavior): a Frousseux pops up next to the tile, the token's player
 * picks a victim among the other players (their heads, as for the Thief Bell), and the Frousseux flies to them and
 * steals, the loot flying from them to it with sparkles and a laugh.
 * <ul>
 *     <li><b>Coins</b>: it steals them right away, then hovers around the victim for {@link #DEFENCE_TICKS}. Each blow
 *     the victim lands on it gives one coin back (it flies back to them) and sends the Frousseux elsewhere around
 *     them, mostly behind them: they must turn and aim fast.</li>
 *     <li><b>Stars</b>: stolen at once, no defence.</li>
 * </ul>
 * Then it brings what it still carries to its player and vanishes in a puff of smoke. It is a board actor: no wild
 * behaviour, invulnerable, never saved, always removed at the end ({@link BoardActors}).
 * <p>
 * Nothing is ever lost: the loot is the victim's until stolen, then carried (here) until given; a party ending, the
 * Frousseux gone or the server stopping in the middle settles it at once (to its player, else back to the victim, else
 * dropped). Server thread only.
 */
public final class FrousseuxThefts {
    /** It pops up and rises next to the tile (ticks), before it may leave. */
    public static final int APPEAR_TICKS = 20;
    /** How fast it flies to the victim and back (blocks a tick). */
    public static final double FLY_SPEED = 0.55;
    /** Flying longer than this (ticks), or farther than {@link #BLINK_DISTANCE}: it blinks there in a puff. */
    public static final int MAX_FLIGHT_TICKS = 160;
    public static final double BLINK_DISTANCE = 48;
    /** The victim's time to hit it back (coins): 7 seconds. */
    public static final int DEFENCE_TICKS = 140;
    /** After a blow, so long before another one counts (its item flight must be over: ticks). */
    public static final int HIT_COOLDOWN = FrousseuxEntity.ITEM_FLIGHT_TICKS + 2;
    /** Stars: it shows its loot a moment before leaving. */
    public static final int STAR_PAUSE_TICKS = 20;
    /** Giving the loot to its player, before the puff. */
    public static final int DELIVER_TICKS = 16;
    /** How far around the victim it hovers, and how fast it goes round (radians a tick). */
    private static final double ORBIT_RADIUS = 2.3, ORBIT_SPEED = 0.045;
    /** Most coins drawn flying at once. */
    private static final int MAX_FLYING_COINS = 12;

    public enum Phase { APPEAR, TO_VICTIM, DEFENCE, SHOW, RETURN, DELIVER }

    /** How a theft started (or why not). */
    public enum Start { STARTED, NO_PLAYER, NOBODY }

    /** The running thefts, by the UUID of the token that landed. */
    private static final Map<UUID, Theft> RUNNING = ServerMemory.forgetOnStop(new HashMap<>());

    private FrousseuxThefts() {
    }

    public static void initialize() {
        // Stopping in the middle: the loot is settled (the Frousseux is never saved)
        ServerLifecycleEvents.SERVER_STOPPING.register(server -> {
            for (Theft theft : new ArrayList<>(RUNNING.values())) theft.abort();
        });
    }

    public static boolean isRunning(MobEntity token) {
        return RUNNING.containsKey(token.getUuid());
    }

    /** The phase of the theft of {@code token}'s landing, null if none. */
    public static @Nullable Phase phase(MobEntity token) {
        Theft theft = RUNNING.get(token.getUuid());
        return theft == null ? null : theft.phase;
    }

    /** The Frousseux of {@code token}'s landing, null if none. */
    public static @Nullable FrousseuxEntity actor(MobEntity token) {
        Theft theft = RUNNING.get(token.getUuid());
        return theft == null ? null : theft.actor;
    }

    /** What the Frousseux of {@code token}'s landing carries now (0 if none). */
    public static int carried(MobEntity token) {
        Theft theft = RUNNING.get(token.getUuid());
        return theft == null ? 0 : theft.carried;
    }

    /**
     * {@code token} stopped on the Frousseux space {@code tile} of {@code party}: its player picks a victim and a
     * Frousseux steals {@code amount} coins (or stars) from them; {@code onDone} runs once it has vanished. Nothing
     * happens without the token's player online ({@link Start#NO_PLAYER}) or another player to rob
     * ({@link Start#NOBODY}): {@code onDone} is not called then, and the party is told.
     */
    public static Start start(ServerWorld world, BlockPos tile, MobEntity token, PartyControllerEntity party,
                              boolean stars, int amount, Runnable onDone) {
        if (RUNNING.containsKey(token.getUuid())) return Start.STARTED;
        UUID senderId = token instanceof TokenizedEntityInterface tokenized ? tokenized.steveparty$getTokenOwner() : null;
        ServerPlayerEntity sender = senderId == null ? null : world.getServer().getPlayerManager().getPlayer(senderId);
        if (sender == null || sender.isDisconnected()) {
            tell(party, null, null, Text.translatable("message.steveparty.frousseux_space.no_player").formatted(Formatting.GRAY));
            return Start.NO_PLAYER;
        }
        List<ServerPlayerEntity> candidates = ThiefBellEffect.candidates(party, sender);
        if (candidates.isEmpty()) {
            tell(party, sender, null, Text.translatable("message.steveparty.frousseux_space.nobody").formatted(Formatting.GRAY));
            return Start.NOBODY;
        }
        PartyCurrency currency = stars ? PartyCurrency.STAR : PartyCurrency.COIN;
        Theft theft = new Theft(world, tile.toImmutable(), token, sender.getUuid(), party, currency, Math.max(1, amount), onDone);
        if (!theft.spawn()) return Start.NOBODY;
        RUNNING.put(token.getUuid(), theft);
        theft.ask(sender, candidates);
        SCHEDULER.repeat(theft.task, 1, theft::tick, () -> !theft.done, () -> {
        });
        return Start.STARTED;
    }

    /** To the party's audience, and to these players. */
    private static void tell(PartyControllerEntity party, @Nullable ServerPlayerEntity a, @Nullable ServerPlayerEntity b, Text message) {
        List<ServerPlayerEntity> audience = new ArrayList<>(party.getPartyAudience());
        if (a != null && !audience.contains(a)) audience.add(a);
        if (b != null && !audience.contains(b)) audience.add(b);
        MessageUtils.sendToPlayers(audience, message, MessageUtils.MessageType.CHAT);
    }

    private static float yawToward(Vec3d from, Vec3d to) {
        return (float) (MathHelper.atan2(to.z - from.z, to.x - from.x) * MathHelper.DEGREES_PER_RADIAN) - 90f;
    }

    /** One theft: its Frousseux, its victim, what it carries. */
    private static final class Theft {
        final UUID task = UUID.randomUUID();
        final ServerWorld world;
        final BlockPos tile;
        final MobEntity token;
        final UUID senderId;
        final PartyControllerEntity party;
        final PartyCurrency currency;
        final int wanted;
        final Runnable onDone;
        FrousseuxEntity actor;
        Phase phase = Phase.APPEAR;
        int tick, phaseTick;
        @Nullable UUID victimId;
        boolean picked;
        @Nullable DicePrompts.Prompt prompt;
        /** Stolen and not given yet. */
        int carried;
        /** Given back to the victim by their blows. */
        int defended;
        double angle;
        double spin = ORBIT_SPEED;
        int hitCooldown;
        /** Who it gives the loot to on its way back. */
        @Nullable UUID recipientId;
        Vec3d appearAt = Vec3d.ZERO;
        boolean done;

        Theft(ServerWorld world, BlockPos tile, MobEntity token, UUID senderId, PartyControllerEntity party,
              PartyCurrency currency, int wanted, Runnable onDone) {
            this.world = world;
            this.tile = tile;
            this.token = token;
            this.senderId = senderId;
            this.party = party;
            this.currency = currency;
            this.wanted = wanted;
            this.onDone = onDone;
        }

        boolean stars() {
            return currency == PartyCurrency.STAR;
        }

        ItemStack template() {
            return party.getCurrency(currency);
        }

        Text currencyName() {
            return template().getName();
        }

        @Nullable ServerPlayerEntity player(@Nullable UUID id) {
            if (id == null) return null;
            ServerPlayerEntity player = world.getServer().getPlayerManager().getPlayer(id);
            return player == null || player.isDisconnected() || !player.isAlive() ? null : player;
        }

        /** It pops up beside the tile, on the token's right, low, and will rise. */
        boolean spawn() {
            FrousseuxEntity one = ModEntities.FROUSSEUX.create(world);
            if (one == null) return false;
            one.setColor(FrousseuxColor.random(world.getRandom()));
            one.makeBoardActor();
            BoardActors.join(task, one);
            Vec3d stand = BoardSpaces.standPos(world, tile);
            Vec3d side = Vec3d.fromPolar(0, token.getYaw() + 90).multiply(0.9);
            appearAt = stand.add(side);
            float yaw = token.getYaw();
            one.refreshPositionAndAngles(appearAt.x, appearAt.y - 0.5, appearAt.z, yaw, 0);
            one.setHeadYaw(yaw);
            one.setBodyYaw(yaw);
            world.spawnEntity(one);
            actor = one;
            one.onBoardHit(this::hit);
            world.spawnParticles(ParticleTypes.POOF, appearAt.x, appearAt.y + 0.3, appearAt.z, 10, 0.2, 0.2, 0.2, 0.02);
            world.spawnParticles(ParticleTypes.SMOKE, appearAt.x, appearAt.y + 0.2, appearAt.z, 6, 0.15, 0.1, 0.15, 0.01);
            world.playSound(null, appearAt.x, appearAt.y, appearAt.z, SoundEvents.ITEM_FIRECHARGE_USE, SoundCategory.NEUTRAL, 0.4f, 1.7f);
            return true;
        }

        /** Its player picks the victim: the heads of the others, with what each holds; unanswered, the richest. */
        void ask(ServerPlayerEntity sender, List<ServerPlayerEntity> candidates) {
            ThiefBellEffect.Variant variant = stars() ? ThiefBellEffect.Variant.GOLDEN : ThiefBellEffect.Variant.THIEF;
            ServerPlayerEntity fallback = ThiefBellEffect.defaultTarget(party, candidates, variant);
            List<DicePrompts.Option> options = new ArrayList<>();
            for (ServerPlayerEntity candidate : candidates) {
                options.add(new DicePrompts.Option(PowerUp.headOf(candidate), Text.translatable("gui.steveparty.thief_bell.option",
                        candidate.getDisplayName(), ThiefBellEffect.holdings(party, candidate, currency), currencyName())
                        .formatted(Formatting.WHITE)));
            }
            Text title = Text.translatable("gui.steveparty.frousseux_space.prompt", wanted, currencyName());
            List<UUID> ids = candidates.stream().map(Entity::getUuid).toList();
            prompt = DicePrompts.ask(sender, title, DicePrompts.Layout.LIST, options, DicePrompts.TIMEOUT_TICKS,
                    Math.max(0, candidates.indexOf(fallback)), index -> {
                        if (done) return;
                        victimId = ids.get(Math.max(0, Math.min(index, ids.size() - 1)));
                        picked = true;
                    });
            if (prompt == null) {
                victimId = fallback == null ? ids.getFirst() : fallback.getUuid();
                picked = true;
            }
        }

        void tick() {
            if (done) return;
            tick++;
            phaseTick++;
            if (hitCooldown > 0) hitCooldown--;
            if (actor == null || actor.isRemoved() || party.isRemoved() || !party.getPartyData().isStarted()) {
                abort();
                return;
            }
            switch (phase) {
                case APPEAR -> tickAppear();
                case TO_VICTIM -> tickToVictim();
                case DEFENCE -> tickDefence();
                case SHOW -> tickShow();
                case RETURN -> tickReturn();
                case DELIVER -> {
                    if (phaseTick >= DELIVER_TICKS) vanish();
                }
            }
        }

        void go(Phase next) {
            phase = next;
            phaseTick = 0;
        }

        /** It rises beside the tile, its flame flaring up, then bobs there until the victim is picked. */
        void tickAppear() {
            float rise = Math.min(1f, phaseTick / 14f);
            double eased = rise * rise * (3 - 2 * rise);
            double bob = Math.sin(tick * 0.2) * 0.06;
            place(appearAt.add(0, -0.5 + eased * 1.4 + (rise >= 1 ? bob : 0), 0), actor.getYaw());
            if (phaseTick == 8) {
                world.spawnParticles(ParticleTypes.FLAME, actor.getX(), actor.getY() + FrousseuxEntity.HEIGHT + 0.25,
                        actor.getZ(), 6, 0.05, 0.1, 0.05, 0.01);
                actor.boardLaugh();
            }
            ServerPlayerEntity sender = player(senderId);
            if (sender != null) face(sender.getEyePos());
            if (phaseTick >= APPEAR_TICKS && picked) {
                ServerPlayerEntity victim = player(victimId);
                if (victim == null || victim.getWorld() != world) {
                    victimGone();
                    return;
                }
                actor.boardLaugh();
                go(Phase.TO_VICTIM);
            }
        }

        /** Where it hovers around the victim at {@code angle}: about their chest, bobbing. */
        Vec3d around(ServerPlayerEntity victim, double at) {
            double bob = Math.sin(tick * 0.25) * 0.12;
            return new Vec3d(victim.getX() + Math.cos(at) * ORBIT_RADIUS, victim.getY() + 0.75 + bob,
                    victim.getZ() + Math.sin(at) * ORBIT_RADIUS);
        }

        void tickToVictim() {
            ServerPlayerEntity victim = player(victimId);
            if (victim == null || victim.getWorld() != world) {
                victimGone();
                return;
            }
            if (phaseTick == 1) {
                Vec3d from = actor.getPos().subtract(victim.getPos());
                angle = Math.atan2(from.z, from.x);
            }
            if (fly(around(victim, angle), phaseTick)) steal(victim);
        }

        /** It takes the loot: right away, all it can (no more than the victim holds), flying to it. */
        void steal(ServerPlayerEntity victim) {
            ItemStack template = template();
            int held = ThiefBellEffect.holdings(party, victim, currency);
            int taken = held <= 0 ? 0 : InventoryUtils.take(victim.getInventory(), template, Math.min(wanted, held));
            face(victim.getEyePos());
            if (taken <= 0) {
                tell(party, player(senderId), victim, Text.translatable("message.steveparty.frousseux_space.nothing",
                        victim.getDisplayName(), currencyName()).formatted(Formatting.GRAY));
                actor.boardLaugh();
                vanish();
                return;
            }
            carried = taken;
            actor.setBoardCarried(template.copyWithCount(Math.min(carried, template.getMaxCount())));
            actor.boardItemFlight(victim, template.copyWithCount(Math.min(carried, template.getMaxCount())), true);
            sprayCoins(victim, Math.min(carried, MAX_FLYING_COINS), true);
            Vec3d at = victim.getPos().add(0, victim.getHeight() * 0.55, 0);
            world.spawnParticles(ParticleTypes.ELECTRIC_SPARK, at.x, at.y, at.z, 10, 0.25, 0.25, 0.25, 0.05);
            world.spawnParticles(ParticleTypes.WAX_ON, at.x, at.y, at.z, 5, 0.25, 0.25, 0.25, 0.5);
            world.playSound(null, at.x, at.y, at.z, SoundEvents.ENTITY_EXPERIENCE_ORB_PICKUP, SoundCategory.PLAYERS, 0.8f, 0.7f);
            world.playSound(null, at.x, at.y, at.z, SoundEvents.ENTITY_VILLAGER_NO, SoundCategory.PLAYERS, 0.5f, 1.2f);
            actor.boardLaugh();
            tell(party, player(senderId), victim, Text.translatable("message.steveparty.frousseux_space.stolen",
                    victim.getDisplayName(), carried, currencyName()).formatted(Formatting.GOLD));
            if (stars()) {
                go(Phase.SHOW);
            } else {
                hitCooldown = HIT_COOLDOWN;
                go(Phase.DEFENCE);
                countdown(victim);
            }
        }

        /** The defence: it hovers around the victim, now and then turning back, flame flickering with the seconds. */
        void tickDefence() {
            ServerPlayerEntity victim = player(victimId);
            if (victim == null || victim.getWorld() != world) {
                leave();
                return;
            }
            if (phaseTick % 30 == 0 && world.getRandom().nextBoolean()) spin = -spin;
            angle += spin;
            place(around(victim, angle), actor.getYaw());
            face(victim.getEyePos());
            if (phaseTick % 20 == 0 && phaseTick < DEFENCE_TICKS) countdown(victim);
            if (phaseTick >= DEFENCE_TICKS) leave();
        }

        /** Each second of the defence: how long is left, in the victim's action bar, a tick and a flicker. */
        void countdown(ServerPlayerEntity victim) {
            int left = Math.max(1, (DEFENCE_TICKS - phaseTick + 19) / 20);
            MessageUtils.sendToPlayer(victim, Text.translatable("message.steveparty.frousseux_space.defend", currencyName(), left)
                    .formatted(left <= 2 ? Formatting.RED : Formatting.GOLD), MessageUtils.MessageType.ACTION_BAR);
            world.playSound(null, actor.getX(), actor.getY(), actor.getZ(), SoundEvents.BLOCK_NOTE_BLOCK_HAT.value(),
                    SoundCategory.NEUTRAL, 0.6f, left <= 2 ? 1.6f : 1.2f);
            world.spawnParticles(ParticleTypes.SMALL_FLAME, actor.getX(), actor.getY() + FrousseuxEntity.HEIGHT + 0.25,
                    actor.getZ(), 3, 0.04, 0.06, 0.04, 0.01);
        }

        /**
         * A blow at it: from the victim, during the defence, it drops one coin back to them (it flies back) and pops up
         * elsewhere around them, mostly behind their back. Never hurts it.
         */
        boolean hit(@Nullable Entity attacker) {
            if (done || phase != Phase.DEFENCE || hitCooldown > 0 || carried <= 0) return false;
            if (attacker == null || !attacker.getUuid().equals(victimId) || !(attacker instanceof ServerPlayerEntity victim)) return false;
            hitCooldown = HIT_COOLDOWN;
            ItemStack template = template();
            carried--;
            defended++;
            InventoryUtils.giveOrDrop(victim, template, 1);
            actor.setBoardCarried(carried > 0 ? template.copyWithCount(Math.min(carried, template.getMaxCount())) : ItemStack.EMPTY);
            actor.boardItemFlight(victim, template.copyWithCount(1), false);
            Vec3d from = actor.getBoundingBox().getCenter();
            world.spawnParticles(ParticleTypes.POOF, from.x, from.y, from.z, 10, 0.15, 0.15, 0.15, 0.03);
            world.spawnParticles(ParticleTypes.SMOKE, from.x, from.y + 0.2, from.z, 4, 0.1, 0.1, 0.1, 0.01);
            world.playSound(null, victim.getX(), victim.getY(), victim.getZ(), SoundEvents.ENTITY_ITEM_PICKUP, SoundCategory.PLAYERS, 0.7f, 1.5f);
            // behind them: their back ± 75°, so they have to turn round to aim again
            double back = Math.toRadians(victim.getYaw() + 90 + 180);
            angle = back + (world.getRandom().nextDouble() - 0.5) * Math.toRadians(150);
            spin = world.getRandom().nextBoolean() ? ORBIT_SPEED : -ORBIT_SPEED;
            Vec3d to = around(victim, angle);
            place(to, actor.getYaw());
            face(victim.getEyePos());
            world.spawnParticles(ParticleTypes.POOF, to.x, to.y + FrousseuxEntity.HEIGHT / 2, to.z, 6, 0.12, 0.12, 0.12, 0.02);
            world.spawnParticles(ParticleTypes.WAX_OFF, to.x, to.y + FrousseuxEntity.HEIGHT / 2, to.z, 3, 0.2, 0.2, 0.2, 0.5);
            actor.boardLaugh();
            MessageUtils.sendToPlayer(victim, Text.translatable("message.steveparty.frousseux_space.hit", currencyName(), carried)
                    .formatted(Formatting.GREEN), MessageUtils.MessageType.ACTION_BAR);
            if (carried <= 0) {
                tell(party, player(senderId), victim, Text.translatable("message.steveparty.frousseux_space.all_back",
                        victim.getDisplayName(), currencyName()).formatted(Formatting.GREEN));
                phaseTick = DEFENCE_TICKS; // nothing left: it vanishes next tick (not in the middle of the blow)
            }
            return true;
        }

        void tickShow() {
            ServerPlayerEntity victim = player(victimId);
            if (victim != null && victim.getWorld() == world) {
                place(around(victim, angle), actor.getYaw());
                face(victim.getEyePos());
            }
            if (phaseTick >= STAR_PAUSE_TICKS) leave();
        }

        /** Its loot taken (or the victim gone): back to its player, else back to the victim, else dropped. */
        void leave() {
            if (carried <= 0) {
                vanish();
                return;
            }
            ServerPlayerEntity victim = player(victimId);
            if (victim != null && defended > 0) {
                tell(party, player(senderId), victim, Text.translatable("message.steveparty.frousseux_space.defended",
                        victim.getDisplayName(), defended, currencyName()).formatted(Formatting.GREEN));
            }
            ServerPlayerEntity sender = player(senderId);
            recipientId = sender != null && sender.getWorld() == world ? sender.getUuid()
                    : victim != null && victim.getWorld() == world ? victim.getUuid() : null;
            if (recipientId == null) {
                dropLoot();
                vanish();
                return;
            }
            go(Phase.RETURN);
        }

        void tickReturn() {
            ServerPlayerEntity recipient = player(recipientId);
            if (recipient == null || recipient.getWorld() != world) {
                leave();
                return;
            }
            Vec3d look = recipient.getRotationVec(1f);
            Vec3d front = new Vec3d(look.x, 0, look.z);
            front = front.lengthSquared() < 1.0E-4 ? new Vec3d(0, 0, 1) : front.normalize();
            // in front of them, a little to their right: in sight, not in their face
            Vec3d right = new Vec3d(-front.z, 0, front.x);
            Vec3d spot = recipient.getPos().add(front.multiply(1.9)).add(right.multiply(0.7)).add(0, 1.0, 0);
            if (fly(spot, phaseTick)) deliver(recipient);
        }

        /** Its player gets the loot (flying from it to them), then the puff. */
        void deliver(ServerPlayerEntity recipient) {
            ItemStack template = template();
            int given = carried;
            carried = 0;
            InventoryUtils.giveOrDrop(recipient, template, given);
            actor.setBoardCarried(ItemStack.EMPTY);
            actor.boardItemFlight(recipient, template.copyWithCount(Math.min(given, template.getMaxCount())), false);
            face(recipient.getEyePos());
            world.playSound(null, recipient.getX(), recipient.getY(), recipient.getZ(), SoundEvents.ENTITY_EXPERIENCE_ORB_PICKUP,
                    SoundCategory.PLAYERS, 0.8f, 1.3f);
            actor.boardLaugh();
            Text message = recipient.getUuid().equals(senderId)
                    ? Text.translatable("message.steveparty.frousseux_space.delivered", recipient.getDisplayName(), given, currencyName())
                    : Text.translatable("message.steveparty.frousseux_space.returned", recipient.getDisplayName(), given, currencyName());
            tell(party, recipient, null, message.copy().formatted(Formatting.GOLD));
            go(Phase.DELIVER);
        }

        /**
         * Flies toward {@code to} ({@code ticks} into the flight); true once there. Too far, or too long: it blinks
         * there in a puff.
         */
        boolean fly(Vec3d to, int ticks) {
            Vec3d at = actor.getPos();
            Vec3d way = to.subtract(at);
            double length = way.length();
            if (length > BLINK_DISTANCE || ticks > MAX_FLIGHT_TICKS) {
                Vec3d from = actor.getBoundingBox().getCenter();
                world.spawnParticles(ParticleTypes.POOF, from.x, from.y, from.z, 10, 0.15, 0.15, 0.15, 0.03);
                place(to, yawToward(at, to));
                world.spawnParticles(ParticleTypes.POOF, to.x, to.y + 0.3, to.z, 8, 0.15, 0.15, 0.15, 0.03);
                return true;
            }
            if (length <= FLY_SPEED) {
                place(to, actor.getYaw());
                return true;
            }
            Vec3d next = at.add(way.multiply(FLY_SPEED / length));
            place(next, yawToward(at, to));
            if (ticks % 3 == 0) world.spawnParticles(ParticleTypes.SMALL_FLAME, at.x, at.y + FrousseuxEntity.HEIGHT + 0.2, at.z, 1, 0.02, 0.02, 0.02, 0.0);
            return false;
        }

        void place(Vec3d at, float yaw) {
            actor.refreshPositionAndAngles(at.x, at.y, at.z, yaw, 0);
            actor.setHeadYaw(yaw);
            actor.setBodyYaw(yaw);
            actor.setVelocity(Vec3d.ZERO);
        }

        void face(Vec3d target) {
            float yaw = yawToward(actor.getPos(), target);
            actor.setYaw(yaw);
            actor.setHeadYaw(yaw);
            actor.setBodyYaw(yaw);
        }

        /** The coins (or stars) flying from {@code player} to it ({@code toIt}), as item crumbs, for show. */
        void sprayCoins(ServerPlayerEntity player, int count, boolean toIt) {
            Vec3d them = player.getPos().add(0, player.getHeight() * 0.6, 0);
            Vec3d it = actor.getBoundingBox().getCenter();
            Vec3d from = toIt ? them : it, to = toIt ? it : them;
            Vec3d way = to.subtract(from);
            ItemStackParticleEffect crumb = new ItemStackParticleEffect(ParticleTypes.ITEM, template().copyWithCount(1));
            for (int i = 0; i < count; i++) {
                double s = 0.18 + world.getRandom().nextDouble() * 0.06;
                world.spawnParticles(crumb, from.x, from.y, from.z, 0, way.x, way.y + 0.6, way.z, s);
            }
        }

        void victimGone() {
            tell(party, player(senderId), null, Text.translatable("message.steveparty.frousseux_space.victim_gone").formatted(Formatting.GRAY));
            leave();
        }

        void dropLoot() {
            if (carried <= 0) return;
            ItemStack template = template();
            int left = carried;
            carried = 0;
            Vec3d at = actor != null ? actor.getPos() : BoardSpaces.standPos(world, tile);
            while (left > 0) {
                int count = Math.min(left, template.getMaxCount());
                left -= count;
                world.spawnEntity(new ItemEntity(world, at.x, at.y, at.z, template.copyWithCount(count)));
            }
        }

        /** Stopped in the middle (party over, Frousseux gone, server stopping): the loot is settled at once. */
        void abort() {
            if (done) return;
            if (carried > 0) {
                ServerPlayerEntity to = player(senderId);
                if (to == null) to = player(victimId);
                if (to != null) {
                    InventoryUtils.giveOrDrop(to, template(), carried);
                    carried = 0;
                } else {
                    dropLoot();
                }
            }
            DicePrompts.cancel(prompt);
            vanish();
        }

        /** The puff of smoke: a flame blown out. */
        void vanish() {
            if (actor != null && !actor.isRemoved()) {
                Vec3d at = actor.getBoundingBox().getCenter();
                world.spawnParticles(ParticleTypes.POOF, at.x, at.y, at.z, 14, 0.2, 0.2, 0.2, 0.03);
                world.spawnParticles(ParticleTypes.LARGE_SMOKE, at.x, at.y + 0.2, at.z, 5, 0.12, 0.15, 0.12, 0.01);
                world.spawnParticles(ParticleTypes.SMOKE, at.x, at.y + 0.4, at.z, 8, 0.1, 0.2, 0.1, 0.02);
                world.playSound(null, at.x, at.y, at.z, ModSounds.FROUSSEUX_BLOWN_OUT, SoundCategory.NEUTRAL, 0.9f, 1.1f);
            }
            finish();
        }

        void finish() {
            if (done) return;
            done = true;
            SCHEDULER.cancel(task);
            RUNNING.remove(token.getUuid());
            if (carried > 0) dropLoot();
            if (actor != null) actor.onBoardHit(null);
            BoardActors.end(task);
            onDone.run();
        }
    }
}
