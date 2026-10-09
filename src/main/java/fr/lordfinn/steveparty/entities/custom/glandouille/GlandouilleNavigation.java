package fr.lordfinn.steveparty.entities.custom.glandouille;

import net.minecraft.entity.ai.pathing.LandPathNodeMaker;
import net.minecraft.entity.ai.pathing.MobNavigation;
import net.minecraft.entity.ai.pathing.PathNodeNavigator;
import net.minecraft.entity.mob.MobEntity;
import net.minecraft.world.World;
import net.minecraft.world.chunk.ChunkCache;

/**
 * A Glandouille walks the way its whole tower fits: the path search reads the tower's height (cached in the bottom
 * one, {@link GlandouilleEntity#towerBlocks()}) instead of its own, so a node is walkable only with that many free
 * blocks above it. No block is read beyond what the vanilla search reads for a mob that tall.
 */
public class GlandouilleNavigation extends MobNavigation {
    public GlandouilleNavigation(GlandouilleEntity glandouille, World world) {
        super(glandouille, world);
    }

    @Override
    protected PathNodeNavigator createPathNodeNavigator(int range) {
        this.nodeMaker = new TowerNodeMaker();
        this.nodeMaker.setCanEnterOpenDoors(true);
        return new PathNodeNavigator(this.nodeMaker, range);
    }

    /** The vanilla land search, as tall as the tower. */
    static final class TowerNodeMaker extends LandPathNodeMaker {
        @Override
        public void init(ChunkCache cachedWorld, MobEntity entity) {
            super.init(cachedWorld, entity);
            if (entity instanceof GlandouilleEntity glandouille && !glandouille.isBoardActor()) {
                this.entityBlockYSize = Math.max(this.entityBlockYSize, glandouille.towerBlocks());
            }
        }
    }
}
