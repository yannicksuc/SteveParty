package fr.lordfinn.steveparty.entities.custom.trichaudron;

import fr.lordfinn.steveparty.entities.ModEntities;
import net.minecraft.util.math.Vec3d;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

/**
 * A Trichaudron's head and neck hit boxes ({@link TrichaudronPartEntity}), server side: one on each head it shows and
 * one on the middle of its neck, spawned when missing (and only while it is alive), moved onto them every tick (their
 * aim, as the server knows it: TrichaudronEntity#nozzle, #neckMiddle), removed for a head it no longer shows and all
 * with it. Wild, tamed or a board actor alike: in a fight its heads and necks can be hit, on a board its heads are what
 * the player clicks to pick a prize.
 */
public final class TrichaudronParts {
    /** A head's hit box (blocks, a cube) round its middle (TrichaudronEntity#headCenter): the skull, its sway included. */
    public static final float HEAD_SIZE = 1.6f;
    /** A neck's hit box, round its middle. */
    public static final float NECK_SIZE = 1.2f;
    /** A blow on a head hurts this many times as much as on the body (a neck: as much). */
    public static final float HEAD_DAMAGE = 1.5f;

    private final TrichaudronEntity owner;
    /** Per head ({@link TrichaudronEntity#ALL_HEADS}): its head's and its neck's boxes, null while none. */
    private final TrichaudronPartEntity[] heads = new TrichaudronPartEntity[TrichaudronEntity.MAX_HEADS];
    private final TrichaudronPartEntity[] necks = new TrichaudronPartEntity[TrichaudronEntity.MAX_HEADS];

    TrichaudronParts(TrichaudronEntity owner) {
        this.owner = owner;
    }

    /** Spawns the missing boxes, removes those of hidden heads, moves the others onto their head and neck. */
    void tick() {
        if (owner.getWorld().isClient) return;
        boolean alive = owner.isAlive() && !owner.isRemoved();
        for (int head = 0; head < TrichaudronEntity.MAX_HEADS; head++) {
            boolean shown = alive && owner.showsHead(head);
            heads[head] = keep(heads[head], shown, head, false);
            necks[head] = keep(necks[head], shown, head, true);
            if (!shown) continue;
            place(heads[head], owner.headCenter(head));
            place(necks[head], owner.neckMiddle(head));
        }
    }

    /** {@code part} kept if {@code wanted} (a new one spawned if it is gone), else removed: what to hold now. */
    private @Nullable TrichaudronPartEntity keep(@Nullable TrichaudronPartEntity part, boolean wanted, int head, boolean neck) {
        if (part != null && (part.isRemoved() || !wanted)) {
            part.discard();
            part = null;
        }
        if (part != null || !wanted) return part;
        TrichaudronPartEntity spawned = TrichaudronPartEntity.of(ModEntities.TRICHAUDRON_PART, owner, head, neck);
        place(spawned, neck ? owner.neckMiddle(head) : owner.headCenter(head));
        return owner.getWorld().spawnEntity(spawned) ? spawned : null;
    }

    /** Centres the box on {@code centre}. */
    private static void place(TrichaudronPartEntity part, Vec3d centre) {
        if (part == null) return;
        part.refreshPositionAndAngles(centre.x, centre.y - part.getHeight() / 2, centre.z, 0, 0);
    }

    /** Every box now (tests). */
    public List<TrichaudronPartEntity> all() {
        List<TrichaudronPartEntity> all = new ArrayList<>();
        for (TrichaudronPartEntity part : heads) if (part != null && !part.isRemoved()) all.add(part);
        for (TrichaudronPartEntity part : necks) if (part != null && !part.isRemoved()) all.add(part);
        return all;
    }

    /** {@code head}'s box (its neck's if {@code neck}), null if none. */
    public @Nullable TrichaudronPartEntity of(int head, boolean neck) {
        TrichaudronPartEntity part = neck ? necks[head] : heads[head];
        return part == null || part.isRemoved() ? null : part;
    }

    /** All removed (its Trichaudron is gone). */
    void discard() {
        for (int head = 0; head < TrichaudronEntity.MAX_HEADS; head++) {
            if (heads[head] != null) heads[head].discard();
            if (necks[head] != null) necks[head].discard();
            heads[head] = necks[head] = null;
        }
    }
}
