package fr.lordfinn.steveparty.mixin;

import fr.lordfinn.steveparty.items.custom.CustomFireworkRocketEntity;
import net.minecraft.entity.projectile.FireworkRocketEntity;
import org.spongepowered.asm.mixin.Mixin;

//
@Mixin(FireworkRocketEntity.class)
public abstract class CustomFireworkRocketEntityMixin implements CustomFireworkRocketEntity {

    @Override
    public void steveparty$setLifetime(int lifetime) {
        ((FireworkRocketEntityAccessor) this).setLifeTime(lifetime);
    }
}
