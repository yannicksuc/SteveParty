package fr.lordfinn.steveparty.mixin;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import fr.lordfinn.steveparty.entities.TokenBase;
import fr.lordfinn.steveparty.entities.TokenizedEntityInterface;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityDimensions;
import net.minecraft.entity.data.DataTracker;
import net.minecraft.entity.data.TrackedData;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Hitbox of the tokens: body + coloured base (see {@link TokenBase}).
 * <p>
 * Only the dimensions stored by {@code calculateDimensions} (hitbox, {@code getHeight()}, eye height, attachment
 * points) get the base: {@code getDimensions(EntityPose)} keeps returning the body alone. The dimensions are
 * recomputed as soon as the tokenized flag changes, on both sides (the flag is tracked data: this covers
 * tokenization, untokenization, NBT loading and the client sync).
 */
@Mixin(Entity.class)
public abstract class TokenBaseDimensionsMixin {
    @Shadow
    @Final
    protected DataTracker dataTracker;

    /** Whether the current dimensions include the base. */
    @Unique
    private boolean steveparty$hasTokenBase;

    @Shadow
    public abstract void calculateDimensions();

    @ModifyExpressionValue(method = "calculateDimensions", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/entity/Entity;getDimensions(Lnet/minecraft/entity/EntityPose;)Lnet/minecraft/entity/EntityDimensions;"))
    private EntityDimensions steveparty$addTokenBase(EntityDimensions dimensions) {
        boolean token = this.steveparty$isToken();
        this.steveparty$hasTokenBase = token;
        return token ? TokenBase.withBase(dimensions) : dimensions;
    }

    @Inject(method = "onTrackedDataSet", at = @At("TAIL"))
    private void steveparty$refreshTokenBase(TrackedData<?> data, CallbackInfo ci) {
        if (this.steveparty$isToken() != this.steveparty$hasTokenBase) {
            this.calculateDimensions();
        }
    }

    @Unique
    private boolean steveparty$isToken() {
        // The data tracker does not exist yet while the entity is being constructed
        return this.dataTracker != null && (Object) this instanceof TokenizedEntityInterface token
                && token.steveparty$isTokenized();
    }
}
