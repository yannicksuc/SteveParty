package fr.lordfinn.steveparty.blocks.custom;

import fr.lordfinn.steveparty.blocks.ModBlockEntities;
import fr.lordfinn.steveparty.persistent_state.TeleportationPadBooksStorage;
import fr.lordfinn.steveparty.persistent_state.TeleportationPadStorageManager;
import fr.lordfinn.steveparty.utils.TickableBlockEntity;
import net.minecraft.block.BlockState;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.component.ComponentMap;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtElement;
import net.minecraft.network.listener.ClientPlayPacketListener;
import net.minecraft.network.packet.Packet;
import net.minecraft.network.packet.s2c.play.BlockEntityUpdateS2CPacket;
import net.minecraft.registry.RegistryWrapper;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.util.ItemScatterer;
import net.minecraft.util.math.BlockPos;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import software.bernie.geckolib.animatable.GeoBlockEntity;
import software.bernie.geckolib.animatable.instance.AnimatableInstanceCache;
import software.bernie.geckolib.animation.*;
import software.bernie.geckolib.util.GeckoLibUtil;
import java.util.Optional;

import static fr.lordfinn.steveparty.components.ModComponents.SOCKETED_STORY;

public class TeleportationPadBlockEntity extends BlockEntity implements GeoBlockEntity, TickableBlockEntity {
    protected static final RawAnimation NO_STORY_ANIM = RawAnimation.begin().thenLoop("no-story");
    protected static final RawAnimation STORY_ANIM = RawAnimation.begin().thenLoop("story");
    protected static final RawAnimation STORY_CLOSED_ANIM = RawAnimation.begin().thenLoop("story-closed");
    private final AnimatableInstanceCache cache = GeckoLibUtil.createInstanceCache(this);
    public ItemStack book = ItemStack.EMPTY;
    public long lastTime = 0;
    public Float currentYaw = null;

    public TeleportationPadBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.BIG_BOOK_ENTITY, pos, state);
    }

    @Override
    public void registerControllers(AnimatableManager.ControllerRegistrar controllers) {
        controllers.add(new AnimationController<>(this, "no-story", 0, this::noStoryAnimController).transitionLength(0));
        controllers.add(new AnimationController<>(this, "story", 20, this::storyAnimController).transitionLength(20));
        controllers.add(new AnimationController<>(this, "story-closed", 20, this::storyClosedAnimController).transitionLength(20));

    }

    @Override
    protected void readComponents(ComponentsAccess components) {
        super.readComponents(components);
        this.book = components.getOrDefault(SOCKETED_STORY, ItemStack.EMPTY);
    }

    @Override
    protected void addComponents(ComponentMap.Builder componentMapBuilder) {
        super.addComponents(componentMapBuilder);
        componentMapBuilder.add(SOCKETED_STORY, this.book);
    }

    @Override
    public void writeNbt(NbtCompound nbt, RegistryWrapper.WrapperLookup wrapper) {
        if (book != null && !book.isEmpty()) {
            NbtElement itemNbt = book.toNbt(wrapper);
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
        Optional<ItemStack> catalogueNbt = Optional.empty();
        if (nbt.contains("catalogue", NbtElement.COMPOUND_TYPE)) { // Ensure "catalogue" exists and is a compound
            catalogueNbt = ItemStack.fromNbt(wrapper, nbt.get("catalogue"));
        }
        catalogueNbt.ifPresentOrElse(stack -> book = stack, () -> book = ItemStack.EMPTY);
        boolean isSocketed = nbt.getBoolean("isSocketed");
        if (!isSocketed)
            book = ItemStack.EMPTY;
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
        super.markDirty();
    }



    private PlayState storyClosedAnimController(AnimationState<TeleportationPadBlockEntity> event) {
        if (world != null && !book.isEmpty() && world.getClosestPlayer(pos.getX(), pos.getY(), pos.getZ(), 5d, false) == null) {
            return event.setAndContinue(STORY_CLOSED_ANIM);
        }
        event.setAnimation(STORY_ANIM);
        return PlayState.STOP;
    }

    protected <T extends TeleportationPadBlockEntity> PlayState storyAnimController(final AnimationState<T> event) {
        if (!book.isEmpty() && (world == null || world.getClosestPlayer(pos.getX(), pos.getY(), pos.getZ(), 5d, false) != null)) {
            return event.setAndContinue(STORY_ANIM);
        }
        event.setAnimation(STORY_CLOSED_ANIM);
        return PlayState.STOP;
    }

    protected <T extends TeleportationPadBlockEntity> PlayState noStoryAnimController(final AnimationState<T> event) {
        if (book.isEmpty())
            return event.setAndContinue(NO_STORY_ANIM);
        return PlayState.STOP;
    }

    @Override
    public AnimatableInstanceCache getAnimatableInstanceCache() {
        return cache;
    }

    public void setBook(@NotNull ItemStack itemStack) {
        if (world == null || world.isClient || itemStack.isEmpty() && (book == null || book.isEmpty())) return;
        if (!book.isEmpty()) {
            ItemScatterer.spawn(world, pos.getX() + 0.5f, pos.getY() + 0.5f, pos.getZ() + 0.5f, book);
        }
        book = itemStack.copy();
        if (this.getWorld() != null) {
            this.getWorld().playSound(null, this.pos, SoundEvents.BLOCK_TRIAL_SPAWNER_SPAWN_ITEM_BEGIN, SoundCategory.BLOCKS, 1.0F, 1.0F);
            this.markDirty();

            TeleportationPadBooksStorage storage = TeleportationPadStorageManager.getBooksStorage((ServerWorld) world);
            if (book.isEmpty())
                storage.removeTeleportationPad(pos);
            else
                storage.addTeleportationPad(pos, book);
        }
    }

    @Override
    public void tick() {
        if (this.world == null || this.world.isClient) return;
        if (world.getTime() % 45 == 0  && !book.isEmpty()) {
            world.playSound(
                    null,
                    this.pos,
                    SoundEvents.BLOCK_AMETHYST_BLOCK_CHIME,
                    SoundCategory.AMBIENT,
                    0.1F,
                    1.0F
            );
        }
    }

    public ItemStack getBook() {
        return book;
    }
}
