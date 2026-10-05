# Port to Minecraft 1.21.1: compile error inventory

Phase 0 of the 1.21.3 → 1.21.1 port (full switch, no multi-version). Branch `port/1.21.1`.

## Versions chosen

| | 1.21.3 (before) | 1.21.1 (now) | Source |
|---|---|---|---|
| Minecraft | 1.21.3 | **1.21.1** | |
| Yarn | 1.21.3+build.2 | **1.21.1+build.3** (latest) | meta.fabricmc.net `/v2/versions/yarn/1.21.1` |
| Fabric Loader | 0.16.9 | **0.16.9** (kept) | every dependency below accepts it |
| Fabric API | 0.110.0+1.21.3 | **0.116.17+1.21.1** (latest) | maven.fabricmc.net metadata, Modrinth |
| GeckoLib | 4.7.1 | **4.7.3** | cloudsmith maven `geckolib-fabric-1.21.1` |
| Sodium (dev runtime) | mc1.21.3-0.6.5-fabric | **mc1.21.1-0.6.13-fabric** | Modrinth API |
| Iris (dev runtime) | 1.8.1+1.21.3-fabric | **1.8.8+1.21.1-fabric** | Modrinth API (latest release; needs Sodium 0.6.x) |
| REI | 17.0.807+fabric | **16.0.799+fabric** | Modrinth API (latest 1.21.1) |
| Architectury API | 14.0.4+fabric | **13.0.11+fabric** | Modrinth API |
| Cloth Config (dev runtime) | 16.0.143+fabric | **15.0.140+fabric** | Modrinth API |

Why GeckoLib 4.7.3 and not the latest (4.9.3): GeckoLib ≥ 4.7.4 for 1.21.1 was built with Loom 1.10+ (4.9.x with Loom 1.17)
and needs Fabric Loader ≥ 0.17. Loom 1.9.2 (this project) refuses it at configuration time
("Mod was built with a newer version of Loom"). 4.7.3 is the latest one built with Loom 1.9.2, and it is the same
4.7 API as the 4.7.1 the mod used on 1.21.3. Moving to 4.9.x later means bumping Loom (and Gradle 8.11 → what Loom requires) and the loader.

Other edits: `fabric.mod.json` depends `"minecraft": "~1.21.1"` (via `~${minecraft_version}`) and `"fabric-api": "*"`
(was the deprecated `"fabric"` id); README and `docs/store-listing.md` requirement lines and badge. The release pipeline
(`build.gradle` Modrinth/CurseForge blocks) reads `project.minecraft_version`, so it now publishes for 1.21.1 with no
extra change; `.github/workflows` has no hard-coded game version.

## How the errors were collected

javac stops a source set at its first failing compile and Gradle skips the dependent ones (`compileClientJava`,
`compileGametestJava` need `compileJava`). To list everything in one go, a temporary init script (not committed)
registers a `portCompileAll` task that compiles **main + client + gametest sources in one javac pass** against the
union of their classpaths, with the Mixin annotation processor args of `compileClientJava` and `-Xmaxerrs 10000`:

```groovy
// gradlew -I allerrors.init.gradle portCompileAll -x compileJava -x compileClientJava
allprojects { afterEvaluate { p ->
  def ss = p.sourceSets
  p.tasks.register('portCompileAll', JavaCompile) {
    source = ss.main.java + ss.client.java + ss.gametest.java
    classpath = (ss.client.compileClasspath + ss.gametest.compileClasspath)
        .filter { !it.path.replace('\\','/').contains('/build/classes/') && !it.path.replace('\\','/').contains('/build/resources/') }
    destinationDirectory = p.layout.buildDirectory.dir('portCompileAll')
    options.encoding = 'UTF-8'; options.release.set(21)
    def cc = p.tasks.named('compileClientJava', JavaCompile).get()
    options.annotationProcessorPath = cc.options.annotationProcessorPath
    doFirst { options.compilerArgs = cc.options.compilerArgs.findAll { it.toString().startsWith('-A') } + ['-Xmaxerrs','10000'] }
  }
}}
```

Each group agent can reuse it and filter the output on its own files: it compiles even while other groups are broken.

Result: **781 errors in 235 files** (main 351 / client 320 / gametest 110). Full list: [`errors.txt`](errors.txt)
(`file:line: message  [symbol; location]`).

**These counts are a lower bound.** javac does not report what an unresolved type hides: once a file imports a class
that no longer exists (`EntityRenderState`, `WireOrientation`, `RecipeDisplay`…), every use of it counts once, and the
method bodies behind a broken `@Override` are not checked. Expect a second wave in each group after the first fixes.
Mixins are worse: a mixin whose `@Inject`/`@At` target (a method name/descriptor string) changed still compiles and
only fails when the game loads (`defaultRequire: 1` → crash). The Mixin AP runs with `-Aquiet=true`, so it does not
report missing targets at compile time either. Every mixin must be checked against 1.21.1 (see each group).

## Errors by kind (top)

| Count | Error |
|---:|---|
| 444 | cannot find symbol (class/method/field removed or renamed) |
| 70 | method does not override or implement a method from a supertype |
| 38 | invalid method reference (`RenderLayer::getGuiTextured` and friends) |
| 29 | package `net.minecraft.client.render.entity.state` does not exist |
| 13 | `EntityType.create(ServerWorld, SpawnReason)` |
| 12 | `int` → `Vector3f` (dust particle colour) |
| 10+5+4+… | datagen `RecipeGenerator` API (`offerStonecuttingRecipe`, `generateFamily`, `createShaped`…) |
| 8 / 4 / 3 | `kill(ServerWorld)`, `damage(ServerWorld, …)`, `teleport(…, boolean)` |

Most frequent missing symbols: `WireOrientation` 25, `ScheduledTickView` 21, `ModelTransformationMode` (moved) 29,
`SpawnReason.LOAD` 18, `ActionResult.PASS_TO_DEFAULT_BLOCK_ACTION` 16, `EntityAttributes.SCALE` 14,
`CraftingRecipeInput.size()` 13, `NativeImage.setColorArgb/getColorArgb` 19, `PlayerEntityRenderState` 13,
`LivingEntityRenderState` 12, `ActionResult.Success` 9, `ColorHelper.lerp` 7, `EquippableComponent` 8.

## 1.21.3 → 1.21.1 API table (verified against Yarn 1.21.1+build.3 with `javap`)

