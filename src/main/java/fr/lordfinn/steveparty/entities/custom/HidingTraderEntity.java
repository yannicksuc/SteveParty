package fr.lordfinn.steveparty.entities.custom;

import fr.lordfinn.steveparty.entities.TokenBase;

import com.google.gson.JsonElement;
import com.google.gson.JsonParser;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.JsonOps;
import fr.lordfinn.steveparty.Steveparty;
import fr.lordfinn.steveparty.blocks.custom.CashRegisterBlockEntity;
import fr.lordfinn.steveparty.blocks.custom.TradingStallBlockEntity;
import fr.lordfinn.steveparty.items.ModItems;
import fr.lordfinn.steveparty.items.custom.BandanaItem;
import fr.lordfinn.steveparty.items.custom.TokenItem;
import fr.lordfinn.steveparty.persistent_state.TraderStallRegistry;
import fr.lordfinn.steveparty.persistent_state.VendorLinkPersistentState;
import fr.lordfinn.steveparty.screen_handlers.custom.CustomizableMerchantScreenHandler;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.component.DataComponentTypes;
import net.minecraft.component.type.LoreComponent;
import net.minecraft.entity.*;
import net.minecraft.entity.ai.pathing.PathNodeType;
import net.minecraft.entity.attribute.DefaultAttributeContainer;
import net.minecraft.entity.attribute.EntityAttributes;
import net.minecraft.entity.damage.DamageSource;
import net.minecraft.entity.data.DataTracker;
import net.minecraft.entity.data.TrackedData;
import net.minecraft.entity.data.TrackedDataHandlerRegistry;
import net.minecraft.entity.mob.MobEntity;
import net.minecraft.entity.passive.MerchantEntity;
import net.minecraft.entity.passive.PassiveEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.inventory.Inventory;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtElement;
import net.minecraft.screen.MerchantScreenHandler;
import net.minecraft.screen.ScreenHandler;
import net.minecraft.screen.SimpleNamedScreenHandlerFactory;
import net.minecraft.screen.slot.SlotActionType;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvent;
import net.minecraft.sound.SoundEvents;
import net.minecraft.text.Style;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.ActionResult;
import net.minecraft.util.Hand;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import net.minecraft.village.MerchantInventory;
import net.minecraft.village.TradeOffer;
import net.minecraft.village.TradeOfferList;
import net.minecraft.world.LocalDifficulty;
import net.minecraft.world.ServerWorldAccess;
import net.minecraft.world.World;
import org.jetbrains.annotations.Nullable;
import software.bernie.geckolib.animatable.GeoEntity;
import software.bernie.geckolib.animatable.instance.AnimatableInstanceCache;
import software.bernie.geckolib.animatable.instance.SingletonAnimatableInstanceCache;
import software.bernie.geckolib.animation.*;
import software.bernie.geckolib.animation.AnimationState;
import software.bernie.geckolib.util.ClientUtil;

import java.util.ArrayList;
import java.util.List;
import java.util.OptionalInt;
import java.util.UUID;

public class HidingTraderEntity extends MerchantEntity implements GeoEntity {

    private final TradeOfferList tradeOffers = new TradeOfferList();
    private VendorLinkPersistentState vendorLinkPersistentState;
    private final List<Inventory> storages = new ArrayList<>();
    private final List<TradingStallBlockEntity> tradingStalls = new ArrayList<>();
    private final List<CashRegisterBlockEntity> cashRegisters = new ArrayList<>();
    private final AnimatableInstanceCache cache = new SingletonAnimatableInstanceCache(this);

    /** The box flaps flip open one after another and the merchant pops out. Followed by IDLE_ANIM. */
    protected static final RawAnimation OPEN_ANIM = RawAnimation.begin().thenPlay("open");
    protected static final RawAnimation IDLE_ANIM = RawAnimation.begin().thenLoop("idle");
    protected static final RawAnimation CLOSED_ANIM = RawAnimation.begin().thenPlayAndHold("closed");

    private Integer optionalScreenHandlerId = null;
    /** Merchant screen of the current customer: only one player can trade with the trader at a time. */
    @Nullable
    private ScreenHandler activeScreenHandler = null;
    /** Extra reach (on top of the entity interaction range) before the customer is released. */
    private static final double CUSTOMER_EXTRA_REACH = 4.0D;
    /** Ticks between two availability checks of the offers (stock, stall lock) while a customer trades. */
    private static final int OFFER_REFRESH_INTERVAL = 10;
    private static final int OFFER_AVAILABLE = 0;
    private static final int OFFER_OUT_OF_STOCK = 1;
    private static final int OFFER_LOCKED = 2;
    /** Availability of each offer as last sent to the customer (to only resend on change). */
    private final List<Integer> sentOfferStates = new ArrayList<>();
    /** Client-side countdown (in ticks) before playing the disguise block place sound, -1 when idle. */
    private int pendingPlaceSoundTicks = -1;
    private BlockState blockState = Blocks.GOLD_BLOCK.getDefaultState();
    /** First player who linked a Shopkeeper Key to this trader (null until then). Mirrored in {@link VendorLinkPersistentState}. */
    @Nullable
    private UUID ownerUuid = null;
    /** Ticks between two synchronizations of the owner with the persistent link state. */
    private static final int OWNER_SYNC_INTERVAL = 20;
    private static final TrackedData<String> BLOCK_STATE = DataTracker.registerData(HidingTraderEntity.class, TrackedDataHandlerRegistry.STRING);
    /** Number of bandana colours (textures hiding_trader_<colour>.png, see the art sources). */
    public static final int BANDANA_COLORS = 5;
    public static final String BANDANA_COLOR_NBT = "BandanaColor";
    /** Bandana colour 0-4 (teal, blue, pink, orange, yellow), -1 until picked. */
    private static final TrackedData<Integer> BANDANA_COLOR = DataTracker.registerData(HidingTraderEntity.class, TrackedDataHandlerRegistry.INTEGER);
    public static final String HAS_BANDANA_NBT = "HasBandana";
    /** False once his bandana was stolen with shears (bald), until a player gives him one back. */
    private static final TrackedData<Boolean> HAS_BANDANA = DataTracker.registerData(HidingTraderEntity.class, TrackedDataHandlerRegistry.BOOLEAN);
    public static final String HIDE_START_NBT = "HideStart";
    /** World time from which he stays closed for {@link #THEFT_HIDE_TICKS} (after a theft), Long.MIN_VALUE if none. */
    private static final TrackedData<Long> HIDE_START = DataTracker.registerData(HidingTraderEntity.class, TrackedDataHandlerRegistry.LONG);
    /** After a theft: closed for 20 s, ignoring players. */
    public static final int THEFT_HIDE_TICKS = 400;
    /** fun_shocked (1.3 s) plays before he hides. */
    public static final int SHOCK_TICKS = 26;
    /** A player within this range (not a spectator) makes him come out. */
    public static final double OPEN_RANGE = 15;
    /** Unassigned merchants wander at most this far from their home. */
    public static final double WANDER_RADIUS = 16;
    public static final String HOME_NBT = "Home";
    /** Hidden (closed as a block), recomputed once per tick on both sides; read by animations, sounds, goals. */
    private boolean hidden = true;
    /** Server: where he wanders around (first position), null until his first tick. */
    @Nullable
    private BlockPos home = null;
    /** Server: linked to a shop (owner, Shopkeeper Key links or trading stall), refreshed with the owner sync. */
    private boolean assigned = true;
    /** Server: snapped to the block grid since he last closed. */
    private boolean gridAligned = false;
    /** Server: age until which a little animation is playing (no wandering meanwhile). */
    private int funBusyUntil = 0;

