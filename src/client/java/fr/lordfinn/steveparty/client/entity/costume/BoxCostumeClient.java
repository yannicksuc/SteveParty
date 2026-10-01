package fr.lordfinn.steveparty.client.entity.costume;

import fr.lordfinn.steveparty.Steveparty;
import fr.lordfinn.steveparty.items.ModItems;
import fr.lordfinn.steveparty.items.custom.BoxCostumeItem;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.rendering.v1.BuiltinItemRendererRegistry;
import net.fabricmc.fabric.api.client.rendering.v1.HudRenderCallback;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.network.AbstractClientPlayerEntity;
import net.minecraft.client.render.RenderLayer;
import net.minecraft.client.render.RenderTickCounter;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.render.entity.state.PlayerEntityRenderState;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvent;
import net.minecraft.sound.SoundEvents;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.ColorHelper;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.RotationAxis;
import net.minecraft.util.math.Vec3d;
import org.jetbrains.annotations.Nullable;

import java.util.Map;
import java.util.WeakHashMap;

/**
 * Client side of the Box Costume: one animated box per wearer in view (everybody sees it: the chest slot and the
 * sneaking flag are synced), the merchant's sounds when it closes and opens, the wearer's own view from inside the
 * box, and the item icon.
 */
public final class BoxCostumeClient {
    /** The merchant ducks into his box in 0.3 s ("closed"): the wearer disappears inside it then. */
    public static final int DUCK_TICKS = 6;
    /** The merchant's sounds, on the costume's animations: flaps folding (0.3 s), the block when the last one slaps shut; popping out as the box rises. */
    private static final int CLOSE_SOUND_TICKS = 5, PLACE_SOUND_DELAY = 5, OPEN_SOUND_TICKS = 2;
    /** The wearer is no longer drawn once the box is (nearly) on the ground: he is squashed inside it by then. */
    private static final float INSIDE_LIFT = 0.04F;
    /** Height of the wearer's body, squashed into the box on the ground (head under the flaps), as a share of his own. */
    private static final float SQUASHED = 0.42F;
    /** Walking: limb speed above 0.08 for 2 ticks, standing: below 0.03 for 5 ticks (the merchant's hysteresis). */
    private static final float WALK_START = 0.08F, WALK_STOP = 0.03F;
    private static final Identifier VIEW_TEXTURE = Steveparty.id("textures/misc/box_costume_view.png");
    /** The inside view fades in over this many ticks once ducked. */
    private static final float VIEW_FADE_TICKS = 6.0F;

    private static final Map<PlayerEntity, BoxCostumeAnimatable> BOXES = new WeakHashMap<>();
    private static BoxCostumeRenderer renderer;
    /** The item icon: the merchant's box as is, flaps open. */
    private static final BoxCostumeAnimatable ICON = new BoxCostumeAnimatable();

    private BoxCostumeClient() {
    }

    public static void initialize() {
        ClientTickEvents.END_WORLD_TICK.register(BoxCostumeClient::tick);
        HudRenderCallback.EVENT.register(BoxCostumeClient::renderView);
        BuiltinItemRendererRegistry.INSTANCE.register(ModItems.BOX_COSTUME, (stack, mode, matrices, vertexConsumers, light, overlay) -> {
            ICON.block = BoxCostumeItem.getBlock(stack);
            matrices.push();
            // Item space is the block [0, 1]: the box from the centre of its floor, minus the merchant's 4 px lift
            matrices.translate(0.5F, -0.25F, 0.5F);
            renderBox(matrices, ICON, vertexConsumers, light, MinecraftClient.getInstance().getRenderTickCounter().getTickDelta(false), 0.0F);
            matrices.pop();
        });
    }

    private static BoxCostumeRenderer renderer() {
        if (renderer == null) renderer = new BoxCostumeRenderer();
        return renderer;
    }

    private static void renderBox(MatrixStack matrices, BoxCostumeAnimatable box, VertexConsumerProvider vertexConsumers, int light,
                                  float tickDelta, float waistFit) {
        BoxCostumeRenderer boxRenderer = renderer();
        // Every box shares one model clock (the game time) so they never make the shared model's time jump
        var manager = box.getAnimatableInstanceCache().getManagerForId(boxRenderer.getInstanceId(box));
        if (manager.getFirstTickTime() == -1) manager.startedAt(0);
        boxRenderer.renderBox(matrices, box, vertexConsumers, light, tickDelta, waistFit);
    }

    /** @return the box of this wearer, or null if they don't wear the costume (as of the last client tick). */
    @Nullable
    public static BoxCostumeAnimatable boxOf(PlayerEntity player) {
        return BOXES.get(player);
    }

    /** Fully inside the box (closed on the ground): the wearer is not drawn, only the box. */
    public static boolean isDucked(@Nullable BoxCostumeAnimatable box) {
        return box != null && box.hidden && box.hiddenTicks >= DUCK_TICKS;
    }

    /**
     * Not drawn, only his box: the box (as last drawn) is on the ground, or he has been hidden long enough. Until
     * then he is drawn sinking into it (see {@link #bodySquash}): hiding and standing up are one continuous motion.
     */
    public static boolean isInsideBox(@Nullable BoxCostumeAnimatable box) {
        return box != null && (isDucked(box) || box.lift < INSIDE_LIFT);
    }

    /**
     * Vertical scale of the wearer's body for a box this far up (0 on the ground, 1 worn): he shrinks into the box
     * as it drops, head under its rim before the flaps fold, and grows back out of it as it rises.
     */
    public static float bodySquash(float lift) {
        return MathHelper.lerp(MathHelper.clamp(lift, 0.0F, 1.0F), SQUASHED, 1.0F);
    }

