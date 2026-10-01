package fr.lordfinn.steveparty.client.mixin;

import fr.lordfinn.steveparty.client.access.BoxCostumeRenderState;
import fr.lordfinn.steveparty.client.entity.costume.BoxCostumeAnimatable;
import net.minecraft.client.render.entity.state.PlayerEntityRenderState;
import org.jetbrains.annotations.Nullable;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;

@Mixin(PlayerEntityRenderState.class)
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