    public HidingTraderEntity(EntityType<? extends MerchantEntity> type, World world) {
        super(type, world);
        // Wandering (unassigned merchants): never into water or fire, lava is already forbidden
        this.setPathfindingPenalty(PathNodeType.WATER, -1.0F);
        this.setPathfindingPenalty(PathNodeType.WATER_BORDER, 16.0F);
        this.setPathfindingPenalty(PathNodeType.DANGER_FIRE, -1.0F);
        this.setPathfindingPenalty(PathNodeType.DAMAGE_FIRE, -1.0F);
        // Goals are already registered by MobEntity's constructor (server side).
        // Inventories/offers are resolved lazily in fillRecipes(): the UUID is not final yet at construction time.
    }

    @Nullable
    private VendorLinkPersistentState getVendorLinkState() {
        if (vendorLinkPersistentState == null && this.getWorld() instanceof ServerWorld serverWorld) {
            vendorLinkPersistentState = VendorLinkPersistentState.get(serverWorld.getServer());
        }
        return vendorLinkPersistentState;
    }

    /**
     * Keeps the owner saved on the entity and the one of the persistent link state identical: the state wins
     * (it may have been claimed while the trader was not loaded), else the entity's owner is written to it.
     */
    private void syncOwner() {
        VendorLinkPersistentState linkState = getVendorLinkState();
        if (linkState == null) return;
        UUID stateOwner = linkState.getOwner(this.getUuid());
        if (stateOwner != null) {
            ownerUuid = stateOwner;
        } else if (ownerUuid != null) {
            linkState.setOwner(this.getUuid(), ownerUuid);
        }
    }

    /** @return the player owning this trader, or null if no player linked a Shopkeeper Key to it yet. */
    @Nullable
    public UUID getOwnerUuid() {
        if (!this.getWorld().isClient) syncOwner();
        return ownerUuid;
    }

    /**
     * Ownership check done when a player links a Shopkeeper Key to this trader: a trader without owner (new, or
     * created before ownership existed) is claimed by the player.
     *
     * @return true if the player is (now) the owner
     */
    public boolean claimOrCheckOwner(PlayerEntity player) {
        VendorLinkPersistentState linkState = getVendorLinkState();
        if (linkState == null) return false;
        syncOwner();
        boolean owner = linkState.claimOrCheckOwner(this.getUuid(), player.getUuid());
        if (owner) ownerUuid = player.getUuid();
        return owner;
    }

    @Override
    protected void initDataTracker(DataTracker.Builder builder) {
        super.initDataTracker(builder);
        builder.add(BLOCK_STATE, "");
        builder.add(BANDANA_COLOR, -1);
        builder.add(HAS_BANDANA, true);
        builder.add(HIDE_START, Long.MIN_VALUE);
    }

    @Override
    public void onTrackedDataSet(TrackedData<?> data) {
        super.onTrackedDataSet(data);
        if (BLOCK_STATE.equals(data)) {
            String blockStateJson = this.dataTracker.get(BLOCK_STATE);
            JsonElement jsonElement = JsonParser.parseString(blockStateJson);
            BlockState.CODEC.parse(JsonOps.INSTANCE, jsonElement).resultOrPartial(HidingTraderEntity::printWarnForFailDecodeBlockState)
                    .ifPresent(decodedBlockState -> this.blockState = decodedBlockState);
        }
    }

    public static DefaultAttributeContainer.Builder setAttributes() {
        return LivingEntity.createLivingAttributes()
                .add(EntityAttributes.MAX_HEALTH, 20.0D)
                .add(EntityAttributes.ATTACK_DAMAGE, 0.0D)
                .add(EntityAttributes.ATTACK_SPEED, 0.0D)
                .add(EntityAttributes.KNOCKBACK_RESISTANCE, 0.3D)
                .add(EntityAttributes.MOVEMENT_SPEED, 0.3D)
                .add(EntityAttributes.FOLLOW_RANGE, 16.0D);
    }

    @Override
    protected void initGoals() {
        this.goalSelector.add(1, new HidingTraderGoals.WanderNearHomeGoal(this, 0.55, 60));
        this.goalSelector.add(2, new HidingTraderGoals.LookAtAttentionTargetGoal(this, (float) OPEN_RANGE));
    }

    @Override
    public boolean isPersistent() {
        return true;
    }

    @Override
    public void sendOffers(PlayerEntity player, Text name, int levelProgress) {
        OptionalInt opened = player.openHandledScreen(new SimpleNamedScreenHandlerFactory(
                        (syncId, playerInventory, playerx) -> new CustomizableMerchantScreenHandler(syncId, playerInventory, this) {
                            @Override
                            public void onSlotClick(int slotIndex, int button, SlotActionType actionType, PlayerEntity clicker) {
                                // Re-check the stock right before the result can be taken (the stock may have
                                // been removed since the offer was displayed).
                                if (slotIndex == OUTPUT_ID && !HidingTraderEntity.this.canTakeCurrentTrade(this)) {
                                    return;
                                }
                                super.onSlotClick(slotIndex, button, actionType, clicker);
                            }

                            @Override
                            public boolean canUse(PlayerEntity player) {
                                // Closes the screen when the customer dies, leaves or goes out of reach
                                return super.canUse(player) && HidingTraderEntity.this.isValidCustomer(player);
                            }
                        }, name));
        if (opened.isEmpty()) {
            releaseCustomer(player);
            return;
        }
        optionalScreenHandlerId = opened.getAsInt();
        activeScreenHandler = player.currentScreenHandler;
        updateTradesToClient(player, levelProgress);
    }

    /** True while the player can keep the trader's screen open (alive, same world, within reach). */
    private boolean isValidCustomer(@Nullable PlayerEntity player) {
        return player != null && player.isAlive() && !player.isRemoved()
                && !(player instanceof ServerPlayerEntity serverPlayer && serverPlayer.isDisconnected())
                && player.getWorld() == this.getWorld() && this.isAlive()
                && player.canInteractWithEntity(this, CUSTOMER_EXTRA_REACH);
    }

    /** True if another player currently trades with this trader. */
    private boolean isBusyFor(PlayerEntity player) {
        PlayerEntity customer = getCustomer();
        if (customer == null || customer == player) return false;
        if (isValidCustomer(customer) && customer.currentScreenHandler == activeScreenHandler) return true;
        releaseCustomer(customer);
        return false;
    }

