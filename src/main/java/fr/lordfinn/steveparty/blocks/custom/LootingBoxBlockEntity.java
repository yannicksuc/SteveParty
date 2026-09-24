package fr.lordfinn.steveparty.blocks.custom;

import fr.lordfinn.steveparty.blocks.ModBlockEntities;
import fr.lordfinn.steveparty.blocks.custom.boardspaces.CartridgeContainerBlockEntity;
import fr.lordfinn.steveparty.blocks.custom.boardspaces.behaviors.InventoryInteractorTileBehavior;
import fr.lordfinn.steveparty.components.InventoryComponent;
import fr.lordfinn.steveparty.items.custom.cartridges.InventoryCartridgeItem;
import fr.lordfinn.steveparty.payloads.custom.BlockPosPayload;
import fr.lordfinn.steveparty.screen_handlers.custom.LootingBoxScreenHandler;
import fr.lordfinn.steveparty.sounds.ModSounds;
import fr.lordfinn.steveparty.utils.TickableBlockEntity;
import net.fabricmc.fabric.api.screenhandler.v1.ExtendedScreenHandlerFactory;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.inventory.Inventory;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.registry.RegistryWrapper;
import net.minecraft.screen.NamedScreenHandlerFactory;
import net.minecraft.screen.ScreenHandler;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.text.Text;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import org.jetbrains.annotations.Nullable;
import software.bernie.geckolib.animatable.GeoBlockEntity;
import software.bernie.geckolib.animatable.instance.AnimatableInstanceCache;
import software.bernie.geckolib.animation.*;
import software.bernie.geckolib.util.GeckoLibUtil;

import java.util.HashSet;
import java.util.List;
import java.util.Random;
import java.util.Set;
import java.util.UUID;

import static fr.lordfinn.steveparty.blocks.custom.LootingBoxBlock.ACTIVATED;
import static fr.lordfinn.steveparty.blocks.custom.LootingBoxBlock.TRIGGERED;
import static fr.lordfinn.steveparty.components.ModComponents.*;

public class LootingBoxBlockEntity extends CartridgeContainerBlockEntity implements NamedScreenHandlerFactory, GeoBlockEntity, TickableBlockEntity {

    // -------------------------
    // Constants
    // -------------------------
    protected static final RawAnimation PUNCHED_ANIM = RawAnimation.begin().thenPlay("punched");
    protected static final RawAnimation IDLE = RawAnimation.begin().thenLoop("idle");

    /** Players that already punched a looting box during their current jump (removed when they land). */
    private static final Set<UUID> playersWhoPunchedThisJump = new HashSet<>();

    // -------------------------
    // Fields
    // -------------------------
    private int repeatTime = 2;      // Number of times block can be hit during cooldown
    private int cooldownTime = 60;   // Default 3 seconds
    private int cycleIndex = 0;

    private int cooldownTicks = 0;

    private int hitsRemaining;    // Tracks punches left before cooldown
    private final AnimatableInstanceCache cache = GeckoLibUtil.createInstanceCache(this);

