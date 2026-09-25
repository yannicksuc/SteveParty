package fr.lordfinn.steveparty.client.mixin;

import it.unimi.dsi.fastutil.longs.Long2ObjectMap;
import net.minecraft.client.render.WorldRenderer;
import net.minecraft.entity.player.BlockBreakingInfo;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

import java.util.SortedSet;

/** Every player's block breaking progress, by block position: the villager block cries while it is being broken. */
@Mixin(WorldRenderer.class)
public interface WorldRendererBreakingAccessor {
    @Accessor("blockBreakingProgressions")
    Long2ObjectMap<SortedSet<BlockBreakingInfo>> steveparty$getBlockBreakingProgressions();
}
