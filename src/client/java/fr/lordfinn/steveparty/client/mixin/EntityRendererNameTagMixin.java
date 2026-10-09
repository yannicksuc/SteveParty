package fr.lordfinn.steveparty.client.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import fr.lordfinn.steveparty.client.access.BoxCostumeRenderState;
import fr.lordfinn.steveparty.client.entity.costume.BoxCostumeAnimatable;
import fr.lordfinn.steveparty.client.pipe.PipeTravellerPose;
import fr.lordfinn.steveparty.items.custom.BoxCostumeBlock;
import fr.lordfinn.steveparty.items.custom.BoxCostumeItem;
import net.minecraft.client.render.entity.EntityRenderer;
import net.minecraft.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/**
 * No name tag (nor the score under it) for a player hiding in his Box Costume, from the first tick, for anyone; nor for
 * a traveller in a pipe ({@link PipeTravellerPose}).
 */
@Mixin(EntityRenderer.class)
public abstract class EntityRendererNameTagMixin {
    @WrapOperation(method = "render", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/client/render/entity/EntityRenderer;hasLabel(Lnet/minecraft/entity/Entity;)Z"))
    private boolean steveparty$hideNameTag(EntityRenderer<?> renderer, Entity entity, Operation<Boolean> original) {
        if (PipeTravellerPose.hidesNameTag(entity)) return false;
        // Hidden in the box (or a block of the grid): straight from the synced state, whatever the box animation
        if (entity instanceof net.minecraft.entity.player.PlayerEntity player
                && (BoxCostumeItem.isHiddenInBox(player)
                || BoxCostumeBlock.isBlockAligned(player))) return false;
        if (entity instanceof BoxCostumeRenderState costume) {
            BoxCostumeAnimatable box = costume.steveparty$getBoxCostume();
            if (box != null && box.isHidden()) return false;
        }
        return original.call(renderer, entity);
    }
}