| 1.21.3 | 1.21.1 |
|---|---|
| `client.render.entity.state.*RenderState`, `EntityRenderer#createRenderState/updateRenderState`, `render(S state, …)` | No render states. `EntityRenderer<T>#render(T entity, float yaw, float tickDelta, MatrixStack, VertexConsumerProvider, int light)`; `LivingEntityRenderer<T, M extends EntityModel<T>>` with `setupTransforms(T, MatrixStack, float animationProgress, float bodyYaw, float tickDelta, float scale)`, `scale(T, MatrixStack, float)`, `getRenderLayer(T, boolean, boolean, boolean)`, `isShaking(T)`; `EntityModel<T extends Entity>#setAngles(T, limbAngle, limbDistance, animationProgress, headYaw, headPitch)`, `BipedEntityModel#animateModel(T, …)` |
| `net.minecraft.item.ModelTransformationMode` | `net.minecraft.client.render.model.json.ModelTransformationMode` (client only) |
| `DrawContext#drawTexture(RenderLayer::getGuiTextured, id, x, y, u, v, w, h, tw, th)` | `drawTexture(Identifier, x, y, u, v, w, h, texW, texH)` (and the other `drawTexture(Identifier, …)` overloads); `drawGuiTexture(Identifier, x, y, w, h)` for sprites; colour via `RenderSystem.setShaderColor` |
| `DrawContext#drawStackOverlay` | `drawItemInSlot(TextRenderer, ItemStack, x, y[, String])` |
| `ColorHelper.getArgb/getRed/…/lerp/withAlpha` | `ColorHelper.Argb.getArgb/getRed/getGreen/getBlue/getAlpha/lerp(float,int,int)/withAlpha(int,int)/fromFloats` |
| `NativeImage#getColorArgb/setColorArgb` (ARGB) | `getColor(x,y)` / `setColor(x,y,c)` are **ABGR**: convert with `ColorHelper.Abgr.toAbgr(argb)` / read channels with `ColorHelper.Abgr.getRed…` |
| `VertexRendering.drawBox/drawOutline` | `WorldRenderer.drawBox(MatrixStack, VertexConsumer, Box/coords, r, g, b, a)`, `WorldRenderer.drawShapeOutline(…)` (static) |
| `Fog`, `BackgroundRenderer.applyFog(...) → Fog` | `BackgroundRenderer.applyFog(Camera, FogType, float viewDistance, boolean thickFog, float tickDelta)` (void, sets RenderSystem fog) |
| `net.minecraft.particle.ParticlesMode` | `net.minecraft.client.option.ParticlesMode` |
| `EntityRendererRegistry`/`EquipmentModel`, `item.equipment.*`, `EquippableComponent`, `DataComponentTypes.EQUIPPABLE`, `EquipmentType` | `ArmorItem(RegistryEntry<ArmorMaterial>, ArmorItem.Type, Settings)`; register an `ArmorMaterial` (record: defense map, enchantability, equip sound, repair ingredient supplier, layers, toughness, knockback res.) in `Registries.ARMOR_MATERIAL`; armour textures at `textures/models/armor/<layer>_layer_1.png`. Non-armour equippables: `Equipment` interface (`getSlotType()`) |
| `item.consume.UseAction`, `ConsumableComponent` | `net.minecraft.util.UseAction`; `Item#getUseAction/getMaxUseTime(ItemStack, LivingEntity)/finishUsing` overrides; `FoodComponent` |
| `Item#use → ActionResult` | `Item#use → TypedActionResult<ItemStack>` (`TypedActionResult.success(stack, client)`, `.pass(stack)`, `.consume(stack)`, `.fail(stack)`) |
| `ActionResult.Success`, `SUCCESS_SERVER`, `PASS_TO_DEFAULT_BLOCK_ACTION` | `ActionResult` is an enum: `SUCCESS, SUCCESS_NO_ITEM_USED, CONSUME, CONSUME_PARTIAL, PASS, FAIL`, `ActionResult.success(world.isClient)`. `Block#onUseWithItem` returns **`ItemActionResult`** (`SUCCESS, CONSUME, CONSUME_PARTIAL, PASS_TO_DEFAULT_BLOCK_INTERACTION, SKIP_DEFAULT_BLOCK_INTERACTION, FAIL`) |
| `Item.Settings#registryKey`, `#enchantable(int)`, `#useCooldown`, `#equippable`, `Items.register(Block, BiFunction)` | none: `new Item(new Item.Settings()…)` + `Registry.register(Registries.ITEM, id, item)`; enchantability via `Item#getEnchantability()` override; `Items.register(Block[, UnaryOperator<Settings>])` |
| `AbstractBlock.Settings#registryKey`, `Blocks.register(RegistryKey, Function, Settings)` | `Registry.register(Registries.BLOCK, id, new X(settings))` |
| `neighborUpdate(state, world, pos, block, @Nullable WireOrientation, notify)` | `neighborUpdate(BlockState, World, BlockPos, Block sourceBlock, BlockPos sourcePos, boolean notify)`; `World#updateNeighbors(pos, block)` |
| `getStateForNeighborUpdate(state, WorldView, ScheduledTickView, pos, dir, neighborPos, neighborState, Random)` | `getStateForNeighborUpdate(BlockState, Direction, BlockState neighborState, WorldAccess, BlockPos pos, BlockPos neighborPos)`; schedule ticks on the `WorldAccess` |
| `getPickStack(WorldView, pos, state, boolean includeData)` | `getPickStack(WorldView, BlockPos, BlockState)` |
| `Entity#kill(ServerWorld)`, `damage(ServerWorld, src, amt)`, `isInvulnerableTo(ServerWorld, src)`, `dropStack(ServerWorld, stack[, y])` | `kill()`, `damage(DamageSource, float)`, `isInvulnerableTo(DamageSource)`, `dropStack(ItemStack[, float])` |
| `teleport(ServerWorld, x, y, z, Set<PositionFlag>, yaw, pitch, boolean resetCamera)`, `PlayerPosition`, `PositionFlag.DELTA_*` | `teleport(ServerWorld, x, y, z, Set<PositionFlag>, float yaw, float pitch)`; `PositionFlag` = `X, Y, Z, Y_ROT, X_ROT` only (no velocity flags; set velocity separately + `velocityModified = true`) |
| `EntityType#create(World, SpawnReason)`, `SpawnReason.LOAD`, `SPAWN_ITEM_USE` | `create(World)`; `SpawnReason.SPAWN_EGG` (no `LOAD`: use `create(World)` / `EntityType.getEntityFromNbt`) |
| `EntityAttributes.MAX_HEALTH`, `MOVEMENT_SPEED`, `SCALE`, `ATTACK_DAMAGE`, `FOLLOW_RANGE`, `ENTITY_INTERACTION_RANGE`… | `GENERIC_MAX_HEALTH`, `GENERIC_MOVEMENT_SPEED`, `GENERIC_SCALE`, `GENERIC_ATTACK_DAMAGE`, `GENERIC_FOLLOW_RANGE`, `PLAYER_ENTITY_INTERACTION_RANGE`, `PLAYER_BLOCK_INTERACTION_RANGE` |
| `Entity#getLootTableKey()`, `EntityType#getLootTableKey()` | `getLootTable()` (returns `RegistryKey<LootTable>`) |
| `EntityType` registration with `RegistryKey` (`EntityType.Builder#build(RegistryKey)`) | `EntityType.Builder#build(String id)` / `build()` |
| `World#getTopYInclusive()` | `getTopY() - 1` |
| `DynamicRegistryManager#getOrThrow(RegistryKey<Registry>)` (→ `Registry`), `#stream()` | `get(RegistryKey)` (→ `Registry`), `getWrapperOrThrow(RegistryKey)` (→ `RegistryWrapper.Impl`), `streamAllRegistries()` |
| `Registry#getOptionalValue(id/key)`, `getOptional(key)`, `Identifier`/`RegistryKey#getValue()` on ids | `getOrEmpty(id/key)`, `getEntry(key)`; `RegistryKey#getValue()` still exists, `Identifier` has no `getValue()` (ids are plain `Identifier`) |
| `ItemStack#toNbt(WrapperLookup[, NbtCompound])` | `encode(WrapperLookup[, NbtElement])`, `encodeAllowEmpty`; `ItemStack.fromNbt(WrapperLookup, NbtElement)` |
| `PlayerEntity#giveOrDropStack` | `getInventory().offerOrDrop(stack)` or `giveItemStack(stack)` |
| `ScreenHandler#addPlayerSlots/addPlayerInventorySlots/addPlayerHotbarSlots` | manual `addSlot(new Slot(inv, i, x, y))` loops (3×9 + hotbar) |
| `ItemCooldownManager#getGroup/set(ItemStack…)` | per `Item`: `isCoolingDown(Item)`, `set(Item, ticks)`, `getCooldownProgress(Item, float)` |
| `SoundEvent#id()` | `getId()` |
| `DustParticleEffect(int rgb, float)`, `PacketCodecs.VECTOR_3F` | `DustParticleEffect(Vector3f, float)`; `PacketCodecs.VECTOR3F` |
| `ExplosionImpl` (server impl class) | `net.minecraft.world.explosion.Explosion` is the concrete class (`collectBlocksAndDamageEntities`, `affectWorld(boolean)`) |
| `LootWorldContext`, `net.minecraft.util.context.ContextParameter*` | `LootContextParameterSet(.Builder(ServerWorld).add(LootContextParameters.X, v).build(LootContextTypes.Y))` |
| `ServerRecipeManager`, `PreparedRecipes`, `RecipeEntry#id(): RegistryKey<Recipe<?>>` | `RecipeManager` (`get(Identifier)`, `values()`, `listAllOfType`, `getFirstMatch`); `RecipeEntry(Identifier, T)#id(): Identifier` |
| `recipe.display.*` (`RecipeDisplay`, `SlotDisplay`), `IngredientPlacement`, `Recipe#getIngredientPlacement/getDisplays/getRecipeBookCategory` | none. `Recipe<I>`: `matches(I, World)`, `craft(I, WrapperLookup)`, `fits(int w, int h)`, `getResult(WrapperLookup)`, `getIngredients()`, `getRemainder(I)`, `getSerializer()`, `getType()`, `isIgnoredInRecipeBook()` |
| `CraftingRecipeInput#size()` | `getSize()` (also `getStackCount()`, `getStacks()`) |
| `SpecialCraftingRecipe.SpecialRecipeSerializer` | `net.minecraft.recipe.SpecialRecipeSerializer<T>(Factory<T>)` |
| `Ingredient.ofItem(x)`, `Ingredient.ofItems(RegistryEntryList…)` | `Ingredient.ofItems(ItemConvertible...)`, `Ingredient.fromTag(TagKey)` |
| datagen `RecipeGenerator` / `RecipeProvider.Factory`, `FabricRecipeProvider#getRecipeGenerator(...)`, `createShaped(...)` | `FabricRecipeProvider#generate(RecipeExporter exporter)`; `ShapedRecipeJsonBuilder.create(RecipeCategory, ItemConvertible[, count])`, `ShapelessRecipeJsonBuilder.create(...)`; static `RecipeProvider.offerStonecuttingRecipe(exporter, category, output, input[, count])`, `offerPolishedStoneRecipe(exporter, …)`, `generateFamily(exporter, family, FeatureSet)`; `.criterion(hasItem(x), conditionsFromItem(x))`, `.offerTo(exporter[, Identifier])` |
| REI 17 `REICommonPlugin` (entrypoint `rei_common`) | REI 16 `me.shedaniel.rei.api.common.plugins.REIServerPlugin` (entrypoint `rei_server`, check the REI 16 docs) |
| GeckoLib 4.7.1 (1.21.3) `software.bernie.geckolib.object.Color` | GeckoLib 4.7.3 (1.21.1): renderer method signatures follow 1.21.1 (`GeoEntityRenderer#render(T entity, …)`, `GeoRenderer#defaultRender(…)` arg lists differ, `GeoModel#getTextureResource(T)` etc.): grep the GeckoLib sources jar for the exact signature |

