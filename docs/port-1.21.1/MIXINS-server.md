# Server mixins: 1.21.1 target review

Every `@Mixin` class of `src/main` (`steveparty.mixins.json`, `defaultRequire: 1`) checked with `javap` against the
1.21.1 mapped jar (`minecraft-merged-1.21.1-net.fabricmc.yarn.1_21_1.1.21.1+build.3-v2`), compared with 1.21.3 where a
target moved: target class, `method = "..."` (name and descriptor), `@At(target = ...)`, `@Shadow`, `@Accessor`/`@Invoker`.
A wrong target compiles but crashes at load, so each injection point was looked up in the bytecode.

Status: **OK** = unchanged and valid on 1.21.1; **FIXED** = retargeted in this port; **OTHER GROUP** = valid/invalid
target owned by another group (noted for them).

| Mixin | Target (1.21.1) | Status | Fix / note |
|---|---|---|---|
| BubbleColumnBlockMixin | `BubbleColumnBlock#scheduledTick` (`ServerWorld#getBlockState`), `#canPlaceAt` (`WorldView#getBlockState`) | OK | |
| EntityBoxCostumeCollisionMixin | `Entity#isCollidable()Z` | OK | |
| EntityBubbleColumnMixin | `Entity#onBubbleColumnCollision(Z)V`, `#onBubbleColumnSurfaceCollision(Z)V` | OK | |
| EntityRaycastTileMixin | `Entity#raycast(DFZ)HitResult` | OK | |
| EntityTokenRenameMixin | `Entity#setCustomName(Text)V` | OK | |
| ExplosionImplMixin | `Explosion#collectBlocksAndDamageEntities` (was `ExplosionImpl#getBlocksToDestroy`) | FIXED | `ExplosionImpl` is 1.21.2+. 1.21.1 `Explosion` collects the blocks into `affectedBlocks` then hurts the entities in one method: inject after the `ObjectArrayList#addAll` that fills `affectedBlocks` and remove the protected shop blocks in place (server side only, as before). Class name kept |
| FireworkRocketEntityAccessor | `FireworkRocketEntity.lifeTime` | OK | |
| LivingEntityMixin | `LivingEntity#onStatusEffectRemoved(StatusEffectInstance)V` (was `onStatusEffectsRemoved(Collection)`), `getAttackDistanceScalingFactor`, `isPushable`, `jump` | FIXED | 1.21.1 has the per-effect hook, called for expired, removed and cleared effects: same handler for one effect |
| MixinPistonBlock | `PistonBlock#isMovable` (`BlockState#hasBlockEntity`), `#move(World;BlockPos;Direction;Z)Z` (`PistonHandler#getMovedBlocks`) | OK | |
| PipeDismountMixin | `PlayerEntity#shouldDismount()Z` | OK | |
| PipeFallEntryMixin | `Entity#fall(DZBlockState;BlockPos)V` | OK | |
| PlayerEntityBoxCostumeMixin | `PlayerEntity#tick`, `#getBaseDimensions(EntityPose)` | OK | |
| PlayerFallMixin | `ServerPlayerEntity#tick` | OK | |
| PlayerShoulderInvoker | `PlayerEntity#dropShoulderEntities`, `#setShoulderEntityLeft/Right(NbtCompound)` | OK | |
| PlayerWandInteractMixin | `PlayerEntity#interact(Entity;Hand)ActionResult` | OK | |
| PreparedRecipesMixin | `PreparedRecipes#of` | OTHER GROUP (G4) | `PreparedRecipes` does not exist in 1.21.1: retarget to `RecipeManager#apply(Map, ResourceManager, Profiler)` (it builds the recipe maps itself, no `setRecipes` call) or `RecipeManager#setRecipes(Iterable)`; `RecipeEntry#id()` is an `Identifier` |
| ServerScoreboardMixin | `ServerScoreboard#updateScore`, `#onScoreRemoved`, `#onScoreHolderRemoved`, `#updateRemovedObjective` | OK | |
| ShapedRecipeAccessor | `ShapedRecipe.raw/result/group/category/showNotification` | OK | |
| ShearsItemMixin | `ShearsItem#postMine(...)Z`, `static createToolComponent()` | OTHER GROUP (G6) | targets valid; compile errors only (`Registries.createEntryLookup`, `ToolComponent.Rule.of(RegistryEntryList…)`) |
| SimpleRegistryAliasMixin | `SimpleRegistry#getEntry(Identifier)`, `#get(Identifier)`, `#containsId(Identifier)` | OK | `Registry#getOrEmpty(Identifier)` and the registry codec (`getEntry(Identifier)`) go through them |
| StatusEffectMixin | `StatusEffect` (interface only) | OK | |
| TokenBaseDimensionsMixin | `Entity#calculateDimensions` (`Entity#getDimensions(EntityPose)`), `#onTrackedDataSet`, `dataTracker` | OK | |
| TokenChickenMixin (new) | `ChickenEntity#tickMovement` (`ChickenEntity#dropItem(ItemConvertible)`) | NEW | replaces the `LivingEntity#forEachGiftedItem` override of `TokenEntityMixin` (1.21.2+). The other periodic gifts of 1.21.3's `forEachGiftedItem` (armadillo scute, panda sneeze, sniffer seeds, cat and villager gifts) come from AI code (`mobTick`, goals, brain), which a token (AI disabled) never runs in 1.21.1; the turtle scute stays in `TokenTurtleMixin` |
| TokenEntityMixin | `MobEntity#initDataTracker`, `#readCustomDataFromNbt`, `#writeCustomDataToNbt`, `#tick`, `#canBeLeashed`, `goalSelector`, `targetSelector`, `initGoals` | FIXED | `forEachGiftedItem` override removed (see TokenChickenMixin); `damage(ServerWorld, …)` → `damage(DamageSource, float)` |
| TokenMerchantMixin | `VillagerEntity#interactMob`, `WanderingTraderEntity#interactMob` | OK | |
| TokenPawnInteractionMixin | `AnimalEntity#interactMob` | OK | |
| TokenTurtleMixin | `TurtleEntity#onGrowUp` (`TurtleEntity#dropItem(ItemConvertible;I)`) | FIXED | was `dropItem(ServerWorld;ItemConvertible;I)` (1.21.2+) |
| ZoneBubbleBlockEntityTickMixin | `WorldChunk$DirectBlockEntityTickInvoker#tick()V` (`BlockEntityTicker#tick`) | OK | |
| ZoneBubbleCrafterMixin | `CrafterBlock#craft(BlockState;ServerWorld;BlockPos)V` | OK | |
| ZoneBubbleDispenserMixin | `DispenserBlock#dispense`, `DropperBlock#dispense` `(ServerWorld;BlockState;BlockPos)V` | OK | |
| ZoneBubbleDragonEggMixin | `DragonEggBlock#teleport` (`WorldBorder#contains(BlockPos)`), `@Local(ordinal = 1) BlockPos` | OK | same bytecode as 1.21.3 |
| ZoneBubbleEntityMixin | `Entity#setPos(DDD)V`, `#tryUsePortal(Portal;BlockPos)V` | OK | |
| ZoneBubbleExperienceOrbMixin | `ExperienceOrbEntity#merge(ExperienceOrbEntity)V` | OK | |
| ZoneBubbleExplosionMixin | `Explosion#collectBlocksAndDamageEntities` (was `ExplosionImpl#getBlocksToDestroy` + `#damageEntities`) | FIXED | after the `affectedBlocks` `addAll`: filter the blocks in place (`ZoneBorder.explosionBlocks`) and set `ZoneBorder.explosionAt` for the entity part; cleared at `RETURN`. Shadows `world` (`World`), `affectedBlocks`, `getPosition()`; server worlds only |
| ZoneBubbleFluidRandomTickMixin | `FluidState#onRandomTick(World;BlockPos;Random)V` | FIXED | was `(ServerWorld;…)` (1.21.2+) |
| ZoneBubbleHopperMixin | `HopperBlockEntity#extract(World;Hopper)Z`, `#extract(Inventory;ItemEntity)Z`, `#insert(World;BlockPos;HopperBlockEntity)Z` | OK | |
| ZoneBubbleInteractionManagerMixin | `ServerPlayerInteractionManager#interactBlock`, `#interactItem`, `#tryBreakBlock`, `#processBlockBreakingAction`, `player` | OK | |
| ZoneBubbleItemEntityMixin | `ItemEntity#tryMerge(ItemEntity)V`, `#cannotPickup()Z` | OK | |
| ZoneBubbleLivingEntityMixin | `LivingEntity#addStatusEffect(StatusEffectInstance;Entity)Z` | OK | |
| ZoneBubbleNeighborUpdaterMixin | `ChainRestrictedNeighborUpdater#enqueue(BlockPos;Entry)V`, `world` | OK | |
| ZoneBubblePickupMixin | `onPlayerCollision(PlayerEntity)V` of `ItemEntity`, `ExperienceOrbEntity`, `PersistentProjectileEntity` | OK | |
| ZoneBubblePistonHandlerMixin | `PistonHandler#calculatePush()Z`, `world`, `posFrom`, `pistonDirection`, `getMovedBlocks`, `getBrokenBlocks`, `getMotionDirection` | OK | |
| ZoneBubblePlayerEntityMixin | `PlayerEntity#dropItem(ItemStack;ZZ)ItemEntity` | OK | |
| ZoneBubbleProjectileMixin | `ProjectileEntity#onCollision(HitResult)V` | OK | |
| ZoneBubbleRandomTickMixin | `AbstractBlock$AbstractBlockState#randomTick(ServerWorld;BlockPos;Random)V` | OK | |
| ZoneBubbleScreenHandlerMixin | `ScreenHandler#onSlotClick(IISlotActionType;PlayerEntity)V`, `slots` | OK | |
| ZoneBubbleServerPlayerMixin | `ServerPlayerEntity#teleportTo(TeleportTarget)Entity`, `#teleport(ServerWorld;DDDSet;FF)Z`, `#teleport(ServerWorld;DDDFF)V`, `#dropItem(ItemStack;ZZ)`, `#playerTick` | FIXED | `teleportTo` returns `Entity` in 1.21.1 (was `ServerPlayerEntity`). 1.21.1 only routes a teleport to another world through `teleportTo`; a same-world one (commands) goes straight to `requestTeleport`, so the two `teleport` overloads are judged at `HEAD` too (same world, absolute moves only: a relative move is still judged where it ends). `TeleportTarget` has no `relatives()` in 1.21.1 (always absolute): `ZoneBorder.blocksTeleport` reads `pos()` |
| ZoneBubbleServerWorldMixin | `ServerWorld#tickBlock`, `#tickFluid`, `#tickEntity`, `#addEntity(Entity)Z` | OK | |
| ZoneBubbleWorldChunkMixin | `WorldChunk#setBlockState(BlockPos;BlockState;Z)BlockState` | OK | |
| ZoneBubbleWorldMixin | `World#setBlockState(BlockPos;BlockState;II)Z` | OK | |

Summary: 49 mixins + 1 new (TokenChickenMixin). 40 OK, 7 fixed (ExplosionImplMixin, LivingEntityMixin,
TokenEntityMixin, TokenTurtleMixin, ZoneBubbleExplosionMixin, ZoneBubbleFluidRandomTickMixin,
ZoneBubbleServerPlayerMixin), 2 owned by other groups (PreparedRecipesMixin: G4, target class gone; ShearsItemMixin:
G6, targets fine). `iterateVisibleBlockEntities` is not used by any server mixin (client side, G2).
