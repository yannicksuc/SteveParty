package fr.lordfinn.steveparty.mixin;

import fr.lordfinn.steveparty.Steveparty;
import fr.lordfinn.steveparty.TokenizedEntityInterface;
import fr.lordfinn.steveparty.blocks.tiles.TileEntity;
import fr.lordfinn.steveparty.events.TileReachedEvent;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.data.DataTracker;
import net.minecraft.entity.data.TrackedData;
import net.minecraft.entity.data.TrackedDataHandlerRegistry;
import net.minecraft.entity.mob.MobEntity;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.World;
import org.joml.Vector3d;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(MobEntity.class)
public abstract class TokenEntityMixin extends Entity implements TokenizedEntityInterface {

    @Unique
    private static final TrackedData<Boolean> TOKENIZED = DataTracker.registerData(TokenEntityMixin.class, TrackedDataHandlerRegistry.BOOLEAN);
    @Unique
    private static final TrackedData<Integer> NB_STEPS = DataTracker.registerData(TokenEntityMixin.class, TrackedDataHandlerRegistry.INTEGER);
    @Unique
    private Vec3d targetPosition;
    @Unique
    private double targetPositionSpeed;

    public TokenEntityMixin(EntityType<?> type, World world) {
        super(type, world);
    }

    @Shadow
    protected abstract void initDataTracker(DataTracker.Builder builder);

    @Inject(method = "initDataTracker", at = @At("TAIL"))
    private void addTokenizedField(DataTracker.Builder builder, CallbackInfo ci) {
        builder.add(TOKENIZED, false);
        builder.add(NB_STEPS, 0);
    }

    public boolean steveparty$isTokenized() {
        return this.dataTracker.get(TOKENIZED);
    }

    public void steveparty$setTokenized(boolean tokenized) {
        MobEntity mob = (MobEntity) (Object) this;
        mob.setAiDisabled(tokenized);
        mob.clearGoalsAndTasks();
        mob.setInvulnerable(tokenized);
        if (tokenized) {
            mob.clearGoalsAndTasks();
            mob.setTarget(null);
        }
        this.dataTracker.set(TOKENIZED, tokenized);
    }

    @Inject(method = "readCustomDataFromNbt", at = @At("TAIL"))
    private void onReadCustomDataFromNbt(NbtCompound nbt, CallbackInfo ci) {
        if (nbt.contains("Tokenized", 99)) {
            this.steveparty$setTokenized(nbt.getBoolean("Tokenized"));
        }
        if (nbt.contains("NbSteps", 99)) {
            this.steveparty$setNbSteps(nbt.getInt("NbSteps"));
        }
    }

    @Inject(method = "writeCustomDataToNbt", at = @At("TAIL"))
    private void onWriteCustomDataToNbt(NbtCompound nbt, CallbackInfo ci) {
        nbt.putBoolean("Tokenized", this.steveparty$isTokenized());
        nbt.putInt("NbSteps", this.steveparty$getNbSteps());
    }

    /**
     * Sets the target position for the entity to move towards.
     * @param target The target position.
     * @param speed The speed at which the entity should move towards the target.
     */
    public void steveparty$setTargetPosition(Vector3d target, double speed) {
        this.targetPosition = new Vec3d(target.x(), target.y(), target.z());
        this.targetPositionSpeed = speed;
    }

    public void steveparty$setNbSteps(int step) {
        this.dataTracker.set(NB_STEPS, step);
    }

    public int steveparty$getNbSteps() {
        return this.dataTracker.get(NB_STEPS);
    }

    /**
     * Called every tick to handle manual movement toward the target.
     */
    @Inject(method = "tick", at = @At("TAIL"))
    private void onTick(CallbackInfo ci) {
        if (this.targetPosition != null && !this.getWorld().isClient) {
            Vec3d currentPosition = this.getPos();
            Vec3d direction = targetPosition.subtract(currentPosition).normalize();

            // Move the entity step by step toward the target.
            Vec3d newPosition = currentPosition.add(direction.multiply(targetPositionSpeed));
            this.setPosition(newPosition.x, newPosition.y, newPosition.z);

            // Check if the entity has reached the target (with a small tolerance).
            if (currentPosition.squaredDistanceTo(targetPosition) < targetPositionSpeed) {
                this.setPosition(targetPosition.x, targetPosition.y, targetPosition.z);
                BlockEntity blockEntity = this.getWorld().getBlockEntity(this.getBlockPos().down());
                this.targetPosition = null;
                TileReachedEvent.EVENT.invoker().onTileReached((MobEntity) (Object) this,
                        (blockEntity instanceof TileEntity) ? (TileEntity) blockEntity : null);
            }
        }
    }
}