## Proposed split: 8 disjoint groups

Ownership rule (first match wins, every `.java` file has exactly one owner; files without errors are listed by
directory but still belong to the group for runtime/mixin checks):

| Group | Owns (paths under `fr/lordfinn/steveparty/`) | Files (total / with errors) | Errors |
|---|---|---:|---:|
| **G1** Client entity rendering & render-state mixins | client: `entity/`, `mixin/` LivingEntity*/PlayerEntity*/EntityRender*/FallingBlock*/TokenBase*/BipedEntityModel*/*ModelPipeSwim*, `pipe/PipeTravellerPose`, `squish/`, `telescope/`, `tokenspell/MobTextureColors` | 48 / 33 | 108 |
| **G2** Client block/item rendering, models & world rendering | client: `blockentity/`, `model/`, `items/`, `hammer/`, `renderer/`, `pipe/` (except PipeTravellerPose), `tokenspell/` (except MobTextureColors), `utils/`, `board/`, `flag/`, `minigame/`, top-level `StevepartyClient`/`SwitchableClient`, `mixin/` ItemRenderer*/HeldItemRenderer*/WorldRenderer*/GameRenderer*/Camera* | 83 / 36 | 79 |
| **G3** Client GUI, HUD, screens & screen handlers | client: `gui/`, `screens/`, remaining `mixin/` (HUD/chat/mouse/window/interaction accessors), any other client file; main: `screen_handlers/` | 101 / 27 | 58 |
| **G4** Recipes, recipe datagen & REI | main: `recipes/`, `mixin/PreparedRecipesMixin`, `compat/`; client: `datagen/`, `compat/` | 23 / 16 | 129 |
| **G5** Blocks, block entities & board | main: `blocks/` (except `blocks/custom/pipe/` and `blocks/custom/signs/`), `board/` | 128 / 37 | 133 |
| **G6** Items, equipment, effects, loot, payloads, pipes & signs | main: `items/`, `effect/`, `loot/`, `payloads/`, `events/`, `mixin/ShearsItemMixin`, `blocks/custom/pipe/`, `blocks/custom/signs/` | 119 / 30 | 95 |
| **G7** Entities, zones, server mixins & misc | main: `entities/`, `minigame/`, `utils/`, `mixin/` (all except PreparedRecipesMixin and ShearsItemMixin), and every other main file | 198 / 21 | 69 |
| **G8** Gametests | gametest: everything under `src/gametest` | 81 / 35 | 110 |

