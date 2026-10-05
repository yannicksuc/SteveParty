# Golden Pipe (`powerup_golden_pipe`)

Used at the start of the turn, before the roll: the pawn is warped to the board space just before the one carrying
the Star, so the roll can reach the Star and buy it.

Code: `fr.lordfinn.steveparty.powerups.effects.GoldenPipeEffect` and `StarLocator`.
Tests: `src/gametest/java/fr/lordfinn/steveparty/gametest/GoldenPipeGameTests.java` (batch `golden_pipe`).

## Behaviour

| Situation | Outcome | Used up |
|---|---|---|
| A Star, a space leads to it | `TELEPORTED`, the pawn warps to the space before the Star | yes |
| A Star, no space leads to it | `TELEPORTED`, the pawn warps onto the Star's space | yes |
| No Star (or the Star's position is no board space) | `NO_STAR`, a red message to the user | no |
| The pawn already stands just before the Star | `ALREADY_THERE`, a yellow message to the user | no |
| The pawn is already warping, or is removed | `BUSY`, silent | no |

The space before the Star (`GoldenPipeEffect.spacesBefore` / `spaceBefore`):

1. the board spaces whose **active** cartridge links to the Star's space;
2. a check point linking to it takes no step: the spaces linking to that check point are used instead (recursively);
3. none: the same with the links of every cartridge (an Advanced Tile's other slots);
4. several: the one closest to a start tile (`BoardGraph.distance`), ties at random;
5. none at all: the Star's space itself.

The board is read with `BoardGraph.collect` within `BOARD_RANGE` (96) blocks of the Star; a large tile's Star
position is resolved to its master block (`BoardSpaces.resolve`).

The warp is the Teleport tile's (`TileTeleport.teleport`: shrinking swirl, sounds, pop), in gold (`0xFFC83D`).
During it the pawn loses `TokenStatus.CAN_MOVE` (given back on arrival if it had it). On arrival its back-move trail
(`AdvanceBackMoves`) starts again on the new space.

Announcement to the party audience: « X takes the Golden Pipe! » (`message.steveparty.powerup.golden_pipe.used`),
X being the user's name, or the pawn's name when no user is given.

## Plugging it in

### StarLocator

```java
@FunctionalInterface
public interface StarLocator {
    StarLocator NONE = party -> Optional.empty();
    Optional<BlockPos> currentStarSpace(PartyControllerEntity party);
}
```

Implement it from `feat/star-cartridge`: return the board space carrying the Star on the party's board (any block of
a large tile is fine), or empty when there is none. A method reference works, e.g.
`StarLocator locator = party -> StarSpaces.current(party);`.

### The power-up item (`feat/powerups`)

When the owner uses the Golden Pipe at the start of its turn:

```java
GoldenPipeEffect effect = new GoldenPipeEffect(starLocator); // one instance is enough, it keeps no state
GoldenPipeEffect.Result result = effect.use(partyController, pawn, player, () -> {
    // the pawn stands on its new space: let the turn go on (the roll)
});
if (result.consumed()) stack.decrement(1);
// else: nothing happened, the player was told why; keep the item and let the turn go on as usual
```

- `onArrived` runs only for `TELEPORTED`, once the warp is over (about `TileTeleport.TOTAL_TICKS` = 28 ticks later),
  also if the pawn was removed meanwhile.
- `player` may be null: the announcement then names the pawn and the "nothing happened" messages go to the party
  audience.
- Server side only (`use` returns `BUSY` outside a `ServerWorld`).

### Text keys (en / fr)

- `item.steveparty.powerup_golden_pipe`, `powerup.steveparty.golden_pipe.desc` (+ `.before_star`) and
  `powerup.steveparty.star.desc.no_star` (name and tooltip, see `GoldenPipePowerUp`)
- `message.steveparty.powerup.golden_pipe.used`, `.no_star`, `.already_there`
