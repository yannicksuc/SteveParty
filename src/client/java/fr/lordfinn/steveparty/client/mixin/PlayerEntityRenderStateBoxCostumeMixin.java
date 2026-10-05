package fr.lordfinn.steveparty.client.mixin;

import fr.lordfinn.steveparty.client.access.BoxCostumeRenderState;
import fr.lordfinn.steveparty.client.entity.costume.BoxCostumeAnimatable;
import net.minecraft.client.network.AbstractClientPlayerEntity;
import org.jetbrains.annotations.Nullable;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;

/** A player's Box Costume, for his renderer (1.21.1 has no render states: kept on the player, filled each frame). */
@Mixin(AbstractClientPlayerEntity.class)
public class PlayerEntityRenderStateBoxCostumeMixin implements BoxCostumeRenderState {
    @Unique
    @Nullable
    private BoxCostumeAnimatable steveparty$boxCostume = null;
    @Unique
    private boolean steveparty$inBox = false;

    @Override
    public @Nullable BoxCostumeAnimatable steveparty$getBoxCostume() {
        return steveparty$boxCostume;
    }

    @Override
    public boolean steveparty$isInBox() {
        return steveparty$inBox;
    }

    @Override
    public void steveparty$setBoxCostume(@Nullable BoxCostumeAnimatable box, boolean inBox) {
        this.steveparty$boxCostume = box;
        this.steveparty$inBox = inBox;
    }
}
