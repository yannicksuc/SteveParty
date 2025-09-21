package fr.lordfinn.steveparty.blocks.custom;

import fr.lordfinn.steveparty.blocks.ModBlockEntities;
import fr.lordfinn.steveparty.items.ModItems;
import fr.lordfinn.steveparty.screen_handlers.custom.DiceForgeScreenHandler;
import fr.lordfinn.steveparty.utils.TickableBlockEntity;
import net.minecraft.block.BlockState;
import net.minecraft.block.entity.LootableContainerBlockEntity;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.inventory.Inventories;
import net.minecraft.inventory.Inventory;
import net.minecraft.inventory.SimpleInventory;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.network.listener.ClientPlayPacketListener;
import net.minecraft.network.packet.Packet;
import net.minecraft.network.packet.s2c.play.BlockEntityUpdateS2CPacket;
import net.minecraft.registry.RegistryWrapper;
import net.minecraft.screen.GenericContainerScreenHandler;
import net.minecraft.screen.ScreenHandler;
import net.minecraft.text.Text;
import net.minecraft.util.collection.DefaultedList;
import net.minecraft.util.math.BlockPos;
import org.jetbrains.annotations.Nullable;
import software.bernie.geckolib.animatable.GeoBlockEntity;
import software.bernie.geckolib.animatable.instance.AnimatableInstanceCache;
import software.bernie.geckolib.animation.*;
import software.bernie.geckolib.util.GeckoLibUtil;

public class DiceForgeBlockEntity extends LootableContainerBlockEntity implements GeoBlockEntity, TickableBlockEntity {
    private final AnimatableInstanceCache cache = GeckoLibUtil.createInstanceCache(this);
    protected static final RawAnimation IDLE = RawAnimation.begin().thenLoop("idle");
    private DefaultedList<ItemStack> inventory;
    private float rotationTicks  = 0f;
    private long craftStartTime = -1L; // -1 = not crafting
    private int craftTimeTotal = 100;  // ticks required to complete a craft
    private boolean isCrafting = false;

