package fr.lordfinn.steveparty.blocks.custom;

import fr.lordfinn.steveparty.blocks.ModBlockEntities;
import fr.lordfinn.steveparty.blocks.custom.boardspaces.CartridgeContainerBlockEntity;
import fr.lordfinn.steveparty.components.InventoryComponent;
import fr.lordfinn.steveparty.items.custom.cartridges.InventoryCartridgeItem;
import fr.lordfinn.steveparty.payloads.custom.BlockPosPayload;
import fr.lordfinn.steveparty.screen_handlers.custom.LootingBoxScreenHandler;
import fr.lordfinn.steveparty.sounds.ModSounds;
import net.fabricmc.fabric.api.screenhandler.v1.ExtendedScreenHandlerFactory;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.inventory.Inventory;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.registry.RegistryWrapper;
import net.minecraft.screen.ScreenHandler;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import org.jetbrains.annotations.Nullable;
import software.bernie.geckolib.animatable.GeoBlockEntity;
import software.bernie.geckolib.animatable.instance.AnimatableInstanceCache;
import software.bernie.geckolib.animation.*;
import software.bernie.geckolib.util.GeckoLibUtil;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;

import static fr.lordfinn.steveparty.blocks.custom.LootingBoxBlock.TRIGGERED;
import static fr.lordfinn.steveparty.components.ModComponents.*;

public class LootingBoxBlockEntity extends CartridgeContainerBlockEntity implements ExtendedScreenHandlerFactory<BlockPosPayload>, GeoBlockEntity {

    // -------------------------
    // Constants
    // -------------------------
    protected static final RawAnimation PUNCHED_ANIM = RawAnimation.begin().thenPlay("punched");
    protected static final RawAnimation IDLE = RawAnimation.begin().thenLoop("idle");

    private static final Map<PlayerEntity, Boolean> playerJumpStates = new HashMap<>();

    // -------------------------
    // Fields
    // -------------------------
    private int repeatTime = 1;      // Number of times block can be hit during cooldown
    private int cooldownTime = 60;   // Default 3 seconds
    private int cycleIndex = 0;
    private final AnimatableInstanceCache cache = GeckoLibUtil.createInstanceCache(this);