Balance note: compile-error counts are only part of the effort. G1 is the heaviest by design work (every
render-state mixin has to be rewritten against the entity-based renderer), G4 is mostly mechanical datagen rewriting,
G7 has few compile errors but owns the runtime check of the 49 server mixins.

Cross-group contracts (agree before editing, or leave a stub):
- `ModBlocks` (G5) registers the pipe and sign blocks (G6); `ModItems` (G6) registers block items. Registration helpers
  change shape (no `RegistryKey` in settings): G5 defines the new `ModBlocks.register(...)` helper first, G6 follows it.
- `ModRecipes` (G4) owns serializer registration; recipe classes used by gametests (G8) and REI (G4).
- Datagen (G4) reads blocks/items from G5/G6: only names, unchanged.
- G8 (gametests) should start last or only fix tests whose subjects are already ported.

### G1. Client entity rendering & render-state mixins (108 errors, 33 files with errors, 48 files owned)

Key API changes:

- Remove every `*RenderStateMixin` (no render states in 1.21.1): the extra fields they carried (box costume, telescope, stencil hammer, squish, token base, pipe pose) must be read from the entity directly in the renderer/model mixins, e.g. inject into `LivingEntityRenderer#render(T, …)` / `setupTransforms(T, …)` / `PlayerEntityModel#setAngles(T, …)` with the entity in hand.
- Retarget all `@Inject/@At` strings to 1.21.1 descriptors (`render(LivingEntity;FFLMatrixStack;…)`, `setupTransforms(…;FFFF)V`, `scale`, `getRenderLayer`, `isShaking`, `shouldFlipUpsideDown(LivingEntity)`), including the mixins that compile today (BipedEntityModelPipeSwim, *ModelPipeSwim, LivingEntityRendererShouldFlip).
- GeckoLib renderers (Mula, MulaStar, Dice, BoxCostume): 4.7.3 on 1.21.1 uses entity-based `GeoEntityRenderer#render(T, …)`/`defaultRender` arg lists; `GeoModel#getTextureResource(T)`.
- `ModelTransformationMode` → `client.render.model.json`; `ParticlesMode` → `client.option`; `Fog` → `BackgroundRenderer.applyFog` (void); `ColorHelper` → `ColorHelper.Argb`.

Files with errors (count):

-   4  `client:client/entity/BlockTexturedBones.java`
-   1  `client:client/entity/BoxedTraderEntityRenderLayer.java`
-   1  `client:client/entity/DiceEntityModel.java`
-   1  `client:client/entity/DiceEntityRenderer.java`
-   7  `client:client/entity/MulaEntityRenderer.java`
-  15  `client:client/entity/MulaStarRenderer.java`
-   5  `client:client/entity/costume/BoxCostumeClient.java`
-   2  `client:client/entity/costume/BoxCostumeRenderer.java`
-   5  `client:client/mixin/EntityRenderDispatcherPipePoseMixin.java`
-   2  `client:client/mixin/EntityRendererBoxCostumeShadowMixin.java`
-   2  `client:client/mixin/FallingBlockEntityRendererVillagerMixin.java`
-   2  `client:client/mixin/LivingEntityRenderStateMixin.java`
-   4  `client:client/mixin/LivingEntityRendererBoxCostumeMixin.java`
-   2  `client:client/mixin/LivingEntityRendererSetupTransformsMixin.java`
-   2  `client:client/mixin/LivingEntityRendererSmoothFlipMixin.java`
-   2  `client:client/mixin/LivingEntityRendererSquishMixin.java`
-   2  `client:client/mixin/PlayerEntityModelBoxCostumeMixin.java`
-   2  `client:client/mixin/PlayerEntityModelMixin.java`
-   2  `client:client/mixin/PlayerEntityModelStencilHammerMixin.java`
-   2  `client:client/mixin/PlayerEntityModelTelescopeMixin.java`
-   2  `client:client/mixin/PlayerEntityRenderStateBoxCostumeMixin.java`
-   2  `client:client/mixin/PlayerEntityRenderStateStencilHammerMixin.java`
-   2  `client:client/mixin/PlayerEntityRenderStateTelescopeMixin.java`
-   2  `client:client/mixin/PlayerEntityRendererBoxCostumeMixin.java`
-   2  `client:client/mixin/PlayerEntityRendererStencilHammerMixin.java`
-   4  `client:client/mixin/PlayerEntityRendererTelescopeMixin.java`
-   4  `client:client/mixin/TokenBaseLivingEntityRendererMixin.java`
-   2  `client:client/mixin/TokenBaseRenderStateMixin.java`
-   8  `client:client/pipe/PipeTravellerPose.java`
-   2  `client:client/squish/SquishAnimations.java`
-   7  `client:client/telescope/TelescopeClient.java`
-   2  `client:client/telescope/TelescopeModel.java`
-   4  `client:client/tokenspell/MobTextureColors.java`

Mixins owned that compile but must be checked against 1.21.1 targets:

`BipedEntityModelPipeSwimMixin`, `DrownedModelPipeSwimMixin`, `LivingEntityRendererShouldFlipMixin`, `PiglinModelPipeSwimMixin`, `SkeletonModelPipeSwimMixin`, `ZombieModelPipeSwimMixin`, `ZombieVillagerModelPipeSwimMixin`

### G2. Client block/item rendering, models & world rendering (79 errors, 36 files with errors, 83 files owned)

Key API changes:

- `ModelTransformationMode` package move (26 hits); item renderer mixins retarget `ItemRenderer#renderItem(ItemStack, ModelTransformationMode, boolean, MatrixStack, …, BakedModel)`.
- `NativeImage#getColorArgb/setColorArgb` → `getColor/setColor` which are **ABGR** (convert with `ColorHelper.Abgr.toAbgr`); `ColorHelper.*` → `ColorHelper.Argb.*`.
- `VertexRendering` → `WorldRenderer.drawBox/drawShapeOutline`.
- Fabric model loading API (0.116.17+1.21.1): `ModelLoadingPlugin#onInitialize(Context)` signature and `BakedModel#getOverrides()` (still `ModelOverrideList` in 1.21.1): check the plugins (`*ModelPlugin`, `TradingStallBakedModel`).
- `EntityRendererRegistry.register` / armour feature renderers (bandana) without `EquipmentModel`; `RenderLayers.getEntityBlockLayer(BlockState, boolean)`.
- `StevepartyClient` holds client registrations touched by every client group: G2 owns it, others ask.

