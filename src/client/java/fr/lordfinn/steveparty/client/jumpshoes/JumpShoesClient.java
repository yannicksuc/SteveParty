package fr.lordfinn.steveparty.client.jumpshoes;

import fr.lordfinn.steveparty.client.payloads.ClientPayloads;
import fr.lordfinn.steveparty.items.custom.jumpshoes.JumpShoes;
import fr.lordfinn.steveparty.items.custom.jumpshoes.TripleJumpChain;
import fr.lordfinn.steveparty.payloads.custom.JumpShoesPayloads;
import fr.lordfinn.steveparty.sounds.ModSounds;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.block.BlockRenderType;
import net.minecraft.block.BlockState;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.entity.Entity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.particle.BlockStateParticleEffect;
import net.minecraft.particle.ParticleEffect;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvent;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import net.minecraft.util.math.random.Random;
import org.jetbrains.annotations.Nullable;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;

/**
 * The Triple Jump Shoes on the client: the client moves its own player (chained jumps, wall slide, wall kick, double
 * jump) for responsiveness, shows the effects right away and reports each move to the server, which relays it to
 * the players around ({@link #onSeen}) for their effects.
 */
public final class JumpShoesClient {
    private static final int BUMP_TICKS = 8;
    private static final TripleJumpChain CHAIN = new TripleJumpChain();
    /** Players around sliding down a wall, by entity id: their wall side. */
    private static final Map<Integer, Direction> REMOTE_SLIDES = new HashMap<>();

    private static boolean wasJumping;
    private static boolean jumpedThisTick;
    private static boolean doubleUsed;
    private static @Nullable Direction slideWall;
    private static int slideTicks;
    /** Ticks since the player last ran into a wall in the air (he sticks to it once falling). */
    private static int bumpTicks;
    private static @Nullable Direction graceWall;
    private static int graceTicks;
    /** The wall face of the last kick (side and block coordinate): no second kick off it before the ground or another wall. */
    private static @Nullable Direction lastKickWall;
    private static int lastKickPlane;

    private JumpShoesClient() {
    }

    public static void initialize() {
        JumpShoes.clientJump = JumpShoesClient::onJump;
        ClientTickEvents.END_CLIENT_TICK.register(JumpShoesClient::tick);
        ClientPayloads.receive(JumpShoesPayloads.Seen.ID, (payload, context) -> onSeen(context.client(), payload));
    }

    public static void clear() {
        REMOTE_SLIDES.clear();
        CHAIN.reset();
        slideWall = null;
        graceWall = null;
        lastKickWall = null;
        doubleUsed = false;
    }

    // ------------------------------------------------------------------ own player

    /** A jump off the ground (LivingEntityMixin): the 2nd and 3rd of a chain go higher. */
    private static void onJump(PlayerEntity player) {
        if (!(player instanceof ClientPlayerEntity self) || !self.isMainPlayer()) return;
        jumpedThisTick = true;
        if (JumpShoes.blocked(self)) {
            CHAIN.reset();
            return;
        }
        Vec3d velocity = self.getVelocity();
        int number = CHAIN.jump(self.age, velocity.horizontalLength());
        if (number < 2) return;
        self.setVelocity(velocity.x, velocity.y * TripleJumpChain.boost(number), velocity.z);
        JumpShoes.Action action = number == 2 ? JumpShoes.Action.JUMP_2 : JumpShoes.Action.JUMP_3;
        effects(self.clientWorld, self, action, null);
        send(action, null);
    }

    private static void tick(MinecraftClient client) {
        if (client.world == null || client.isPaused()) return;
        tickRemote(client.world);
        ClientPlayerEntity player = client.player;
        if (player == null) return;

        boolean jumping = player.input.jumping;
        boolean pressed = jumping && !wasJumping && !jumpedThisTick;
        wasJumping = jumping;
        jumpedThisTick = false;

        if (!JumpShoes.wears(player) || JumpShoes.blocked(player)) {
            CHAIN.reset();
            stopSlide();
            return;
        }
        if (player.hurtTime > 0) CHAIN.reset();
        if (player.isOnGround()) {
            CHAIN.onGround(player.age);
            doubleUsed = false;
            lastKickWall = null;
            graceTicks = 0;
            stopSlide();
            return;
        }

        if (player.horizontalCollision) bumpTicks = BUMP_TICKS;
        else if (bumpTicks > 0) bumpTicks--;
        if (slideWall != null) {
            if (!JumpShoes.touchesWall(player, slideWall, JumpShoes.CLIENT_REACH)) {
                graceWall = slideWall;
                graceTicks = JumpShoes.KICK_GRACE_TICKS;
                stopSlide();
            }
        } else if (player.getVelocity().y < 0) {
            Vec3d toward = intent(player);
            Direction wall = JumpShoes.wallSide(player, JumpShoes.CLIENT_REACH, toward);
            if (wall != null && (bumpTicks > 0
                    || toward.x * wall.getOffsetX() + toward.z * wall.getOffsetZ() > 0.1)) {
                startSlide(player, wall);
            }
        }

        if (pressed) {
            Direction kickWall = slideWall != null ? slideWall : graceTicks > 0 ? graceWall : null;
            if (kickWall != null && canKick(player, kickWall)) {
                kick(player, kickWall);
            } else if (slideWall == null && !doubleUsed && JumpShoes.hasDoubleJump(player)) {
                doubleJump(player);
            }
        }
        if (graceTicks > 0) graceTicks--;

        if (slideWall != null) {
            Vec3d velocity = player.getVelocity();
            if (velocity.y < -JumpShoes.SLIDE_SPEED) player.setVelocity(velocity.x, -JumpShoes.SLIDE_SPEED, velocity.z);
            player.fallDistance = 0;
            slideEffects(player.clientWorld, player, slideWall, slideTicks++);
        }
    }

