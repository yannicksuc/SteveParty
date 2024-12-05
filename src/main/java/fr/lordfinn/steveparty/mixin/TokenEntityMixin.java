package fr.lordfinn.steveparty.mixin;

import fr.lordfinn.steveparty.TokenizedEntityInterface;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.data.DataTracker;
import net.minecraft.entity.data.TrackedData;
import net.minecraft.entity.data.TrackedDataHandlerRegistry;
import net.minecraft.entity.mob.MobEntity;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.world.World;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(MobEntity.class)
public abstract class TokenEntityMixin extends Entity implements TokenizedEntityInterface {

    // Define a new TrackedData field for the "tokenized" state
    @Unique
    private static final TrackedData<Boolean> TOKENIZED = DataTracker.registerData(TokenEntityMixin.class, TrackedDataHandlerRegistry.BOOLEAN);

    public TokenEntityMixin(EntityType<?> type, World world) {
        super(type, world);
    }

    // Add this field to the DataTracker during initialization
    @Shadow
    protected abstract void initDataTracker(DataTracker.Builder builder);

    // Modify the `initDataTracker` method to include the new data field
    @Inject(method = "initDataTracker", at = @At("TAIL"))
    private void addTokenizedField(DataTracker.Builder builder, CallbackInfo ci) {
        builder.add(TOKENIZED, false);  // Initialize with a default value (false)
    }

    // Utility method to check if the entity is tokenized
    public boolean steveparty$isTokenized() {
        return this.dataTracker.get(TOKENIZED);
    }

    // Utility method to set the "tokenized" state
    public void steveparty$setTokenized(boolean tokenized) {
        this.dataTracker.set(TOKENIZED, tokenized);
    }

    @Inject(method = "readCustomDataFromNbt", at = @At("TAIL"))
    private void onReadCustomDataFromNbt(NbtCompound nbt, CallbackInfo ci) {
        if (nbt.contains("Tokenized", 99)) {
            this.steveparty$setTokenized(nbt.getBoolean("Tokenized"));
        }
    }

    @Inject(method = "writeCustomDataToNbt", at = @At("TAIL"))
    private void onWriteCustomDataToNbt(NbtCompound nbt, CallbackInfo ci) {
        nbt.putBoolean("Tokenized", this.steveparty$isTokenized());
    }

}

