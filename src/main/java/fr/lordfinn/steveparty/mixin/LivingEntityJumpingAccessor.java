package fr.lordfinn.steveparty.mixin;

import net.minecraft.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/** Whether a living entity holds its jump key: a Trichaudron reads its riders' (the server keeps their input). */
@Mixin(LivingEntity.class)
public interface LivingEntityJumpingAccessor {
    @Accessor("jumping")
    boolean steveparty$isJumping();
}
