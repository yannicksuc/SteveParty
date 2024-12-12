package fr.lordfinn.steveparty.blocks;

import com.mojang.serialization.Codec;
import fr.lordfinn.steveparty.Steveparty;
import net.minecraft.block.BlockState;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.component.ComponentMap;
import net.minecraft.component.ComponentType;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtElement;
import net.minecraft.network.listener.ClientPlayPacketListener;
import net.minecraft.network.packet.Packet;
import net.minecraft.network.packet.s2c.play.BlockEntityUpdateS2CPacket;
import net.minecraft.registry.RegistryWrapper;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.ItemScatterer;
import net.minecraft.util.math.BlockPos;
import org.jetbrains.annotations.Nullable;
import software.bernie.geckolib.animatable.GeoBlockEntity;
import software.bernie.geckolib.animatable.instance.AnimatableInstanceCache;
import software.bernie.geckolib.animation.*;
import software.bernie.geckolib.util.GeckoLibUtil;
import java.util.Optional;

import static fr.lordfinn.steveparty.components.ModComponents.SOCKETED_STORY;
import static fr.lordfinn.steveparty.components.ModComponents.registerComponent;

public class BigBookEntity extends BlockEntity implements GeoBlockEntity {
    protected static final RawAnimation NO_STORY_ANIM = RawAnimation.begin().thenLoop("no-story");
    protected static final RawAnimation STORY_ANIM = RawAnimation.begin().thenLoop("story");
    private final AnimatableInstanceCache cache = GeckoLibUtil.createInstanceCache(this);
    public ItemStack catalogue = ItemStack.EMPTY;
    public long lastTime = 0;

    public BigBookEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.BIG_BOOK_ENTITY, pos, state);
    }

    @Override
    public void registerControllers(AnimatableManager.ControllerRegistrar controllers) {
        controllers.add(new AnimationController<>(this, "no-story", 0, this::noStoryAnimController));
        controllers.add(new AnimationController<>(this, "story", 0, this::storyAnimController));
    }

    @Override
    protected void readComponents(ComponentsAccess components) {
        super.readComponents(components);
        this.catalogue = components.getOrDefault(SOCKETED_STORY, ItemStack.EMPTY);
    }

    @Override
    protected void addComponents(ComponentMap.Builder componentMapBuilder) {
        super.addComponents(componentMapBuilder);
        componentMapBuilder.add(SOCKETED_STORY, this.catalogue);
    }

    @Override
    public void writeNbt(NbtCompound nbt, RegistryWrapper.WrapperLookup wrapper) {
        if (catalogue != null && !catalogue.isEmpty()) {
            NbtElement itemNbt = catalogue.toNbt(wrapper);
            nbt.put("catalogue", itemNbt);
            nbt.putBoolean("isSocketed", true);
        } else {
            nbt.putBoolean("isSocketed", false);
        }
        super.writeNbt(nbt, wrapper);
    }

    @Override
    public NbtCompound toInitialChunkDataNbt(RegistryWrapper.WrapperLookup registryLookup) {
        return createNbt(registryLookup);
    }

    @Override
    public void readNbt(NbtCompound nbt, RegistryWrapper.WrapperLookup wrapper) {
        super.readNbt(nbt, wrapper);
        Steveparty.LOGGER.info("READ NBT");
        Optional<ItemStack> catalogueNbt = Optional.empty();
        if (nbt.contains("catalogue", NbtElement.COMPOUND_TYPE)) { // Ensure "catalogue" exists and is a compound
            catalogueNbt = ItemStack.fromNbt(wrapper, nbt.get("catalogue"));
        }
        catalogueNbt.ifPresentOrElse(stack -> catalogue = stack, () -> catalogue = ItemStack.EMPTY);
        boolean isSocketed = nbt.getBoolean("isSocketed");
        if (!isSocketed)
            catalogue = ItemStack.EMPTY;
    }

    @Override
    public @Nullable Packet<ClientPlayPacketListener> toUpdatePacket() {
        return BlockEntityUpdateS2CPacket.create(this);
    }

    @Override
    public void markDirty() {
        if (this.world != null && !this.world.isClient && this.world instanceof ServerWorld) {
            this.world.updateListeners(this.pos, this.getCachedState(), this.getCachedState(), 3);
        }
        Steveparty.LOGGER.info("MARK DIRTY CALLED");
        super.markDirty();
    }

    protected <T extends BigBookEntity> PlayState storyAnimController(final AnimationState<T> event) {
        if (!catalogue.isEmpty())
         return event.setAndContinue(STORY_ANIM);
        return PlayState.STOP;
    }

    protected <T extends BigBookEntity> PlayState noStoryAnimController(final AnimationState<T> event) {
        if (catalogue.isEmpty())
            return event.setAndContinue(NO_STORY_ANIM);
        return PlayState.STOP;
    }

    @Override
    public AnimatableInstanceCache getAnimatableInstanceCache() {
        return cache;
    }

    public void setBook(ItemStack itemStack) {
        if (world == null || world.isClient) return;
        if (!catalogue.isEmpty()) {
            ItemScatterer.spawn(world, pos.getX() + 0.5f, pos.getY() + 0.5f, pos.getZ() + 0.5f, catalogue);
        }
        catalogue = itemStack.copy();
        this.markDirty();
    }
}