    private static void tick(ClientWorld world) {
        BOXES.keySet().removeIf(player -> player.getWorld() != world || player.isRemoved() || BoxCostumeItem.getWorn(player).isEmpty());
        for (AbstractClientPlayerEntity player : world.getPlayers()) {
            ItemStack costume = BoxCostumeItem.getWorn(player);
            if (costume.isEmpty()) continue;
            boolean hidden = BoxCostumeItem.isHiddenInBox(player);
            BoxCostumeAnimatable box = BOXES.get(player);
            if (box == null) {
                // Put on (or first seen): no closing / opening sound
                box = new BoxCostumeAnimatable();
                box.hidden = hidden;
                box.hiddenTicks = hidden ? DUCK_TICKS : 0;
                box.hiddenYaw = quarterYaw(player.bodyYaw);
                BOXES.put(player, box);
            }
            box.block = BoxCostumeItem.getBlock(costume);
            box.pos = player.getBlockPos();
            if (hidden != box.hidden) {
                box.hidden = hidden;
                box.hiddenTicks = 0;
                if (hidden) box.hiddenYaw = quarterYaw(player.bodyYaw);
                box.closeSoundTicks = hidden ? CLOSE_SOUND_TICKS : -1;
                box.placeSoundTicks = -1;
                box.openSoundTicks = hidden ? -1 : OPEN_SOUND_TICKS;
            } else if (hidden) {
                box.hiddenTicks++;
            }
            float limbSpeed = player.limbAnimator.getSpeed();
            boolean switching = box.walking ? limbSpeed < WALK_STOP : limbSpeed > WALK_START;
            box.walkSwitchTicks = switching ? box.walkSwitchTicks + 1 : 0;
            if (box.walkSwitchTicks >= (box.walking ? 5 : 2)) {
                box.walking = !box.walking;
                box.walkSwitchTicks = 0;
            }
            tickSounds(world, player, box);
        }
    }

    private static float quarterYaw(float yaw) {
        return Math.round(yaw / 90.0F) * 90.0F;
    }

    private static void tickSounds(ClientWorld world, PlayerEntity player, BoxCostumeAnimatable box) {
        if (box.closeSoundTicks >= 0 && box.closeSoundTicks-- == 0) {
            play(world, player, SoundEvents.ENTITY_PUFFER_FISH_BLOW_OUT, 0.5F, 1.5F);
            box.placeSoundTicks = PLACE_SOUND_DELAY;
        }
        if (box.placeSoundTicks >= 0 && box.placeSoundTicks-- == 0) {
            play(world, player, box.block.getSoundGroup().getPlaceSound(), 1.0F, 1.0F);
        }
        if (box.openSoundTicks >= 0 && box.openSoundTicks-- == 0) {
            play(world, player, SoundEvents.ENTITY_PUFFER_FISH_BLOW_UP, 0.5F, 1.5F);
        }
    }

    private static void play(ClientWorld world, PlayerEntity player, SoundEvent sound, float volume, float pitch) {
        world.playSound(player.getX(), player.getY(), player.getZ(), sound, SoundCategory.PLAYERS, volume, pitch, false);
    }

    /**
     * Draws a wearer's box, from LivingEntityRenderer#render (same origin: the entity position moved by the model
     * offset, undone here so the box stays on the ground). Worn, it follows the body's yaw; hidden, it is a block on
     * the ground, on the quarter turn nearest to the body's yaw when it closed, like the merchant's.
     */
    public static void renderWorn(PlayerEntityRenderState state, BoxCostumeAnimatable box, @Nullable Vec3d modelOffset, MatrixStack matrices,
                                  VertexConsumerProvider vertexConsumers, int light) {
        matrices.push();
        if (modelOffset != null) matrices.translate(-modelOffset.x, -modelOffset.y, -modelOffset.z);
        matrices.scale(state.baseScale, state.baseScale, state.baseScale);
        // On the ground the box is a block: it keeps the quarter turn it was closed on, however the wearer turns inside
        // (turning it with him made its top and bottom faces jump by quarter turns). It turns to / from the body's
        // yaw as it drops / rises.
        float yaw = box.lift >= 1.0F ? state.bodyYaw : MathHelper.lerpAngleDegrees(Math.max(box.lift, 0.0F), box.hiddenYaw, state.bodyYaw);
        matrices.multiply(RotationAxis.POSITIVE_Y.rotationDegrees(180.0F - yaw));
        renderBox(matrices, box, vertexConsumers, light, MinecraftClient.getInstance().getRenderTickCounter().getTickDelta(false), 1.0F);
        matrices.pop();
    }

    /** First person, hidden in the box: the view from inside, through a rectangular slot (nothing shows on the box itself). */
    private static void renderView(DrawContext context, RenderTickCounter tickCounter) {
        MinecraftClient client = MinecraftClient.getInstance();
        if (client.player == null || !client.options.getPerspective().isFirstPerson() || client.options.hudHidden) return;
        BoxCostumeAnimatable box = BOXES.get(client.player);
        if (!isDucked(box)) return;
        float fade = MathHelper.clamp((box.hiddenTicks - DUCK_TICKS + tickCounter.getTickDelta(false)) / VIEW_FADE_TICKS, 0.0F, 1.0F);
        // Lit by the box's block colour, a dim inside
        int mapColor = box.block.getMapColor(client.world, client.player.getBlockPos()).color;
        int tint = ColorHelper.lerp(0.5F, 0xFFFFFF, mapColor);
        int color = ColorHelper.withAlpha(MathHelper.floor(fade * 255.0F), tint);
        int width = context.getScaledWindowWidth(), height = context.getScaledWindowHeight();
        context.drawTexture(RenderLayer::getGuiTexturedOverlay, VIEW_TEXTURE, 0, 0, 0.0F, 0.0F, width, height, width, height, color);
    }
}
