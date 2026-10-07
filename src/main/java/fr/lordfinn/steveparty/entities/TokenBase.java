package fr.lordfinn.steveparty.entities;

import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityAttachmentType;
import net.minecraft.entity.EntityAttachments;
import net.minecraft.entity.EntityDimensions;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;

import static fr.lordfinn.steveparty.items.custom.TokenizerWandItem.DEFAULT_TOKEN_SIZE;
import static fr.lordfinn.steveparty.items.custom.TokenizerWandItem.MAX_TOKEN_SIZE;
import static fr.lordfinn.steveparty.items.custom.TokenizerWandItem.MIN_TOKEN_SIZE;

/**
 * Coloured pawn base ("socle") every token stands on.
 * <p>
 * The base is part of the token's physical hitbox: when the dimensions of a tokenized mob are computed
 * ({@code TokenBaseDimensionsMixin}, in {@code Entity#calculateDimensions}) their height is increased by the base
 * height ({@link #baseHeight}), after the scale attribute (squish) is applied. The base follows the size of the pawn:
 * {@link #BASE_HEIGHT} for a pawn of the default size, thinner under a small pawn, thicker under a big one. The feet
 * of the entity (its position) stay on the ground, at the bottom of the base; the mob model is drawn as much higher
 * by the client renderers. The eye height, name tag and passenger attachment points are raised by the same amount.
 * <p>
 * {@code Entity#getDimensions(EntityPose)} is left untouched: it still returns the dimensions of the mob body alone
 * (see {@link #getBodyHeight}), which is what the squish size computations need.
 */
public final class TokenBase {
    /** Height of the base under a pawn of the default size, in blocks (3 pixels). */
    public static final float BASE_HEIGHT = 3.0F / 16.0F;
    /** The base never gets thinner than one pixel. */
    public static final float MIN_BASE_HEIGHT = 1.0F / 16.0F;

    private static final EntityAttachmentType[] ATTACHMENT_TYPES = EntityAttachmentType.values();

    private TokenBase() {
    }

    /** @return true if {@code entity} is a token (and so stands on a base). */
    public static boolean isToken(Entity entity) {
        return entity instanceof TokenizedEntityInterface token && token.steveparty$isTokenized();
    }

    /** Height of the mob itself, without the base (for a regular entity: its height). */
    public static float getBodyHeight(Entity entity) {
        return entity.getDimensions(entity.getPose()).height();
    }

    /**
     * How big the base is compared to the base of a pawn of the default size: the pawn's size (the biggest dimension
     * of its body, like the size chosen with the wand) over the default size, within the wand's size bounds.
     */
    public static float sizeFactor(EntityDimensions body) {
        float size = Math.max(body.width(), body.height());
        if (!(size > 0)) return 1.0F;
        return MathHelper.clamp(size, MIN_TOKEN_SIZE, MAX_TOKEN_SIZE) / DEFAULT_TOKEN_SIZE;
    }

    /** Height of the base under a body of these dimensions, in blocks. */
    public static float baseHeight(EntityDimensions body) {
        return Math.max(MIN_BASE_HEIGHT, BASE_HEIGHT * sizeFactor(body));
    }

    /** Height of the base {@code entity} stands on (whether it is a token or not). */
    public static float baseHeight(Entity entity) {
        return baseHeight(entity.getDimensions(entity.getPose()));
    }

    /**
     * {@code dimensions} of a mob standing on a base: {@link #baseHeight} higher, with its eyes, name tag and
     * passengers raised as much. The vehicle attachment point (bottom of the entity when it rides something) stays
     * at the bottom of the base.
     */
    public static EntityDimensions withBase(EntityDimensions dimensions) {
        float baseHeight = baseHeight(dimensions);
        EntityAttachments attachments = dimensions.attachments();
        EntityAttachments.Builder builder = EntityAttachments.builder();
        for (EntityAttachmentType type : ATTACHMENT_TYPES) {
            for (int i = 0; ; i++) {
                Vec3d point = attachments.getPointNullable(type, i, 0.0F);
                if (point == null) break;
                builder.add(type, type == EntityAttachmentType.VEHICLE ? point : point.add(0.0, baseHeight, 0.0));
            }
        }
        float height = dimensions.height() + baseHeight;
        return new EntityDimensions(dimensions.width(), height, dimensions.eyeHeight() + baseHeight,
                builder.build(dimensions.width(), height), dimensions.fixed());
    }
}
