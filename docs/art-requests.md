# Art requests (models, animations, textures)

Things the code now expects from the art side. The code works without them (fallbacks), but these make it look right.

## Dice Forge (GeckoLib: `dice_forge` geo + `animations/dice_forge.animation.json`)

State machine driven by `DiceForgeBlockEntity#mainAnimController`:
static (not activated) → `core_insert` (once) → `floating` (loop) → `crafting` (loop while forging).

The forge body (the "observatory") never leaves its block: only the gravity core floats above it. So `root` keeps
position `[0,0,0]` in every animation and only turns (the block outline stays where the model is).

- **The core turns with `root`** (no rotation of its own), so core and observatory always share speed and direction:
  `floating` = one turn in 12 s, `crafting` = one turn in 2 s.
- **Core altitude is code driven** (`DiceForgeCoreLayer`, `DiceForgeBlockEntity#getCoreAltitude`): after the insertion
  it rises out of the plate (+0.5 block), then with the star fragments (a black fragment counts 64), up to
  16 blocks for 256 fragments, smoothly. The orbiting faces follow it, level with the core; while forging, the die forms around the core and turns with it.
- Optional **bone `core`**, child of `root`, pivot at the centre of the seated core `[0,16,0]` (cubes may stay empty).
  When present, the core is rendered on it and fully driven by the animations (the code then skips its own fall and
  altitude); otherwise the code handles the fall and the altitude on `root`.
- **`core_insert`**: exactly 3.0 s, no loop (`CORE_INSERT_TICKS = 60`), ends with `root` at `[0,0,0]`, rotation 0.
  The core falls into the plate during the first 1.5 s (code), the forge shakes on impact (1.5 → 1.9 s).
- **`floating`**: 12 s loop, `root` turns 360°. **`crafting`**: 2 s loop, fast spin and a slight pump of `spike`.

GUI (`textures/gui/dice_forge.png`, drawn by `DiceForgeScreen`):
- The vortex holds the 12 face slots (the count of a face is its weight) and, in the center, the gravity core, which is
  the FORGE button (golden progress ring drawn by the code). Blank faces go in on its left (56,63), the forged die
  comes out on its right (104,63), and the 4 fragment slots sit on its diagonals (61,44), (99,44), (99,82), (61,82)
  (16 px slots, GUI coords; the code draws their frames).

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
