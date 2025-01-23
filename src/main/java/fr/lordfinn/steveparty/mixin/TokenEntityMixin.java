package fr.lordfinn.steveparty.mixin;

import fr.lordfinn.steveparty.blocks.custom.boardspaces.BoardSpaceBlockEntity;
import fr.lordfinn.steveparty.blocks.custom.boardspaces.Tile;
import fr.lordfinn.steveparty.entities.TokenizedEntityInterface;
import fr.lordfinn.steveparty.events.TileReachedEvent;
import fr.lordfinn.steveparty.utils.MessageUtils;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.MovementType;
import net.minecraft.entity.damage.DamageSource;
import net.minecraft.entity.data.DataTracker;
import net.minecraft.entity.data.TrackedData;
import net.minecraft.entity.data.TrackedDataHandlerRegistry;
import net.minecraft.entity.mob.MobEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.text.Text;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.World;
import org.joml.Vector3d;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.Optional;
import java.util.UUID;

import static fr.lordfinn.steveparty.events.TileUpdatedEvent.EVENT;

@Mixin(MobEntity.class)
public abstract class TokenEntityMixin extends LivingEntity implements TokenizedEntityInterface {

    @Unique
    private static final TrackedData<Boolean> TOKENIZED = DataTracker.registerData(TokenEntityMixin.class, TrackedDataHandlerRegistry.BOOLEAN);
    @Unique
    private static final TrackedData<Optional<UUID>> TOKEN_OWNER = DataTracker.registerData(TokenEntityMixin.class, TrackedDataHandlerRegistry.OPTIONAL_UUID);
    @Unique
    private static final TrackedData<Integer> NB_STEPS = DataTracker.registerData(TokenEntityMixin.class, TrackedDataHandlerRegistry.INTEGER);
    @Unique
    private static final TrackedData<Integer> TOKEN_STATUS = DataTracker.registerData(TokenEntityMixin.class, TrackedDataHandlerRegistry.INTEGER);
    @Unique
    private Vec3d targetPosition;
    @Unique
    private double targetPositionSpeed;

    public TokenEntityMixin(EntityType<LivingEntity> type, World world) {
        super(type, world);
    }

    @Shadow
    protected abstract void initDataTracker(DataTracker.Builder builder);

    @Inject(method = "initDataTracker", at = @At("TAIL"))
    private void addTokenizedField(DataTracker.Builder builder, CallbackInfo ci) {
        builder.add(TOKENIZED, false);
        builder.add(TOKEN_OWNER, Optional.empty());
        builder.add(NB_STEPS, 0);
        builder.add(TOKEN_STATUS, 0);
    }

    public boolean steveparty$isTokenized() {
        return this.dataTracker.get(TOKENIZED);
    }

    public void steveparty$setTokenOwner(PlayerEntity owner) {
        if (!this.steveparty$isTokenized() && owner != null) {
            steveparty$setTokenized(true);
        }
        if (owner != null) {
            this.dataTracker.set(TOKEN_OWNER, Optional.of(owner.getUuid()));
        }
    }

    public void steveparty$setTokenOwner(UUID owner) {
        if (!this.steveparty$isTokenized() && owner != null) {
            steveparty$setTokenized(true);
        }
        this.dataTracker.set(TOKEN_OWNER, Optional.ofNullable(owner));
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
        mob.setCustomNameVisible(tokenized);
        this.dataTracker.set(TOKENIZED, tokenized);
    }

    public int steveparty$getStatus() {
        return this.dataTracker.get(TOKEN_STATUS);
    }

    public void steveparty$setStatus(int status) {
        this.dataTracker.set(TOKEN_STATUS, status);
    }

    public UUID steveparty$getTokenOwner() {
        return this.dataTracker.get(TOKEN_OWNER).orElse(null);
    }

    @Inject(method = "readCustomDataFromNbt", at = @At("TAIL"))
    private void onReadCustomDataFromNbt(NbtCompound nbt, CallbackInfo ci) {
        if (nbt.contains("Tokenized", 99)) {
            this.steveparty$setTokenized(nbt.getBoolean("Tokenized"));
        }
        if (nbt.contains("NbSteps", 99)) {
            this.steveparty$setNbSteps(nbt.getInt("NbSteps"));
        }

        if (nbt.contains("TokenOwner", 11)) { // Use tag type 11 for UUID
            UUID tokenOwner = nbt.getUuid("TokenOwner");
            this.steveparty$setTokenOwner(tokenOwner);
        } else {
            this.steveparty$setTokenOwner((UUID) null); // Clear TOKEN_OWNER if missing
        }

        if (nbt.contains("TokenStatus", 99)) {
            this.steveparty$setStatus(nbt.getInt("TokenStatus"));
        }
    }

    @Inject(method = "writeCustomDataToNbt", at = @At("TAIL"))
    private void onWriteCustomDataToNbt(NbtCompound nbt, CallbackInfo ci) {
        nbt.putBoolean("Tokenized", this.steveparty$isTokenized());
        nbt.putInt("NbSteps", this.steveparty$getNbSteps());
        UUID tokenOwner = this.steveparty$getTokenOwner();
        if (tokenOwner != null) { // Safely check if it's not null
            nbt.putUuid("TokenOwner", tokenOwner);
        } else {
            nbt.remove("TokenOwner"); // Remove the key if the value is null
        }
        nbt.putInt("TokenStatus", this.steveparty$getStatus());
    }

