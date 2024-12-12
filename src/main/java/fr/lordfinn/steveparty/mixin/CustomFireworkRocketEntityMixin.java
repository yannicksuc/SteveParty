package fr.lordfinn.steveparty.mixin;

import fr.lordfinn.steveparty.items.CustomFireworkRocketEntity;
import net.minecraft.entity.projectile.FireworkRocketEntity;
import org.spongepowered.asm.mixin.Mixin;

//
@Mixin(FireworkRocketEntity.class)
public abstract class CustomFireworkRocketEntityMixin implements CustomFireworkRocketEntity {

    public void steveparty$setLifetime(int lifetime) {
        ((FireworkRocketEntityAccessor) (Object)this).setLifeTime(lifetime);
    }
}
