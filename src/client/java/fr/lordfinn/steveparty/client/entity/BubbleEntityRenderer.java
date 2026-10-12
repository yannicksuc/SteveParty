package fr.lordfinn.steveparty.client.entity;

import fr.lordfinn.steveparty.client.blockentity.BubbleFilm;
import fr.lordfinn.steveparty.entities.custom.bubble.BubbleEntity;
import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderContext;
import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderEvents;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.Camera;
import net.minecraft.client.render.OverlayTexture;
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
 * The film is drawn once every entity is ({@link #drawFilms}, after the entities, from the farthest to the nearest), in
 * a layer that never writes depth ({@link BubbleLayers}): its riders, whatever order the world draws them in, show
 * through it, tinted by it. A first-person player doesn't see his own bubble from inside: an iridescent vignette at
 * his screen's edges instead (BubbleRiderClient).
 */
public class BubbleEntityRenderer extends EntityRenderer<BubbleEntity> {
    /** Its film's wobble: how much it squashes and stretches (share of its size), how fast (radians per tick). */
    private static final float WOBBLE = 0.04f, WOBBLE_SPEED = 0.15f;
    /** How far it sways off upright (degrees), and how fast (radians per tick, two slightly different ways). */
    private static final float SWAY = 3f, SWAY_SPEED_X = 0.05f, SWAY_SPEED_Z = 0.037f;
    /** How big its item shows (blocks), how fast it turns (degrees per tick). */
    private static final float ITEM_SCALE = 0.75f, ITEM_TURN = 2f;

    /** The films to draw this frame, once every entity is drawn. */
    private static final List<Film> FILMS = new ArrayList<>();

    private record Film(Vec3d center, float side, int phase, int light) {
    }

    private final ItemRenderer items;

    public BubbleEntityRenderer(EntityRendererFactory.Context context) {
        super(context);
        this.items = context.getItemRenderer();
        this.shadowRadius = 0;
    }

    /** Draws the films once every entity is drawn. */
    public static void initialize() {
        WorldRenderEvents.AFTER_ENTITIES.register(BubbleEntityRenderer::drawFilms);
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
        if (!inside) {
            Vec3d at = bubble.getLerpedPos(tickDelta);
            FILMS.add(new Film(new Vec3d(at.x, at.y + side / 2, at.z), side, bubble.getId() & 63, light));
        }
        super.render(bubble, yaw, tickDelta, matrices, consumers, light);
    }

    /** The films of this frame, from the farthest to the nearest, wobbling and swaying like soap bubbles. */
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
        VertexConsumer buffer = consumers.getBuffer(BubbleLayers.FILM);
        float time = clock();
        for (Film film : FILMS) {
            float phase = film.phase() * 0.1f;
            float squash = WOBBLE * MathHelper.sin(time * WOBBLE_SPEED + phase);
            matrices.push();
            matrices.translate(film.center().x - eye.x, film.center().y - eye.y, film.center().z - eye.z);
            matrices.multiply(RotationAxis.POSITIVE_X.rotationDegrees(SWAY * MathHelper.sin(time * SWAY_SPEED_X + phase)));
            matrices.multiply(RotationAxis.POSITIVE_Z.rotationDegrees(SWAY * MathHelper.sin(time * SWAY_SPEED_Z + phase * 1.7f)));
            matrices.scale(1 - squash / 2, 1 + squash, 1 - squash / 2);
            BubbleFilm.Tier tier = BubbleFilm.tier(film.side());
            BubbleFilm.cube(matrices.peek(), buffer, tier.sprite().getSprite(), 0, 0, 0, film.side() / 2, tier.from(), tier.to(),
                    1, 1, 1, 1, film.light());
            matrices.pop();
        }
        consumers.draw(BubbleLayers.FILM);
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