    // -------------------------
    // Constructor
    // -------------------------
    public LootingBoxBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.LOOTING_BOX_ENTITY, pos, state, 1);
    }

    // -------------------------
    // Animations
    // -------------------------
    @Override
    public void registerControllers(AnimatableManager.ControllerRegistrar controllers) {
        controllers.add(new AnimationController<>(this, "main", 0, this::animController)
                .triggerableAnim("punched", PUNCHED_ANIM));
    }

    private PlayState animController(AnimationState<LootingBoxBlockEntity> state) {
        return state.setAndContinue(IDLE);
    }

    @Override
    public AnimatableInstanceCache getAnimatableInstanceCache() {
        return cache;
    }

    @Override
    public void triggerAnim(@Nullable String controllerName, String animName) {
        if (world.isClient) {
            world.setBlockState(pos, this.getCachedState().with(TRIGGERED, true));
        }
        GeoBlockEntity.super.triggerAnim(controllerName, animName);
    }

    // -------------------------
    // NBT Persistence
    // -------------------------
    @Override
    public void writeNbt(NbtCompound nbt, RegistryWrapper.WrapperLookup registryManager) {
        super.writeNbt(nbt, registryManager);
        nbt.putInt("RepeatTime", repeatTime);
        nbt.putInt("CooldownTime", cooldownTime);
    }

    @Override
    public void readNbt(NbtCompound nbt, RegistryWrapper.WrapperLookup registryManager) {
        super.readNbt(nbt, registryManager);
        if (nbt.contains("RepeatTime")) repeatTime = nbt.getInt("RepeatTime");
        if (nbt.contains("CooldownTime")) cooldownTime = nbt.getInt("CooldownTime");
    }

    // -------------------------
    // Screen Handling
    // -------------------------
    @Override
    public @Nullable ScreenHandler createMenu(int syncId, PlayerInventory playerInventory, PlayerEntity player) {
        return new LootingBoxScreenHandler(syncId, playerInventory, this);
    }

    @Override
    public BlockPosPayload getScreenOpeningData(ServerPlayerEntity serverPlayerEntity) {
        return new BlockPosPayload(pos);
    }

    // -------------------------
    // Getters and Setters
    // -------------------------
    public int getCooldownTime() {
        return cooldownTime;
    }

    public void setCooldownTime(int cooldownTime) {
        this.cooldownTime = cooldownTime;
    }

    public int getRepeatTime() {
        return repeatTime;
    }

    public void setRepeatTime(int repeatTime) {
        this.repeatTime = repeatTime;
    }

    private int getCycleIndex() {
        return cycleIndex;
    }

    private void setCycleIndex(int cycleIndex) {
        this.cycleIndex = cycleIndex;
    }

    // -------------------------
    // Player Interaction
    // -------------------------
    public void onPlayerInteract(ServerPlayerEntity player) {
        boolean playerJumpState = playerJumpStates.getOrDefault(player, false);
        if (!playerJumpState && processInventoryAction()) {
            triggerAnim("main", "punched");
            world.scheduleBlockTick(pos, this.getCachedState().getBlock(), 18);
            world.playSound(null, pos, SoundEvents.ENTITY_ITEM_FRAME_ADD_ITEM, SoundCategory.BLOCKS, 1.0f, 1.0f);
            world.playSound(null, pos, ModSounds.POP_SOUND_EVENT, SoundCategory.BLOCKS, 0.8f, 1.3f);
            playerJumpStates.put(player, true);
        }
    }

    public static void resetPlayerInteractionState(ServerPlayerEntity player) {
        playerJumpStates.put(player, false);
    }

    // -------------------------
    // Inventory Actions
    // -------------------------
    public boolean processInventoryAction() {
        World world = this.getWorld();
        ItemStack stack = getStack(0);
        if (stack instanceof ItemStack itemStack &&
                itemStack.getOrDefault(INVENTORY_COMPONENT, null) instanceof InventoryComponent cartridgeInventory &&
                itemStack.get(INVENTORY_POS) instanceof BlockPos connectedInventoryPos &&
                world.getBlockEntity(connectedInventoryPos) instanceof Inventory connectedInventory) {

            int selectionState = InventoryCartridgeItem.getSelectionState(itemStack);
            return switch (selectionState) {
                case 1 -> transferAllItems(cartridgeInventory, connectedInventory);
                case 2 -> transferNextItemInCycle(cartridgeInventory, connectedInventory);
                default -> transferRandomItem(cartridgeInventory, connectedInventory);
            };
        }
        return false;
    }

    private boolean transferAllItems(InventoryComponent cartridgeInventory, Inventory connectedInventory) {
        boolean success = false;
        for (ItemStack stack : cartridgeInventory.getItems()) {
            success |= handleTransfer(stack, connectedInventory);
        }
        return success;
    }

    private boolean transferRandomItem(InventoryComponent cartridgeInventory, Inventory connectedInventory) {
        List<ItemStack> items = cartridgeInventory.getItems().stream().filter(stack -> !stack.isEmpty()).toList();
        if (!items.isEmpty()) {
            return handleTransfer(items.get(new Random().nextInt(items.size())), connectedInventory);
        }
        return false;
    }

    private boolean transferNextItemInCycle(InventoryComponent cartridgeInventory, Inventory connectedInventory) {
        List<ItemStack> items = cartridgeInventory.getItems().stream().filter(stack -> !stack.isEmpty()).toList();
        if (!items.isEmpty()) {
            ItemStack stack = items.get(cycleIndex);
            boolean success = handleTransfer(stack, connectedInventory);
            cycleIndex = (cycleIndex + 1) % items.size();
            return success;
        }
        return false;
    }

    private boolean handleTransfer(ItemStack stack, Inventory connectedInventory) {
        boolean shouldTakeFromPlayer = Boolean.TRUE.equals(stack.get(IS_NEGATIVE));
        return !shouldTakeFromPlayer && transferItem(stack, connectedInventory);
    }

    private boolean transferItem(ItemStack stack, Inventory sourceInventory) {
        for (int i = 0; i < sourceInventory.size(); i++) {
            ItemStack sourceStack = sourceInventory.getStack(i);
            if (ItemStack.areItemsEqual(sourceStack, stack)) {
                int transferableAmount = Math.min(sourceStack.getCount(), stack.getCount());
                Block.dropStack(world, pos.down(), new ItemStack(stack.getItem(), transferableAmount));
                sourceStack.decrement(transferableAmount);
                return true;
            }
        }
        return false;
    }
}
