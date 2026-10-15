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

/**
 * The link of a {@link PartyLinked} block to the party it serves, as the Tile Linker Brush paints it: one element of a
 * party (the Party Controller, any block of a board, a Mini-game Controller, a podium, a pipe), whose party is found
 * through it ({@link LinkedParty}). Kept by the block itself (no cartridge), saved with it, sent to the clients (the
 * brush draws it, the block may show it).
 * <p>
 * Saved as {@code LinkDimension} + {@code LinkPos} in the block's data (the Party Rewinder's keys, kept as they were).
 */
public final class PartyLink {
    /** How long the party found through the link is kept before it is looked for again (ticks). */
    private static final int REFRESH_TICKS = 20;

    private final BlockEntity owner;
    private final Runnable sync;
    private @Nullable GlobalPos link;
    private @Nullable PartyControllerEntity party;
    private long partyAt = Long.MIN_VALUE;

    /**
     * @param owner the block entity it belongs to (saved when the link changes)
     * @param sync  tells the clients (the owner's data sent again)
     */
    public PartyLink(BlockEntity owner, Runnable sync) {
        this.owner = owner;
        this.sync = sync;
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
        if (link == null) return;
        nbt.putString("LinkDimension", link.dimension().getValue().toString());
        nbt.putLong("LinkPos", link.pos().asLong());
    }

    public void read(NbtCompound nbt) {
        Identifier dimension = nbt.contains("LinkPos") ? Identifier.tryParse(nbt.getString("LinkDimension")) : null;
        link = dimension == null ? null : GlobalPos.create(RegistryKey.of(RegistryKeys.WORLD, dimension), BlockPos.fromLong(nbt.getLong("LinkPos")));
        partyAt = Long.MIN_VALUE;
    }
}
