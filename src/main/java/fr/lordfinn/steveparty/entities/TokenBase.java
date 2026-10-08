package fr.lordfinn.steveparty.entities;

import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityAttachmentType;
import net.minecraft.entity.EntityAttachments;
import net.minecraft.entity.EntityDimensions;
import net.minecraft.util.math.Vec3d;

/**
 * Coloured pawn base ("socle") every token stands on.
 * <p>
 * The base is part of the token's physical hitbox: when the dimensions of a tokenized mob are computed
 * ({@code TokenBaseDimensionsMixin}, in {@code Entity#calculateDimensions}) their height is increased by the base
 * height ({@link #baseHeight}), after the scale attribute (squish) is applied. The base follows the width of the
 * pawn's hitbox, always in the same proportions: the smallest octagon around the hitbox square ({@link #baseApothem}),
 * {@link #HEIGHT_PER_APOTHEM} as high as it is wide from its centre (the texture's pixels stay square). The feet
 * of the entity (its position) stay on the ground, at the bottom of the base; the mob model is drawn as much higher
 * by the client renderers. The eye height, name tag and passenger attachment points are raised by the same amount.
 * <p>
 * {@code Entity#getDimensions(EntityPose)} is left untouched: it still returns the dimensions of the mob body alone
 * (see {@link #getBodyHeight}), which is what the squish size computations need.
 */
public final class TokenBase {
    /**
     * Centre to flat side of the octagon, per block of hitbox width: the smallest octagon (flat sides facing the axes
     * and the diagonals) holding the hitbox square, corners included (they touch its diagonal sides).
     */
    public static final float APOTHEM_PER_WIDTH = (float) (1.0 / Math.sqrt(2.0));
    /**
     * Height of the base per block of apothem: the texture's top (16 pixels from flat side to flat side) and its side
     * strip (3 pixels high) keep square pixels at any size.
     */
    public static final float HEIGHT_PER_APOTHEM = 3.0F / 8.0F;

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

    /** Centre to flat side of the base under a hitbox {@code width} blocks wide, in blocks. */
    public static float baseApothem(float width) {
        return Float.isFinite(width) && width > 0 ? width * APOTHEM_PER_WIDTH : 0.0F;
    }

    /** Height of the base under a body of these dimensions, in blocks. */
    public static float baseHeight(EntityDimensions body) {
        return baseApothem(body.width()) * HEIGHT_PER_APOTHEM;
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
