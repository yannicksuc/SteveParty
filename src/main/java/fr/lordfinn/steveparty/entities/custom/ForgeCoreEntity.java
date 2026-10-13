package fr.lordfinn.steveparty.entities.custom;

import fr.lordfinn.steveparty.blocks.custom.DiceForgeBlockEntity;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.damage.DamageSource;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.registry.tag.DamageTypeTags;
import net.minecraft.entity.data.DataTracker;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import net.minecraft.world.explosion.Explosion;
import org.jetbrains.annotations.Nullable;

/**
 * The hitbox of the dice forge core floating in the sky (the core itself is drawn by the forge): a player hitting it
 * (hand, or an arrow or anything they threw) makes the core blow up, see {@link DiceForgeBlockEntity#explodeCore}.
 * Nothing else does: explosions (another core, TNT, a Nuke), fire, mobs and the rest leave it alone, so one blast can
 * never set off a chain of cores. Invisible, it neither
 * moves, collides nor gets saved: the forge spawns it and keeps it on its core, once risen out of reach of a
 * player hitting the forge itself ({@link DiceForgeBlockEntity#CORE_HIT_ALTITUDE}).
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
    public boolean damage(DamageSource source, float amount) {
        if (!(this.getWorld() instanceof ServerWorld world)) return false;
        if (isRemoved() || !isTrigger(source)) return false;
        discard();
        if (forgePos != null && world.getBlockEntity(forgePos) instanceof DiceForgeBlockEntity forge) {
            forge.explodeCore(source.getAttacker());
        }
        return true;
    }

    /** Only a player's own hit or shot sets it off: never an explosion (even one a player caused), nor anything else. */
    public static boolean isTrigger(DamageSource source) {
        return source.getAttacker() instanceof PlayerEntity player && !player.isSpectator()
                && !source.isIn(DamageTypeTags.IS_EXPLOSION) && !source.isIn(DamageTypeTags.IS_FIRE);
    }

    @Override
    public boolean isInvulnerableTo(DamageSource source) {
        return !isTrigger(source);
    }

    @Override
    public boolean isImmuneToExplosion(Explosion explosion) {
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
