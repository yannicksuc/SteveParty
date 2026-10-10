package fr.lordfinn.steveparty.entities.custom.trichaudron;

import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityDimensions;
import net.minecraft.entity.EntityPose;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.damage.DamageSource;
import net.minecraft.entity.data.DataTracker;
import net.minecraft.entity.data.TrackedData;
import net.minecraft.entity.data.TrackedDataHandlerRegistry;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.util.ActionResult;
import net.minecraft.util.Hand;
import net.minecraft.world.World;
import org.jetbrains.annotations.Nullable;

/**
 * One hit box of a Trichaudron's heads, like the Ender Dragon's parts but a plain entity of its own (so the vanilla
 * crosshair, attacks, arrows and clicks reach it with no hook): a head ({@link TrichaudronParts#HEAD_SIZE}) or the
 * middle of its neck ({@link TrichaudronParts#NECK_SIZE}). Its Trichaudron spawns it, moves it every tick onto the
 * head as it aims, and removes it with itself (TrichaudronParts). A blow on it goes to the Trichaudron's body (a head's
 * a little harder: TrichaudronEntity#damagePart); a click on it, to the Trichaudron as a click on itself. On a board
 * actor, either picks that head (TrichaudronEntity#pickHead). Invisible, never saved, it neither collides nor moves by
 * itself.
 */
public class TrichaudronPartEntity extends Entity {
    /** Which head ({@link TrichaudronEntity#ALL_HEADS}), and whether this is its neck: synced, for its size. */
    private static final TrackedData<Byte> HEAD = DataTracker.registerData(TrichaudronPartEntity.class, TrackedDataHandlerRegistry.BYTE);
    private static final TrackedData<Boolean> NECK = DataTracker.registerData(TrichaudronPartEntity.class, TrackedDataHandlerRegistry.BOOLEAN);

    /** Server: its Trichaudron. */
    private @Nullable TrichaudronEntity owner;

    public TrichaudronPartEntity(EntityType<? extends TrichaudronPartEntity> type, World world) {
        super(type, world);
        this.noClip = true;
        setNoGravity(true);
    }

    /** Server: a hit box of {@code owner}'s {@code head}, its head or its neck. */
    public static TrichaudronPartEntity of(EntityType<? extends TrichaudronPartEntity> type, TrichaudronEntity owner, int head, boolean neck) {
        TrichaudronPartEntity part = new TrichaudronPartEntity(type, owner.getWorld());
        part.owner = owner;
        part.dataTracker.set(HEAD, (byte) head);
        part.dataTracker.set(NECK, neck);
        part.calculateDimensions();
        return part;
    }

    public @Nullable TrichaudronEntity getOwner() {
        return owner;
    }

    public int getHead() {
        return dataTracker.get(HEAD);
    }

    public boolean isNeck() {
        return dataTracker.get(NECK);
    }

    @Override
    protected void initDataTracker(DataTracker.Builder builder) {
        builder.add(HEAD, (byte) 0);
        builder.add(NECK, false);
    }

    @Override
    public void onTrackedDataSet(TrackedData<?> data) {
        super.onTrackedDataSet(data);
        if (NECK.equals(data)) calculateDimensions();
    }

    @Override
    public EntityDimensions getDimensions(EntityPose pose) {
        float size = isNeck() ? TrichaudronParts.NECK_SIZE : TrichaudronParts.HEAD_SIZE;
        return EntityDimensions.fixed(size, size);
    }

    /** Server: gone with its Trichaudron (or when it no longer shows this head: TrichaudronParts). */
    @Override
    public void tick() {
        super.tick();
        if (!getWorld().isClient && (owner == null || owner.isRemoved() || !owner.isAlive())) discard();
    }

    /** A blow: to its Trichaudron (TrichaudronEntity#damagePart). */
    @Override
    public boolean damage(DamageSource source, float amount) {
        if (getWorld().isClient || owner == null || isInvulnerableTo(source)) return false;
        return owner.damagePart(this, source, amount);
    }

    /** A click: a board actor's head is picked; a wild or tamed one is clicked as its body is (its bucket, its seat). */
    @Override
    public ActionResult interact(PlayerEntity player, Hand hand) {
        if (getWorld().isClient) return ActionResult.SUCCESS;
        if (owner == null) return ActionResult.PASS;
        if (owner.isBoardActor()) return owner.pickHead(player, getHead()) ? ActionResult.SUCCESS : ActionResult.PASS;
        return owner.interact(player, hand);
    }

    /** One of its Trichaudron's: never in the way of its own body's searches. */
    @Override
    public boolean isPartOf(Entity entity) {
        return this == entity || owner == entity;
    }

    @Override
    public boolean canHit() {
        return !isRemoved();
    }

    @Override
    public boolean isCollidable() {
        return false;
    }

    @Override
    public boolean isPushable() {
        return false;
    }

    @Override
    public boolean shouldSave() {
        return false;
    }

    @Override
    protected void readCustomDataFromNbt(NbtCompound nbt) {
    }

    @Override
    protected void writeCustomDataToNbt(NbtCompound nbt) {
    }
}