    /** Where the player wants to go: his movement keys, and his speed. */
    private static Vec3d intent(ClientPlayerEntity player) {
        float forward = player.input.movementForward, sideways = player.input.movementSideways;
        float sin = MathHelper.sin(player.getYaw() * MathHelper.RADIANS_PER_DEGREE);
        float cos = MathHelper.cos(player.getYaw() * MathHelper.RADIANS_PER_DEGREE);
        Vec3d velocity = player.getVelocity();
        return new Vec3d(sideways * cos - forward * sin + velocity.x * 2, 0, forward * cos + sideways * sin + velocity.z * 2);
    }

    private static void startSlide(ClientPlayerEntity player, Direction wall) {
        slideWall = wall;
        slideTicks = 0;
        graceTicks = 0;
        doubleUsed = false;
        CHAIN.reset();
        if (lastKickWall != null && (lastKickWall != wall || lastKickPlane != plane(player, wall))) lastKickWall = null;
        effects(player.clientWorld, player, JumpShoes.Action.SLIDE_START, wall);
        send(JumpShoes.Action.SLIDE_START, wall);
    }

    private static void stopSlide() {
        if (slideWall == null) return;
        slideWall = null;
        send(JumpShoes.Action.SLIDE_STOP, null);
    }

    private static int plane(PlayerEntity player, Direction wall) {
        BlockPos block = JumpShoes.wallBlock(player, wall);
        return wall.getAxis() == Direction.Axis.X ? block.getX() : block.getZ();
    }

    private static boolean canKick(PlayerEntity player, Direction wall) {
        return lastKickWall != wall || lastKickPlane != plane(player, wall);
    }

    private static void kick(ClientPlayerEntity player, Direction wall) {
        Direction away = wall.getOpposite();
        Vec3d look = player.getRotationVector();
        Vec3d direction = JumpShoes.kickDirection(look.x, look.z, away.getOffsetX(), away.getOffsetZ());
        player.setVelocity(direction.x * JumpShoes.KICK_AWAY_SPEED, JumpShoes.KICK_UP_SPEED, direction.z * JumpShoes.KICK_AWAY_SPEED);
        lastKickWall = wall;
        lastKickPlane = plane(player, wall);
        slideWall = null; // the kick ends the slide, on the server too
        graceTicks = 0;
        doubleUsed = false;
        CHAIN.reset();
        effects(player.clientWorld, player, JumpShoes.Action.WALL_KICK, wall);
        send(JumpShoes.Action.WALL_KICK, wall);
    }

    private static void doubleJump(ClientPlayerEntity player) {
        doubleUsed = true;
        CHAIN.reset();
        Vec3d velocity = player.getVelocity();
        player.setVelocity(velocity.x, JumpShoes.DOUBLE_JUMP_SPEED, velocity.z);
        effects(player.clientWorld, player, JumpShoes.Action.DOUBLE_JUMP, null);
        send(JumpShoes.Action.DOUBLE_JUMP, null);
    }

    private static void send(JumpShoes.Action action, @Nullable Direction side) {
        if (!ClientPlayNetworking.canSend(JumpShoesPayloads.Move.ID)) return;
        ClientPlayNetworking.send(new JumpShoesPayloads.Move((byte) action.ordinal(), (byte) (side == null ? -1 : side.getId())));
    }

    // ------------------------------------------------------------------ the others

    private static void onSeen(MinecraftClient client, JumpShoesPayloads.Seen seen) {
        if (client.world == null || !(client.world.getEntityById(seen.entityId()) instanceof PlayerEntity player)) return;
        JumpShoes.Action action = JumpShoes.Action.byId(seen.action());
        if (action == null) return;
        Direction side = seen.side() >= 0 && seen.side() < 6 ? Direction.byId(seen.side()) : null;
        if (action == JumpShoes.Action.SLIDE_START && side != null) REMOTE_SLIDES.put(seen.entityId(), side);
        else REMOTE_SLIDES.remove(seen.entityId());
        effects(client.world, player, action, side);
    }

    private static void tickRemote(ClientWorld world) {
        if (REMOTE_SLIDES.isEmpty()) return;
        for (Iterator<Map.Entry<Integer, Direction>> it = REMOTE_SLIDES.entrySet().iterator(); it.hasNext(); ) {
            Map.Entry<Integer, Direction> slide = it.next();
            Entity entity = world.getEntityById(slide.getKey());
            if (!(entity instanceof PlayerEntity player) || entity.isRemoved() || entity.isOnGround()) {
                it.remove();
                continue;
            }
            slideEffects(world, player, slide.getValue(), player.age);
        }
    }

