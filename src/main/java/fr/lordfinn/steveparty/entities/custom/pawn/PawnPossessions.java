package fr.lordfinn.steveparty.entities.custom.pawn;

import fr.lordfinn.steveparty.Steveparty;
import fr.lordfinn.steveparty.effect.SquishEffect;
import fr.lordfinn.steveparty.entities.ModEntities;
import fr.lordfinn.steveparty.entities.TokenizedEntityInterface;
import fr.lordfinn.steveparty.items.custom.TokenizerWandItem;
import fr.lordfinn.steveparty.particles.MagicShapeEffect;
import fr.lordfinn.steveparty.particles.SpellPalette;
import fr.lordfinn.steveparty.utils.MessageUtils;
import net.fabricmc.fabric.api.entity.event.v1.ServerEntityWorldChangeEvents;
import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.event.player.AttackBlockCallback;
import net.fabricmc.fabric.api.event.player.AttackEntityCallback;
import net.fabricmc.fabric.api.event.player.UseBlockCallback;
import net.fabricmc.fabric.api.event.player.UseEntityCallback;
import net.fabricmc.fabric.api.event.player.UseItemCallback;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.minecraft.entity.EntityDimensions;
import net.minecraft.entity.EntityPose;
import net.minecraft.entity.attribute.EntityAttribute;
import net.minecraft.entity.attribute.EntityAttributeInstance;
import net.minecraft.entity.attribute.EntityAttributeModifier;
import net.minecraft.entity.attribute.EntityAttributes;
import net.minecraft.entity.effect.StatusEffects;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.registry.RegistryKey;
import net.minecraft.registry.entry.RegistryEntry;
import net.minecraft.registry.tag.DamageTypeTags;
import net.minecraft.registry.tag.FluidTags;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.text.Text;
import net.minecraft.util.ActionResult;
import net.minecraft.util.Identifier;
import net.minecraft.util.TypedActionResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.World;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.function.Predicate;

/**
 * Players turned into pawns (server side).
 * <p>
 * <b>The spell</b> ({@link #startSpell}, from the Tokenizer Wand): for {@link TokenizerWandItem#TRANSFORM_DURATION}
 * ticks the player shrinks to the chosen pawn size and can't move (held in place, no walking, no jumping, no flying).
 * Then a {@link PlayerPawnEntity} appears where they stand: the spell always ends with a pawn, even when the player
 * left in the meantime.
 * <p>
 * <b>Inside the pawn</b>: the player is invisible, tiny (their hitbox inside the pawn's, so clicks hit the pawn),
 * held at the pawn's position, safe from everything but what bypasses invulnerability, can't act on the world; their
 * camera is the pawn's (vanilla camera entity: first person from the statue's eyes, which they don't see; the statue
 * in third person). Nothing of this is saved: temporary attribute modifiers, invisibility flag, camera.
 * <p>
 * <b>Leaving it</b> ({@link #eject}): sneak, or anything that ends the possession: the pawn removed (stored in a Token,
 * killed, unloaded, gone to another dimension), in lava, the player disconnecting, dying, changing dimension or game
 * mode, or teleported (vanilla resets the camera). The player gets their size back next to the pawn; the pawn stays,
 * empty, a regular pawn. A player is in one pawn at most, and a pawn holds one player at most.
 * <p>
 * Every possession is checked once per server tick ({@link #tick}), so whatever ends it the player is out within a
 * tick; the pawn's removal ejects at once ({@link #onPawnRemoved}).
 */
public final class PawnPossessions {
    /** Id of the temporary attribute modifiers (size, frozen) of a player under the spell or inside a pawn. */
    private static final Identifier MODIFIER = Steveparty.id("player_pawn");
    /** Smallest scale of a living entity: the player's hitbox inside the pawn's. */
    private static final double INSIDE_SCALE = 0.0625;
    /** Ticks between the pawn's spawn and the camera switch (the pawn must exist on the client first). */
    private static final int CAMERA_DELAY = 2;
    /** Client side: whether the local player is inside a pawn (set by the client, see StevepartyClient). */
    private static Predicate<PlayerEntity> clientInside = player -> false;

    /** A player under the spell: where they are held, and the pawn that will come out of it. */
    private static final class Spell {
        final UUID player;
        final String name;
        final UUID caster;
        final float size;
        final int color;
        final RegistryKey<World> world;
        final Vec3d pos;
        final float yaw;
        int ticksLeft = TokenizerWandItem.TRANSFORM_DURATION;

