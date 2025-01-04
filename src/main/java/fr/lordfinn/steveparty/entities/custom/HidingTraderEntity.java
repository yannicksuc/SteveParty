package fr.lordfinn.steveparty.entities.custom;

import com.google.gson.JsonElement;
import com.google.gson.JsonParser;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.JsonOps;
import fr.lordfinn.steveparty.Steveparty;
import fr.lordfinn.steveparty.items.custom.TokenItem;
import fr.lordfinn.steveparty.screens.CustomizableMerchantScreenHandler;
import net.fabricmc.fabric.api.networking.v1.PacketByteBufs;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.block.entity.TrappedChestBlockEntity;
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
import net.minecraft.item.AirBlockItem;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtElement;
import net.minecraft.network.PacketByteBuf;
import net.minecraft.registry.Registries;
import net.minecraft.screen.SimpleNamedScreenHandlerFactory;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
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
import net.minecraft.village.TradedItem;
import net.minecraft.world.World;
import org.jetbrains.annotations.Nullable;
import software.bernie.geckolib.animatable.GeoEntity;
import software.bernie.geckolib.animatable.instance.AnimatableInstanceCache;
import software.bernie.geckolib.animatable.instance.SingletonAnimatableInstanceCache;
import software.bernie.geckolib.animation.*;
import software.bernie.geckolib.animation.AnimationState;

import java.util.*;


public class HidingTraderEntity extends MerchantEntity implements GeoEntity {
    private final TradeOfferList tradeOffers = new TradeOfferList();
    private final List<Inventory> nearbyInventories = new ArrayList<>();
    private final AnimatableInstanceCache cache = new SingletonAnimatableInstanceCache(this);
    protected static final RawAnimation IDLE_ANIM = RawAnimation.begin().thenLoop("idle");
    private Integer optionalScreenHandlerId = null;
    private boolean canBuy = false;
    private BlockState blockState = Blocks.GOLD_BLOCK.getDefaultState();
    private static final TrackedData<String> BLOCK_STATE = DataTracker.registerData(HidingTraderEntity.class, TrackedDataHandlerRegistry.STRING);


