# Padlock (Cadenas)

Item id: `powerup_padlock`. Effect: `fr.lordfinn.steveparty.powerups.effects.PowerUpProtection`.

A player who uses a Padlock at the start of their turn is protected until the start of their next turn against
the other players' attacks: the **Thief Bell** (coins or star stolen) and the **Traps** laid by the others.

- **One attack**: the protection parries one attack, then it is used up. One cheap power-up doesn't make its
  player immune to everything played during a whole round, and a second Trap or Bell is still worth playing.
- **Not stacked**: a second Padlock while protected changes nothing (`protect` returns `false`: don't spend it).
- **Expires** at the start of the player's next turn (`TokenTurnPartyStep.start`). The extra turn of a Replay tile
  does not end it.
- **Saved** with the party (`PartyData`, NBT key `ProtectedTokens`): it survives a save and a reload.
- **Shown** in the party HUD: the Padlock's icon in the « bonus » column of the standings, next to the player's name
  (`PartyLiveData.bonusesOf`).
- **Announced** in the chat of the party's audience: « X is protected by a Padlock until their next turn! », then
  « X's Padlock blocked the Thief Bell! » / « ... blocked a Trap! ».

Players are identified by their **token** (pawn) UUID, like everywhere in a party. `PowerUpProtection.tokenOf`
finds a player's token from the player's UUID.

## API

| Method | Use |
| --- | --- |
| `protect(controller, token)` | The Padlock's use. `false`: already protected or not in the party, don't spend the item. |
| `isProtected(controller, token)` | Read only: would an attack be blocked? |
| `consume(controller, token, Attack)` | Uses the protection up and announces it. `true`: **cancel the attack**. |
| `consume(controller, token)` | Same, announced as « an attack ». |
| `expire(controller, token)` | Called at the start of the player's turn; nothing else should need it. |
| `tokenOf(controller, playerUuid)` | The player's token in this party, or `null`. |

`Attack` is `THIEF_BELL` or `TRAP`. All methods are server side and accept a `null` token (not protected).

## Thief Bell

At the `// PADLOCK:` hook, before anything is taken from the victim, once the victim is known:

```java
UUID victimToken = PowerUpProtection.tokenOf(controller, victim.getUuid()); // if only the player is known
if (PowerUpProtection.consume(controller, victimToken, PowerUpProtection.Attack.THIEF_BELL)) {
    // Blocked: nothing stolen. The Bell is still spent (the thief chose a protected target).
    return;
}
// ... steal the coins or the star
```

Call `consume` only when the theft would really happen (a valid victim with something to take), so the protection
isn't used up by a Bell that would have done nothing.

## Traps

At the `// PADLOCK:` hook, when a pawn triggers a Trap laid by **another** player (a player's own Trap is not an
attack and must not use the protection up):

```java
if (!trapOwnerToken.equals(victimToken)
        && PowerUpProtection.consume(controller, victimToken, PowerUpProtection.Attack.TRAP)) {
    // Blocked: no effect on the pawn. Remove the Trap as if it had worked (decide with the Trap's design).
    return;
}
// ... apply the Trap
```

## Power-up use (socle)

```java
if (!PowerUpProtection.protect(controller, turn.getTokenUUID())) return false; // already protected: keep the item
stack.decrement(1);
```