Files with errors (count):

-   6  `client:client/StevepartyClient.java`
-   2  `client:client/SwitchableClient.java`
-   3  `client:client/blockentity/DiceForgeConvergenceRenderer.java`
-   1  `client:client/blockentity/DiceForgeCoreLayer.java`
-   2  `client:client/blockentity/DiceForgeOrbitRenderer.java`
-   2  `client:client/blockentity/PodiumRenderer.java`
-   2  `client:client/blockentity/StencilCanvasBlockEntityRenderer.java`
-   1  `client:client/blockentity/TradingStallBakedModel.java`
-   2  `client:client/blockentity/TradingStallBlockEntityRenderer.java`
-   1  `client:client/blockentity/VillagerBlockEntityRenderer.java`
-   4  `client:client/board/ChevronSprites.java`
-   2  `client:client/board/WrenchClient.java`
-   1  `client:client/flag/FlagPalettes.java`
-   1  `client:client/hammer/StencilHammerStrikes.java`
-   1  `client:client/items/DiceModulePips.java`
-   2  `client:client/items/StencilItemRenderer.java`
-   2  `client:client/minigame/MiniGamePageClient.java`
-   2  `client:client/minigame/PageZoneClient.java`
-   2  `client:client/mixin/ItemRendererStencilHammerFaceMixin.java`
-   2  `client:client/mixin/ItemRendererTileFaceMixin.java`
-   2  `client:client/mixin/ItemRendererWandOrbitMixin.java`
-   2  `client:client/model/BrickShadeModelPlugin.java`
-   2  `client:client/model/ConnectedPlasticModelPlugin.java`
-   2  `client:client/model/PolishedTilesItemModel.java`
-   2  `client:client/model/TradingStallModelPlugin.java`
-   1  `client:client/model/sign/MaterialSprites.java`
-   1  `client:client/model/sign/SignModel.java`
-   2  `client:client/model/sign/StencilSignModelPlugin.java`
-   1  `client:client/model/sign/StencilSignModels.java`
-   5  `client:client/pipe/MiniGamePipeNotchRenderer.java`
-   2  `client:client/pipe/PipeModelPlugin.java`
-   1  `client:client/renderer/GlowingCuboidRenderer.java`
-   2  `client:client/tokenspell/TokenSpellHand.java`
-   5  `client:client/tokenspell/WandOrbit.java`
-   2  `client:client/utils/StencilResourceManager.java`
-   6  `client:client/utils/TileStampTextures.java`

Mixins owned that compile but must be checked against 1.21.1 targets:

`CameraPipeViewMixin`, `GameRendererFovMixin`, `GameRendererStencilHammerShakeMixin`, `GameRendererTelescopeMixin`, `HeldItemRendererStencilHammerMixin`, `HeldItemRendererTokenSpellMixin`, `WorldRendererBreakingAccessor`, `WorldRendererDeferredGlowsMixin`

### G3. Client GUI, HUD, screens & screen handlers (58 errors, 27 files with errors, 101 files owned)

Key API changes:

- `context.drawTexture(RenderLayer::getGuiTextured, id, …)` (32 hits) → `drawTexture(Identifier, x, y, u, v, w, h, texW, texH)`; sprite variants → `drawGuiTexture(Identifier, …)`; tint with `RenderSystem.setShaderColor` + `enableBlend` (the colour int argument does not exist).
- `drawStackOverlay` → `drawItemInSlot`; `ColorHelper` → `ColorHelper.Argb`; `NativeImage` ABGR as in G2.
- `TooltipComponent#getHeight()` (no TextRenderer arg in 1.21.1) and `getWidth(TextRenderer)`; `drawItems/drawText` signatures.
- `ScreenHandler#addPlayerSlots` → manual slot loops.

Files with errors (count):

-   3  `client:client/gui/ConsolePaint.java`
-   3  `client:client/gui/MiniGamePageTooltipComponent.java`
-   1  `client:client/gui/PartyGui.java`
-   1  `client:client/gui/StencilGunHud.java`
-   4  `client:client/gui/TileStampTooltipComponent.java`
-   1  `client:client/gui/ToolHud.java`
-   4  `client:client/gui/party/HudDraw.java`
-   6  `client:client/gui/party/HudPaint.java`
-   1  `client:client/gui/party/NoticeHud.java`
-   3  `client:client/gui/party/TurnBarHud.java`
-   2  `client:client/screens/BoardSpaceScreen.java`
-   1  `client:client/screens/CartridgeContainerScreen.java`
-   1  `client:client/screens/CashRegisterScreen.java`
-   3  `client:client/screens/DiceForgeScreen.java`
-   1  `client:client/screens/GoalPoleBaseScreen.java`
-   2  `client:client/screens/HopSwitchScreen.java`
-   3  `client:client/screens/MiniGamePageEditorScreen.java`
-   1  `client:client/screens/MiniGamesCatalogueScreen.java`
-   4  `client:client/screens/PartyControllerScreen.java`
-   2  `client:client/screens/StencilGunScreen.java`
-   4  `client:client/screens/StencilMakerScreen.java`
-   1  `client:client/screens/TokenSpellScreen.java`
-   1  `client:client/screens/TradingStallScreen.java`
-   1  `main:screen_handlers/custom/CartridgeContainerScreenHandler.java`
-   1  `main:screen_handlers/custom/CashRegisterScreenHandler.java`
-   2  `main:screen_handlers/custom/MiniGamesCatalogueScreenHandler.java`
-   1  `main:screen_handlers/custom/TradingStallScreenHandler.java`

Mixins owned that compile but must be checked against 1.21.1 targets:

`BossBarHudAccessor`, `ChatHudAccessor`, `ClientPlayerInteractionManagerVillagerPunchMixin`, `DrawContextDiceModulesMixin`, `InGameHudAccessor`, `InGameHudToolHudMixin`, `MouseBackgroundDevMixin`, `MouseScrollMixin`, `PipeMountMessageMixin`, `WindowBackgroundDevMixin`

### G4. Recipes, recipe datagen & REI (129 errors, 16 files with errors, 23 files owned)

Key API changes:

- Custom recipes: drop `getIngredientPlacement`, `getRecipeBookCategory`, `getDisplays`/`SlotDisplay`/`RecipeDisplay`; implement `fits(int,int)`, `getResult(WrapperLookup)`, `getIngredients()`; `CraftingRecipeInput#size()` → `getSize()`; serializers with `SpecialRecipeSerializer<T>(Factory)` from `net.minecraft.recipe`.
- `PreparedRecipesMixin` (1.21.2 `PreparedRecipes`) → rewrite against `RecipeManager` (e.g. `apply`/`setRecipes`) with `RecipeEntry(Identifier, …)`.
- Datagen: `FabricRecipeProvider#generate(RecipeExporter)`; static `RecipeProvider.offer*` helpers take the exporter first; `ShapedRecipeJsonBuilder.create(...)`; `generateFamily(exporter, family, FeatureSet)`.
- REI 16: `REIServerPlugin` instead of `REICommonPlugin`, update the `rei_common` entrypoint in `fabric.mod.json`; check the REI 16 display API for the client plugin.
- Data: convert the 109 hand-written recipe JSONs to object ingredients (see Non-code impacts), then `runDatagen` at the end.

Files with errors (count):

-   1  `client:client/compat/rei/CartridgeApplicationDisplay.java`
-   3  `client:client/compat/rei/SteveReiPlugin.java`
-  76  `client:client/datagen/StevepartyRecipeProvider.java`
-   3  `main:compat/rei/SteveReiCommonPlugin.java`
-   4  `main:mixin/PreparedRecipesMixin.java`
-  21  `main:recipes/DiceModuleRecipe.java`
-   2  `main:recipes/FlagDyeRecipe.java`
-   1  `main:recipes/FlagFromWoolRecipe.java`
-   1  `main:recipes/MaterialShapedRecipe.java`
-   3  `main:recipes/MiniGamePageCopyRecipe.java`
-   5  `main:recipes/ModRecipes.java`
-   2  `main:recipes/MultiDiceRecipe.java`
-   3  `main:recipes/StencilCopyRecipe.java`
-   2  `main:recipes/TileSizeRecipe.java`
-   1  `main:recipes/TradingStallRecipe.java`
-   1  `main:recipes/UnmirroredShapedRecipe.java`

### G5. Blocks, block entities & board (133 errors, 37 files with errors, 128 files owned)

Key API changes:

- `neighborUpdate(…, WireOrientation, …)` → `(…, Block, BlockPos sourcePos, boolean)`; `getStateForNeighborUpdate` new arg order (`Direction, BlockState, WorldAccess, BlockPos, BlockPos`) without `ScheduledTickView`/`Random`.
- `onUseWithItem` returns `ItemActionResult` (`PASS_TO_DEFAULT_BLOCK_INTERACTION`, …); `onUse` returns enum `ActionResult` (no `ActionResult.Success`, no `SUCCESS_SERVER`: use `ActionResult.success(world.isClient)` / `CONSUME`).
- Block registration without `RegistryKey`/`Settings#registryKey` (`ModBlocks`), defines the helper G6 follows; `getPickStack(WorldView, BlockPos, BlockState)`.
- `ItemStack#toNbt` → `encode`; `DustParticleEffect(Vector3f, float)`; `LootWorldContext` → `LootContextParameterSet`; `teleport` without `PlayerPosition`/`DELTA_*` flags; `UseAction` package; `PlayerEntity#giveOrDropStack`.
- `PartyController`: `software.bernie.geckolib.object.Color` import (GeckoLib 4.7.3 location).

Files with errors (count):

-   5  `main:blocks/ModBlocks.java`
-   2  `main:blocks/custom/BoardSpaceRedstoneRouterBlock.java`
-   9  `main:blocks/custom/DiceForgeBlock.java`
-   1  `main:blocks/custom/DiceForgeBlockEntity.java`
-   2  `main:blocks/custom/GoalPoleBaseBlock.java`
-   1  `main:blocks/custom/GoalPoleBaseBlockEntity.java`
-   3  `main:blocks/custom/GoalPoleBlock.java`
-   3  `main:blocks/custom/HopSwitchBlock.java`
-   6  `main:blocks/custom/MiniGameControllerBlock.java`
-   1  `main:blocks/custom/MiniGameControllerBlockEntity.java`
-   5  `main:blocks/custom/PartyBellBlock.java`
-  11  `main:blocks/custom/PartyController/PartyController.java`
-   5  `main:blocks/custom/PartyController/PartyControllerEntity.java`
-   1  `main:blocks/custom/PartyController/steps/MiniGameTeleports.java`
-   3  `main:blocks/custom/PiggyBankBlock.java`
-   8  `main:blocks/custom/PlasticBlock.java`
-   3  `main:blocks/custom/PlasticFenceBlock.java`
-   3  `main:blocks/custom/PlotBlock.java`
-   8  `main:blocks/custom/PodiumBlock.java`
-   1  `main:blocks/custom/StarFragmentsBlock.java`
-   1  `main:blocks/custom/StencilMakerBlockEntity.java`
-   1  `main:blocks/custom/TradingStallBlock.java`
-   7  `main:blocks/custom/VillagerBlock.java`
-   3  `main:blocks/custom/boardspaces/ABoardSpaceBlock.java`
-   4  `main:blocks/custom/boardspaces/ATileBlock.java`
-   6  `main:blocks/custom/boardspaces/CartridgeContainer.java`
-   1  `main:blocks/custom/boardspaces/CheckPointBlock.java`
-   3  `main:blocks/custom/boardspaces/TileContents.java`
-   7  `main:blocks/custom/boardspaces/TileFeedback.java`
-   7  `main:blocks/custom/boardspaces/TilePartBlock.java`
-   2  `main:blocks/custom/boardspaces/TileTeleport.java`
-   4  `main:blocks/custom/villager/VillagerBlockEntity.java`
-   1  `main:blocks/custom/villager/VillagerSoul.java`
-   1  `main:blocks/switchable/SwitchableConfig.java`
-   2  `main:blocks/switchable/SwitchedOffBlock.java`
-   1  `main:blocks/switchable/SwitchedOffBlockEntity.java`
-   1  `main:board/BoardLinks.java`

### G6. Items, equipment, effects, loot, payloads, pipes & signs (95 errors, 30 files with errors, 119 files owned)

Key API changes:

- `Item#use` → `TypedActionResult<ItemStack>`; `Item.Settings` without `registryKey`/`enchantable`/`equippable`/`useCooldown` (`ModItems`).
- Bandana / triple-jump shoes / box costume: `EquippableComponent`/`EquipmentType` → `ArmorItem` + registered `ArmorMaterial` (or `Equipment#getSlotType`), move bandana textures to `textures/models/armor/`, delete `models/equipment/*.json`.
- `EntityAttributes.SCALE` → `GENERIC_SCALE`; `isGliding()` → `isFallFlying()`; `EntityType#getLootTableKey` → `getLootTable`; `ColorHelper.Argb`.
- Payloads: `PacketCodecs.VECTOR_3F` → `VECTOR3F`; `DustParticleEffect` colour as `Vector3f`.
- Pipe and sign blocks: same block API changes as G5 (`ScheduledTickView`, `ItemActionResult`, `teleport`).

