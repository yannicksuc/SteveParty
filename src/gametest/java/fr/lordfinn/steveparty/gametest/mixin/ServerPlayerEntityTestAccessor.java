package fr.lordfinn.steveparty.gametest.mixin;

import net.minecraft.server.network.ServerPlayerEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/** A test player hit right after it joined (TestPlayers#vulnerable): no joining grace. */
@Mixin(ServerPlayerEntity.class)
public interface ServerPlayerEntityTestAccessor {
    @Accessor("joinInvulnerabilityTicks")
    void steveparty$setJoinInvulnerabilityTicks(int ticks);
}