    /**
     * Sets the target position for the entity to move towards.
     * @param target The target position.
     * @param speed The speed at which the entity should move towards the target.
     */
    public void steveparty$setTargetPosition(Vector3d target, double speed) {
        this.targetPosition = new Vec3d(target.x(), target.y(), target.z());
        this.targetPositionSpeed = speed;
        this.setVelocity(Vec3d.ZERO);
    }

    public void steveparty$setNbSteps(int step) {
        this.dataTracker.set(NB_STEPS, step);
    }

    public int steveparty$getNbSteps() {
        return this.dataTracker.get(NB_STEPS);
    }
    @Unique
    final
    double deceleration = 0.1;  // Horizontal deceleration
    @Unique
    final
    double gravity = 0.05;      // Gravity force (default value for Minecraft is around 0.08)
    @Unique
    final
    double threshold = 0.05;    // Threshold for stopping horizontal movement
    /**
     * Called every tick to handle manual movement toward the target.
     */
    @Inject(method = "tick", at = @At("TAIL"))
    private void onTick(CallbackInfo ci) {
        if (this.getWorld().isClient)  return;
        if (this.targetPosition != null) {
            Vec3d currentPosition = this.getPos();
            Vec3d direction = targetPosition.subtract(currentPosition).normalize();

            // Move the entity step by step toward the target.
            Vec3d newPosition = currentPosition.add(direction.multiply(targetPositionSpeed));
            this.setPosition(newPosition.x, newPosition.y, newPosition.z);

            // Check if the entity has reached the target (with a small tolerance).
            if (currentPosition.squaredDistanceTo(targetPosition) < targetPositionSpeed) {
                this.setPosition(targetPosition.x, targetPosition.y, targetPosition.z);
                this.setVelocity(Vec3d.ZERO);
                this.targetPosition = null;
                BlockEntity blockEntity = this.getWorld().getBlockEntity(this.getBlockPos());
                if ((blockEntity instanceof BoardSpaceBlockEntity)) {
                    if (this.steveparty$getNbSteps() > 0) {//TODO Manage negative Steps (Not urgent)
                        if (this.getWorld().getBlockState(this.getBlockPos()).getBlock() instanceof Tile tile) {
                            this.steveparty$setNbSteps(this.steveparty$getNbSteps() - 1);
                        }
                    }
                    TileReachedEvent.EVENT.invoker().onTileReached((MobEntity) (Object) this, (BoardSpaceBlockEntity) blockEntity);
                }
            }
        } else if (this.steveparty$isTokenized()) {
            // Retrieve current velocity
            Vec3d velocity = this.getVelocity();

            // Horizontal deceleration (friction)
            Vec3d horizontalVelocity = new Vec3d(velocity.x, 0, velocity.z); // Only consider horizontal components
            if (horizontalVelocity.length() > threshold) {
                Vec3d decelerationVec = horizontalVelocity.normalize().multiply(deceleration);
                horizontalVelocity = horizontalVelocity.subtract(decelerationVec);
            } else {
                horizontalVelocity = Vec3d.ZERO; // Stop horizontal movement if below the threshold
            }

            // Apply gravity (constant downward force)
            Vec3d verticalVelocity = new Vec3d(0, velocity.y - gravity, 0); // Gravity reduces vertical velocity

            // Combine the horizontal and vertical velocities
            Vec3d newVelocity = new Vec3d(horizontalVelocity.x, verticalVelocity.y, horizontalVelocity.z);

            // If the vertical velocity is small enough, stop falling (you can adjust the threshold if needed)
            if (Math.abs(newVelocity.y) < threshold && isOnGround()) {
                // When on the ground and almost stopped vertically, set the vertical velocity to 0
                this.setVelocity(new Vec3d(newVelocity.x, 0, newVelocity.z)); // Stop vertical movement
            } else {
                // Otherwise, continue with the new velocity
                this.setVelocity(newVelocity);
            }

            // Apply the movement with the new velocity
            this.move(MovementType.SELF, newVelocity);
        }
    }

    @Override
    public boolean damage(ServerWorld world, DamageSource source, float amount) {
        if (this.steveparty$isTokenized()) {
            if (source.getAttacker() instanceof PlayerEntity) {
                MessageUtils.sendToPlayer((ServerPlayerEntity) source.getAttacker(), Text.translatable("message.steveparty.steps_remaining_for", this.steveparty$getNbSteps(), this.getCustomName()), MessageUtils.MessageType.CHAT);
                BlockEntity blockEntity = world.getBlockEntity(this.getBlockPos());
                if (blockEntity instanceof BoardSpaceBlockEntity)
                    EVENT.invoker().onTileUpdated((MobEntity) (Object) this, (BoardSpaceBlockEntity) blockEntity);
            }
            return false;
        }
        return super.damage(world, source, amount);
    }
}
