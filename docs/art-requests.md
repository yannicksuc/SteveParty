# Art requests (models, animations, textures)

Things the code now expects from the art side. The code works without them (fallbacks), but these make it look right.

## Dice Forge (GeckoLib: `dice_forge` geo + `animations/dice_forge.animation.json`)

State machine driven by `DiceForgeBlockEntity#mainAnimController`:
static (not activated) → `core_insert` (once) → `floating` (loop) → `crafting` (loop while forging).

- **Bone `core`**, child of `root`, pivot at the centre of the seated core `[0,16,0]` (cubes may stay empty).
  When present, the gravity core item is rendered on it and fully driven by the animations;
  otherwise the code computes the fall itself on `root` (`DiceForgeCoreLayer`).
- **`core_insert`**: exactly 3.0 s, no loop (`CORE_INSERT_TICKS = 60`). Must end with `root` at position `[0,8,0]`,
  rotation 0, to chain with `floating`. With the `core` bone: descend `[0,20,0]` → `[0,0,0]` between 0 and 1.5 s.
- **`floating`**: 12 s loop, `root` floats around +8 px and turns 360°.
- **`crafting`**: 2 s loop, fast spin and a slight pump of `spike`.
  (Placeholder versions of the three animations exist; replace them freely, keep names and durations.)

GUI:
- `textures/gui/dice_forge.png` may draw the 5 frames the code currently draws itself (16 px slots, GUI coords):
  fragments at (80,42), (101,63), (80,84), (59,63); output at (80,63).
  The "Dice Forge" title at (8,6) overlaps the vortex (already the case before).
- `textures/gui/dice_forge_widgets.png` (64×64): button 40×14 normal at (0,0), hover (0,16), disabled (0,32),
  progress gauge (0,48). The button sits at (134,2).

## Trading Stall GUI

The sale-mode button uses vanilla button sprites and item icons (emerald = free shop, redstone torch = sale available,
redstone dust = waiting for a signal). A dedicated icon set is welcome but not required.

## Mula sitting (GeckoLib: `mula` geo + `animations/entity/mula.animation.json`)

A tamed Mula can now be ordered to sit by its owner (`MulaSitGoal`): it glides straight down and hovers 0.4 block
above the ground, and stays there. The model has no sit animation, so it plays `idle` meanwhile
(`MulaEntity#animationPredicate`, branch `isInSittingPose()`).

- **`sit`** (loop, ~3 s): a "resting" hover — body lower and tucked, slow shallow bob, eyes half closed / sleepy
  look if the texture allows. It must loop seamlessly; the code would switch `IDLE_ANIM` to it in the sitting branch.
- Optional **`sit_down`** (play once, ~0.5 s) chained into `sit`, and **`stand_up`** (~0.5 s) back to `idle`.

## Dice Forge: core removal

Sneak + right-click (empty hand) takes the gravity core back: the forge goes back to its static state at once
(controller stopped). An optional **`core_remove`** animation (play once, ~1 s, the core rising out of the hole,
the reverse of the first half of `core_insert`) could be added; the code would need a short "removing" state.