    /** Frees the trader for other players, closing the customer's merchant screen if still open. */
    private void releaseCustomer(@Nullable PlayerEntity customer) {
        ScreenHandler handler = activeScreenHandler;
        activeScreenHandler = null;
        this.setCustomer(null);
        if (customer instanceof ServerPlayerEntity serverPlayer && handler != null && serverPlayer.currentScreenHandler == handler) {
            serverPlayer.closeHandledScreen();
        }
    }

    /**
     * Server-side check done when a player clicks the trade result slot.
     * If the linked storages no longer hold the sold item, the offer is disabled and the result slot cleared.
     */
    private boolean canTakeCurrentTrade(MerchantScreenHandler handler) {
        if (this.getWorld().isClient) return true;
        if (!(handler.getSlot(0).inventory instanceof MerchantInventory merchantInventory)) return true;
        TradeOffer offer = merchantInventory.getTradeOffer();
        if (offer == null || merchantInventory.getStack(2).isEmpty()) return true;
        if (getOfferState(offer) == OFFER_AVAILABLE) return true;
        refreshOfferAvailability();
        offer.disable();
        merchantInventory.updateOffers();
        PlayerEntity customer = getCustomer();
        if (customer != null) updateTradesToClient(customer, 0);
        handler.syncState();
        return false;
    }

    private void updateTradesToClient(PlayerEntity player, int levelProgress) {
        if (player == null) return;
        if (optionalScreenHandlerId != null) {
            TradeOfferList tradeOfferList = this.getOffers();
            if (!tradeOfferList.isEmpty()) {
                this.setCustomer(player);
                player.sendTradeOffers(optionalScreenHandlerId, createClientOffers(tradeOfferList), levelProgress, this.getExperience(), this.isLeveledMerchant(), this.canRefreshTrades());
            }
        }
    }

    /**
     * Offers as shown to the client: same order and count as the server list (trade selection is index based),
     * but an offer of a locked stall (ONE_SALE_PER_SIGNAL waiting for a pulse) is shown disabled with a hint
     * added to the tooltip of its sold item. The server offers are never modified.
     */
    private TradeOfferList createClientOffers(TradeOfferList offers) {
        TradeOfferList clientOffers = new TradeOfferList();
        sentOfferStates.clear();
        for (TradeOffer offer : offers) {
            int state = getOfferState(offer);
            sentOfferStates.add(state);
            if (state != OFFER_LOCKED) {
                clientOffers.add(offer);
                continue;
            }
            ItemStack hintedSellItem = offer.getSellItem().copy();
            hintedSellItem.apply(DataComponentTypes.LORE, LoreComponent.DEFAULT, lore -> lore.with(
                    Text.translatableWithFallback("gui.steveparty.trading_stall.credit.waiting", "Waiting for a redstone signal")
                            .setStyle(Style.EMPTY.withColor(Formatting.RED).withItalic(false))));
            clientOffers.add(new TradeOffer(offer.getFirstBuyItem(), offer.getSecondBuyItem(), hintedSellItem,
                    offer.getMaxUses(), offer.getMaxUses(), offer.getMerchantExperience(), offer.getPriceMultiplier()));
        }
        return clientOffers;
    }

    /** A dead trader releases its shop: owner and links are forgotten, the blocks can be claimed again. */
    @Override
    public void onDeath(DamageSource damageSource) {
        super.onDeath(damageSource);
        if (this.getWorld() instanceof ServerWorld serverWorld) {
            VendorLinkPersistentState.get(serverWorld.getServer()).forgetVendor(this.getUuid());
        }
    }

    @Override
    public void onRemoved() {
        TraderStallRegistry.unlinkTraderFromAllStalls(this.getUuid());
        super.onRemoved();
    }

    @Override
    public boolean isLeveledMerchant() {
        return false;
    }

    private void updateInventories() {
        World world = this.getWorld();
        tradingStalls.clear();
        cashRegisters.clear();
        storages.clear();
        VendorLinkPersistentState linkState = getVendorLinkState();
        if (linkState == null) return;
        // Only the blocks linked in the trader's own dimension
        linkState.getVendorLinks(this.getUuid(), world.getRegistryKey()).forEach(pos -> {
            BlockEntity blockEntity = world.getBlockEntity(pos);
            if (blockEntity instanceof Inventory) {
                if (blockEntity instanceof TradingStallBlockEntity)
                    tradingStalls.add((TradingStallBlockEntity) blockEntity);
                else if (blockEntity instanceof CashRegisterBlockEntity)
                    cashRegisters.add((CashRegisterBlockEntity) blockEntity);
                else
                    storages.add((Inventory) blockEntity);
            }
        });
    }

    public void updateTradeOffers() {
        tradeOffers.clear();
        for (Inventory inventory : tradingStalls) {
            if (inventory instanceof TradingStallBlockEntity stall) {
                tradeOffers.addAll(stall.getTradeOffers());
            }
        }
        refreshOfferAvailability();
    }

    /** Availability of an offer: locked stall first, then missing stock. */
    private int getOfferState(TradeOffer offer) {
        if (offer instanceof TradingStallBlockEntity.ExactTradeOffer exactOffer && !exactOffer.isSaleAllowed()) {
            return OFFER_LOCKED;
        }
        return isStockAvailable(offer.getSellItem()) ? OFFER_AVAILABLE : OFFER_OUT_OF_STOCK;
    }

    /**
     * Enables the offers that can be bought (stock present, stall unlocked) and disables the others. Offers are
     * never exhausted by their uses: they are re-enabled as soon as the stock is back or the stall is unlocked.
     *
     * @return true if the availability differs from what was last sent to the customer
     */
    private boolean refreshOfferAvailability() {
        List<Integer> states = new ArrayList<>(tradeOffers.size());
        for (TradeOffer offer : tradeOffers) {
            int state = getOfferState(offer);
            states.add(state);
            if (state == OFFER_AVAILABLE) {
                if (offer.isDisabled() || offer.getUses() > 0) offer.resetUses();
            } else if (!offer.isDisabled()) {
                offer.disable();
            }
        }
        return !states.equals(sentOfferStates);
    }

    private boolean isStockAvailable(ItemStack itemStack) {
        if (itemStack == null || itemStack.isEmpty()) return true;
        int requiredAmount = itemStack.getCount();
        int stockAmount = 0;
        for (Inventory inventory : storages) {
            if (isRemovedStorage(inventory)) continue;
            for (int slot = 0; slot < inventory.size(); slot++) {
                ItemStack stack = inventory.getStack(slot);
                if (ItemStack.areItemsAndComponentsEqual(stack, itemStack)) {
                    stockAmount += stack.getCount();
                    if (stockAmount >= requiredAmount) {
                        return true;
                    }
                }
            }
        }
        return false;
    }

    private static boolean isRemovedStorage(Inventory inventory) {
        return inventory instanceof BlockEntity blockEntity && blockEntity.isRemoved();
    }

