package fr.lordfinn.steveparty.entities.custom;

import com.google.gson.JsonElement;
import com.google.gson.JsonParser;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.JsonOps;
import fr.lordfinn.steveparty.Steveparty;
import fr.lordfinn.steveparty.blocks.custom.CashRegisterBlockEntity;
import fr.lordfinn.steveparty.blocks.custom.TradingStallBlockEntity;
import fr.lordfinn.steveparty.items.ModItems;
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
import net.minecraft.entity.ai.goal.LookAtEntityGoal;
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
import net.minecraft.screen.MerchantScreenHandler;
import net.minecraft.screen.ScreenHandler;
import net.minecraft.screen.SimpleNamedScreenHandlerFactory;
import net.minecraft.screen.slot.SlotActionType;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
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
import net.minecraft.util.math.Vec3d;
import net.minecraft.village.MerchantInventory;
import net.minecraft.village.TradeOffer;
import net.minecraft.village.TradeOfferList;
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

    public HidingTraderEntity(EntityType<? extends MerchantEntity> type, World world) {
        super(type, world);
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
        this.goalSelector.add(0, new LookAtEntityGoal(this, PlayerEntity.class, 15.0F, 1.0F));
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
        if (player.getMainHandStack().getItem() instanceof TokenItem || player.getStackInHand(hand).isOf(Items.LEAD) || player.getStackInHand(hand).isOf(ModItems.SHOPKEEPER_KEY)) {
            return ActionResult.PASS;
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

    @Override
    public void registerControllers(AnimatableManager.ControllerRegistrar controllers) {
        controllers.add(new AnimationController<>(this, "Idle", 5, this::idleAnimController)
                .setSoundKeyframeHandler(context -> {
                    if (this.getWorld() == null) return;
                    if (!lastHidingState)
                        ClientUtil.getLevel().playSound(ClientUtil.getClientPlayer(), this.getBlockPos(), SoundEvents.ENTITY_PUFFER_FISH_BLOW_UP, SoundCategory.NEUTRAL, 0.5F, 1.5F);
                    lastHidingState = true;
                }));
        controllers.add(new AnimationController<>(this, "Stare", 2, this::closedAnimController)
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
        if (!this.getWorld().isClient && this.age % OWNER_SYNC_INTERVAL == 0) {
            syncOwner();
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
        if (this.getWorld().isClient && pendingPlaceSoundTicks >= 0) {
            if (pendingPlaceSoundTicks-- == 0) {
                Vec3d soundPos = this.getBlockPos().toCenterPos();
                this.getWorld().playSound(soundPos.x, soundPos.y, soundPos.z, this.blockState.getSoundGroup().getPlaceSound(), SoundCategory.NEUTRAL, 1F, 1.0F, false);
            }
        }
    }

    private PlayState idleAnimController(AnimationState<HidingTraderEntity> event) {
        if (!isHiding()) {
            return event.setAndContinue(IDLE_ANIM);
        }
        event.setAnimation(CLOSED_ANIM);
        return PlayState.STOP;
    }

    private PlayState closedAnimController(AnimationState<HidingTraderEntity> event) {
        if (isHiding()) {
            return event.setAndContinue(CLOSED_ANIM);
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
        return isHiding() ? blockState.getSoundGroup().getHitSound() : SoundEvents.ENTITY_VILLAGER_HURT;
    }

    @Override
    protected SoundEvent getDeathSound() {
        return isHiding() ? blockState.getSoundGroup().getBreakSound() : SoundEvents.ENTITY_VILLAGER_DEATH;
    }

    public BlockState getBlockState() {
        return blockState;
    }

    public void setBlockState(BlockState blockState) {
        this.blockState = blockState;
        syncBlockData(blockState);
    }

    private boolean isHiding() {
        return ((getWorld() != null && getWorld().getClosestPlayer(getPos().getX(), getPos().getY(), getPos().getZ(), 15d, player -> !player.isSneaking()) == null) || isLeashed());
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
        return isHiding() ? null : SoundEvents.ENTITY_VILLAGER_AMBIENT;
    }
}
