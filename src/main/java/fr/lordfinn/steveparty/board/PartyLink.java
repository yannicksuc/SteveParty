package fr.lordfinn.steveparty.board;

import fr.lordfinn.steveparty.blocks.custom.PartyController.PartyControllerEntity;
import fr.lordfinn.steveparty.blocks.custom.PartyController.PartyData;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.registry.RegistryKey;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.GlobalPos;
import org.jetbrains.annotations.Nullable;

import java.util.Optional;
import java.util.function.BooleanSupplier;

/**
 * The link of a {@link PartyLinked} block to the party it serves, as the Tile Linker Brush paints it: one element of a
 * party (the Party Controller, any block of a board, a Mini-game Controller, a podium, a pipe), whose party is found
 * through it ({@link LinkedParty}). Kept by the block itself (no cartridge), saved with it, sent to the clients (the
 * brush draws it, the block may show it).
 * <p>
 * Saved as {@code LinkDimension} + {@code LinkPos} in the block's data (the Party Rewinder's keys, kept as they were).
 * <p>
 * Unlinked, its block serves no party (see {@link PartyLinked}). A block that found its party alone before it had to be
 * linked ({@link #migratingFromNearest}) links itself once, saved without {@code LinkSettled}: to the Party Controller
 * nearest to it within its old radius, a running party's first, as soon as one is loaded (a try a second, up to
 * {@link #MIGRATION_TRIES}), then never again ({@code LinkSettled} saved). A new block starts unlinked.
 */
public final class PartyLink {
    /** How long the party found through the link is kept before it is looked for again (ticks). */
    private static final int REFRESH_TICKS = 20;

    private final BlockEntity owner;
    private final Runnable sync;
    private @Nullable GlobalPos link;
    private @Nullable PartyControllerEntity party;
    private long partyAt = Long.MIN_VALUE;
    /** How many times (a second apart) an old block looks for a controller to link to before it gives up. */
    public static final int MIGRATION_TRIES = 10;
    /** The radius in which an old block found its party alone (0: it never did, no migration). */
    private int migrationRange = 0;
    /** Whether an old block served a party otherwise (its mini-game pages...): it settles without linking itself. */
    private BooleanSupplier servedOtherwise = () -> false;
    /** Whether it may still link itself (saved before it had to be linked, not done yet): see {@link #tick}. */
    private boolean migrating = false;
    private int migrationTries = 0;

    /**
     * @param owner the block entity it belongs to (saved when the link changes)
     * @param sync  tells the clients (the owner's data sent again)
     */
    public PartyLink(BlockEntity owner, Runnable sync) {
        this.owner = owner;
        this.sync = sync;
    }

    /**
     * Its block found its party alone before it had to be linked, the nearest one within {@code range} blocks: saved
     * that way (no {@code LinkSettled}), it links itself once ({@link #tick}). Called once, from its constructor.
     *
     * @param servedOtherwise whether it served a party otherwise then (its mini-game pages...): it settles unlinked
     */
    public PartyLink migratingFromNearest(int range, BooleanSupplier servedOtherwise) {
        this.migrationRange = range;
        this.servedOtherwise = servedOtherwise;
        return this;
    }

    /**
     * Server tick of its block: an old block (see {@link #migratingFromNearest}) links itself to the Party Controller
     * nearest to it, a running party's first, as soon as one is loaded (a few tries a second apart, then it stays
     * unlinked). Done once: saved, it never links itself again.
     */
    public void tick(ServerWorld world) {
        if (!migrating || world.getTime() % 20 != Math.floorMod(owner.getPos().asLong(), 20)) return;
        if (link == null && !servedOtherwise.getAsBoolean()) {
            Optional<PartyControllerEntity> nearest = PartyControllerEntity.getClosestPartyControllerEntity(world, owner.getPos(), migrationRange);
            if (nearest.isEmpty() && ++migrationTries < MIGRATION_TRIES) return;
            migrating = false;
            nearest.ifPresent(controller -> set(GlobalPos.create(world.getRegistryKey(), controller.getPos().toImmutable())));
        }
        migrating = false;
        owner.markDirty();
    }

    /** The element it is linked to, null for none. */
    public @Nullable GlobalPos get() {
        return link;
    }

    /** Links it to {@code link} (null: none): saved and told to the clients. */
    public void set(@Nullable GlobalPos link) {
        this.link = link;
        partyAt = Long.MIN_VALUE;
        owner.markDirty();
        sync.run();
    }

    /** The element, in the owner's world; null for none or in another dimension. */
    public @Nullable BlockPos inWorld() {
        return link != null && owner.getWorld() != null && link.dimension().equals(owner.getWorld().getRegistryKey()) ? link.pos() : null;
    }

    /**
     * What its element belongs to, found now ({@link LinkedParty#NONE} unlinked).
     *
     * @param load whether the element's chunk may be loaded to find it out
     */
    public LinkedParty linked(ServerWorld world, boolean load) {
        return link == null ? LinkedParty.NONE : LinkedParty.of(world, link, load);
    }

    /**
     * The party of its element (or the one playing the mini-game of its pages): once found, looked for again at most
     * every {@link #REFRESH_TICKS}; null unlinked or for none.
     */
    public @Nullable PartyControllerEntity party(ServerWorld world) {
        if (link == null) return null;
        long now = world.getTime();
        // Kept only once found: a party set up right after the link (or a chunk loading) is seen at once
        if (party == null || party.isRemoved() || now - partyAt >= REFRESH_TICKS || now < partyAt) {
            party = linked(world, false).playingParty();
            partyAt = now;
        }
        return party;
    }

    /** {@link #party} if its party runs, or stands on its end when {@code includeEnded}; null otherwise. */
    public @Nullable PartyControllerEntity runningParty(ServerWorld world, boolean includeEnded) {
        PartyControllerEntity found = party(world);
        if (found == null) return null;
        PartyData data = found.getPartyData();
        return data.isStarted() || (includeEnded && data.isAtEnd()) ? found : null;
    }

    public void write(NbtCompound nbt) {
        if (migrationRange > 0) {
            if (migrating) nbt.putBoolean("LinkMigrating", true);
            else nbt.putBoolean("LinkSettled", true);
        }
        if (link == null) return;
        nbt.putString("LinkDimension", link.dimension().getValue().toString());
        nbt.putLong("LinkPos", link.pos().asLong());
    }

    public void read(NbtCompound nbt) {
        Identifier dimension = nbt.contains("LinkPos") ? Identifier.tryParse(nbt.getString("LinkDimension")) : null;
        link = dimension == null ? null : GlobalPos.create(RegistryKey.of(RegistryKeys.WORLD, dimension), BlockPos.fromLong(nbt.getLong("LinkPos")));
        partyAt = Long.MIN_VALUE;
        // Saved before it had to be linked (neither key): links itself once (tick)
        migrating = migrationRange > 0 && (nbt.getBoolean("LinkMigrating") || !nbt.contains("LinkSettled"));
    }
}