    public DiceForgeBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.DICE_FORGE_ENTITY, pos, state);
        this.inventory = DefaultedList.ofSize(27, ItemStack.EMPTY);
    }

    protected void writeNbt(NbtCompound nbt, RegistryWrapper.WrapperLookup registries) {
        super.writeNbt(nbt, registries);
        if (!this.writeLootTable(nbt)) {
            Inventories.writeNbt(nbt, this.inventory, registries);
        }
        nbt.putBoolean("IsCrafting", isCrafting);
        nbt.putLong("CraftStartTime", craftStartTime);
    }

    protected void readNbt(NbtCompound nbt, RegistryWrapper.WrapperLookup registries) {
        super.readNbt(nbt, registries);
        this.inventory = DefaultedList.ofSize(this.size(), ItemStack.EMPTY);
        if (!this.readLootTable(nbt)) {
            Inventories.readNbt(nbt, this.inventory, registries);
        }
        this.isCrafting = nbt.getBoolean("IsCrafting");
        this.craftStartTime = nbt.getLong("CraftStartTime");
    }

    public void activate() {
        world.setBlockState(pos, getCachedState().with(DiceForgeBlock.ACTIVATED, true));
    }

    @Override
    public void tick() {
        if (world.isClient) {
            rotationTicks++; // client-side rotation for rendering
            return;
        }

        // --- Server-side crafting logic ---
        if (isCrafting) {
            if (craftStartTime + craftTimeTotal < world.getTime() ) finishCraft();
        } else {
            startCrafting();
        }
    }

    private void startCrafting() {
        if (!isCrafting && hasRequiredPowerStar()) {
            isCrafting = true;
            craftStartTime = world.getTime();
            consumePowerStar();
            markDirty(); // sends update to client
        }
    }


    @Override
    public void registerControllers(AnimatableManager.ControllerRegistrar controllerRegistrar) {
        controllerRegistrar.add(new AnimationController<>(this, "idle", 0, this::idleAnimController));
    }

    private PlayState idleAnimController(AnimationState<DiceForgeBlockEntity> state) {
        return state.setAndContinue(IDLE);
    }

    @Override
    public AnimatableInstanceCache getAnimatableInstanceCache() {
        return cache;
    }

    public boolean isActivated() {
        return getCachedState().get(DiceForgeBlock.ACTIVATED);
    }

    public int size() {
        return 13;
    }
    protected DefaultedList<ItemStack> getHeldStacks() {
        return this.inventory;
    }

    protected void setHeldStacks(DefaultedList<ItemStack> inventory) {
        this.inventory = inventory;
    }

    protected Text getContainerName() {
        return Text.translatable("block.steveparty.dice_forge");
    }

    @Override
    public NbtCompound toInitialChunkDataNbt(RegistryWrapper.WrapperLookup registries) {
        NbtCompound nbt = super.toInitialChunkDataNbt(registries);
        Inventories.writeNbt(nbt, this.inventory, registries);
        nbt.putBoolean("IsCrafting", isCrafting);
        nbt.putLong("CraftStartTime", craftStartTime);
        return nbt;
    }

    @Override
    public @Nullable Packet<ClientPlayPacketListener> toUpdatePacket() {
        return BlockEntityUpdateS2CPacket.create(this);
    }

    public void sync() {
        if (world != null && !world.isClient) {
            world.updateListeners(pos, getCachedState(), getCachedState(), 3);
        }
    }

    @Override
    public void markDirty() {
        super.markDirty();
        sync();
    }

    private boolean hasRequiredPowerStar() {
        for (ItemStack stack : inventory) {
            if (!stack.isEmpty() && stack.isOf(ModItems.POWER_STAR)) {
                return true;
            }
        }
        return false;
    }

    private void consumePowerStar() {
        for (int i = 0; i < inventory.size(); i++) {
            ItemStack stack = inventory.get(i);
            if (!stack.isEmpty() && stack.isOf(ModItems.POWER_STAR)) {
                stack.decrement(1);
                if (stack.isEmpty()) inventory.set(i, ItemStack.EMPTY);
                markDirty();
                return;
            }
        }
    }

    private void finishCraft() {
        // Gather dice faces
        DefaultedList<ItemStack> faces = DefaultedList.ofSize(27, ItemStack.EMPTY);
        int count = 0;
        for (ItemStack stack : inventory) {
            if (!stack.isEmpty() && stack.getItem() != ModItems.POWER_STAR) {
                faces.set(count++, stack);
                if (count >= 6) break;
            }
        }

        // Ensure 6 faces (duplicate if less)
        while (count < 6) {
            faces.set(count, faces.get(count % count).copy());
            count++;
        }

        craftStartTime = -1;
        isCrafting = false;

        /*
        // Remove used faces from inventory
        int removed = 0;
        for (int i = 0; i < inventory.size() && removed < 6; i++) {
            ItemStack stack = inventory.get(i);
            if (!stack.isEmpty() && stack != ModBlocks.POWER_STAR) {
                inventory.set(i, ItemStack.EMPTY);
                removed++;
            }
        }

        // TODO: create the new dice item and place in output slot or drop
        ItemStack newDice = new ItemStack(ModBlocks.CRAFTED_DICE); // replace with your item
        // Try to insert in first empty slot
        for (int i = 0; i < inventory.size(); i++) {
            if (inventory.get(i).isEmpty()) {
                inventory.set(i, newDice);
                break;
            }
        }*/

        markDirty();
    }

    @Override
    protected ScreenHandler createScreenHandler(int syncId, PlayerInventory playerInventory) {
        return new DiceForgeScreenHandler(syncId, playerInventory, this);
    }

    public DefaultedList<ItemStack> getInventory() {
        return this.inventory;
    }


    public float getRotationTicks() {
        return rotationTicks;
    }

    public float getCraftProgress(float partialTick) {
        if (!isCrafting || craftStartTime < 0) return 0f;

        double currentTime = (world != null) ? world.getTime() + partialTick : 0;
        float progress = (float) ((currentTime - craftStartTime) / craftTimeTotal);
        return Math.min(1f, Math.max(0f, progress));
    }

    public boolean isCrafting() {
        return isCrafting;
    }
}
