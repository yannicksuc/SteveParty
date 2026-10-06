# Power-ups (bonus): how the base works

A power-up is an item a player uses **at the start of their turn, before their roll, on top of it** (a die replaces
the roll, a power-up adds to the turn). Code: `src/main/java/fr/lordfinn/steveparty/powerups/`.

## The power-ups

| Id | Name (en / fr) | Price | Target | Effect (`powerups/effects/`) | Kept (not consumed) when |
|---|---|---|---|---|---|
| `mushroom` | Mushroom / Champignon | 5 | none | +3 to the roll (`modifyRoll`) | never |
| `double_coins` | Double Coins / Double pièces | 8 | none | gains doubled (`modifyCoinsGained`) | never |
| `padlock` | Padlock / Cadenas | 8 | none | `PowerUpProtection.protect` | already protected |
| `trap` | Trap / Piège | 10 | none (the pawn's space) | `TrapEffect.use` | pawn on no board space |
| `thief_bell` | Thief Bell / Cloche voleuse | 12 | player | `ThiefBellEffect.steal` (5-15 coins) | target has no coins |
| `star_whistle` | Star Whistle / Sifflet d'étoile | 15 | none | `StarWhistleEffect.use` | no Star, no other Star space |
| `golden_pipe` | Golden Pipe / Tuyau doré | 25 | none | `GoldenPipeEffect.use`, turn held until arrival | no Star, already before it |
| `golden_thief_bell` | Golden Thief Bell / Cloche voleuse dorée | 40 | player | `ThiefBellEffect.steal` (one star) | target has no star |

A bell parried by a Padlock is consumed (and so is the Padlock). The Padlock is used up by the Thief Bell only when
a theft would really happen, and by another player's Trap only (`TrapEffect.isProtected`, never its owner's).

**The Star:** the Golden Pipe and the Star Whistle read and move it through `PowerUpStar.relocator()`, by default
`PartyStarRelocator`: the party's star of the Star Cartridge (`PartyStars`; the whistle moves it like a purchase does,
`PartyStars.place`, announced). To plug another one (a test), `PowerUpStar.install(...)`. A board without an active
star space has no star: both are then refused, not consumed.

## How many a player carries (`PowerUpLimit`)

The Party Controller's « Max power-ups » setting (`getMaxPowerUps`, NBT `MaxPowerUps`, Settings page, 3 by default,
0 = no limit) caps what a player of a **running** party carries: every `PowerUpItem` and every die **carrying**
the Power-up module (spent when rolled), counted by items; any other die comes back to its roller and does not count. Enforced in
`CustomizableMerchantScreenHandler` (every Boxed Trader / shop stop purchase: refused, nothing paid; a Trading Stall
sells through a Boxed Trader), `InventoryInteractorTileBehavior.handleTransfer` (the extra stays in the chest) and
`PowerUpPickupMixin` (items on the ground). Outside a party: no limit. A Power-up die's tooltip shows `[Power-up] [Consumed]` and the power-up points; thrown
during its player's turn it is the turn's power-up (`PowerUpService.onDieThrown`: refused after another one).

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
| `pickPlayer(use, others, pick)` | `PLAYER` only: asks which player, calls `pick` once (maybe 15 s later; the turn is held meanwhile) | a list of heads, the first one when unanswered |
| `refusal(use)` | own conditions, checked after the common ones and once the target is known. Return a `Text` to refuse (nothing consumed) | `null` |
| `apply(use)` | the effect. Returns `Result.APPLIED` (then consumed, remembered, announced) or `Result.refused(text)` / `refusedSilently()` (nothing consumed, the turn forgets it). An effect still at work after it returns (a warp) calls `use.holdTurn(maxTicks)` and runs the returned `Runnable` when done | `APPLIED` |
| `modifyRoll(outcome, turn)` | the roll of that turn is final (after the die's modules; never for a blank roll). A change of steps is shown in the action-bar result as `(4 +3 Mushroom)` | unchanged |
| `modifyCoinsGained(coins, turn)` | the turn's player gains `coins > 0` of the party's coin. Never called for losses | unchanged |
| `announcement(use)`, `effectLines()`, `descriptionArgs()` | texts; `announcement` null = no announcement (the effect speaks for itself, or must stay secret) | lang keys above |
| `hasGlint()` | its item shines | `false` |

`PowerUpUse` gives the world, the player, the `PartyControllerEntity`, the `TokenTurnPartyStep` (token, owner…), the
target, and `state()`.

## Common rules (`PowerUpService.use`)

Refused with a red action-bar message, **nothing consumed**, when:
- it is not the player's own turn (`PowerUpService.turnOf`: the current step of a running party is a
  `TokenTurnPartyStep` in progress whose owner is the player, not waiting for an absent token);
- the roll already counts (`TokenTurnPartyStep.hasRolled()`; a blank roll does not count);
- a power-up was already used this turn (one per turn);
- a power-up is still at work (`PowerUpTurn.isHeld`: a player being picked, a warp);
- `TILE` without a board space clicked, `PLAYER` without another online player;
- `refusal(use)` says no.

Otherwise `apply(use)`; if it applied: one item consumed (not in creative), the turn keeps the power-up, the party
audience gets the announcement (action bar + chat, `✦` in the power-up's colour, sound and particles). If it refused,
nothing is consumed and the player may still use one this turn.

**Holding the turn:** while a power-up is at work (`PowerUpService.hold`, at most a given time), the dice items
refuse to be thrown (`PowerUpService.refusesRoll`) and no other power-up may be used. The Thief Bell's pick and
the Golden Pipe's warp hold it: the roll comes after.

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
- A `PLAYER` power-up is applied **when the prompt is answered** (or times out); the turn is checked again then:
  if the turn moved on meanwhile, nothing happens.
- `modifyRoll` is called once per final roll: mark in `data()` what was spent, otherwise every roll of the turn
  (a Replay gives a new turn, but a forced re-roll would not) gets it again.
- Keep `modifyCoinsGained` for gains: losses never reach it, and it must not return a negative number.
- A power-up removed from the mod but saved in a turn loads as "used, no effect" (`PowerUps.SPENT`).
- Tests: `PowerUpGameTests` (uses `DiceTestKit.party`); mock players are creative, switch them to survival to see
  the item consumed.
