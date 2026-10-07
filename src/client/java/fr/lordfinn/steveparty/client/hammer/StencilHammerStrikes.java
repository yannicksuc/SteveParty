package fr.lordfinn.steveparty.client.hammer;

import fr.lordfinn.steveparty.blocks.custom.signs.StencilCanvasBlockEntity;
import fr.lordfinn.steveparty.items.custom.StencilHammerStrike;
import fr.lordfinn.steveparty.payloads.custom.StencilHammerStrikePayload;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.particle.BlockStateParticleEffect;
import net.minecraft.particle.DustParticleEffect;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.registry.Registries;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.util.Arm;
import net.minecraft.util.DyeColor;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.RotationAxis;
import net.minecraft.util.math.Vec3d;
import net.minecraft.util.math.random.Random;
import org.jetbrains.annotations.Nullable;

import java.util.HashMap;
import java.util.Map;

/**
 * Client side of the Stencil Hammer strikes ({@link StencilHammerStrike}): which players are swinging and where they
 * are in their swing (first person pose, third person arm), the impact (a wooden drum thump and a light wet splat, a
 * burst of paint, a tiny camera shake for the striker), and the stamp reveals the canvas renderer draws (the pattern spreading from
 * the hit point once the head lands).
 * <p>
 * Timeline, in ticks from the click: wind-up (0 to 4, the hammer goes up and back), smash (4 to 5, fast, down onto
 * the aimed point), impact at 5, a short hold with the head squashed, then the recovery.
 */
public final class StencilHammerStrikes {
    private static final float WIND_UP_END = 4.0F;
    private static final float IMPACT = StencilHammerStrike.IMPACT_TICK;
    private static final float HOLD_END = IMPACT + 1.0F;
    private static final float END = StencilHammerStrike.DURATION;
    /** How long the stamped pattern takes to spread from the hit point. */
    public static final float REVEAL_TICKS = 6.0F;
    private static final float SHAKE_TICKS = 4.0F;

    private static final class Strike {
        final StencilHammerStrikePayload payload;
        final long start;
        boolean impactPlayed;

        Strike(StencilHammerStrikePayload payload, long start) {
            this.payload = payload;
            this.start = start;
        }
    }

    /**
     * A stamp being revealed on a canvas: the symbol there before the strike (still drawn under the new one while it
     * spreads, and alone until the head lands), and where the head lands.
     */
    public static final class Reveal {
        public final long impact;
        public final Vec3d hit;
        /** The canvas renderer's cache of the symbol drawn before the strike (null: none drawn yet). */
        public final @Nullable Object oldCache;
        public final @Nullable DyeColor oldColor;
        public final int oldFade;
        public final boolean oldGlowing;

        Reveal(long impact, Vec3d hit, @Nullable Object oldCache, @Nullable DyeColor oldColor, int oldFade, boolean oldGlowing) {
            this.impact = impact;
            this.hit = hit;
            this.oldCache = oldCache;
            this.oldColor = oldColor;
            this.oldFade = oldFade;
            this.oldGlowing = oldGlowing;
        }

        /** Ticks since the head landed (negative before). */
        public float sinceImpact(ClientWorld world, float tickDelta) {
            return world.getTime() - impact + tickDelta;
        }
    }

    private static final Map<Integer, Strike> STRIKES = new HashMap<>();
    private static final Map<BlockPos, Reveal> REVEALS = new HashMap<>();
    private static long shakeStart = Long.MIN_VALUE / 2;

    private StencilHammerStrikes() {
    }

