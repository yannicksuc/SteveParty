package fr.lordfinn.steveparty.entities.custom;

import fr.lordfinn.steveparty.blocks.custom.DiceForgeBlockEntity;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.damage.DamageSource;
import net.minecraft.entity.data.DataTracker;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import org.jetbrains.annotations.Nullable;

/**
 * The hitbox of the dice forge core floating in the sky (the core itself is drawn by the forge): hitting it (hand,
 * arrow, explosion...) makes the core blow up, see {@link DiceForgeBlockEntity#explodeCore}. Invisible, it neither
 * moves, collides nor gets saved: the forge spawns it and keeps it on its core.
 */
public class ForgeCoreEntity extends Entity {
    @Nullable
    private BlockPos forgePos;

    public ForgeCoreEntity(EntityType<? extends ForgeCoreEntity> type, World world) {
        super(type, world);
        this.noClip = true;
        setNoGravity(true);
    }

    public void setForgePos(BlockPos forgePos) {
        this.forgePos = forgePos;
    }

    @Override
    public void tick() {
        super.tick();
        if (getWorld() instanceof ServerWorld world && (forgePos == null
                || !(world.getBlockEntity(forgePos) instanceof DiceForgeBlockEntity forge) || !forge.isActivated())) {
            discard();
        }
    }

    @Override
    public boolean damage(ServerWorld world, DamageSource source, float amount) {
        if (isRemoved()) return false;
        discard();
        if (forgePos != null && world.getBlockEntity(forgePos) instanceof DiceForgeBlockEntity forge) {
            forge.explodeCore(source.getAttacker());
        }
        return true;
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
    protected void initDataTracker(DataTracker.Builder builder) {
    }

    @Override
    protected void readCustomDataFromNbt(NbtCompound nbt) {
    }

    @Override
    protected void writeCustomDataToNbt(NbtCompound nbt) {
    }
}
