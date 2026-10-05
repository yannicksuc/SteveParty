# Trap power-up: wiring the effect

The Trap (`powerup_trap`, icon `textures/item/powerup_trap.png`) sets a hidden trap on the board space the user's token stands on. The next token of **another** player that **stops** there gives the trap's owner **10 coins**, at most what that player holds. The trap is then gone.

Code: `fr.lordfinn.steveparty.powerups.effects`

- `TrapEffect`: the effect.
- `TrapState`: the traps of one party.

Tests: `PowerupTrapGameTests`.

## Rules

- **Where:** the board space under the user's token when the power-up is used. A large tile counts as one space, its main block. Nothing to pick: one click, no menu, and every power-up is used at the start of the turn, while the token stands on a space. A choice among the spaces in range would need a selection screen and range rules, for little gain.
- **One trap per space.** A new trap replaces the one there, whoever set it. The user is not told that a trap was replaced, so the other player's trap stays secret.
- **Stops only.** The trap is checked in `BoardSpaceBlockEntity#onDestinationReached`, which only runs when a token ends its move: the last step, a Stop space, or the arrival after a teleport. Passing over the space does nothing.
- **Never its owner.** Neither the player who set it nor the token they used sets it off. The trap stays for the others.
- **Coins:** items of the party's coin (`PartyCurrency.COIN`), moved from the victim's inventory to the owner's (`InventoryUtils.take` / `giveOrDrop`). A victim with no coins still sets the trap off: it is gone, with a message saying so.
- **Offline players:** while the owner or the victim is not connected, the trap does not go off and waits for the next stop (`Outcome.WAITING`).
- **Hidden:**
  - Traps live in the party's data, on the server.
  - `PartyData#writeToPacket` does not send them, and the controller sends no NBT to the clients.
  - Only the owner gets a marker: every second, 3 faint red specks over each of their traps. These particles are sent to them alone (`ServerWorld#spawnParticles(viewer, ...)`). No client code is involved.
- **When it goes off:**
  - a chat message to the players within 100 blocks;
  - a tripwire click and evoker fangs;
  - crit and smoke particles.
- **Saved:**
  - `PartyData` holds a `TrapState`, written under `PowerupTraps` in the Party Controller's NBT.
  - `PartyData#reset` clears it, so a new party starts without traps.

## API

```java
// The power-up is used (the base's "use at the start of the turn" step)
TrapEffect.Placed placed = TrapEffect.use(controller, token);
// SET or REPLACED: spend the item. NO_SPACE / NO_PLAYER: nothing set, keep it.

// Low level (tests, commands): set a trap without feedback
TrapEffect.place(controller, spacePos, ownerUuid, ownerTokenUuidOrNull);
TrapEffect.trapAt(controller, spacePos);            // TrapState.Trap or null
controller.getPartyData().getTraps();               // all the traps of the party

// Already wired: a party token ends its move on a space
TrapEffect.Result r = TrapEffect.onTokenStopped(controller, space, token);
// r.outcome(): NONE, OWN, WAITING, DISARMED, SPRUNG; r.coins(): coins moved
```

`use` tells the user privately: an action-bar message and a soft tripwire sound, heard by them alone.

## Already wired in this branch

| Where | What |
|---|---|
| `BoardSpaceBlockEntity#onDestinationReached` (first line) | `TrapEffect.onTokenStopped(partyController, this, token)`, before the space's own role (shop, replay, teleport...) |
| `PartyData` | `traps` field, `getTraps()`, read and written with the party, cleared by `reset()` |
| `Steveparty#onInitialize` | `TrapEffect.initialize()`, the owner's marker (a server tick) |
| `lang/en_us.json`, `lang/fr_fr.json` | `item.steveparty.powerup_trap`, `tooltip.steveparty.powerup.trap`, `message.steveparty.powerup.trap.*` |

## To wire when merging with `feat/powerups`

1. In the Trap item's use (the base's "use a power-up" moment), call `TrapEffect.use(controller, token)`. Spend the item only on `SET` / `REPLACED`.
2. Tooltip: `tooltip.steveparty.powerup.trap` (adapt it to the base's tooltip convention if it has one).
3. Padlock: see below.

## Padlock hook

In `TrapEffect`, look for `// PADLOCK:`.

- `isProtected(controller, token, victim)` returns `false` for now. Make it return `true` when the victim holds an active Padlock, and consume the Padlock there.
- The `DISARMED` branch of `onTokenStopped` is already written:
  - it removes the trap, and no coins move;
  - it plays an iron-trapdoor sound and a cloud of particles;
  - it announces `message.steveparty.powerup.trap.disarmed` ("%1$s's Padlock disarms %2$s's trap!").
