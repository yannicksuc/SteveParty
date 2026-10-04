/**
 * The mini-game bubble: a mini-game played in place, in a box zone of the real world, given back as it was.
 * <p>
 * <b>Who does what</b>
 * <ul>
 *   <li>{@link fr.lordfinn.steveparty.minigame.zone.ZoneBubbles}: the entry point and the server-wide state (the
 *   live bubbles, the players of each, the stashed inventories, the lifecycle events, the tick that sends players
 *   back in or out, recovery after a crash). The rest of the mod only talks to it and to {@code ZoneBubble}.</li>
 *   <li>{@link fr.lordfinn.steveparty.minigame.zone.ZoneBubble}: one session: its members, its snapshot (block
 *   entities and entities when it began), its journal, its restoration (in passes, under a budget per tick).</li>
 *   <li>{@link fr.lordfinn.steveparty.minigame.zone.ZoneBorder}: the checks the mixins
 *   ({@code mixin/ZoneBubble*Mixin}) call on the game's own events: the wall, the origin of a change, the
 *   transfers across.</li>
 *   <li>{@link fr.lordfinn.steveparty.minigame.zone.ZonePlayerRules}: the polite refusals of player clicks (Fabric
 *   events), above the border.</li>
 *   <li>{@code ZoneJournal}, {@code ZoneStorage}, {@code ZonePlayerStash}: the first state of each changed position,
 *   the files (written off the server thread, flushed before every world save), the players' real inventories.</li>
 *   <li>{@link fr.lordfinn.steveparty.minigame.zone.ZoneForbidden}: what the server does not allow in a zone.</li>
 * </ul>
 * <b>The rules of the hot paths</b> (server performance comes first)
 * <ul>
 *   <li>No session: every mixin reads {@code ZoneBorder.ACTIVE} first and leaves. Nothing else, ever.</li>
 *   <li>A session somewhere: every check runs on the server thread only (a client, a world generation or chunk IO
 *   thread leaves at once), and finds the side of a place with {@code ZoneBubbles.at}, which first tests the box of
 *   all live zones: an event far from every zone costs six comparisons.</li>
 *   <li>No allocation on a path that runs per block, per entity move or per tick, unless it is about to refuse
 *   something. The zone is never walked: block entities come from the chunks' tables, entities from the entity
 *   sections, forbidden blocks from the sections' palettes.</li>
 *   <li>What a session costs follows what it changes, not the size of its zone; the restorations of all zones share
 *   one budget a tick ({@code miniGameBubbleRestorePerTick}).</li>
 * </ul>
 * <b>Mixins</b>: injections only ({@code @Inject}, {@code @ModifyExpressionValue}, {@code @WrapOperation} where an origin must be
 * left whatever happens), no {@code @Redirect} or
 * {@code @Overwrite}, all required (a hook that fails to apply would open the border: the game does not start).
 * Those that enter an origin and those that filter a result at {@code RETURN} have priority 2000: applied after
 * other mods, they also see their early returns.
 */
package fr.lordfinn.steveparty.minigame.zone;
