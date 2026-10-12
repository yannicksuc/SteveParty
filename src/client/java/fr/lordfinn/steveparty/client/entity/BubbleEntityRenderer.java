package fr.lordfinn.steveparty.client.entity;

import fr.lordfinn.steveparty.client.blockentity.BubbleFilm;
import fr.lordfinn.steveparty.entities.custom.bubble.BubbleEntity;
import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderContext;
import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderEvents;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.Camera;
import net.minecraft.client.render.OverlayTexture;
import net.minecraft.client.render.RenderLayer;
import net.minecraft.client.render.VertexConsumer;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.render.entity.EntityRenderer;
import net.minecraft.client.render.entity.EntityRendererFactory;
import net.minecraft.client.render.item.ItemRenderer;
import net.minecraft.client.render.model.json.ModelTransformationMode;
import net.minecraft.client.texture.SpriteAtlasTexture;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.item.ItemStack;
import net.minecraft.util.Identifier;
import net.minecraft.util.Util;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.RotationAxis;
import net.minecraft.util.math.Vec3d;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * A Bubbler's bubble: a cube of the Spawn Bubble's soap film (see {@link BubbleFilm}) wobbling and swaying, its riders
 * drawn by their own renderers inside it (they ride it), or its item turning slowly in its middle.
 * <p>
 * The film is drawn last of the world's see-through things ({@link #drawFilms}, after the translucent terrain, from the
 * farthest to the nearest, {@link BubbleLayers}), once every entity is drawn and flushed: whatever lies inside it (its
 * riders' skin, armour, cape and held items, an item, other passengers) is already in the depth, so its faces in front
 * of them tint them and its faces behind them stay hidden, whatever order the world (or a batching mod) draws the
 * entities in. A first-person player sees his own bubble around him, its film's inner faces (both sides of a face are
 * drawn), fainter ({@link #INSIDE_ALPHA}): he is at least a few tenths of a block from its walls, far beyond the near
 * plane, and his hand and held item are drawn after the world, over it.
 * <p>
 * About to burst by itself ({@link BubbleEntity#weakness}: its last seconds left still, or its Bubbled effect ending),
 * it weakens for all to see: its wobble quickens and shakes, its film thins and flickers.
 * <p>
 * Strained by puffs chained too fast ({@link BubbleEntity#strain}), it shows it like an over-blown balloon, for its
 * riders inside as for everyone around: it swells, its film thins and blushes toward a warning pink-red, its wobble
 * gets tighter and quicker; right after a hard puff it trembles. Calm again, it settles back as the strain eases.
 */
public class BubbleEntityRenderer extends EntityRenderer<BubbleEntity> {
    /** Its film's wobble: how much it squashes and stretches (share of its size), how fast (radians per tick). */
    private static final float WOBBLE = 0.04f, WOBBLE_SPEED = 0.15f;
    /** How far it sways off upright (degrees), and how fast (radians per tick, two slightly different ways). */
    private static final float SWAY = 3f, SWAY_SPEED_X = 0.05f, SWAY_SPEED_Z = 0.037f;
    /** How big its item shows (blocks), how fast it turns (degrees per tick). */
    private static final float ITEM_SCALE = 0.75f, ITEM_TURN = 2f;
    /** How strongly a first-person player sees his own bubble's film around him (alpha). */
    private static final float INSIDE_ALPHA = 0.6f;
    /** Weakening: how much faster it wobbles, how much wider, its shake (share of its size), how thin its film gets. */
    private static final float WEAK_SPEED = 3f, WEAK_WOBBLE = 1.5f, WEAK_SHAKE = 0.02f, WEAK_THIN = 0.5f;
    /**
     * Strain: how much it swells (share of its size), how much faster and tighter it wobbles, how thin its film gets,
     * how far its green and blue fade (toward pink-red), and how hard it trembles after a hard puff (share of its size).
     */
    private static final float STRAIN_SWELL = 0.07f, STRAIN_SPEED = 2f, STRAIN_TIGHT = 0.4f, STRAIN_THIN = 0.35f,
            STRAIN_GREEN = 0.55f, STRAIN_BLUE = 0.4f, TREMBLE = 0.035f;

    /** The films to draw this frame, once every entity is drawn. */
    private static final List<Film> FILMS = new ArrayList<>();

    /** A film to draw: its centre, side, place in its wobble, light, alpha, how weak, strained and trembling it is (0..1). */
    private record Film(Vec3d center, float side, int phase, int light, float alpha, float weakness, float strain, float tremble) {
    }

    private final ItemRenderer items;

    public BubbleEntityRenderer(EntityRendererFactory.Context context) {
        super(context);
        this.items = context.getItemRenderer();
        this.shadowRadius = 0;
    }

    /** Draws the films after the translucent terrain, every entity drawn. */
    public static void initialize() {
        WorldRenderEvents.AFTER_TRANSLUCENT.register(BubbleEntityRenderer::drawFilms);
    }

    @Override
    public void render(BubbleEntity bubble, float yaw, float tickDelta, MatrixStack matrices, VertexConsumerProvider consumers, int light) {
        float side = bubble.getSide();
        float time = clock();
        ItemStack item = bubble.getItem();
        if (!item.isEmpty()) {
            matrices.push();
            matrices.translate(0, side / 2 - ITEM_SCALE * 0.25f, 0);
            matrices.multiply(RotationAxis.POSITIVE_Y.rotationDegrees(time * ITEM_TURN + bubble.getId() * 37));
            matrices.scale(ITEM_SCALE, ITEM_SCALE, ITEM_SCALE);
            items.renderItem(item, ModelTransformationMode.GROUND, light, OverlayTexture.DEFAULT_UV, matrices, consumers, bubble.getWorld(),
                    bubble.getId());
            matrices.pop();
        }
        MinecraftClient client = MinecraftClient.getInstance();
        boolean inside = client.options.getPerspective().isFirstPerson() && client.getCameraEntity() != null
                && client.getCameraEntity().getVehicle() == bubble;
        Vec3d at = bubble.getLerpedPos(tickDelta);
        FILMS.add(new Film(new Vec3d(at.x, at.y + side / 2, at.z), side, bubble.getId() & 63, light, inside ? INSIDE_ALPHA : 1,
                bubble.weakness(), bubble.strain(), bubble.trembling()));
        super.render(bubble, yaw, tickDelta, matrices, consumers, light);
    }

    /**
     * The films of this frame, from the farthest to the nearest, wobbling and swaying like soap bubbles. The entity
     * buffers still pending are drawn first (a batching mod such as ImmediatelyFast holds the riders' bodies until the
     * end of the frame): drawn after the film, they would cover it.
     */
    private static void drawFilms(WorldRenderContext context) {
        if (FILMS.isEmpty()) return;
        MatrixStack matrices = context.matrixStack();
        if (matrices == null) {
            FILMS.clear();
            return;
        }
        Camera camera = context.camera();
        Vec3d eye = camera.getPos();
        FILMS.sort(Comparator.comparingDouble((Film film) -> film.center().squaredDistanceTo(eye)).reversed());
        VertexConsumerProvider.Immediate consumers = MinecraftClient.getInstance().getBufferBuilders().getEntityVertexConsumers();
        consumers.draw();
        RenderLayer layer = BubbleLayers.film();
        VertexConsumer buffer = consumers.getBuffer(layer);
        float time = clock();
        for (Film film : FILMS) {
            float phase = film.phase() * 0.1f, weak = film.weakness(), strain = film.strain();
            float wobbleSpeed = WOBBLE_SPEED * (1 + WEAK_SPEED * weak + STRAIN_SPEED * strain);
            float squash = WOBBLE * (1 + WEAK_WOBBLE * weak) * (1 - STRAIN_TIGHT * strain) * MathHelper.sin(time * wobbleSpeed + phase)
                    + WEAK_SHAKE * weak * MathHelper.sin(time * 2.3f + phase * 3)
                    + TREMBLE * film.tremble() * MathHelper.sin(time * 4.7f + phase);
            float alpha = film.alpha() * (1 - WEAK_THIN * weak) * (1 - STRAIN_THIN * strain)
                    * (1 - 0.25f * weak * (0.5f + 0.5f * MathHelper.sin(time * (1.5f + 2 * weak))));
            float swell = 1 + STRAIN_SWELL * strain;
            matrices.push();
            matrices.translate(film.center().x - eye.x, film.center().y - eye.y, film.center().z - eye.z);
            matrices.multiply(RotationAxis.POSITIVE_X.rotationDegrees(SWAY * MathHelper.sin(time * SWAY_SPEED_X + phase)));
            matrices.multiply(RotationAxis.POSITIVE_Z.rotationDegrees(SWAY * MathHelper.sin(time * SWAY_SPEED_Z + phase * 1.7f)));
            matrices.scale(swell * (1 - squash / 2), swell * (1 + squash), swell * (1 - squash / 2));
            BubbleFilm.Tier tier = BubbleFilm.tier(film.side());
            BubbleFilm.cube(matrices.peek(), buffer, tier.sprite().getSprite(), 0, 0, 0, film.side() / 2, tier.from(), tier.to(),
                    1, 1 - STRAIN_GREEN * strain, 1 - STRAIN_BLUE * strain, alpha, film.light());
            matrices.pop();
        }
        consumers.draw(layer);
        FILMS.clear();
    }

    /** The clock in ticks, wrapped each hour (precise as a float; see SpawnBubbleRenderer). */
    private static float clock() {
        return (Util.getMeasuringTimeMs() % 3_600_000L) / 50f;
    }

    @Override
    public Identifier getTexture(BubbleEntity bubble) {
        return SpriteAtlasTexture.BLOCK_ATLAS_TEXTURE;
    }
}