        Spell(ServerPlayerEntity player, UUID caster, float size, int color) {
            this.player = player.getUuid();
            this.name = player.getGameProfile().getName();
            this.caster = caster;
            this.size = size;
            this.color = color;
            this.world = player.getWorld().getRegistryKey();
            this.pos = player.getPos();
            this.yaw = player.getYaw();
        }
    }

    /** A player inside a pawn. */
    private static final class Possession {
        final UUID pawn;
        final RegistryKey<World> world;
        int age;
        boolean camera;

        Possession(PlayerPawnEntity pawn) {
            this.pawn = pawn.getUuid();
            this.world = pawn.getWorld().getRegistryKey();
        }
    }

    private static final Map<UUID, Spell> SPELLS = new HashMap<>();
    private static final Map<UUID, Possession> POSSESSIONS = new HashMap<>();

    private PawnPossessions() {
    }

    public static void initialize() {
        ServerTickEvents.END_SERVER_TICK.register(PawnPossessions::tick);
        ServerPlayConnectionEvents.DISCONNECT.register((handler, server) -> playerLeft(handler.player));
        ServerLivingEntityEvents.AFTER_DEATH.register((entity, source) -> {
            if (entity instanceof ServerPlayerEntity player) playerLeft(player);
        });
        ServerEntityWorldChangeEvents.AFTER_PLAYER_CHANGE_WORLD.register((player, origin, destination) -> playerLeft(player));
        // Inside the pawn (or under the spell), a player is a statue: only what bypasses invulnerability hurts (void, /kill)
        ServerLivingEntityEvents.ALLOW_DAMAGE.register((entity, source, amount) -> !(entity instanceof ServerPlayerEntity player)
                || !isBusy(player) || source.isIn(DamageTypeTags.BYPASSES_INVULNERABILITY));
        // ...and does nothing to the world (both sides: the client asks isInsideAPawn too)
        AttackBlockCallback.EVENT.register((player, world, hand, pos, direction) -> blocked(player));
        AttackEntityCallback.EVENT.register((player, world, hand, entity, hit) -> blocked(player));
        UseBlockCallback.EVENT.register((player, world, hand, hit) -> blocked(player));
        UseEntityCallback.EVENT.register((player, world, hand, entity, hit) -> blocked(player));
        UseItemCallback.EVENT.register((player, world, hand) -> blocked(player) == ActionResult.FAIL
                ? TypedActionResult.fail(player.getStackInHand(hand)) : TypedActionResult.pass(player.getStackInHand(hand)));
        ServerLifecycleEvents.SERVER_STOPPED.register(server -> {
            SPELLS.clear();
            POSSESSIONS.clear();
        });
    }

    /** Client side: how to know whether the local player is inside a pawn. */
    public static void setClientCheck(Predicate<PlayerEntity> check) {
        clientInside = check;
    }

    /** Whether {@code player} is inside a pawn (both sides). */
    public static boolean isInsideAPawn(PlayerEntity player) {
        if (player.getWorld().isClient) return clientInside.test(player);
        return POSSESSIONS.containsKey(player.getUuid());
    }

    /** Whether {@code player} is under the token spell, about to become a pawn (server side). */
    public static boolean isUnderSpell(PlayerEntity player) {
        return SPELLS.containsKey(player.getUuid());
    }

    private static boolean isBusy(PlayerEntity player) {
        return isUnderSpell(player) || POSSESSIONS.containsKey(player.getUuid());
    }

    private static ActionResult blocked(PlayerEntity player) {
        if (player.isSpectator()) return ActionResult.PASS;
        return isInsideAPawn(player) || (!player.getWorld().isClient && isUnderSpell(player)) ? ActionResult.FAIL : ActionResult.PASS;
    }

    /** Whether the spell can take {@code target}: a player in the world, not already under it nor in a pawn. */
    public static boolean canTokenize(PlayerEntity target) {
        return target.isAlive() && !target.isSpectator() && !isBusy(target);
    }