    private void consumeStock(ItemStack itemStack) {
        if (itemStack == null || itemStack.isEmpty()) return;
        int remainingAmount = itemStack.getCount();
        for (Inventory inventory : storages) {
            if (isRemovedStorage(inventory)) continue;
            boolean modified = false;
            for (int slot = 0; slot < inventory.size() && remainingAmount > 0; slot++) {
                ItemStack stack = inventory.getStack(slot);
                if (ItemStack.areItemsAndComponentsEqual(stack, itemStack)) {
                    int consumed = Math.min(stack.getCount(), remainingAmount);
                    stack.decrement(consumed);
                    remainingAmount -= consumed;
                    modified = true;
                }
            }
            if (modified) {
                inventory.markDirty();
            }
            if (remainingAmount <= 0) {
                return;
            }
        }
    }

    public void distributeItemStackAcrossCashRegisters(ItemStack stack) {
        if (stack == null || stack.isEmpty()) {
            return;
        }
        for (CashRegisterBlockEntity inventory : cashRegisters) {
            if (stack.isEmpty()) {
                break;
            }
            if (inventory.isRemoved()) continue;
            boolean modified = false;
            for (int slot = 0; slot < inventory.size() && !stack.isEmpty(); slot++) {
                ItemStack targetStack = inventory.getStack(slot);
                if (targetStack.isEmpty()) {
                    inventory.setStack(slot, stack.split(Math.min(stack.getCount(), stack.getMaxCount())));
                    modified = true;
                } else if (ItemStack.areItemsAndComponentsEqual(stack, targetStack)) {
                    int transferableAmount = Math.min(stack.getCount(), targetStack.getMaxCount() - targetStack.getCount());
                    if (transferableAmount > 0) {
                        targetStack.increment(transferableAmount);
                        stack.decrement(transferableAmount);
                        modified = true;
                    }
                }
            }
            if (modified) {
                inventory.markDirty();
            }
        }
        // Cash registers full or missing: never destroy the payment, drop it at the trader instead
        if (!stack.isEmpty() && this.getWorld() instanceof ServerWorld serverWorld) {
            this.dropStack(serverWorld, stack.copyAndEmpty());
        }
    }

    @Override
    public boolean canBeLeashed() {
        return true;
    }

    @Override
    protected ActionResult interactMob(PlayerEntity player, Hand hand) {
        // A board token is a still pawn: no trade, no bandana (the items acting on entities still work)
        if (TokenBase.isToken(this)) return ActionResult.PASS;
        if (player.getMainHandStack().getItem() instanceof TokenItem || player.getStackInHand(hand).isOf(Items.LEAD) || player.getStackInHand(hand).isOf(ModItems.SHOPKEEPER_KEY)) {
            return ActionResult.PASS;
        }
        ItemStack held = player.getStackInHand(hand);
        if (held.isOf(Items.SHEARS) && hasBandana() && !hidden) {
            if (!this.getWorld().isClient) stealBandana(player, held, hand);
            return ActionResult.SUCCESS;
        }
        if (held.isOf(ModItems.BANDANA) && !hasBandana() && !hidden) {
            if (!this.getWorld().isClient) giveBandana(player, held);
            return ActionResult.SUCCESS;
        }
        if (!this.getWorld().isClient && player instanceof ServerPlayerEntity) {
            // One customer at a time: never rebuild the offers under another player's screen
            if (isBusyFor(player)) {
                player.sendMessage(Text.translatableWithFallback("message.steveparty.trader.busy", "The trader is busy."), true);
                return ActionResult.SUCCESS;
            }
            this.fillRecipes();
            if (storages.isEmpty() || tradeOffers.isEmpty())
                return ActionResult.PASS;
            this.setCustomer(player);
            this.sendOffers(player, this.getDisplayName(), 0);
            if (isAttentionTarget(player)) playHappyGesture();
            return ActionResult.SUCCESS;
        }
        return super.interactMob(player, hand);
    }

    @Override
    protected void updatePassengerPosition(Entity passenger, PositionUpdater positionUpdater) {
        if (this.hasPassenger(passenger)) {
            passenger.setPos(this.getX(), this.getY(), this.getZ());
            passenger.rotate(this.getYaw(), this.getPitch());
            passenger.setHeadYaw(this.getYaw());
            passenger.updateTrackedHeadRotation(this.getYaw(), 0);
        }
    }

    @Override
    public Box getBoundingBox(EntityPose pose) {
        if (this.hasPassengers()) {
            Entity passenger = this.getFirstPassenger();
            if (passenger == null) return super.getHitbox();
            return passenger.getBoundingBox().expand(0.2);
        }
        return super.getBoundingBox();
    }

    @Override
    protected Box getHitbox() {
        return super.getBoundingBox();
    }

    @Override
    protected Box getAttackBox() {
        return super.getBoundingBox();
    }

    @Override
    public void readNbt(NbtCompound nbt) {
        super.readNbt(nbt);
        if (nbt.contains("isInvisible")) {
            this.setInvisible(nbt.getBoolean("isInvisible"));
        }
        if (nbt.contains("blockState")) {
            String blockStateJson = nbt.getString("blockState");
            JsonElement jsonElement = JsonParser.parseString(blockStateJson);
            BlockState.CODEC.parse(JsonOps.INSTANCE, jsonElement).resultOrPartial(HidingTraderEntity::printWarnForFailDecodeBlockState)
                    .ifPresent(this::setBlockState);
        }
        if (nbt.containsUuid("ShopOwner")) {
            ownerUuid = nbt.getUuid("ShopOwner");
        }
        if (nbt.contains(HAS_BANDANA_NBT, NbtElement.BYTE_TYPE)) {
            setHasBandana(nbt.getBoolean(HAS_BANDANA_NBT));
        }
        if (nbt.contains(HIDE_START_NBT, NbtElement.LONG_TYPE)) {
            this.dataTracker.set(HIDE_START, nbt.getLong(HIDE_START_NBT));
        }
        if (nbt.contains(HOME_NBT, NbtElement.LONG_TYPE)) {
            home = BlockPos.fromLong(nbt.getLong(HOME_NBT));
        }
        if (nbt.contains(BANDANA_COLOR_NBT, NbtElement.NUMBER_TYPE)) {
            setBandanaColor(nbt.getInt(BANDANA_COLOR_NBT));
        } else if (getBandanaColor() < 0) {
            // Trader saved before bandanas existed, or summoned with NBT but without a colour
            rollBandanaColor();
        }
    }

    private static void printWarnForFailDecodeBlockState(String error) {
        // Removed logger statement
    }