Files with errors (count):

-   1  `main:blocks/custom/pipe/MiniGamePipeBlock.java`
-   7  `main:blocks/custom/pipe/PipeBlock.java`
-   3  `main:blocks/custom/pipe/PipeTravel.java`
-   5  `main:blocks/custom/signs/AbstractStencilSignBlock.java`
-   3  `main:blocks/custom/signs/PlasticRoadSignBlock.java`
-   1  `main:blocks/custom/signs/SignMaterial.java`
-   4  `main:blocks/custom/signs/StencilInteractions.java`
-   5  `main:blocks/custom/signs/StencilPaintBlock.java`
-   4  `main:effect/SquishEffect.java`
-   1  `main:events/ModEvents.java`
-   3  `main:items/ModItems.java`
-   5  `main:items/custom/BandanaItem.java`
-   4  `main:items/custom/BoxCostumeItem.java`
-   3  `main:items/custom/DefaultDiceItem.java`
-   2  `main:items/custom/MiniGamePageItem.java`
-   2  `main:items/custom/MiniGamesCatalogueItem.java`
-   1  `main:items/custom/MultiDiceItem.java`
-   3  `main:items/custom/StencilGunItem.java`
-   2  `main:items/custom/StencilHammerStrike.java`
-   1  `main:items/custom/TokenItem.java`
-  13  `main:items/custom/TokenizerWandItem.java`
-   6  `main:items/custom/TripleJumpShoesItem.java`
-   2  `main:items/custom/WrenchItem.java`
-   3  `main:items/custom/cartridges/CartridgeItem.java`
-   3  `main:loot/ModLootTableModifiers.java`
-   2  `main:mixin/ShearsItemMixin.java`
-   2  `main:payloads/custom/ArrowParticlesPayload.java`
-   1  `main:payloads/custom/EnchantedCircularParticlePayload.java`
-   2  `main:payloads/custom/FloatingTextPayload.java`
-   1  `main:payloads/custom/StencilHammerStrikePayload.java`

### G7. Entities, zones, server mixins & misc (69 errors, 21 files with errors, 198 files owned)

Key API changes:

- `EntityType.Builder#build(RegistryKey)` → `build(String)`/`build()` (`ModEntities`); `EntityAttributes.*` → `GENERIC_*` / `PLAYER_*`.
- `damage/kill/isInvulnerableTo/dropStack(ServerWorld, …)` → no-world overloads; `getTopYInclusive()` → `getTopY() - 1`; `DynamicRegistryManager#stream()` → `streamAllRegistries()`, `getOrThrow(RegistryKey<Registry>)` → `get(...)`/`getWrapperOrThrow(...)`; `Registry#getOptionalValue` → `getOrEmpty`; `create(World, SpawnReason)` → `create(World)`.
- `ExplosionImpl` → `Explosion` (ExplosionImplMixin, ZoneBubbleExplosionMixin retarget).
- Runtime check of all 49 server mixins in `steveparty.mixins.json` (`defaultRequire: 1`): ZoneBubble* hooks into `World`, `ServerWorld`, `WorldChunk`, hoppers, pistons, crafter, dispenser, neighbor updater, random ticks… compile today but many targets changed between 1.21.1 and 1.21.3 (e.g. `NeighborUpdater` lost `WireOrientation`, `ServerWorld#tick` internals, `Explosion`).

Files with errors (count):

-   7  `main:entities/ModEntities.java`
-   1  `main:entities/custom/BoxedTraderBoxes.java`
-  11  `main:entities/custom/BoxedTraderEntity.java`
-  11  `main:entities/custom/DiceEntity.java`
-   3  `main:entities/custom/DirectionDisplayEntity.java`
-   1  `main:entities/custom/ForgeCoreEntity.java`
-  10  `main:entities/custom/MulaEntity.java`
-   2  `main:entities/custom/MulaRebirths.java`
-   1  `main:entities/custom/MulaSpawnSites.java`
-   1  `main:entities/custom/MulaStarEntity.java`
-   1  `main:entities/custom/PipeCarrierEntity.java`
-   1  `main:entities/custom/goals/MulaGoals.java`
-   2  `main:minigame/zone/ZoneBorder.java`
-   3  `main:minigame/zone/ZoneBubble.java`
-   1  `main:minigame/zone/ZoneBubbles.java`
-   1  `main:minigame/zone/ZoneForbidden.java`
-   1  `main:minigame/zone/ZonePlayerRules.java`
-   2  `main:mixin/ExplosionImplMixin.java`
-   5  `main:mixin/TokenEntityMixin.java`
-   2  `main:mixin/ZoneBubbleExplosionMixin.java`
-   2  `main:utils/GravityPull.java`

Mixins owned that compile but must be checked against 1.21.1 targets:

`BubbleColumnBlockMixin`, `EntityBoxCostumeCollisionMixin`, `EntityBubbleColumnMixin`, `EntityRaycastTileMixin`, `EntityTokenRenameMixin`, `FireworkRocketEntityAccessor`, `LivingEntityMixin`, `MixinPistonBlock`, `PipeDismountMixin`, `PipeFallEntryMixin`, `PlayerEntityBoxCostumeMixin`, `PlayerFallMixin`, `PlayerShoulderInvoker`, `PlayerWandInteractMixin`, `ServerScoreboardMixin`, `ShapedRecipeAccessor`, `SimpleRegistryAliasMixin`, `StatusEffectMixin`, `TokenBaseDimensionsMixin`, `TokenMerchantMixin`, `TokenPawnInteractionMixin`, `TokenTurtleMixin`, `ZoneBubbleBlockEntityTickMixin`, `ZoneBubbleCrafterMixin`, `ZoneBubbleDispenserMixin`, `ZoneBubbleDragonEggMixin`, `ZoneBubbleEntityMixin`, `ZoneBubbleExperienceOrbMixin`, `ZoneBubbleFluidRandomTickMixin`, `ZoneBubbleHopperMixin`, `ZoneBubbleInteractionManagerMixin`, `ZoneBubbleItemEntityMixin`, `ZoneBubbleLivingEntityMixin`, `ZoneBubbleNeighborUpdaterMixin`, `ZoneBubblePickupMixin`, `ZoneBubblePistonHandlerMixin`, `ZoneBubblePlayerEntityMixin`, `ZoneBubbleProjectileMixin`, `ZoneBubbleRandomTickMixin`, `ZoneBubbleScreenHandlerMixin`, `ZoneBubbleServerPlayerMixin`, `ZoneBubbleServerWorldMixin`, `ZoneBubbleWorldChunkMixin`, `ZoneBubbleWorldMixin`