    // -------------------------
    // Constructor
    // -------------------------
    public LootingBoxBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.LOOTING_BOX_ENTITY, pos, state, 1);
        resetHitsRemaining();
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
        if (world != null && !world.isClient) {
            // Block state changes are server-side only (a client-side change desyncs the TRIGGERED property)
            BlockState state = world.getBlockState(pos);
            if (state.contains(TRIGGERED) && !state.get(TRIGGERED))
                world.setBlockState(pos, state.with(TRIGGERED, true), Block.NOTIFY_ALL);
        }
        if (world != null && world.isClient) {
            int particleCount = 8;
            double speed = 20; // adjust for how fast particles fly out

            for (int i = 0; i < particleCount; i++) {
                double angle = 2 * Math.PI * i / particleCount; // evenly spaced around circle
                double dx = Math.cos(angle) * speed;
                double dz = Math.sin(angle) * speed;

                world.addParticle(
                        ParticleTypes.WAX_OFF,
                        pos.getX() + 0.5 + dx * 0.05,
                        pos.getY() + 0.5,
                        pos.getZ() + 0.5 + dz * 0.05,
                        dx,
                        0,   // no vertical motion
                        dz
                );
            }
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
        nbt.putInt("CooldownTicks", cooldownTicks);
        nbt.putInt("HitsRemaining", hitsRemaining);
        nbt.putInt("CycleIndex", cycleIndex);
    }

    @Override
    public void readNbt(NbtCompound nbt, RegistryWrapper.WrapperLookup registryManager) {
        super.readNbt(nbt, registryManager);
        if (nbt.contains("RepeatTime")) repeatTime = nbt.getInt("RepeatTime");
        if (nbt.contains("CooldownTime")) cooldownTime = nbt.getInt("CooldownTime");
        if (nbt.contains("CooldownTicks")) cooldownTicks = nbt.getInt("CooldownTicks");
        if (nbt.contains("HitsRemaining")) hitsRemaining = nbt.getInt("HitsRemaining");
        if (nbt.contains("CycleIndex")) cycleIndex = nbt.getInt("CycleIndex");
    }

    // -------------------------
    // Screen Handling
    // -------------------------
    @Override
    public @Nullable ScreenHandler createMenu(int syncId, PlayerInventory playerInventory, PlayerEntity player) {

        return new LootingBoxScreenHandler(syncId, playerInventory, this);
    }

    @Override
    public net.minecraft.text.Text getDisplayName() {
        return Text.empty();
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
        if (world.isClient) return;
        if (!getCachedState().get(ACTIVATED)) return;

        boolean playerJumpState = playersWhoPunchedThisJump.contains(player.getUuid());
        if (!playerJumpState && processInventoryAction()) {

            // Trigger animation & particles
            triggerAnim("main", "punched");

            // Play sounds
            world.playSound(null, pos, ModSounds.POP_SOUND_EVENT, SoundCategory.BLOCKS, 0.8f, 1.3f);
            world.playSound(null, pos, SoundEvents.BLOCK_AMETHYST_BLOCK_STEP, SoundCategory.BLOCKS, 1f, 0.5f);

            // Register player's jump state
            playersWhoPunchedThisJump.add(player.getUuid());

            // Reduce remaining punches
            hitsRemaining--;
            markDirty();

            if (hitsRemaining <= 0) {
                world.setBlockState(pos, this.getCachedState().with(ACTIVATED, false).with(TRIGGERED, true));
                cooldownTicks = cooldownTime;
            }
            world.scheduleBlockTick(pos, this.getCachedState().getBlock(), 14);
        }
    }


    public static void resetPlayerInteractionState(ServerPlayerEntity player) {
        playersWhoPunchedThisJump.remove(player.getUuid());
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
            // The cartridge content may have shrunk since the index was stored
            cycleIndex = Math.floorMod(cycleIndex, items.size());
            ItemStack stack = items.get(cycleIndex);
            boolean success = handleTransfer(stack, connectedInventory);
            cycleIndex = (cycleIndex + 1) % items.size();
            markDirty();
            return success;
        }
        return false;
    }

    private boolean handleTransfer(ItemStack stack, Inventory connectedInventory) {
        boolean shouldTakeFromPlayer = Boolean.TRUE.equals(stack.get(IS_NEGATIVE));
        return !shouldTakeFromPlayer && transferItem(stack, connectedInventory);
    }

    private boolean transferItem(ItemStack stack, Inventory sourceInventory) {
        // Drops exactly the wanted amount (possibly taken from several slots), keeping the item components
        return InventoryInteractorTileBehavior.extractMatching(stack, sourceInventory, toDrop -> {
            int count = toDrop.getCount();
            Block.dropStack(world, pos.down(), toDrop);
            return count;
        }) > 0;
    }

    public void resetHitsRemaining() {
        hitsRemaining = getRepeatTime();
    }

    @Override
    public void tick() {
        if (world.isClient) return; // <--- skip client

        BlockState currentState = world.getBlockState(pos);

        if (cooldownTicks > 0) {
            cooldownTicks--;
            if (cooldownTicks <= 0) {
                world.setBlockState(pos, currentState.with(ACTIVATED, true).with(TRIGGERED, false), Block.NOTIFY_ALL);
                resetHitsRemaining();
                markDirty();
            }
        }

        ItemStack stack = getStack(0);
        boolean hasItems = false;
        if (stack.getOrDefault(INVENTORY_COMPONENT, null) instanceof InventoryComponent cartridgeInventory) {
            hasItems = !cartridgeInventory.isEmpty();
        }

        if (!hasItems && currentState.get(ACTIVATED)) {
            world.setBlockState(pos, currentState.with(ACTIVATED, false), Block.NOTIFY_ALL);
        }

        if (hasItems && !currentState.get(ACTIVATED) && cooldownTicks <= 0) {
            world.setBlockState(pos, currentState.with(ACTIVATED, true), Block.NOTIFY_ALL);
            resetHitsRemaining();
        }
    }
}
