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
 * ({@code TokenBaseDimensionsMixin}, in {@code Entity#calculateDimensions}) their height is increased by
 * {@link #BASE_HEIGHT}, after the scale attribute (squish) is applied: the base keeps the same size whatever the size
 * of the mob. The feet of the entity (its position) stay on the ground, at the bottom of the base; the mob model is
 * drawn {@link #BASE_HEIGHT} higher by the client renderers. The eye height, name tag and passenger attachment points
 * are raised by the same amount.
 * <p>
 * {@code Entity#getDimensions(EntityPose)} is left untouched: it still returns the dimensions of the mob body alone
 * (see {@link #getBodyHeight}), which is what the squish size computations need.
 */
public final class TokenBase {
    /** Height of the base, in blocks (3 pixels). */
    public static final float BASE_HEIGHT = 3.0F / 16.0F;

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
     * {@code dimensions} of a mob standing on a base: {@link #BASE_HEIGHT} higher, with its eyes, name tag and
     * passengers raised as much. The vehicle attachment point (bottom of the entity when it rides something) stays
     * at the bottom of the base.
     */
    public static EntityDimensions withBase(EntityDimensions dimensions) {
        EntityAttachments attachments = dimensions.attachments();
        EntityAttachments.Builder builder = EntityAttachments.builder();
        for (EntityAttachmentType type : ATTACHMENT_TYPES) {
            for (int i = 0; ; i++) {
                Vec3d point = attachments.getPointNullable(type, i, 0.0F);
                if (point == null) break;
                builder.add(type, type == EntityAttachmentType.VEHICLE ? point : point.add(0.0, BASE_HEIGHT, 0.0));
            }
        }
        float height = dimensions.height() + BASE_HEIGHT;
        return new EntityDimensions(dimensions.width(), height, dimensions.eyeHeight() + BASE_HEIGHT,
                builder.build(dimensions.width(), height), dimensions.fixed());
    }
}
