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
import net.minecraft.screen.SimpleNamedScreenHandlerFactory;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvent;
import net.minecraft.sound.SoundEvents;
import net.minecraft.text.Text;
import net.minecraft.util.ActionResult;
import net.minecraft.util.Hand;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Direction;
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
    private BlockState blockState = Blocks.GOLD_BLOCK.getDefaultState();
    private static final TrackedData<String> BLOCK_STATE = DataTracker.registerData(HidingTraderEntity.class, TrackedDataHandlerRegistry.STRING);

    public HidingTraderEntity(EntityType<? extends MerchantEntity> type, World world) {
        super(type, world);
        if (!world.isClient) {
            vendorLinkPersistentState = VendorLinkPersistentState.get(this.getWorld().getServer());
            updateInventories();
            updateTradeOffers();
        }
        initGoals();
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
        player.openHandledScreen(new SimpleNamedScreenHandlerFactory(
                        (syncId, playerInventory, playerx) -> new CustomizableMerchantScreenHandler(syncId, playerInventory, this), name))
                .ifPresent(id -> optionalScreenHandlerId = id);
        updateTradesToClient(player, levelProgress);
    }

    private void updateTradesToClient(PlayerEntity player, int levelProgress) {
        if (optionalScreenHandlerId != null) {
            TradeOfferList tradeOfferList = this.getOffers();
            if (!tradeOfferList.isEmpty()) {
                this.setCustomer(player);
                player.sendTradeOffers(optionalScreenHandlerId, tradeOfferList, levelProgress, this.getExperience(), this.isLeveledMerchant(), this.canRefreshTrades());
            }
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
        vendorLinkPersistentState.getVendorLinks(uuid).forEach(pos -> {
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
        validateTradeStock();
    }

    private void validateTradeStock() {
        for (TradeOffer offer : tradeOffers) {
            if (!isStockAvailable(offer.getSellItem())) {
                offer.disable();
            }
        }
    }

    private boolean isStockAvailable(ItemStack itemStack) {
        if (itemStack == null || itemStack.isEmpty()) return true;
        int requiredAmount = itemStack.getCount();
        int stockAmount = 0;
        for (Inventory inventory : storages) {
            for (int slot = 0; slot < inventory.size(); slot++) {
                ItemStack stack = inventory.getStack(slot);
                if (stack.getItem() == itemStack.getItem()) {
                    stockAmount += stack.getCount();
                    if (stockAmount >= requiredAmount) {
                        return true;
                    }
                }
            }
        }
        return false;
    }

    private void consumeStock(ItemStack itemStack) {
        int remainingAmount = itemStack.getCount();
        for (Inventory inventory : storages) {
            for (int slot = 0; slot < inventory.size(); slot++) {
                ItemStack stack = inventory.getStack(slot);
                if (stack.getItem() == itemStack.getItem()) {
                    int consumed = Math.min(stack.getCount(), remainingAmount);
                    stack.decrement(consumed);
                    remainingAmount -= consumed;
                    if (remainingAmount <= 0) {
                        return;
                    }
                }
            }
        }
    }

    public void distributeItemStackAcrossCashRegisters(ItemStack stack) {
        if (stack == null || stack.isEmpty()) {
            return;
        }
        for (Inventory inventory : cashRegisters) {
            if (stack.isEmpty()) {
                break;
            }
            for (int slot = 0; slot < inventory.size(); slot++) {
                ItemStack targetStack = inventory.getStack(slot);
                if (targetStack.isEmpty()) {
                    inventory.setStack(slot, stack.split(stack.getCount()));
                    break;
                } else if (stack.getItem().equals(targetStack.getItem())) {
                    int transferableAmount = Math.min(stack.getCount(), targetStack.getMaxCount() - targetStack.getCount());
                    if (transferableAmount > 0) {
                        targetStack.increment(transferableAmount);
                        stack.decrement(transferableAmount);
                    }
                }
                if (stack.isEmpty()) {
                    break;
                }
            }
            if (stack.isEmpty()) {
                inventory.markDirty();
            }
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
        nbt.putBoolean("isInvisible", this.isInvisible());
        if (nbt.contains("blockState")) {
            String blockStateJson = nbt.getString("blockState");
            JsonElement jsonElement = JsonParser.parseString(blockStateJson);
            BlockState.CODEC.parse(JsonOps.INSTANCE, jsonElement).resultOrPartial(HidingTraderEntity::printWarnForFailDecodeBlockState)
                    .ifPresent(this::setBlockState);
        }
        initGoals();
    }

    private static void printWarnForFailDecodeBlockState(String error) {
        // Removed logger statement
    }

    @Override
    public NbtCompound writeNbt(NbtCompound nbt) {
        if (nbt.contains("isInvisible")) {
            this.setInvisible(nbt.getBoolean("isInvisible"));
        }
        if (blockState != null) {
            DataResult<JsonElement> result = BlockState.CODEC.encodeStart(JsonOps.INSTANCE, blockState);
            result.resultOrPartial(HidingTraderEntity::printWarnForFailDecodeBlockState).ifPresent(jsonElement -> nbt.putString("blockState", jsonElement.toString()));
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
    protected void afterUsing(TradeOffer offer) {
        consumeStock(offer.getSellItem());
        distributeItemStackAcrossCashRegisters(offer.getFirstBuyItem().itemStack().copy());
        if (offer.getSecondBuyItem().isPresent())
            distributeItemStackAcrossCashRegisters(offer.getSecondBuyItem().get().itemStack().copy());
        validateTradeStock();
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
                    Steveparty.SCHEDULER.schedule(UUID.randomUUID(), 10, () -> ClientUtil.getLevel().playSound(ClientUtil.getClientPlayer(), this.getBlockPos(), this.blockState.getSoundGroup().getPlaceSound(), SoundCategory.NEUTRAL, 1F, 1.0F));
                }));
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