    @Override
    public NbtCompound writeNbt(NbtCompound nbt) {
        nbt.putBoolean("isInvisible", this.isInvisible());
        if (blockState != null) {
            DataResult<JsonElement> result = BlockState.CODEC.encodeStart(JsonOps.INSTANCE, blockState);
            result.resultOrPartial(HidingTraderEntity::printWarnForFailDecodeBlockState).ifPresent(jsonElement -> nbt.putString("blockState", jsonElement.toString()));
        }
        if (ownerUuid != null) {
            nbt.putUuid("ShopOwner", ownerUuid);
        }
        if (getBandanaColor() >= 0) {
            nbt.putInt(BANDANA_COLOR_NBT, getBandanaColor());
        }
        nbt.putBoolean(HAS_BANDANA_NBT, hasBandana());
        long hideStart = this.dataTracker.get(HIDE_START);
        if (hideStart != Long.MIN_VALUE) {
            nbt.putLong(HIDE_START_NBT, hideStart);
        }
        if (home != null) {
            nbt.putLong(HOME_NBT, home.asLong());
        }
        return super.writeNbt(nbt);
    }

    private void disableAllTrades() {
        tradeOffers.forEach(TradeOffer::disable);
    }

    private void enableAllTrades() {
        tradeOffers.forEach(TradeOffer::resetUses);
    }

    @Override
    public TradeOfferList getOffers() {
        return tradeOffers;
    }

    @Override
    public void setOffersFromServer(TradeOfferList offers) {
        tradeOffers.clear();
        tradeOffers.addAll(offers);
    }

    @Override
    public void trade(TradeOffer offer) {
        // Last line of defense: the result slot click is already guarded, but never keep an offer
        // enabled once its stock is gone or its stall is locked.
        if (!this.getWorld().isClient && getOfferState(offer) != OFFER_AVAILABLE) {
            offer.disable();
            Steveparty.LOGGER.warn("Trader {} completed a trade without stock or on a locked stall for {}", this.getUuid(), offer.getSellItem());
        }
        super.trade(offer);
    }

    @Override
    protected void afterUsing(TradeOffer offer) {
        consumeStock(offer.getSellItem());
        // Deposit the stacks the player really paid with (exact components); fall back to the offer's price
        List<ItemStack> payment = offer instanceof TradingStallBlockEntity.ExactTradeOffer exactOffer
                ? exactOffer.takeLastPayment() : List.of();
        if (payment.isEmpty()) {
            distributeItemStackAcrossCashRegisters(offer.getDisplayedFirstBuyItem().copy());
            if (!offer.getDisplayedSecondBuyItem().isEmpty())
                distributeItemStackAcrossCashRegisters(offer.getDisplayedSecondBuyItem().copy());
        } else {
            payment.forEach(this::distributeItemStackAcrossCashRegisters);
        }
        // ONE_SALE_PER_SIGNAL: the purchase consumes the stall credit (locked again until the next pulse)
        if (offer instanceof TradingStallBlockEntity.ExactTradeOffer exactOffer && exactOffer.getStall() != null) {
            exactOffer.getStall().onSale();
        }
        refreshOfferAvailability();
        updateTradesToClient(getCustomer(), 0);
        triggerCashRegisters();
        if (hasPassengers() && getFirstPassenger() instanceof MobEntity passenger) {
            boolean silentStatus = passenger.isSilent();
            passenger.setSilent(false);
            passenger.playAmbientSound();
            passenger.setSilent(silentStatus);
        } else
            this.playSound(SoundEvents.ENTITY_VILLAGER_TRADE, 1.0F, this.getSoundPitch());
    }

    private void triggerCashRegisters() {
        cashRegisters.forEach(CashRegisterBlockEntity::trigger);
    }

    @Override
    public int getExperience() {
        return 0;
    }

    @Override
    public @Nullable PassiveEntity createChild(ServerWorld world, PassiveEntity entity) {
        return null;
    }

    private boolean lastHidingState = false;

    // Little random animations, triggered by the server (see tickFunAnimations): while the merchant is out, and
    // rare hints that someone lives inside while it is hidden. They all start and end on the idle / closed pose.
    private static final String IDLE_CONTROLLER = "Idle";
    private static final String STARE_CONTROLLER = "Stare";
    private static final String[] OPEN_FUN_ANIMS = {"fun_peek", "fun_coucou", "fun_shimmy", "fun_tap", "fun_yawn", "fun_sneeze", "fun_wave",
            "fun_laugh", "fun_balance", "fun_bonk"};
    /** Played when his bandana is stolen, just before he hides. */
    private static final String SHOCKED_ANIM = "fun_shocked";
    /** Played when a player gives him a bandana back. */
    private static final String GIVE_BACK_ANIM = "fun_laugh";
    /** Sound keyframe effect of fun_shocked's angry puff. */
    private static final String ANGRY_SOUND_KEYFRAME = "angry";
    /**
     * Client: walking (waddle animation) or standing (idle), from the smoothed limb speed with hysteresis so tiny speeds
     * never make him flicker between the two: starts after 2 ticks above 0.08, stops after 5 ticks below 0.03.
     */
    private boolean walking = false;
    private int walkSwitchTicks = 0;
    /** Box waddle while an unassigned merchant walks around. */
    protected static final RawAnimation WALK_ANIM = RawAnimation.begin().thenLoop("walk");
    /** Upper bound of a little animation's length, in ticks (no wandering meanwhile). */
    private static final int FUN_BUSY_TICKS = 75;
    private static final String BONK_ANIM = "fun_bonk";
    /** Sound keyframe effect of fun_laugh (the other sound keyframes are named "Sound"). */
    private static final String LAUGH_SOUND_KEYFRAME = "laugh";
    /** fun_bonk: stars circle the dazed head (after the squish and the rebound): from 23 ticks after the trigger, for 22 ticks. */
    private static final int BONK_STARS_DELAY = 23, BONK_STARS_TICKS = 22;
    private int bonkStarsTick = -1;
    /** Played when a customer opens the trade screen. */
    private static final String HAPPY_ANIM = "fun_happy";
    private static final String[] HIDDEN_FUN_ANIMS = {"hidden_peek", "hidden_hop", "hidden_breath"};
    /** The closed box at rest, after a hidden animation (without replaying the closing). */
    protected static final RawAnimation HIDDEN_ANIM = RawAnimation.begin().thenPlayAndHold("hidden");
    /** Ticks after the merchant came out / hid before a random animation may play (open is 1.6 s, closed 1.08 s). */
    private static final int FUN_SETTLE_TICKS = 60;
    private static final int OPEN_FUN_MIN_TICKS = 120, OPEN_FUN_RANGE_TICKS = 181;       // every 6-15 s
    private static final int HIDDEN_FUN_MIN_TICKS = 600, HIDDEN_FUN_RANGE_TICKS = 1201;  // every 30-90 s
    private static final double HIDDEN_FUN_PLAYER_RANGE = 24;
    /** Server side: when the hidden state last changed, next random animation. */
    private int hidingChangedAge = 0;
    private int nextFunAge = HIDDEN_FUN_MIN_TICKS;
    @Nullable
    private String lastFunAnim = null;

