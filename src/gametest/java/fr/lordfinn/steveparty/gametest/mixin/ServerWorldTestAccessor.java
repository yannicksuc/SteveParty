package fr.lordfinn.steveparty.gametest.mixin;

import net.minecraft.entity.Entity;
import net.minecraft.server.world.ServerEntityManager;
import net.minecraft.server.world.ServerWorld;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/** The tests' land loaded on the spot (TestArea): the entities of its chunks read without waiting for a tick. */
@Mixin(ServerWorld.class)
public interface ServerWorldTestAccessor {
    @Accessor("entityManager")
    ServerEntityManager<Entity> steveparty$entityManager();
}
