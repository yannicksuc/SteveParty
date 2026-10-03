package fr.lordfinn.steveparty.mixin;

import fr.lordfinn.steveparty.blocks.custom.PartyController.PartyControllerEntity;
import fr.lordfinn.steveparty.entities.TokenizedEntityInterface;
import net.minecraft.entity.Entity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.text.Text;
import org.jetbrains.annotations.Nullable;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * A token renamed during a party (a name tag, /data, any change of its custom name): its party shows the new name
 * right away (see {@link PartyControllerEntity#onTokenRenamed}).
 */
@Mixin(Entity.class)
public abstract class EntityTokenRenameMixin {
    @Inject(method = "setCustomName", at = @At("TAIL"))
    private void steveparty$tokenRenamed(@Nullable Text name, CallbackInfo ci) {
        Entity self = (Entity) (Object) this;
        if (self.getWorld() instanceof ServerWorld world && self instanceof TokenizedEntityInterface token && token.steveparty$isTokenized())
            PartyControllerEntity.onTokenRenamed(world, self.getUuid(), name);
    }
}