    @Override
    public void registerControllers(AnimatableManager.ControllerRegistrar controllers) {
        AnimationController<HidingTraderEntity> idle = new AnimationController<>(this, IDLE_CONTROLLER, 5, this::idleAnimController)
                .receiveTriggeredAnimations();
        for (String anim : OPEN_FUN_ANIMS) idle.triggerableAnim(anim, RawAnimation.begin().thenPlay(anim));
        idle.triggerableAnim(HAPPY_ANIM, RawAnimation.begin().thenPlay(HAPPY_ANIM));
        idle.triggerableAnim(SHOCKED_ANIM, RawAnimation.begin().thenPlay(SHOCKED_ANIM));
        AnimationController<HidingTraderEntity> stare = new AnimationController<>(this, STARE_CONTROLLER, 2, this::closedAnimController)
                .receiveTriggeredAnimations();
        for (String anim : HIDDEN_FUN_ANIMS) stare.triggerableAnim(anim, RawAnimation.begin().thenPlay(anim));
        controllers.add(idle
                .setSoundKeyframeHandler(context -> {
                    if (this.getWorld() == null) return;
                    if (LAUGH_SOUND_KEYFRAME.equals(context.getKeyframeData().getSound())) {
                        // fun_laugh: a pitched villager "hah!"
                        ClientUtil.getLevel().playSound(ClientUtil.getClientPlayer(), this.getBlockPos(), SoundEvents.ENTITY_VILLAGER_CELEBRATE,
                                SoundCategory.NEUTRAL, 0.6F, 1.15F + this.random.nextFloat() * 0.2F);
                        return;
                    }
                    if (ANGRY_SOUND_KEYFRAME.equals(context.getKeyframeData().getSound())) {
                        // fun_shocked: an offended villager "hmph"
                        ClientUtil.getLevel().playSound(ClientUtil.getClientPlayer(), this.getBlockPos(), SoundEvents.ENTITY_VILLAGER_NO,
                                SoundCategory.NEUTRAL, 0.7F, 1.25F);
                        return;
                    }
                    if (!lastHidingState)
                        ClientUtil.getLevel().playSound(ClientUtil.getClientPlayer(), this.getBlockPos(), SoundEvents.ENTITY_PUFFER_FISH_BLOW_UP, SoundCategory.NEUTRAL, 0.5F, 1.5F);
                    lastHidingState = true;
                }));
        controllers.add(stare
                .setSoundKeyframeHandler(context -> {
                    if (this.getWorld() == null) return;
                    lastHidingState = false;
                    ClientUtil.getLevel().playSound(ClientUtil.getClientPlayer(), this.getBlockPos(), SoundEvents.ENTITY_PUFFER_FISH_BLOW_OUT, SoundCategory.NEUTRAL, 0.5F, 1.5F);
                    // Place sound played 10 ticks later from tick(), on the client thread
                    pendingPlaceSoundTicks = 10;
                }));
    }

    @Override
    public void tick() {
        super.tick();
        boolean nowHidden = computeHiding();
        if (nowHidden != hidden) {
            hidden = nowHidden;
            if (!this.getWorld().isClient) onHiddenChanged(nowHidden);
        }
        if (!this.getWorld().isClient && hidden && !gridAligned) {
            // Closed (just now, or already when loaded): line the disguise up with the block grid
            snapToGrid();
            gridAligned = true;
        }
        if (!this.getWorld().isClient && home == null) {
            home = this.getBlockPos();
        }
        if (!this.getWorld().isClient && getBandanaColor() < 0) {
            // Spawned from code without initialize() nor NBT (e.g. the villager block fall)
            rollBandanaColor();
        }
        if (!this.getWorld().isClient && this.age % OWNER_SYNC_INTERVAL == 0) {
            syncOwner();
            refreshAssigned();
        }
        if (!this.getWorld().isClient && !TokenBase.isToken(this)) {
            tickFunAnimations();
        }
        if (!this.getWorld().isClient && this.hasCustomer()) {
            PlayerEntity customer = this.getCustomer();
            if (!isValidCustomer(customer) || customer.currentScreenHandler != activeScreenHandler) {
                // Screen closed, customer dead/disconnected/gone to another dimension or out of reach
                releaseCustomer(customer);
            } else if (this.age % OFFER_REFRESH_INTERVAL == 0 && refreshOfferAvailability()) {
                // Stock back/missing or stall (un)locked by redstone while the screen is open
                if (customer.currentScreenHandler instanceof MerchantScreenHandler handler
                        && handler.getSlot(0).inventory instanceof MerchantInventory merchantInventory) {
                    merchantInventory.updateOffers();
                    handler.sendContentUpdates();
                }
                updateTradesToClient(customer, 0);
            }
        }
        if (this.getWorld().isClient) {
            float limbSpeed = this.limbAnimator.getSpeed();
            boolean switching = walking ? limbSpeed < 0.03F : limbSpeed > 0.08F;
            walkSwitchTicks = switching ? walkSwitchTicks + 1 : 0;
            if (walkSwitchTicks >= (walking ? 5 : 2)) {
                walking = !walking;
                walkSwitchTicks = 0;
            }
        }
        if (this.getWorld().isClient && pendingPlaceSoundTicks >= 0) {
            if (pendingPlaceSoundTicks-- == 0) {
                Vec3d soundPos = this.getBlockPos().toCenterPos();
                this.getWorld().playSound(soundPos.x, soundPos.y, soundPos.z, this.blockState.getSoundGroup().getPlaceSound(), SoundCategory.NEUTRAL, 1F, 1.0F, false);
            }
        }
    }

    /**
     * Server side: now and then, a random little animation. While the merchant is out (not during "open", nor while a
     * customer has the trade screen open) every 6-15 s; while it is hidden, rarely (every 30-90 s) and only when a
     * player is close enough to notice. Random per trader, so they never play in sync. Cheap: the hiding state is
     * sampled every 10 ticks, the rest is a counter.
     */
    private void tickFunAnimations() {
        if (bonkStarsTick >= 0) tickBonkStars();
        if (this.age < nextFunAge) return;
        boolean settled = this.age - hidingChangedAge >= FUN_SETTLE_TICKS;
        if (!hidden) {
            if (!settled || this.hasCustomer() || !this.getNavigation().isIdle()) {
                nextFunAge = this.age + 20;
                return;
            }
            String anim = pickFunAnim(OPEN_FUN_ANIMS);
            triggerAnim(IDLE_CONTROLLER, anim);
            funBusyUntil = this.age + FUN_BUSY_TICKS;
            if (BONK_ANIM.equals(anim)) bonkStarsTick = 0;
            nextFunAge = this.age + OPEN_FUN_MIN_TICKS + this.random.nextInt(OPEN_FUN_RANGE_TICKS);
        } else {
            // Only when someone who might notice is around (players with a Bandana don't count: he ignores them)
            if (!settled || findAttentionTarget(HIDDEN_FUN_PLAYER_RANGE) == null) {
                nextFunAge = this.age + 40;
                return;
            }
            triggerAnim(STARE_CONTROLLER, pickFunAnim(HIDDEN_FUN_ANIMS));
            nextFunAge = this.age + HIDDEN_FUN_MIN_TICKS + this.random.nextInt(HIDDEN_FUN_RANGE_TICKS);
        }
    }

