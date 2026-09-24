package fr.lordfinn.steveparty.client.mixin;

import fr.lordfinn.steveparty.client.token.TokenBaseRenderState;
import net.minecraft.client.render.entity.state.LivingEntityRenderState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;

/** Token base data of the living entity render states (filled by TokenBaseLivingEntityRendererMixin). */
@Mixin(LivingEntityRenderState.class)
public class TokenBaseRenderStateMixin implements TokenBaseRenderState {
    @Unique
    private boolean steveparty$token;
    @Unique
    private int steveparty$baseColor;
    @Unique
    private float steveparty$baseHeight;
    @Unique
    private float steveparty$baseRadius;

    @Override
    public boolean steveparty$isToken() {
        return this.steveparty$token;
    }

    @Override
    public int steveparty$getBaseColor() {
        return this.steveparty$baseColor;
    }

    @Override
    public float steveparty$getBaseHeight() {
        return this.steveparty$baseHeight;
    }

    @Override
    public float steveparty$getBaseRadius() {
        return this.steveparty$baseRadius;
    }

    @Override
    public void steveparty$setTokenBase(boolean token, int color, float height, float radius) {
        this.steveparty$token = token;
        this.steveparty$baseColor = color;
        this.steveparty$baseHeight = height;
        this.steveparty$baseRadius = radius;
    }
}