    public static void initialize() {
        StencilHammerStrike.localStrike = StencilHammerStrikes::start;
        ClientPlayNetworking.registerGlobalReceiver(StencilHammerStrikePayload.ID,
                (payload, context) -> context.client().execute(() -> start(payload)));
        ClientTickEvents.END_CLIENT_TICK.register(StencilHammerStrikes::tick);
        ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> client.execute(() -> {
            STRIKES.clear();
            REVEALS.clear();
        }));
    }

    // ---------------------------------------------------------------- strikes

    private static void start(StencilHammerStrikePayload payload) {
        ClientWorld world = MinecraftClient.getInstance().world;
        if (world == null) return;
        long now = world.getTime();
        STRIKES.put(payload.entityId(), new Strike(payload, now));
        Vec3d hit = new Vec3d(payload.hit());
        Reveal reveal = world.getBlockEntity(payload.canvasPos()) instanceof StencilCanvasBlockEntity canvas && canvas.hasShape()
                ? new Reveal(now + (long) IMPACT, hit, canvas.getRenderCache(), canvas.getColor(), canvas.getFade(), canvas.isGlowing())
                : new Reveal(now + (long) IMPACT, hit, null, null, 0, false);
        REVEALS.put(payload.canvasPos().toImmutable(), reveal);
        // The wind-up: a soft whoosh
        var entity = world.getEntityById(payload.entityId());
        Vec3d at = entity != null ? entity.getPos() : hit;
        float loudness = loudness(payload);
        world.playSound(at.x, at.y + 1, at.z, SoundEvents.ENTITY_PLAYER_ATTACK_NODAMAGE, SoundCategory.PLAYERS, 0.18F * loudness, 0.75F, false);
    }

    private static void tick(MinecraftClient client) {
        ClientWorld world = client.world;
        if (world == null) {
            STRIKES.clear();
            REVEALS.clear();
            return;
        }
        long now = world.getTime();
        STRIKES.values().removeIf(strike -> {
            if (!strike.impactPlayed && now - strike.start >= IMPACT) {
                strike.impactPlayed = true;
                impact(client, world, strike.payload);
            }
            return now - strike.start > END || now < strike.start;
        });
        REVEALS.values().removeIf(reveal -> now - reveal.impact > REVEAL_TICKS + 1 || now < reveal.impact - END);
    }

    /** How much quieter the strike of another player sounds than one's own (still heard, not deafening). */
    private static final float OTHERS_LOUDNESS = 0.35F;

    /** Full for the striker, {@link #OTHERS_LOUDNESS} for the players around. */
    private static float loudness(StencilHammerStrikePayload payload) {
        MinecraftClient client = MinecraftClient.getInstance();
        return client.player != null && client.player.getId() == payload.entityId() ? 1F : OTHERS_LOUDNESS;
    }

    /** The head lands: thud, wet splat, burst of paint, camera shake for the striker. */
    private static void impact(MinecraftClient client, ClientWorld world, StencilHammerStrikePayload payload) {
        Vec3d hit = new Vec3d(payload.hit());
        Direction side = payload.side();
        Vec3d normal = Vec3d.of(side.getVector());
        DyeColor color = payload.color() < 0 ? null : DyeColor.byId(payload.color());
        Random random = world.getRandom();
        float loudness = loudness(payload);

        // A wooden drum hit: a low, round thump (bass drum, wood and a barrel's thud, a soft cloth hit), no metal
        world.playSound(hit.x, hit.y, hit.z, SoundEvents.BLOCK_NOTE_BLOCK_BASEDRUM.value(), SoundCategory.PLAYERS, 0.3F * loudness, 0.7F, false);
        world.playSound(hit.x, hit.y, hit.z, SoundEvents.BLOCK_WOOD_HIT, SoundCategory.PLAYERS, 0.25F * loudness, 0.6F, false);
        world.playSound(hit.x, hit.y, hit.z, SoundEvents.BLOCK_BARREL_CLOSE, SoundCategory.PLAYERS, 0.15F * loudness, 0.7F, false);
        world.playSound(hit.x, hit.y, hit.z, SoundEvents.BLOCK_WOOL_HIT, SoundCategory.PLAYERS, 0.3F * loudness, 0.7F, false);
        if (color != null) {
            // The paint: a light wet splat
            world.playSound(hit.x, hit.y, hit.z, SoundEvents.ENTITY_SLIME_SQUISH_SMALL, SoundCategory.PLAYERS, 0.2F * loudness, 1.1F, false);
            world.playSound(hit.x, hit.y, hit.z, SoundEvents.BLOCK_HONEY_BLOCK_STEP, SoundCategory.PLAYERS, 0.15F * loudness, 1.2F, false);
        } else {
            world.playSound(hit.x, hit.y, hit.z, SoundEvents.BLOCK_STONE_HIT, SoundCategory.PLAYERS, 0.2F * loudness, 1.0F, false);
        }

        // Two directions along the struck face
        Vec3d u = side.getAxis() == Direction.Axis.Y ? new Vec3d(1, 0, 0) : new Vec3d(0, 1, 0);
        Vec3d v = normal.crossProduct(u);
        Vec3d origin = hit.add(normal.multiply(0.06));
        BlockState struck = world.getBlockState(BlockPos.ofFloored(hit.subtract(normal.multiply(0.05))));
        BlockState chunks = color != null ? concrete(color) : struck.isAir() ? Blocks.STONE.getDefaultState() : struck;
        // Pixel blobs of paint (or crumbs when engraving) flung off the face
        for (int i = 0; i < 8; i++) {
            double a = random.nextDouble() * Math.PI * 2;
            double speed = 0.12 + random.nextDouble() * 0.18;
            Vec3d vel = u.multiply(Math.cos(a) * speed).add(v.multiply(Math.sin(a) * speed)).add(normal.multiply(0.1 + random.nextDouble() * 0.2));
            world.addParticle(new BlockStateParticleEffect(ParticleTypes.BLOCK, chunks), origin.x, origin.y, origin.z, vel.x, vel.y, vel.z);
        }
        if (color != null) {
            // The splat: a ring of wet paint spreading along the face
            int rgb = color.getFireworkColor(); // the bright tone of the dye, like fresh wet paint
            for (int i = 0; i < 24; i++) {
                double a = i * Math.PI * 2 / 24 + random.nextDouble() * 0.3;
                double speed = 2.0 + random.nextDouble() * 1.5; // dust particles keep a tenth of it
                Vec3d vel = u.multiply(Math.cos(a) * speed).add(v.multiply(Math.sin(a) * speed)).add(normal.multiply(0.3));
                world.addParticle(new DustParticleEffect(Vec3d.unpackRgb(rgb).toVector3f(), 1.8F + random.nextFloat() * 1.0F), origin.x, origin.y, origin.z, vel.x, vel.y, vel.z);
            }
        }
        if (client.player != null && client.player.getId() == payload.entityId()) shakeStart = world.getTime();
    }

    private static BlockState concrete(DyeColor color) {
        var block = Registries.BLOCK.get(Identifier.ofVanilla(color.getName() + "_concrete"));
        return block == Blocks.AIR ? Blocks.WHITE_CONCRETE.getDefaultState() : block.getDefaultState();
    }

    // ---------------------------------------------------------------- queries for the renderers

    /** Ticks into the swing of {@code entityId}'s hammer in that hand, or -1 when it is not swinging it. */
    public static float strikeTicks(int entityId, boolean mainHand, float tickDelta) {
        Strike strike = STRIKES.get(entityId);
        ClientWorld world = MinecraftClient.getInstance().world;
        if (strike == null || world == null || strike.payload.mainHand() != mainHand) return -1;
        float t = world.getTime() - strike.start + tickDelta;
        return t < 0 || t > END ? -1 : t;
    }

    /** Ticks into the swing of {@code entityId}'s hammer, whichever hand, or -1. */
    public static float strikeTicks(int entityId, float tickDelta) {
        Strike strike = STRIKES.get(entityId);
        return strike == null ? -1 : strikeTicks(entityId, strike.payload.mainHand(), tickDelta);
    }

    public static boolean strikesWithMainHand(int entityId) {
        Strike strike = STRIKES.get(entityId);
        return strike == null || strike.payload.mainHand();
    }

    public static @Nullable Reveal reveal(BlockPos pos) {
        return REVEALS.isEmpty() ? null : REVEALS.get(pos);
    }

    // ---------------------------------------------------------------- poses

    /**
     * A key pose of the first person swing, around the grip: angle in degrees (+ back towards the camera, - forward
     * and down), roll in degrees (towards the outside of the hand), and the grip's shift (x right, y up, z towards
     * the camera).
     */
    private record Pose(float angle, float roll, float x, float y, float z) {
        static final Pose REST = new Pose(0, 0, 0, 0, 0);
        /** Up and back, a little outwards. */
        static final Pose WOUND_UP = new Pose(48, -12, 0.08F, 0.22F, 0.06F);
        /** The head on the aimed point: brought in front of the view, forward and down. */
        static final Pose IMPACT = new Pose(-58, 4, -0.3F, 0.14F, -0.16F);

        Pose lerp(float f, Pose to) {
            return new Pose(MathHelper.lerp(f, angle, to.angle), MathHelper.lerp(f, roll, to.roll), MathHelper.lerp(f, x, to.x),
                    MathHelper.lerp(f, y, to.y), MathHelper.lerp(f, z, to.z));
        }
    }

    /**
     * Where the hand grips the handle, in the first person item frame (before the model's display transform): the
     * swing turns around it. It follows from the hammer's first person display transform (the grip is about 18
     * pixels under the head's centre, the whole model at 0.4 scale).
     */
    private static final float GRIP_X = -0.08F, GRIP_Y = -0.12F, GRIP_Z = -0.11F;

    private static Pose pose(float t) {
        if (t < WIND_UP_END) {
            float w = 1 - (float) Math.pow(1 - t / WIND_UP_END, 3); // ease out: quick up, slowing at the top
            return Pose.REST.lerp(w, Pose.WOUND_UP);
        }
        if (t < IMPACT) {
            float s = (t - WIND_UP_END) / (IMPACT - WIND_UP_END);
            return Pose.WOUND_UP.lerp(s * s, Pose.IMPACT); // ease in: the smash accelerates into the surface
        }
        if (t < HOLD_END) return Pose.IMPACT;
        float r = MathHelper.clamp((t - HOLD_END) / (END - HOLD_END), 0, 1);
        r = r < 0.5F ? 4 * r * r * r : 1 - (float) Math.pow(-2 * r + 2, 3) / 2; // ease in-out back to rest
        return Pose.IMPACT.lerp(r, Pose.REST);
    }

    /** First person: the held hammer (item frame, before its display transform) raised, smashed down, squashed. */
    public static void applyFirstPerson(MatrixStack matrices, Arm arm, float t) {
        Pose p = pose(t);
        float side = arm == Arm.RIGHT ? 1 : -1;
        matrices.translate(p.x() * side, p.y(), p.z());
        matrices.translate(GRIP_X * side, GRIP_Y, GRIP_Z);
        matrices.multiply(RotationAxis.POSITIVE_X.rotationDegrees(p.angle()));
        matrices.multiply(RotationAxis.POSITIVE_Z.rotationDegrees(p.roll() * side));
        // Squash on impact: the hammer shortens along its handle and bulges, then springs back
        float squash = t < IMPACT ? 0 : MathHelper.clamp(1 - (t - IMPACT) / 2.5F, 0, 1);
        if (squash > 0) matrices.scale(1 + 0.1F * squash, 1 - 0.14F * squash, 1 + 0.1F * squash);
        matrices.translate(-GRIP_X * side, -GRIP_Y, -GRIP_Z);
    }

    /**
     * Third person: pitch of the arm holding the hammer, from its pitch as the game posed it: raised over the head,
     * then brought down in front onto the aimed point.
     */
    public static float armPitch(float restPitch, float t) {
        if (t < WIND_UP_END) {
            float w = 1 - (float) Math.pow(1 - t / WIND_UP_END, 3);
            return MathHelper.lerp(w, restPitch, -2.9F);
        }
        if (t < IMPACT) {
            float s = (t - WIND_UP_END) / (IMPACT - WIND_UP_END);
            return MathHelper.lerp(s * s, -2.9F, -0.45F);
        }
        if (t < HOLD_END) return -0.45F;
        float r = MathHelper.clamp((t - HOLD_END) / (END - HOLD_END), 0, 1);
        return MathHelper.lerp(r * r * (3 - 2 * r), -0.45F, restPitch);
    }

    /** Camera shake of the striker when the head lands, in degrees (roll, pitch). */
    public static void applyShake(MatrixStack matrices, float tickDelta) {
        ClientWorld world = MinecraftClient.getInstance().world;
        if (world == null) return;
        float dt = world.getTime() - shakeStart + tickDelta;
        if (dt < 0 || dt > SHAKE_TICKS) return;
        float amp = 1 - dt / SHAKE_TICKS;
        amp *= amp;
        matrices.multiply(RotationAxis.POSITIVE_Z.rotationDegrees(1.1F * amp * MathHelper.sin(dt * 5.0F)));
        matrices.multiply(RotationAxis.POSITIVE_X.rotationDegrees(0.9F * amp * MathHelper.cos(dt * 4.0F)));
    }
}