### G8. Gametests (110 errors, 35 files with errors, 81 files owned)

Key API changes:

- `SpawnReason.LOAD` / `SPAWN_ITEM_USE` → `create(World)` / `SPAWN_EGG`; `kill(ServerWorld)` → `kill()`; `teleport(…, boolean)` → 7-arg form; `EntityAttributes.SCALE` → `GENERIC_SCALE`.
- Recipe tests: `ServerRecipeManager` → `RecipeManager#get(Identifier)`; no `getDisplays`/`getIngredientPlacement`; `RecipeEntry#id()` is an `Identifier`.
- `getOrThrow(RegistryKeys.ENCHANTMENT)` → `getWrapperOrThrow(...)`/`get(...)`; `ItemStack#toNbt` → `encode`; `ItemCooldownManager` per `Item`; `SoundEvent#id()` → `getId()`; `onUseWithItem` asserts on `ItemActionResult`; `EquippableComponent` checks → `ArmorItem`/`Equipment`.
- Start after G4–G7 have landed their API choices (tests call into them).

Files with errors (count):

-  12  `gametest:gametest/BoxedTraderGameTests.java`
-   2  `gametest:gametest/CoreGameTests.java`
-   1  `gametest:gametest/DecisionsGameTests.java`
-   2  `gametest:gametest/DiceForgeGameTests.java`
-   1  `gametest:gametest/DiceModulesGameTests.java`
-   6  `gametest:gametest/DiceRecipeGameTests.java`
-   3  `gametest:gametest/GameplayRulesGameTests.java`
-   3  `gametest:gametest/GravityGameTests.java`
-   1  `gametest:gametest/LootAndBrewingGameTests.java`
-   2  `gametest:gametest/MiniGameControllerGameTests.java`
-   1  `gametest:gametest/MiniGamePipeGameTests.java`
-   2  `gametest:gametest/MiniGameZoneGameTests.java`
-   2  `gametest:gametest/MulaFeedbackGameTests.java`
-   1  `gametest:gametest/MulaHomeGameTests.java`
-   1  `gametest:gametest/MulaRebirthGameTests.java`
-   2  `gametest:gametest/MulaSiteCapGameTests.java`
-   5  `gametest:gametest/PipeGameTests.java`
-  13  `gametest:gametest/RecipeGameTests.java`
-   1  `gametest:gametest/ReplayTileGameTests.java`
-   7  `gametest:gametest/ShopGameTests.java`
-   1  `gametest:gametest/StarFragmentMixingGameTests.java`
-   1  `gametest:gametest/StarFragmentsBlockGameTests.java`
-   5  `gametest:gametest/StencilGameTests.java`
-   1  `gametest:gametest/TileContentsGameTests.java`
-   2  `gametest:gametest/TileFeedbackGameTests.java`
-   1  `gametest:gametest/TileStampGameTests.java`
-   3  `gametest:gametest/TileTeleportGameTests.java`
-   3  `gametest:gametest/TokenBaseGameTests.java`
-   2  `gametest:gametest/TokenPawnGameTests.java`
-   2  `gametest:gametest/TokenSpellGameTests.java`
-   3  `gametest:gametest/VillagerBlockGameTests.java`
-   8  `gametest:gametest/ZoneBubbleExploitGameTests.java`
-   4  `gametest:gametest/ZoneBubbleGameTests.java`
-   5  `gametest:gametest/ZoneBubbleLifecycleGameTests.java`
-   1  `gametest:gametest/ZoneForbiddenGameTests.java`


## Non-code impacts (data and assets)

- **Recipe JSON** (`src/main/resources/data/steveparty/recipe`, 119 hand-written files): 1.21.2+ writes ingredients
  as plain strings (`"key": {"P": "steveparty:black_plastic_block"}`, `"#tag"`, lists of strings); 1.21.1 needs objects:
  `{"item": "steveparty:black_plastic_block"}` / `{"tag": "c:…"}` (a list of those for alternatives). **109/119 files**
  use the string form and must be converted (scriptable; shaped `key`, shapeless `ingredients`, stonecutting
  `ingredient`, smithing `base/addition/template`, and the custom `steveparty:*` serializers whose codecs use
  `Ingredient.CODEC`). `result` (`{"id", "count"}`) is the same in both. Owner: G4.
- **Generated data** (`src/main/generated`, 1756 recipes, 1748 with string ingredients, plus loot tables, tags,
  advancements, block/item models): regenerate with `./gradlew runDatagen` **after** everything compiles (datagen
  does not compile yet: `StevepartyRecipeProvider` has 76 errors). Last step of the port.
- **Equipment models** (`assets/steveparty/models/equipment/bandana_*.json`, 5 files) and
  `textures/entity/equipment/humanoid/bandana_*.png`: 1.21.2+ only. On 1.21.1 armour textures live at
  `assets/<ns>/textures/models/armor/<material>_layer_1.png` (named after the `ArmorMaterial.Layer` id). Move/rename
  the PNGs, delete the equipment JSONs. Owner: G6 (BandanaItem); the GeckoLib armour items (box costume, shoes) use
  `GeoArmorRenderer` and are unaffected by the vanilla layout.
- **Item model definitions**: 1.21.3 still uses `models/item/*.json` (the `items/` folder is 1.21.4), so no change.
- **Item components in data**: no `minecraft:consumable`, `equippable`, `item_model`, `use_cooldown`, `repairable`,
  `enchantable`, `damage_resistant`, `glider` in data/assets JSON (grepped). Code that sets them on stacks/settings is
  in the API table above.
- **Enchantment** (`data/steveparty/enchantment/game_master.json`), loot tables, tags, advancements: formats are the
  same between 1.21.1 and 1.21.3 for what the mod uses (regenerated ones will follow the code anyway).
- **Pack format**: no `pack.mcmeta` in the mod (Fabric provides it), nothing to change. No shaders / post effects.
- **Gametest structures**: none (`src/gametest/resources` holds only tags), no DataVersion downgrade issue.
- **REI entrypoint**: `fabric.mod.json` `rei_common` → REI 16 name (see G4).

## Final steps (after all groups)

1. Full compile (`compileJava compileClientJava compileGametestJava`), then fix the second-wave errors.
2. Convert the hand-written recipe JSONs, then `./gradlew runDatagen` and review the `src/main/generated` diff.
3. `runGametest`, then a short in-game check of every mixin-backed feature (mixin target failures only show at load).
4. Update the wiki and the store listing screenshots/notes if behaviour changed.
