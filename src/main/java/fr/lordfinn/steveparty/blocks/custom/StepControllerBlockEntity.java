package fr.lordfinn.steveparty.blocks.custom;

import fr.lordfinn.steveparty.Steveparty;
import fr.lordfinn.steveparty.blocks.ModBlockEntities;
import fr.lordfinn.steveparty.blocks.custom.PartyController.PartyControllerEntity;
import fr.lordfinn.steveparty.utils.TickableBlockEntity;
import net.minecraft.block.BlockState;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtElement;
import net.minecraft.network.listener.ClientPlayPacketListener;
import net.minecraft.network.packet.Packet;
import net.minecraft.network.packet.s2c.play.BlockEntityUpdateS2CPacket;
import net.minecraft.registry.RegistryWrapper;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.util.math.BlockPos;
import org.jetbrains.annotations.Nullable;
import software.bernie.geckolib.animatable.GeoBlockEntity;
import software.bernie.geckolib.animatable.instance.AnimatableInstanceCache;
import software.bernie.geckolib.animation.*;
import software.bernie.geckolib.util.GeckoLibUtil;

import java.util.Comparator;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

public class StepControllerBlockEntity extends BlockEntity implements GeoBlockEntity, TickableBlockEntity {
    private final AnimatableInstanceCache cache = GeckoLibUtil.createInstanceCache(this);
    protected static final RawAnimation IDLE = RawAnimation.begin().thenLoop("idle");
    protected static final RawAnimation UP = RawAnimation.begin().thenLoop("up");
    protected static final RawAnimation SIDE = RawAnimation.begin().thenLoop("side");
    protected static final RawAnimation DOWN = RawAnimation.begin().thenLoop("down");
    protected static final RawAnimation ACTIVATED = RawAnimation.begin().thenPlay ("powered");
    public int mode = 0;
    public boolean wasPowered = false;

    public StepControllerBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.STEP_CONTROLLER_ENTITY, pos, state);
    }

    @Override
    public void writeNbt(NbtCompound nbt, RegistryWrapper.WrapperLookup wrapper) {
        nbt.putInt("mode", this.mode);
        nbt.putBoolean("wasPowered", this.wasPowered);
        super.writeNbt(nbt, wrapper);
    }

    @Override
    public void readNbt(NbtCompound nbt, RegistryWrapper.WrapperLookup wrapper) {
        super.readNbt(nbt, wrapper);
        if (nbt.contains("mode", NbtElement.INT_TYPE)) {
            this.mode = nbt.getInt("mode");
        }
        if (nbt.contains("wasPowered")) {
            this.wasPowered = nbt.getBoolean("wasPowered");
        }
    }

    @Override
    public void registerControllers(AnimatableManager.ControllerRegistrar controllerRegistrar) {
        controllerRegistrar.add(new AnimationController<>(this, "idle", 0, this::idleAnimController));
        controllerRegistrar.add(new AnimationController<>(this, "mode", 5, this::modeAnimController));
        controllerRegistrar.add(new AnimationController<>(this, "activation", 5, this::activationAnimController));
    }

    private PlayState activationAnimController(AnimationState<StepControllerBlockEntity> state) {
        if (wasPowered || (!state.getController().hasAnimationFinished() && state.getController().getCurrentRawAnimation() == ACTIVATED)) {
            return state.setAndContinue(ACTIVATED);
        }
       return state.setAndContinue(IDLE);
    }

    private PlayState modeAnimController(AnimationState<StepControllerBlockEntity> state) {
        if (mode == 0)
            return state.setAndContinue(UP);
        if (mode == 1)
            return state.setAndContinue(SIDE);
        if (mode == 2)
            return state.setAndContinue(DOWN);
        return PlayState.STOP;
    }

    private PlayState idleAnimController(AnimationState<StepControllerBlockEntity> state) {
        return state.setAndContinue(IDLE);
    }

    @Override
    public AnimatableInstanceCache getAnimatableInstanceCache() {
        return cache;
    }

    @Override
    public void tick() {
        if (this.world != null && !this.world.isClient) {
            boolean isPowered = this.world.isReceivingRedstonePower(this.pos);
            if (isPowered != wasPowered) {
                wasPowered = isPowered;
                if (wasPowered)
                    trigger();
                this.markDirty();
                this.sync();
            }
        }
    }

    private void trigger() {
        if (this.world != null) {
            PartyControllerEntity.getActivePartyControllers().stream()
                    .filter(entity -> entity.getPartyData().isStarted())
                    .filter(entity -> entity.getPos().getSquaredDistance(this.pos) < 64)
                    .min(Comparator.comparingDouble(entity -> entity.getPos().getSquaredDistance(this.pos)))
                    .ifPresentOrElse(partyControllerEntity ->  {
                        world.playSound(null, this.pos, SoundEvents.BLOCK_TRIAL_SPAWNER_ABOUT_TO_SPAWN_ITEM, SoundCategory.BLOCKS, 1.0F, 1.0F);
                        switch (this.mode) {
                            case 0:
                                partyControllerEntity.nextStep();
                            case 1:
                                partyControllerEntity.restartStep();
                            case 2:
                                partyControllerEntity.previousStep();
                            default:
                                break;
                        }
                        world.playSound(null, this.pos, SoundEvents.BLOCK_TRIAL_SPAWNER_ABOUT_TO_SPAWN_ITEM, SoundCategory.BLOCKS, 1.0F, 1.0F);
                        world.playSound(null, this.pos, SoundEvents.BLOCK_TRIAL_SPAWNER_OPEN_SHUTTER, SoundCategory.BLOCKS, 1.0F, 1.0F);
                    }, () -> {
                        world.playSound(null, this.pos, SoundEvents.EVENT_MOB_EFFECT_TRIAL_OMEN, SoundCategory.BLOCKS, 1.0F, 1.0F);
                    });
        }
    }

    public void sync() {
        if (this.world instanceof ServerWorld serverWorld) {
            serverWorld.getChunkManager().markForUpdate(this.pos);
        }
    }

    public void cycleMode() {
        // Cycle threw the modes (0 = up, 1 = side, 2 = down)
        if (this.world != null) {
            mode = ((mode + 1) % 3);
            this.world.playSound(null, this.getPos(), SoundEvents.BLOCK_COPPER_TRAPDOOR_OPEN, SoundCategory.BLOCKS, 1.0F, 2.0F);
            this.markDirty();
            this.sync();
        }
    }

    @Nullable
    @Override
    public Packet<ClientPlayPacketListener> toUpdatePacket() {
        return BlockEntityUpdateS2CPacket.create(this);
    }

    @Override
    public NbtCompound toInitialChunkDataNbt(RegistryWrapper.WrapperLookup registryLookup) {
        return createNbt(registryLookup);
    }
}