    public HidingTraderEntity(EntityType<? extends MerchantEntity> type, World world) {
        super(type, world);
        updateTradeOffers();
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
            BlockState.CODEC.parse(JsonOps.INSTANCE, jsonElement).resultOrPartial(error -> Steveparty.LOGGER.warn("Failed to decode block state: {}", error))
                    .ifPresent(decodedBlockState -> this.blockState = decodedBlockState);
        }
    }

    public static DefaultAttributeContainer.Builder setAttributes() {
        return LivingEntity.createLivingAttributes().add(EntityAttributes.MAX_HEALTH, 20.0D)
                .add(EntityAttributes.ATTACK_DAMAGE, 0.0D)
                .add(EntityAttributes.ATTACK_SPEED, 0.0D)
                .add(EntityAttributes.KNOCKBACK_RESISTANCE, 0.3D)
                .add(EntityAttributes.MOVEMENT_SPEED, 0.3D)
                .add(EntityAttributes.FOLLOW_RANGE, 16.0D);
    }

    @Override
    protected void initGoals() {
        this.goalSelector.add(0, new LookAtEntityGoal(this, PlayerEntity.class, 3.0F, 1.0F));
    }

    @Override
    public boolean isPersistent() {
        return true;
    }

    @Override
    public void sendOffers(PlayerEntity player, Text name, int levelProgress) {
        player.openHandledScreen(new SimpleNamedScreenHandlerFactory((syncId, playerInventory, playerx)
                -> new CustomizableMerchantScreenHandler(syncId, playerInventory, this), name)).ifPresent(id -> optionalScreenHandlerId = id);
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
    public boolean isLeveledMerchant() {
        return false;
    }

    public boolean isBottomBlockPowered() {
        // Check if the block beneath the villager is powered by redstone
        BlockPos villagerPos = this.getBlockPos();
        BlockPos bottomBlockPos = villagerPos.down();
        World world = this.getWorld();
        return world.isReceivingRedstonePower(bottomBlockPos) || world.isEmittingRedstonePower(bottomBlockPos, Direction.UP);
    }

    public void updateTradeOffers() {
        tradeOffers.clear();

        // Find the trapped chest and set trades based on its contents
        for (Inventory inventory : nearbyInventories) {
            if (inventory instanceof TrappedChestBlockEntity) {
                for (int column = 0; column < 9; column++) {
                    ItemStack firstBuyItem = inventory.getStack(column);
                    ItemStack secondBuyItem = inventory.getStack(column + 9);
                    ItemStack sellItem = inventory.getStack(column + 18);

                    if (!firstBuyItem.isEmpty() && !sellItem.isEmpty() &&
                            !(firstBuyItem.getItem() instanceof AirBlockItem) &&
                            !(sellItem.getItem() instanceof AirBlockItem)) {
                        TradeOffer offer = new TradeOffer(
                                new TradedItem(firstBuyItem.getItem(), firstBuyItem.getCount()),
                                secondBuyItem.isEmpty() ? Optional.empty() : Optional.of(new TradedItem(secondBuyItem.getItem(), secondBuyItem.getCount())),
                                sellItem,
                                1, 0, 0
                        );
                        tradeOffers.add(offer);
                    }
                }
                break; // Only process the first trapped chest
            }
        }
        validateTradeStock();
    }

    // Check stock and adjust trade availability
    private void validateTradeStock() {
        for (TradeOffer offer : tradeOffers) {
            if (!isStockAvailable(offer.getSellItem())) {
                offer.disable();
            }
        }
    }

    // Check if sufficient stock is available for an item
    private boolean isStockAvailable(ItemStack itemStack) {
        if (itemStack == null || itemStack.isEmpty()) return true;
        int requiredAmount = itemStack.getCount();
        int stockAmount = 0;
        for (Inventory inventory : nearbyInventories.stream().filter(inv -> !(inv instanceof TrappedChestBlockEntity)).toList()) {
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

    // Remove items from stock after a trade
    private void consumeStock(ItemStack itemStack) {
        int remainingAmount = itemStack.getCount();
        for (Inventory inventory : nearbyInventories.stream().filter(inv -> !(inv instanceof TrappedChestBlockEntity)).toList()) {
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

    public void distributeItemStackAcrossInventories(ItemStack stack) {
        if (stack == null || stack.isEmpty()) {
            return; // Nothing to distribute
        }

        Steveparty.LOGGER.info("distributeItemStackAcrossInventories stack: {}", stack);

        for (Inventory inventory : nearbyInventories.stream().filter(inv -> !(inv instanceof TrappedChestBlockEntity)).toList()) {
            if (stack.isEmpty()) {
                break; // Stop if the stack is already empty
            }
            Steveparty.LOGGER.info("distributeItemStackAcrossInventories inventory: {}", inventory);

            for (int slot = 0; slot < inventory.size(); slot++) {
                ItemStack targetStack = inventory.getStack(slot);

                // Check if the slot is empty or compatible with the item
                if (targetStack.isEmpty()) {
                    // Add to empty slot
                    inventory.setStack(slot, stack.split(stack.getCount()));
                    break;
                } else if (stack.getItem().equals(targetStack.getItem())) {
                    // Add to existing stack if compatible
                    int transferableAmount = Math.min(stack.getCount(),
                            targetStack.getMaxCount() - targetStack.getCount());
                    if (transferableAmount > 0) {
                        targetStack.increment(transferableAmount);
                        stack.decrement(transferableAmount);
                    }
                }
                Steveparty.LOGGER.info("distributeItemStackAcrossInventories targetStack: {}", targetStack);
                // Stop if the stack is empty after this iteration
                if (stack.isEmpty()) {
                    break;
                }
            }

            // Mark inventory as dirty if any changes are made
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
        if (player.getMainHandStack().getItem() instanceof TokenItem || player.getStackInHand(hand).isOf(Items.LEAD)) {
            return ActionResult.PASS;
        }
        if (!this.getWorld().isClient && player instanceof ServerPlayerEntity) {
            this.fillRecipes();
            if(nearbyInventories.isEmpty() || tradeOffers.isEmpty())
                return ActionResult.PASS;
            if (!canBuy)
                disableAllTrades();
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
            if (passenger ==null) return super.getHitbox();
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
            BlockState.CODEC.parse(JsonOps.INSTANCE, jsonElement).resultOrPartial(error -> Steveparty.LOGGER.warn("Failed to decode block state: {}", error))
                    .ifPresent(this::setBlockState);
        }
        initGoals();
    }

    @Override
    public NbtCompound writeNbt(NbtCompound nbt) {
        if (nbt.contains("isInvisible")) {
            this.setInvisible(nbt.getBoolean("isInvisible"));
        }
        // Serialize the BlockState to a JsonElement
        if (blockState != null) {
            DataResult<JsonElement> result = BlockState.CODEC.encodeStart(JsonOps.INSTANCE, blockState);
            result.resultOrPartial(error -> Steveparty.LOGGER.warn("Failed to encode block state: {}", error)).ifPresent(jsonElement -> {
                nbt.putString("blockState", jsonElement.toString());
            });
        }
        return super.writeNbt(nbt);
    }

    @Override
    public void tick() {
        super.tick();
        if (!this.getWorld().isClient && !canBuy && isBottomBlockPowered()) {
            canBuy = true;
        }
    }

    private void disableAllTrades() {
        tradeOffers.forEach(TradeOffer::disable);
    }

    private void enableAllTrades() {
        tradeOffers.forEach(TradeOffer::resetUses);
    }

    // Add nearby inventories (for example, containers that are nearby)
    public void addNearbyInventories() {
        BlockPos villagerPos = this.getBlockPos();
        World world = this.getWorld();
        nearbyInventories.clear(); // Clear existing inventories

        int radius = 5; // Search radius
        for (int x = -radius; x <= radius; x++) {
            for (int y = -radius; y <= radius; y++) {
                for (int z = -radius; z <= radius; z++) {
                    BlockPos currentPos = villagerPos.add(x, y, z);
                    BlockEntity blockEntity = world.getBlockEntity(currentPos);
                    if (blockEntity instanceof Inventory) {
                        nearbyInventories.add((Inventory) blockEntity);
                    }
                }
            }
        }
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
        distributeItemStackAcrossInventories(offer.getFirstBuyItem().itemStack().copy());
        if (offer.getSecondBuyItem().isPresent())
            distributeItemStackAcrossInventories(offer.getSecondBuyItem().get().itemStack().copy());

        if (isBottomBlockPowered()) {
            canBuy = true;
            enableAllTrades(); // Allow trades
        } else {
            canBuy = false;
            disableAllTrades(); // Disable trades
        }
        validateTradeStock();
        updateTradesToClient(getCustomer(), 0);
        if (hasPassengers() && getFirstPassenger() instanceof MobEntity passenger) {
            boolean silentStatus = passenger.isSilent();
            passenger.setSilent(false);
            passenger.playAmbientSound();
            passenger.setSilent(silentStatus);
        } else
            this.playSound(SoundEvents.ENTITY_VILLAGER_TRADE, 1.0F, this.getSoundPitch());
    }

    @Override
    public int getExperience() {
        return 0; // No experience for this merchant
    }

    @Override
    public @Nullable PassiveEntity createChild(ServerWorld world, PassiveEntity entity) {
        return null;
    }

    @Override
    public void registerControllers(AnimatableManager.ControllerRegistrar controllers) {
        controllers.add(new AnimationController<>(this, "Idle", 5, this::idleAnimController));
    }

    private PlayState idleAnimController(AnimationState<HidingTraderEntity> event) {
        return event.setAndContinue(IDLE_ANIM);
    }

    @Override
    public AnimatableInstanceCache getAnimatableInstanceCache() {
        return cache;
    }

    @Override
    protected void fillRecipes() {
        addNearbyInventories();
        updateTradeOffers();
    }

    @Override
    public void playAmbientSound() {
        this.playSoundIfNotSilent(SoundEvents.ENTITY_VILLAGER_AMBIENT);
    }

    @Override
    protected SoundEvent getHurtSound(DamageSource source) {
        return SoundEvents.ENTITY_VILLAGER_HURT;
    }

    @Override
    protected SoundEvent getDeathSound() {
        // Return villager death sound
        return SoundEvents.ENTITY_VILLAGER_DEATH;
    }

    public BlockState getBlockState() {
        return blockState;
    }

    public void setBlockState(BlockState blockState) {
        this.blockState = blockState;
        syncBlockData(blockState);
    }

    private void syncBlockData(BlockState blockState) {
        if (!this.getWorld().isClient) { // Ensure this runs only on the server
            // Serialize the BlockState and update the DataTracker
            DataResult<JsonElement> result = BlockState.CODEC.encodeStart(JsonOps.INSTANCE, blockState);
            result.resultOrPartial(error -> Steveparty.LOGGER.warn("Failed to encode block state: {}", error)).ifPresent(jsonElement -> {
                this.dataTracker.set(BLOCK_STATE, jsonElement.toString());
            });
        }
    }
}
