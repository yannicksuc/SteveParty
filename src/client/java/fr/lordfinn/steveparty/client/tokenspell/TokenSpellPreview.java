package fr.lordfinn.steveparty.client.tokenspell;

import net.minecraft.client.render.entity.state.LivingEntityRenderState;
import net.minecraft.entity.EntityDimensions;
import net.minecraft.entity.EntityPose;
import net.minecraft.entity.LivingEntity;

/**
 * Live preview of the token spell: while the size slider is moved, the targeted mob is rendered at the chosen size.
 * Client-side render scale override only (the hitbox and the server are untouched). Render thread only.
 */
public final class TokenSpellPreview {
    private static final int NONE = Integer.MIN_VALUE;
    private static int entityId = NONE;
    private static float size;

    private TokenSpellPreview() {
    }

    /** Renders the entity {@code id} with its biggest dimension equal to {@code sizeInBlocks}. */
    public static void set(int id, float sizeInBlocks) {
        entityId = id;
        size = sizeInBlocks;
    }

    public static void clear() {
        entityId = NONE;
    }

    /** Applies the preview size, if {@code entity} is being previewed, to its render state. */
    public static void apply(LivingEntity entity, LivingEntityRenderState state) {
        if (entityId == NONE || entity.getId() != entityId) return;
        // Body dimensions (without a token base), already scaled like state.baseScale
        EntityDimensions body = entity.getDimensions(EntityPose.STANDING);
        float current = Math.max(body.width(), body.height());
        if (current > 0 && size > 0) {
            state.baseScale *= size / current;
        }
    }
}