    /** fun_bonk: a few sparkles circling the dazed head (every other tick, 2 opposite ones). */
    private void tickBonkStars() {
        int t = bonkStarsTick++ - BONK_STARS_DELAY;
        if (t >= BONK_STARS_TICKS || hidden) {
            bonkStarsTick = -1;
            return;
        }
        if (t < 0 || t % 2 != 0 || !(this.getWorld() instanceof ServerWorld serverWorld)) return;
        double angle = t * 0.45;
        double headY = this.getY() + 1.75;
        for (int i = 0; i < 2; i++) {
            double a = angle + i * Math.PI;
            serverWorld.spawnParticles(ParticleTypes.WAX_OFF, this.getX() + Math.cos(a) * 0.38, headY, this.getZ() + Math.sin(a) * 0.38,
                    1, 0, 0, 0, 0);
        }
    }

    /** A random animation of the list, never the same one twice in a row. */
    private String pickFunAnim(String[] anims) {
        int index = this.random.nextInt(anims.length);
        if (anims[index].equals(lastFunAnim)) index = (index + 1) % anims.length;
        lastFunAnim = anims[index];
        return lastFunAnim;
    }

    /** "Happy to see you" when a customer opens the trade screen, once the merchant is fully out. */
    private void playHappyGesture() {
        if (!hidden && this.age - hidingChangedAge >= FUN_SETTLE_TICKS) {
            this.getNavigation().stop();
            triggerAnim(IDLE_CONTROLLER, HAPPY_ANIM);
            funBusyUntil = this.age + FUN_BUSY_TICKS;
            nextFunAge = Math.max(nextFunAge, this.age + OPEN_FUN_MIN_TICKS);
        }
    }

    private PlayState idleAnimController(AnimationState<HidingTraderEntity> event) {
        // A board token: no animation at all (a random one is cut too), the model rests in its default pose
        if (TokenBase.isToken(this)) return PlayState.STOP;
        AnimationController<HidingTraderEntity> controller = event.getController();
        if (!hidden) {
            // A little random animation: let it play, "idle" resumes after it (it ends on idle's first frame)
            if (controller.isPlayingTriggeredAnimation()) return PlayState.CONTINUE;
            // "open" then "idle" are chained here rather than queued in one RawAnimation: GeckoLib 4 lerps a queued
            // animation from the pose saved when the FIRST one started (the closed box), which made the box snap back
            // and re-open at the end of "open". setAnimation() snapshots the current pose (= idle's first frame).
            RawAnimation current = controller.getCurrentRawAnimation();
            if (current == null || current == CLOSED_ANIM || (current == OPEN_ANIM && !controller.hasAnimationFinished())) {
                return event.setAndContinue(OPEN_ANIM);
            }
            return event.setAndContinue(walking ? WALK_ANIM : IDLE_ANIM);
        }
        // Hiding: also cuts a random animation short, the Stare controller closes the box
        event.setAnimation(CLOSED_ANIM);
        return PlayState.STOP;
    }

    private PlayState closedAnimController(AnimationState<HidingTraderEntity> event) {
        if (TokenBase.isToken(this)) return PlayState.STOP;
        AnimationController<HidingTraderEntity> controller = event.getController();
        if (hidden) {
            if (controller.isPlayingTriggeredAnimation()) return PlayState.CONTINUE;
            RawAnimation current = controller.getCurrentRawAnimation();
            if (current == CLOSED_ANIM || current == HIDDEN_ANIM) return event.setAndContinue(current);
            if (current == null || current == IDLE_ANIM) return event.setAndContinue(CLOSED_ANIM);
            // After a rare hidden animation (it ends on the closed block pose): stay closed, without closing again
            return event.setAndContinue(HIDDEN_ANIM);
        }
        event.setAnimation(IDLE_ANIM);
        return PlayState.STOP;
    }

    @Override
    public AnimatableInstanceCache getAnimatableInstanceCache() {
        return cache;
    }

    @Override
    protected void fillRecipes() {
        updateInventories();
        updateTradeOffers();
    }

    @Override
    protected SoundEvent getHurtSound(DamageSource source) {
        return hidden ? blockState.getSoundGroup().getHitSound() : SoundEvents.ENTITY_VILLAGER_HURT;
    }

    @Override
    protected SoundEvent getDeathSound() {
        return hidden ? blockState.getSoundGroup().getBreakSound() : SoundEvents.ENTITY_VILLAGER_DEATH;
    }

    @Override
    public @Nullable EntityData initialize(ServerWorldAccess world, LocalDifficulty difficulty, SpawnReason spawnReason, @Nullable EntityData entityData) {
        if (getBandanaColor() < 0) rollBandanaColor();
        return super.initialize(world, difficulty, spawnReason, entityData);
    }

    /** @return the bandana colour 0-4, or -1 if it was not picked yet (client side before sync). */
    public int getBandanaColor() {
        return this.dataTracker.get(BANDANA_COLOR);
    }

    public void setBandanaColor(int color) {
        this.dataTracker.set(BANDANA_COLOR, MathHelper.clamp(color, 0, BANDANA_COLORS - 1));
    }

    private void rollBandanaColor() {
        setBandanaColor(this.random.nextInt(BANDANA_COLORS));
    }

    public BlockState getBlockState() {
        return blockState;
    }

    public void setBlockState(BlockState blockState) {
        this.blockState = blockState;
        syncBlockData(blockState);
    }

    /**
     * Closed as a block when no player is within {@link #OPEN_RANGE} (spectators and sneaking players don't count;
     * a Bandana wearer makes him come out even while sneaking, he just ignores him), when leashed, or for a while after
     * his bandana was stolen.
     */
    private boolean computeHiding() {
        // A board token is a still pawn: out of his box, whoever is around
        if (TokenBase.isToken(this)) return false;
        World world = this.getWorld();
        if (world == null || isLeashed() || isTheftHidden()) return true;
        return world.getClosestPlayer(this.getX(), this.getY(), this.getZ(), OPEN_RANGE,
                entity -> entity instanceof PlayerEntity player && drawsHimOut(player)) == null;
    }

    /** Whether this player, within {@link #OPEN_RANGE}, makes him come out: not a spectator, and not sneaking unless he wears a Bandana. */
    public static boolean drawsHimOut(PlayerEntity player) {
        return !player.isSpectator() && (!player.isSneaking() || wearsBandana(player));
    }

    /** @return whether he is closed as a block (state of the last tick). */
    public boolean isHidden() {
        return hidden;
    }

    /** Still closed after a theft (the window starts once the shocked animation has played). */
    public boolean isTheftHidden() {
        long start = this.dataTracker.get(HIDE_START);
        if (start == Long.MIN_VALUE || this.getWorld() == null) return false;
        long time = this.getWorld().getTime();
        return time >= start && time < start + THEFT_HIDE_TICKS;
    }

    /** Closes the merchant for {@link #THEFT_HIDE_TICKS} ticks from {@code delay} ticks from now, whoever is around. */
    public void startTheftHiding(int delay) {
        this.dataTracker.set(HIDE_START, this.getWorld().getTime() + delay);
    }

