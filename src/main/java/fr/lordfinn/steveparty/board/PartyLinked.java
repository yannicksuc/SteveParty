package fr.lordfinn.steveparty.board;

/**
 * A block whose settings live in the block itself and that serves a party it is linked to with the Tile Linker Brush:
 * the Party Rewinder (Magnéto de Party), the Piggy Bank, the Party Antenna. Its link ({@link PartyLink}) goes to any
 * element of a party (the Party Controller, any block of a board, a Mini-game Controller...), the party is found through
 * it ({@link LinkedParty}).
 * <p>
 * The brush, its overlay (yellow drifting dots), the undo of its strokes and the saving are shared: a block entity
 * adopts it by
 * <ol>
 *     <li>implementing this interface, holding a {@code new PartyLink(this, this::syncToClients)} (a
 *     {@code SyncedBlockEntity} sends its data to the clients) returned by {@link #partyLink()};</li>
 *     <li>calling {@link PartyLink#write} / {@link PartyLink#read} from its {@code writeNbt} / {@code readNbt} (the
 *     data has to reach the clients too: the brush draws the link from there);</li>
 *     <li>finding its party with {@link PartyLink#runningParty} (or {@link PartyLink#party}) only: unlinked, it serves
 *     no party (never the nearest one around) and shows it;</li>
 *     <li>letting the brush's use through: its block's {@code onUse} returns {@code PASS} when
 *     {@code TileLinkerBrush.isBrush(player.getMainHandStack())}.</li>
 * </ol>
 * Nothing else: {@link BrushLinks} takes any {@code PartyLinked} block entity as a holder ({@link PartyLinks}).
 */
public interface PartyLinked {
    /** Its link (the implementer is a block entity: the brush finds it at its position). */
    PartyLink partyLink();
}
