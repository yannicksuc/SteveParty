package fr.lordfinn.steveparty.mixin;

import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.nbt.NbtCompound;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

/** The parrots on a player's shoulders, for the mini-game bubble: they neither come into a session nor leave it. */
@Mixin(PlayerEntity.class)
public interface PlayerShoulderInvoker {
    @Invoker("dropShoulderEntities")
    void steveparty$dropShoulderEntities();

    @Invoker("setShoulderEntityLeft")
    void steveparty$setShoulderEntityLeft(NbtCompound entity);

    @Invoker("setShoulderEntityRight")
    void steveparty$setShoulderEntityRight(NbtCompound entity);
}
