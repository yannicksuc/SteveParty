package fr.lordfinn.steveparty.gametest.mixin;

import net.minecraft.server.world.ServerChunkManager;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

/** The tests' land loaded on the spot (TestArea): its chunk tickets applied without waiting for a tick. */
@Mixin(ServerChunkManager.class)
public interface ServerChunkManagerTestInvoker {
    @Invoker("updateChunks")
    boolean steveparty$updateChunks();
}
