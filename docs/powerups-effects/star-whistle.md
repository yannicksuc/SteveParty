# Star Whistle (`powerup_star_whistle`): wiring the effect

The effect lives in `fr.lordfinn.steveparty.powerups.effects`, with no item, recipe or shop entry:

| Class | Role |
|---|---|
| `StarLocator` | Reads the Star: `Optional<BlockPos> currentStarSpace(PartyControllerEntity party)`. Same file as on `feat/powerup-pipe` (Golden Pipe), byte for byte. |
| `StarRelocator extends StarLocator` | Moves the Star: `activeStarSpaces(party)`, `moveStarTo(party, space)`, and `moveStarElsewhere(party, Random)` built on them. |
| `StarWhistleEffect` | The power-up's effect: `use(party, token, user, random)` returns a `Result`. |

## 1. Implement `StarRelocator` on the Star cartridge (`feat/star-cartridge`)

```java
public final class StarCartridgeRelocator implements StarRelocator {
    @Override public Optional<BlockPos> currentStarSpace(PartyControllerEntity party) { /* the space carrying the Star */ }
    @Override public List<BlockPos> activeStarSpaces(PartyControllerEntity party) { /* spaces whose Star cartridge is active */ }
    @Override public boolean moveStarTo(PartyControllerEntity party, BlockPos space) { /* take it off, put it on space, no message */ }
}
```

- `activeStarSpaces` includes the space carrying the Star; the effect leaves it out itself.
- `moveStarTo` must not announce anything: the effect does it.
- If the cartridge already has a "respawn elsewhere after purchase" routine, it can override `moveStarElsewhere`
  with it, as long as it never picks the same space and returns `false` when nothing moved.
  `StarRelocator.pickOther(spaces, current, random)` gives the uniform pick used by default.
- The Golden Pipe only needs a `StarLocator`: the same `StarCartridgeRelocator` instance serves both power-ups.

## 2. Call the effect from the power-up item (`feat/powerups`)

At the start of the turn, when the player uses the Star Whistle:

```java
StarWhistleEffect.Result result = new StarWhistleEffect(relocator)
        .use(party, token, player, world.getRandom());
if (result.consumed()) stack.decrement(1);
// the turn goes on (the roll) in every case
```

- `token` and `player` may be null; the announcement names the player, else the pawn.
- Outcomes:
  - `MOVED`: the Star went from `from()` to `to()`, the whistle is used up. The party hears
    « X whistles: the Star flies off to another space! », a two-note flute on the old space and a chime on the new
    one, with notes and sparkles.
  - `NO_OTHER_SPACE`: the Star is on the only active Star space; the user is told, nothing is used up.
  - `NO_STAR`: the board has no Star; the user is told, nothing is used up.

## 3. Lang and texture

- Keys already added (en/fr): `item.steveparty.powerup_star_whistle`, `item.steveparty.powerup_star_whistle.description`,
  `message.steveparty.powerup.star_whistle.used`, `.no_star`, `.no_other_space`.
- Icon: `textures/item/powerup_star_whistle.png` (made separately). The item model and registration come with the item.

## Tests

`StarWhistleGameTests` (registered in `src/gametest/resources/fabric.mod.json`) uses an in-memory `StarRelocator`:
the Star always changes space and reaches every other one, nothing moves with a single active space, nothing with no Star.
