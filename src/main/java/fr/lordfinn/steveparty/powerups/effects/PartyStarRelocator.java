package fr.lordfinn.steveparty.powerups.effects;

import fr.lordfinn.steveparty.blocks.custom.PartyController.PartyControllerEntity;
import fr.lordfinn.steveparty.service.PartyStars;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.random.Random;

import java.util.List;
import java.util.Optional;

/**
 * The party's star of the Star Cartridge ({@link PartyStars}), as the power-ups see it: where it stands
 * ({@link PartyControllerEntity#getStarSpace}), the board's active star spaces, and its move to another one, the same
 * as after a purchase ({@link PartyStars#place}: « The star moved to … », its sound and sparkles). Installed in
 * {@link PowerUpStar} at start-up.
 */
public final class PartyStarRelocator implements StarRelocator {
    public static final PartyStarRelocator INSTANCE = new PartyStarRelocator();

    private PartyStarRelocator() {
    }

    @Override
    public Optional<BlockPos> currentStarSpace(PartyControllerEntity party) {
        return Optional.ofNullable(party.getStarSpace());
    }

    @Override
    public List<BlockPos> activeStarSpaces(PartyControllerEntity party) {
        return party.getWorld() instanceof ServerWorld world ? PartyStars.activeStarSpaces(world, party) : List.of();
    }

    @Override
    public boolean moveStarTo(PartyControllerEntity party, BlockPos space) {
        if (!(party.getWorld() instanceof ServerWorld world) || !PartyStars.isActiveStarSpace(world, space)) return false;
        party.setStarSpace(space.toImmutable());
        party.markDirty();
        PartyStars.sync(world);
        return true;
    }

    /** As after a purchase: another active star space at random, announced, never the same one. */
    @Override
    public boolean moveStarElsewhere(PartyControllerEntity party, Random random) {
        BlockPos current = party.getStarSpace();
        if (current == null || !(party.getWorld() instanceof ServerWorld world)) return false;
        if (StarRelocator.pickOther(activeStarSpaces(party), current, random).isEmpty()) return false;
        BlockPos moved = PartyStars.place(party, world, current);
        return moved != null && !moved.equals(current);
    }
}
