package fr.lordfinn.steveparty.blocks.custom;

import fr.lordfinn.steveparty.blocks.SyncedBlockEntity;
import fr.lordfinn.steveparty.blocks.ModBlockEntities;
import fr.lordfinn.steveparty.blocks.custom.PartyController.PartyControllerEntity;
import fr.lordfinn.steveparty.minigame.MiniGamePages;
import fr.lordfinn.steveparty.minigame.MiniGameSession;
import fr.lordfinn.steveparty.utils.TickableBlockEntity;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import net.minecraft.block.BlockState;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtElement;
import net.minecraft.nbt.NbtHelper;
import net.minecraft.nbt.NbtList;
import net.minecraft.registry.RegistryWrapper;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.util.math.BlockPos;
import software.bernie.geckolib.animatable.GeoBlockEntity;
import software.bernie.geckolib.animatable.instance.AnimatableInstanceCache;
import software.bernie.geckolib.animation.*;
import software.bernie.geckolib.util.GeckoLibUtil;

/**
 * The step controller: a redstone pulse (rising edge) moves a party one step, by its mode (0: next, 1: restart, 2:
 * previous).
 * <ul>
 *     <li>Not linked: the nearest party within {@value #RANGE} blocks, in its world.</li>
 *     <li>Linked to mini-game pages (page in hand, click: see {@code Podiums#clickLink}): the mini-game being played
 *     right now on one of those pages, by a party or as a test, however far and in whatever dimension; none: the pulse
 *     does nothing. « Next » ends the mini-game with the places as they stand (see {@code MiniGamePartyStep}).</li>
 * </ul>
 * The links are kept by the pages; the pages it is linked to are only mirrored here for the clients (its label).
 */
public class StepControllerBlockEntity extends SyncedBlockEntity implements GeoBlockEntity, TickableBlockEntity {
    private final AnimatableInstanceCache cache = GeckoLibUtil.createInstanceCache(this);
    protected static final RawAnimation IDLE = RawAnimation.begin().thenLoop("idle");
    protected static final RawAnimation UP = RawAnimation.begin().thenLoop("up");
    protected static final RawAnimation SIDE = RawAnimation.begin().thenLoop("side");
    protected static final RawAnimation DOWN = RawAnimation.begin().thenLoop("down");
    protected static final RawAnimation ACTIVATED = RawAnimation.begin().thenPlay ("powered");
    private static final int RANGE = 64; //TODO Add config to manage range
    public int mode = 0;
    public boolean wasPowered = false;
    /** The pages it is linked to, as the pages say (checked again every second): what the clients show. */
    private List<UUID> linkedPages = List.of();
    private static final int LINK_CHECK_TICKS = 20;


    public StepControllerBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.STEP_CONTROLLER_ENTITY, pos, state);
    }

    @Override
    public void writeNbt(NbtCompound nbt, RegistryWrapper.WrapperLookup wrapper) {
        nbt.putInt("mode", this.mode);
        nbt.putBoolean("wasPowered", this.wasPowered);
        if (!linkedPages.isEmpty()) {
            NbtList pages = new NbtList();
            linkedPages.forEach(page -> pages.add(NbtHelper.fromUuid(page)));
            nbt.put("LinkedPages", pages);
        }
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
        List<UUID> pages = new ArrayList<>();
        for (NbtElement element : nbt.getList("LinkedPages", NbtElement.INT_ARRAY_TYPE)) pages.add(NbtHelper.toUuid(element));
        this.linkedPages = List.copyOf(pages);
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
            if (this.world.getTime() % LINK_CHECK_TICKS == 0) refreshLinkedPages();
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

    /** The pages this controller is linked to (server: as the pages say now; client: as last told). */
    public List<UUID> getLinkedPages() {
        if (this.world instanceof ServerWorld serverWorld) {
            return MiniGamePages.pageIdsAt(serverWorld, this.pos);
        }
        return linkedPages;
    }

    /** Tells the clients the pages it is linked to, if they changed (a link made or removed, here or in a page's editor). */
    public void refreshLinkedPages() {
        if (!(this.world instanceof ServerWorld)) return;
        List<UUID> pages = getLinkedPages();
        if (pages.equals(linkedPages)) return;
        linkedPages = List.copyOf(pages);
        this.markDirty();
        this.sync();
    }

    /** The party a pulse acts on now: the one playing a linked page's mini-game, or (not linked) the nearest one. */
    public Optional<PartyControllerEntity> target() {
        if (!(this.world instanceof ServerWorld)) return Optional.empty();
        List<UUID> pages = getLinkedPages();
        if (!pages.isEmpty()) return PartyControllerEntity.getPartyPlayingPage(pages);
        // "previous" may also bring back a party that reached its END step
        return PartyControllerEntity.getClosestSteppablePartyControllerEntity(this.world, this.pos, RANGE, this.mode == 2);
    }

    /**
     * A redstone pulse: linked to a page, the mini-game being played on it (a party's or a test's) ends or stops, by
     * the controller's mode; else the party it acts on ({@link #target}) moves by that mode.
     */
    public void trigger() {
        if (this.world != null && this.world instanceof ServerWorld) {
            List<UUID> pages = getLinkedPages();
            if (!pages.isEmpty()) {
                MiniGameSession session = MiniGameSession.playing(pages);
                world.playSound(null, this.pos, session != null ? SoundEvents.BLOCK_TRIAL_SPAWNER_OPEN_SHUTTER : SoundEvents.EVENT_MOB_EFFECT_TRIAL_OMEN,
                        SoundCategory.BLOCKS, 1.0F, 1.0F);
                if (session != null) session.step(this.mode);
                return;
            }
            target()
                    .ifPresentOrElse(partyControllerEntity ->  {
                        world.playSound(null, this.pos, SoundEvents.BLOCK_TRIAL_SPAWNER_ABOUT_TO_SPAWN_ITEM, SoundCategory.BLOCKS, 1.0F, 1.0F);
                        switch (this.mode) {
                            case 0 -> partyControllerEntity.nextStep();
                            case 1 -> partyControllerEntity.restartStep();
                            case 2 -> partyControllerEntity.previousStep();
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
}