    // ------------------------------------------------------------------ effects

    /** A move's particles and sound, for the player who made it and for those around. */
    private static void effects(ClientWorld world, PlayerEntity player, JumpShoes.Action action, @Nullable Direction side) {
        Random random = world.random;
        double x = player.getX(), y = player.getY(), z = player.getZ();
        BlockState ground = world.getBlockState(BlockPos.ofFloored(x, y - 0.2, z));
        switch (action) {
            case JUMP_2 -> {
                dust(world, ground, x, y, z, 6, 0.3);
                for (int i = 0; i < 2; i++) {
                    world.addParticle(ParticleTypes.POOF, x + (random.nextDouble() - 0.5) * 0.4, y + 0.05,
                            z + (random.nextDouble() - 0.5) * 0.4, 0, 0.02, 0);
                }
                sound(world, player, ModSounds.JUMP_SHOES_HOP);
            }
            case JUMP_3 -> {
                dust(world, ground, x, y, z, 14, 0.45);
                ring(world, x, y, z, 20, 0.12);
                for (int i = 0; i < 5; i++) {
                    world.addParticle(ParticleTypes.POOF, x + (random.nextDouble() - 0.5) * 0.6, y + 0.1,
                            z + (random.nextDouble() - 0.5) * 0.6, 0, 0.08 + random.nextDouble() * 0.05, 0);
                }
                sound(world, player, ModSounds.JUMP_SHOES_LEAP);
            }
            case DOUBLE_JUMP -> {
                ring(world, x, y, z, 12, 0.07);
                sound(world, player, ModSounds.JUMP_SHOES_DOUBLE_JUMP);
            }
            case WALL_KICK -> {
                if (side == null) return;
                double cx = x + side.getOffsetX() * player.getWidth() / 2, cz = z + side.getOffsetZ() * player.getWidth() / 2;
                BlockState wall = world.getBlockState(JumpShoes.wallBlock(player, side));
                dust(world, wall, cx, y + 0.6, cz, 8, 0.25);
                for (int i = 0; i < 4; i++) {
                    world.addParticle(ParticleTypes.POOF, cx, y + 0.3 + random.nextDouble() * 0.8, cz,
                            -side.getOffsetX() * 0.05, 0.02, -side.getOffsetZ() * 0.05);
                }
                sound(world, player, ModSounds.JUMP_SHOES_WALL_KICK);
            }
            case SLIDE_START -> {
                if (side != null) slideEffects(world, player, side, 0);
            }
            case SLIDE_STOP -> {
            }
        }
    }

    /** Light friction while sliding: a grain of the wall at the feet or at the hands, a very quiet scrape now and then. */
    private static void slideEffects(ClientWorld world, PlayerEntity player, Direction side, int tick) {
        if (tick % 2 == 0) {
            double out = player.getWidth() / 2 + 0.02;
            double height = tick % 4 == 0 ? 0.1 : 1.3;
            BlockState wall = world.getBlockState(JumpShoes.wallBlock(player, side));
            ParticleEffect grain = grain(wall);
            world.addParticle(grain, player.getX() + side.getOffsetX() * out, player.getY() + height,
                    player.getZ() + side.getOffsetZ() * out, 0, 0.03, 0);
        }
        if (tick % 6 == 0) sound(world, player, ModSounds.JUMP_SHOES_SLIDE);
    }

    private static void dust(ClientWorld world, BlockState state, double x, double y, double z, int count, double spread) {
        ParticleEffect grain = grain(state);
        Random random = world.random;
        for (int i = 0; i < count; i++) {
            double dx = (random.nextDouble() - 0.5) * 2 * spread, dz = (random.nextDouble() - 0.5) * 2 * spread;
            world.addParticle(grain, x + dx, y + 0.05, z + dz, dx * 0.15, 0.06 + random.nextDouble() * 0.06, dz * 0.15);
        }
    }

    /** A ring of smoke spreading on the ground. */
    private static void ring(ClientWorld world, double x, double y, double z, int count, double speed) {
        for (int i = 0; i < count; i++) {
            double angle = Math.PI * 2 * i / count;
            double cos = Math.cos(angle), sin = Math.sin(angle);
            world.addParticle(ParticleTypes.CLOUD, x + cos * 0.3, y + 0.05, z + sin * 0.3, cos * speed, 0, sin * speed);
        }
    }

    private static ParticleEffect grain(BlockState state) {
        if (state.isAir() || state.getRenderType() == BlockRenderType.INVISIBLE) return ParticleTypes.POOF;
        return new BlockStateParticleEffect(ParticleTypes.BLOCK, state);
    }

    private static void sound(ClientWorld world, PlayerEntity player, SoundEvent sound) {
        world.playSound(player.getX(), player.getY(), player.getZ(), sound, SoundCategory.PLAYERS, 1.0f,
                0.9f + world.random.nextFloat() * 0.2f, false);
    }
}