    /**
     * The token spell hits {@code target} (validated by the wand): they shrink to a pawn of {@code size} blocks
     * and are held in place until the pawn comes out of the spell.
     */
    public static void startSpell(ServerPlayerEntity target, UUID caster, float size, int color) {
        Spell spell = new Spell(target, caster, size, color);
        SPELLS.put(target.getUuid(), spell);
        float startScale = target.getScale();
        float targetScale = size / PlayerPawnEntity.HEIGHT;
        setModifier(target, EntityAttributes.GENERIC_SCALE, targetScale / Math.max(1.0E-3F, startScale) - 1);
        freeze(target);
        target.calculateDimensions();
        SquishEffect.playAnimation(target, startScale, targetScale, TokenizerWandItem.TRANSFORM_DURATION, size);
    }

    private static void tick(MinecraftServer server) {
        if (!SPELLS.isEmpty()) {
            for (Spell spell : new ArrayList<>(SPELLS.values())) tickSpell(server, spell);
        }
        if (!POSSESSIONS.isEmpty()) {
            for (Map.Entry<UUID, Possession> entry : new ArrayList<>(POSSESSIONS.entrySet())) {
                tickPossession(server, entry.getKey(), entry.getValue());
            }
        }
    }

    private static void tickSpell(MinecraftServer server, Spell spell) {
        ServerPlayerEntity player = server.getPlayerManager().getPlayer(spell.player);
        boolean here = player != null && player.isAlive() && player.getWorld().getRegistryKey() == spell.world;
        if (here) {
            // Can't move: held where the spell caught them (a creative player can't fly away either)
            if (player.squaredDistanceTo(spell.pos) > 1.0E-4) {
                player.networkHandler.requestTeleport(spell.pos.x, spell.pos.y, spell.pos.z, player.getYaw(), player.getPitch());
            }
            if (player.getAbilities().flying) {
                player.getAbilities().flying = false;
                player.sendAbilitiesUpdate();
            }
            player.setVelocity(Vec3d.ZERO);
        }
        if (--spell.ticksLeft > 0 && here) return;
        SPELLS.remove(spell.player);
        ServerWorld world = server.getWorld(spell.world);
        PlayerPawnEntity pawn = world == null ? null : spawnPawn(world, spell);
        if (!here) {
            // Gone before the end of the spell (left, died, elsewhere): the pawn is there, empty
            if (player != null) restore(player);
            return;
        }
        if (pawn == null) {
            restore(player);
            return;
        }
        possess(player, pawn);
    }

    /** The pawn the spell makes of the player: a token of the chosen size, named and coloured after the player. */
    private static @Nullable PlayerPawnEntity spawnPawn(ServerWorld world, Spell spell) {
        PlayerPawnEntity pawn = ModEntities.PLAYER_PAWN.create(world);
        if (pawn == null) return null;
        pawn.refreshPositionAndAngles(spell.pos.x, spell.pos.y, spell.pos.z, spell.yaw, 0);
        pawn.setHeadYaw(spell.yaw);
        pawn.setBodyYaw(spell.yaw);
        pawn.setSkin(spell.player, spell.name);
        TokenizedEntityInterface token = (TokenizedEntityInterface) pawn;
        token.steveparty$setTokenized(true);
        token.steveparty$setTokenOwner(spell.caster);
        token.steveparty$setTokenSize(spell.size);
        if (spell.color != TokenizerWandItem.NO_COLOR) {
            token.steveparty$setTokenColor(spell.color);
            pawn.setCustomName(Text.literal(spell.name).withColor(spell.color));
        } else {
            pawn.setCustomName(Text.literal(spell.name));
        }
        EntityAttributeInstance scale = pawn.getAttributeInstance(EntityAttributes.GENERIC_SCALE);
        if (scale != null) scale.setBaseValue(SquishEffect.scaleForSize(pawn, spell.size));
        if (!world.spawnEntity(pawn)) return null;
        world.spawnParticles(MagicShapeEffect.sparkle(1.3F, 0.9F, 0, SpellPalette.LILAC),
                pawn.getX(), pawn.getY() + pawn.getHeight() / 2, pawn.getZ(), 14, 0.3, 0.4, 0.3, 0.02);
        return pawn;
    }

