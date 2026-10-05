# Power-ups (bonus): how the base works

A power-up is an item a player uses **at the start of their turn, before their roll, on top of it** (a die replaces
the roll, a power-up adds to the turn). Code: `src/main/java/fr/lordfinn/steveparty/powerups/`.

## Plugging in a new power-up

1. One class extending `PowerUp` (see `MushroomPowerUp`, `DoubleCoinsPowerUp`):
   `super("steal_bell", defaultPriceInCoins, Formatting.COLOUR)`.
2. **One line** in `PowerUps` (the only registration point):
   `public static final PowerUp STEAL_BELL = register(new StealBellPowerUp());`
3. Assets and texts (named after the id):
   - item `steveparty:powerup_<id>` (registered by `ModItems` from `PowerUps.all()`, put in the creative tab);
   - `assets/steveparty/models/item/powerup_<id>.json` (`item/generated`) and `textures/item/powerup_<id>.png`;
   - lang (en **and** fr, `LangGameTests` checks both): `item.steveparty.powerup_<id>`,
     `powerup.steveparty.<id>.desc` (tooltip, `%s` = key words from `descriptionArgs()`, or override
     `effectLines()`), `powerup.steveparty.<id>.announce` (`%s` = the player);
   - optionally a recipe `data/steveparty/recipe/powerup_<id>.json` and a line in the guide entry `powerups`
     (`scripts/guide/build_guide.py`).

Everything else is automatic: the use rules, consumption, announcement, tooltip layout, the HUD counting it
(`PartyLiveData.isPowerUp`), the default price on a stall, saving.

## The contract (`PowerUp`)

| Method | When | Default |
|---|---|---|
| `target()` | `NONE` (right-click anywhere), `PLAYER` (a prompt lists the other online players of the party, `use.targetPlayer()`), `TILE` (right-click a board space, `use.targetTile()`) | `NONE` |
| `refusal(use)` | own conditions, checked after the common ones and once the target is known. Return a `Text` to refuse (nothing consumed) | `null` |
| `apply(use)` | right after use: the item is consumed, the use announced. Immediate effects go here (steal coins, move a token…) | nothing |
| `modifyRoll(outcome, turn)` | the roll of that turn is final (after the die's modules; never for a blank roll). A change of steps is shown in the action-bar result as `(4 +3 Mushroom)` | unchanged |
| `modifyCoinsGained(coins, turn)` | the turn's player gains `coins > 0` of the party's coin. Never called for losses | unchanged |
| `announcement(use)`, `effectLines()`, `descriptionArgs()` | texts | lang keys above |

`PowerUpUse` gives the world, the player, the `PartyControllerEntity`, the `TokenTurnPartyStep` (token, owner…), the
target, and `state()`.

## Common rules (`PowerUpService.use`)

Refused with a red action-bar message, **nothing consumed**, when:
- it is not the player's own turn (`PowerUpService.turnOf`: the current step of a running party is a
  `TokenTurnPartyStep` in progress whose owner is the player, not waiting for an absent token);
- the roll already counts (`TokenTurnPartyStep.hasRolled()`; a blank roll does not count);
- a power-up was already used this turn (one per turn);
- `TILE` without a board space clicked, `PLAYER` without another online player;
- `refusal(use)` says no.

Otherwise: one item consumed (not in creative), the turn remembers the power-up, the party audience gets the
announcement (action bar + chat, `✦` in the power-up's colour, sound and particles), then `apply(use)`.

## State until the end of the turn

`TokenTurnPartyStep.getPowerUps()` → `PowerUpTurn`: the power-up used (`used()`) and a free `NbtCompound data()`
for what it remembers (the Mushroom stores `RollBonusSpent` there). Saved with the turn (`PowerUps` key of the step
NBT), reset when the turn starts. A Roll Again turn is a new turn: a new power-up may be used.

The "until the end of the turn" hooks are asked **of the power-up used this turn only**, for as long as the turn is
the current step. Effects that must outlast the turn (a trap left on a space, a lock) do not belong in
`PowerUpTurn`: store them in the party (`PartyData` / `PartyControllerEntity`, saved with it) or in the block entity.

## Hook points in the rest of the mod

- `DiceEntity.onRollFinished` → `PowerUpService.onRollFinished(roller, outcome)`: after the modules, before
  `DiceRollEvent` (so the token walks the changed steps) and before the action-bar announcement (gets the note).
- `DiceRollEffects.applyCoins` (coin faces) → `PowerUpService.coinsGained(roller, coins)`.
- `InventoryInteractorTileBehavior.handleTransfer` (item spaces giving the party's coin) →
  `PowerUpService.itemsGained(player, item, count)`: the extra coins are taken **from the same linked inventory**
  (nothing is created; a short chest gives what it has).
- Anything new that gives the turn's player coins should go through `coinsGained` / `itemsGained` too.
- `TradingStallBlockEntity.getTradeOffers`: a `PowerUpItem` with both price slots empty sells for
  `defaultPrice()` × `steveparty:coin`. Shop cartridges and the Boxed Trader read the stalls: nothing else to do.

## Pitfalls

- Server side only (except tooltips). `PowerUpService.turnOf` scans the loaded controllers: cheap, but do not call it
  every tick.
- A `PLAYER` power-up is consumed **when the prompt is answered** (or times out on the first player); the turn is
  checked again then: if the turn moved on or the roll counted meanwhile, nothing happens.
- `modifyRoll` is called once per final roll: mark in `data()` what was spent, otherwise every roll of the turn
  (a Replay gives a new turn, but a forced re-roll would not) gets it again.
- Keep `modifyCoinsGained` for gains: losses never reach it, and it must not return a negative number.
- A power-up removed from the mod but saved in a turn loads as "used, no effect" (`PowerUps.SPENT`).
- Tests: `PowerUpGameTests` (uses `DiceTestKit.party`); mock players are creative, switch them to survival to see
  the item consumed.