    /**
     * Players the merchant pays attention to (looks at, waves at, is happy to see, peeks at): not spectators, and not
     * players wearing a Bandana on their head, he simply doesn't care about them. They still make him come out and can
     * still trade.
     */
    public static boolean isAttentionTarget(@Nullable PlayerEntity player) {
        return player != null && !player.isSpectator() && !wearsBandana(player);
    }

    public static boolean wearsBandana(PlayerEntity player) {
        return player.getEquippedStack(EquipmentSlot.HEAD).isOf(ModItems.BANDANA);
    }

    /** @return the nearest player within range that he pays attention to, or null. */
    @Nullable
    public PlayerEntity findAttentionTarget(double range) {
        return this.getWorld().getClosestPlayer(this.getX(), this.getEyeY(), this.getZ(), range,
                entity -> entity instanceof PlayerEntity player && isAttentionTarget(player));
    }

    private void refreshAssigned() {
        VendorLinkPersistentState linkState = getVendorLinkState();
        assigned = ownerUuid != null || (linkState != null && !linkState.getVendorLinks(this.getUuid()).isEmpty())
                || !TraderStallRegistry.getLinkedStalls(this.getUuid()).isEmpty();
    }

    /** Linked to a shop: owner, Shopkeeper Key links or trading stall (server side, refreshed every second). */
    public boolean isAssigned() {
        return assigned;
    }

    @Nullable
    public BlockPos getHome() {
        return home;
    }

    /** Server: may wander now (unassigned, out, free: no customer, no little animation, not leashed). */
    public boolean canWander() {
        return !this.getWorld().isClient && !hidden && !assigned && !this.hasCustomer() && !isLeashed()
                && this.age >= funBusyUntil && this.age - hidingChangedAge >= FUN_SETTLE_TICKS;
    }

    @Override
    public int getSafeFallDistance() {
        // Wandering: never down a drop of more than one block
        return 1;
    }

    @Override
    public boolean isPushable() {
        // Closed, he must stay a block of the grid
        return !hidden && super.isPushable();
    }

    private void onHiddenChanged(boolean nowHidden) {
        hidingChangedAge = this.age;
        nextFunAge = this.age + (nowHidden ? HIDDEN_FUN_MIN_TICKS + this.random.nextInt(HIDDEN_FUN_RANGE_TICKS)
                : OPEN_FUN_MIN_TICKS + this.random.nextInt(OPEN_FUN_RANGE_TICKS));
        if (!nowHidden) gridAligned = false;
    }

    /**
     * Stops and lines the merchant up with the block grid as he closes (the disguise must look like a real block): the
     * centre of the nearest free block with a full floor under it, and a yaw on the nearest quarter turn.
     */
    public void snapToGrid() {
        this.getNavigation().stop();
        this.setVelocity(Vec3d.ZERO);
        float yaw = Math.round(this.getYaw() / 90.0F) * 90.0F;
        BlockPos spot = findHidingSpot();
        Vec3d pos = spot != null ? Vec3d.ofBottomCenter(spot) : this.getPos();
        this.refreshPositionAndAngles(pos.x, pos.y, pos.z, yaw, 0.0F);
        this.setHeadYaw(yaw);
        this.setBodyYaw(yaw);
        this.prevYaw = yaw;
        this.prevHeadYaw = yaw;
        this.prevBodyYaw = yaw;
    }

    @Nullable
    private BlockPos findHidingSpot() {
        World world = this.getWorld();
        BlockPos origin = BlockPos.ofFloored(this.getX(), this.getY() + 0.5, this.getZ());
        BlockPos best = null;
        double bestDistance = Double.MAX_VALUE;
        for (BlockPos candidate : BlockPos.iterate(origin.add(-1, -1, -1), origin.add(1, 1, 1))) {
            BlockPos below = candidate.down();
            if (!world.getBlockState(below).isSideSolidFullSquare(world, below, Direction.UP)) continue;
            Vec3d center = Vec3d.ofBottomCenter(candidate);
            if (!world.isSpaceEmpty(this, this.getType().getDimensions().getBoxAt(center))) continue;
            double distance = center.squaredDistanceTo(this.getPos());
            if (distance < bestDistance) {
                bestDistance = distance;
                best = candidate.toImmutable();
            }
        }
        return best;
    }

    public boolean hasBandana() {
        return this.dataTracker.get(HAS_BANDANA);
    }

    public void setHasBandana(boolean hasBandana) {
        this.dataTracker.set(HAS_BANDANA, hasBandana);
    }

    /** Shears on an open merchant with his bandana: it drops, he is shocked, then stays closed for 20 s, bald. */
    private void stealBandana(PlayerEntity player, ItemStack shears, Hand hand) {
        if (!(this.getWorld() instanceof ServerWorld world)) return;
        this.dropStack(world, BandanaItem.create(Math.max(0, getBandanaColor())), 1.4F);
        world.playSoundFromEntity(null, this, SoundEvents.ENTITY_SHEEP_SHEAR, SoundCategory.PLAYERS, 1.0F, 1.0F);
        if (!player.isInCreativeMode()) shears.damage(1, player, LivingEntity.getSlotForHand(hand));
        setHasBandana(false);
        if (this.getCustomer() != null) releaseCustomer(this.getCustomer());
        this.getNavigation().stop();
        bonkStarsTick = -1;
        triggerAnim(IDLE_CONTROLLER, SHOCKED_ANIM);
        startTheftHiding(SHOCK_TICKS);
        nextFunAge = this.age + SHOCK_TICKS + THEFT_HIDE_TICKS;
    }

    /** A Bandana on a bald merchant: he puts it on (its colour becomes his), the item is used up, he is delighted. */
    private void giveBandana(PlayerEntity player, ItemStack bandana) {
        setBandanaColor(BandanaItem.getColor(bandana));
        setHasBandana(true);
        bandana.decrementUnlessCreative(1, player);
        this.getWorld().playSoundFromEntity(null, this, SoundEvents.ITEM_ARMOR_EQUIP_LEATHER.value(), SoundCategory.NEUTRAL, 1.0F, 1.0F);
        this.getNavigation().stop();
        triggerAnim(IDLE_CONTROLLER, GIVE_BACK_ANIM);
        funBusyUntil = this.age + FUN_BUSY_TICKS;
        nextFunAge = Math.max(nextFunAge, this.age + OPEN_FUN_MIN_TICKS);
    }

    private void syncBlockData(BlockState blockState) {
        if (!this.getWorld().isClient) {
            DataResult<JsonElement> result = BlockState.CODEC.encodeStart(JsonOps.INSTANCE, blockState);
            result.resultOrPartial(HidingTraderEntity::printWarnForFailDecodeBlockState).ifPresent(jsonElement -> this.dataTracker.set(BLOCK_STATE, jsonElement.toString()));
        }
    }

    @Override
    protected @Nullable SoundEvent getAmbientSound() {
        if (isSilent()) return null;
        return hidden ? null : SoundEvents.ENTITY_VILLAGER_AMBIENT;
    }
}