    /** {@code player} goes inside {@code pawn} (the camera follows a few ticks later, see {@link #CAMERA_DELAY}). */
    static void possess(ServerPlayerEntity player, PlayerPawnEntity pawn) {
        POSSESSIONS.put(player.getUuid(), new Possession(pawn));
        pawn.setPossessor(player.getUuid());
        setModifier(player, EntityAttributes.GENERIC_SCALE, INSIDE_SCALE / Math.max(1.0E-3, player.getAttributeBaseValue(EntityAttributes.GENERIC_SCALE)) - 1);
        freeze(player);
        player.setInvisible(true);
        player.calculateDimensions();
        holdAt(player, pawn);
    }

    private static void tickPossession(MinecraftServer server, UUID playerId, Possession possession) {
        ServerPlayerEntity player = server.getPlayerManager().getPlayer(playerId);
        ServerWorld world = server.getWorld(possession.world);
        PlayerPawnEntity pawn = world != null && world.getEntity(possession.pawn) instanceof PlayerPawnEntity found ? found : null;
        if (player == null) {
            POSSESSIONS.remove(playerId);
            if (pawn != null) pawn.setPossessor(null);
            return;
        }
        possession.age++;
        boolean sneakOut = possession.camera && player.isSneaking();
        boolean cameraLost = possession.camera && player.getCameraEntity() != pawn;
        if (pawn == null || pawn.isRemoved() || !pawn.isAlive() || !player.isAlive() || player.isSpectator()
                || player.getWorld() != pawn.getWorld() || pawn.isInLava() || sneakOut || cameraLost) {
            eject(player, pawn);
            return;
        }
        holdAt(player, pawn);
        // The camera once the pawn exists on the client, and not while sneak is still held from before (it would
        // let them out at once)
        if (!possession.camera && possession.age >= CAMERA_DELAY && !player.isSneaking()) {
            possession.camera = true;
            player.setCameraEntity(pawn);
            MessageUtils.sendToPlayer(player, Text.translatable("message.steveparty.player_pawn.inside"),
                    MessageUtils.MessageType.ACTION_BAR);
        }
    }

    /** The player stays at the pawn's position (moved with it on the board), out of sight. */
    private static void holdAt(ServerPlayerEntity player, PlayerPawnEntity pawn) {
        player.setPosition(pawn.getX(), pawn.getY(), pawn.getZ());
        player.setVelocity(Vec3d.ZERO);
        player.fallDistance = 0;
        // Potion effects ending reset the flag: set again every tick
        if (!player.isInvisible()) player.setInvisible(true);
        player.getServerWorld().getChunkManager().updatePosition(player);
    }

    /** The pawn left the world (stored in a Token, killed, unloaded...): its player comes out at once. */
    static void onPawnRemoved(PlayerPawnEntity pawn) {
        UUID playerId = pawn.getPossessor();
        if (playerId == null || pawn.getServer() == null) return;
        ServerPlayerEntity player = pawn.getServer().getPlayerManager().getPlayer(playerId);
        if (player != null && POSSESSIONS.containsKey(playerId)) {
            eject(player, pawn);
        } else {
            POSSESSIONS.remove(playerId);
        }
    }

    /** The player left the server, died or changed dimension: out of the spell or of the pawn, nothing kept. */
    private static void playerLeft(ServerPlayerEntity player) {
        if (POSSESSIONS.containsKey(player.getUuid())) {
            Possession possession = POSSESSIONS.get(player.getUuid());
            ServerWorld world = player.getServer() == null ? null : player.getServer().getWorld(possession.world);
            eject(player, world != null && world.getEntity(possession.pawn) instanceof PlayerPawnEntity pawn ? pawn : null);
        }
        // Under the spell: the spell goes on and ends with an empty pawn (see tickSpell); the player is themselves again
        if (SPELLS.containsKey(player.getUuid())) restore(player);
    }

    /**
     * {@code player} comes out of {@code pawn} (null: the pawn is gone): their camera and size back, visible, next to
     * the pawn when it is still in their world. The pawn stays, empty.
     */
    public static void eject(ServerPlayerEntity player, @Nullable PlayerPawnEntity pawn) {
        POSSESSIONS.remove(player.getUuid());
        if (pawn != null && player.getUuid().equals(pawn.getPossessor())) pawn.setPossessor(null);
        restore(player);
        if (player.getCameraEntity() != player) player.setCameraEntity(player);
        if (pawn == null || !player.isAlive() || pawn.getWorld() != player.getWorld()) return;
        ServerWorld world = player.getServerWorld();
        Vec3d spot = exitSpot(world, player, pawn);
        player.teleport(world, spot.x, spot.y, spot.z, pawn.getYaw(), 0);
        player.fallDistance = 0;
        world.playSound(null, spot.x, spot.y, spot.z, SoundEvents.ENTITY_CHICKEN_EGG, SoundCategory.PLAYERS, 0.8F, 1.3F);
        world.spawnParticles(ParticleTypes.POOF, spot.x, spot.y + 0.9, spot.z, 8, 0.25, 0.4, 0.25, 0.02);
    }

