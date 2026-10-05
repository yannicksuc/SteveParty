# Thief Bell: plugging the effect into the power-up base

Effect: `fr.lordfinn.steveparty.powerups.effects.ThiefBellEffect` (items `powerup_thief_bell` and `powerup_golden_thief_bell`).

1. When the item is used at the start of a turn, map it to `Variant.THIEF` or `Variant.GOLDEN`, consume the item, then call `ThiefBellEffect.use(party, thief, variant, random, protection, DicePrompts.TIMEOUT_TICKS, result -> ...)`.
2. `use` shows the player picker (the `DicePrompts` list of the swap face); unanswered, it robs `defaultTarget` (most coins, or most stars for the golden bell). Continue the turn (the roll) in the callback: it may come up to 15 s later.
3. To skip the picker (bots, tests, a target already chosen), call `ThiefBellEffect.steal(party, thief, target, variant, random, protection)` directly.
4. Padlock: pass a `ThiefBellEffect.Protection` that returns true when the target holds a Padlock and consumes it (see the `// PADLOCK:` comments); `Protection.NONE` until the Padlock exists.
5. The `Result` (outcome, amount, thief, target) can feed the party HUD or statistics; the announcement and sounds are already done.
6. Lang keys: `item.steveparty.powerup_(golden_)thief_bell` (names) and `powerup.steveparty.(golden_)thief_bell.desc` (tooltips).

**Wired** (`ThiefBellPowerUp`): the base's player pick is the bell's list (`ThiefBellEffect.ask`); a target with nothing to steal is refused (bell kept); `Protection.padlock(party)` consumes the target's Padlock only when something would be taken (the Padlock then announces it, the bell stays silent). STOLEN and PROTECTED consume the bell.