    /**
     * Where the player comes out: a free spot right next to the pawn (in front of it first), on the ground and out
     * of lava; the pawn's own position when there is none.
     */
    private static Vec3d exitSpot(ServerWorld world, ServerPlayerEntity player, PlayerPawnEntity pawn) {
        EntityDimensions body = player.getDimensions(EntityPose.STANDING);
        Vec3d at = pawn.getPos();
        float yaw = pawn.getYaw() * MathHelper.RADIANS_PER_DEGREE;
        double frontX = -MathHelper.sin(yaw), frontZ = MathHelper.cos(yaw);
        double[][] directions = {{frontX, frontZ}, {frontZ, -frontX}, {-frontZ, frontX}, {-frontX, -frontZ},
                {frontX + frontZ, frontZ - frontX}, {frontX - frontZ, frontZ + frontX},
                {-frontX + frontZ, -frontZ - frontX}, {-frontX - frontZ, -frontZ + frontX}};
        double distance = Math.max(1.0, pawn.getWidth() / 2 + body.width() / 2 + 0.2);
        for (double dy : new double[]{0, 1, -1}) {
            for (double[] direction : directions) {
                double length = Math.sqrt(direction[0] * direction[0] + direction[1] * direction[1]);
                Vec3d candidate = at.add(direction[0] / length * distance, dy, direction[1] / length * distance);
                if (isSafe(world, player, body.getBoxAt(candidate))) return candidate;
            }
        }
        return at;
    }

    private static boolean isSafe(ServerWorld world, ServerPlayerEntity player, Box box) {
        if (!world.isSpaceEmpty(player, box) || world.containsFluid(box)) return false;
        // Something to stand on, and no lava right under it
        Box below = new Box(box.minX, box.minY - 0.5, box.minZ, box.maxX, box.minY, box.maxZ);
        if (world.isSpaceEmpty(player, below)) return false;
        BlockPos under = BlockPos.ofFloored(box.getCenter().x, box.minY - 0.5, box.getCenter().z);
        return !world.getFluidState(under).isIn(FluidTags.LAVA);
    }

    /** Can't move: no walking, no jumping (temporary: never saved with the player). */
    private static void freeze(ServerPlayerEntity player) {
        setModifier(player, EntityAttributes.GENERIC_MOVEMENT_SPEED, -1);
        setModifier(player, EntityAttributes.GENERIC_JUMP_STRENGTH, -1);
        player.setVelocity(Vec3d.ZERO);
        if (player.getAbilities().flying) {
            player.getAbilities().flying = false;
            player.sendAbilitiesUpdate();
        }
    }

    /** The player as before the spell: size, moves, visibility. */
    private static void restore(ServerPlayerEntity player) {
        removeModifier(player, EntityAttributes.GENERIC_SCALE);
        removeModifier(player, EntityAttributes.GENERIC_MOVEMENT_SPEED);
        removeModifier(player, EntityAttributes.GENERIC_JUMP_STRENGTH);
        player.setInvisible(player.hasStatusEffect(StatusEffects.INVISIBILITY));
        // The hitbox at once (not at the next tick of the player): the way out is looked for with it
        player.calculateDimensions();
    }

    private static void setModifier(ServerPlayerEntity player, RegistryEntry<EntityAttribute> attribute, double value) {
        EntityAttributeInstance instance = player.getAttributeInstance(attribute);
        if (instance == null) return;
        instance.removeModifier(MODIFIER);
        instance.addTemporaryModifier(new EntityAttributeModifier(MODIFIER, value, EntityAttributeModifier.Operation.ADD_MULTIPLIED_TOTAL));
    }

    private static void removeModifier(ServerPlayerEntity player, RegistryEntry<EntityAttribute> attribute) {
        EntityAttributeInstance instance = player.getAttributeInstance(attribute);
        if (instance != null) instance.removeModifier(MODIFIER);
    }
}
