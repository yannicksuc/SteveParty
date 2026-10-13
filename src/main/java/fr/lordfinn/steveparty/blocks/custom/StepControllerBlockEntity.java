package fr.lordfinn.steveparty.blocks.custom;

import fr.lordfinn.steveparty.advancement.ModCriteria;
import fr.lordfinn.steveparty.blocks.ModBlockEntities;
import fr.lordfinn.steveparty.blocks.SyncedBlockEntity;
import fr.lordfinn.steveparty.blocks.custom.PartyController.PartyControllerEntity;
import fr.lordfinn.steveparty.blocks.custom.PartyController.StepPulse;
import fr.lordfinn.steveparty.blocks.custom.boardspaces.CartridgeContainerBlockEntity;
import fr.lordfinn.steveparty.minigame.MiniGamePages;
import fr.lordfinn.steveparty.minigame.MiniGamePagesState;
import fr.lordfinn.steveparty.minigame.MiniGamePipes;
import fr.lordfinn.steveparty.minigame.MiniGameSession;
import fr.lordfinn.steveparty.minigame.PartyMiniGameSession;
import fr.lordfinn.steveparty.utils.TickableBlockEntity;
import net.minecraft.block.BlockState;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtElement;
import net.minecraft.nbt.NbtHelper;
import net.minecraft.nbt.NbtList;
import net.minecraft.registry.RegistryKey;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.registry.RegistryWrapper;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.GlobalPos;
import org.jetbrains.annotations.Nullable;
import software.bernie.geckolib.animatable.GeoBlockEntity;
import software.bernie.geckolib.animatable.instance.AnimatableInstanceCache;
import software.bernie.geckolib.animation.*;
import software.bernie.geckolib.util.GeckoLibUtil;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * The Step Controller: a rising edge of redstone moves a party on, by its mode (see {@link StepPulse}: « next », the
 * party stops waiting for what it waits for; « replay », the step is played again). What it drives:
 * <ul>
 *     <li>linked with the Tile Linker Brush to an element of a party ({@link #getLink}, see
 *     {@link fr.lordfinn.steveparty.board.StepControllerLinks}): a board space or any block of a board (its board's
 *     party), the Party Controller (its party), a Mini-game Controller, a podium, a goal pole base or a pipe of a page
 *     (the mini-game of that page, played by a party or as a test), however far and in whatever dimension;</li>
 *     <li>else, linked to mini-game pages (page in hand, click: see {@code Podiums#clickLink}, the links kept by the
 *     pages and only mirrored here for the clients): the mini-game of one of those pages;</li>
 *     <li>else the nearest running party within {@value #RANGE} blocks.</li>
 * </ul>
 * A comparator reads the phase of the party it drives (see {@link PartyControllerEntity#getPhase}; a test of its
 * mini-game: the mini-game phase), 0 for none.
 */
public class StepControllerBlockEntity extends SyncedBlockEntity implements GeoBlockEntity, TickableBlockEntity {
    private final AnimatableInstanceCache cache = GeckoLibUtil.createInstanceCache(this);
    protected static final RawAnimation IDLE = RawAnimation.begin().thenLoop("idle");
    protected static final RawAnimation UP = RawAnimation.begin().thenLoop("up");
    protected static final RawAnimation SIDE = RawAnimation.begin().thenLoop("side");
    protected static final RawAnimation ACTIVATED = RawAnimation.begin().thenPlay("powered");
    public static final int RANGE = 64;
    private static final int CHECK_TICKS = 20;
    private StepPulse.Mode mode = StepPulse.Mode.NEXT;
    private boolean wasPowered = false;
    /** The party element it drives (Tile Linker Brush), null for none. */
    private @Nullable GlobalPos link;
    /** The pages it is linked to, as the pages say (checked again every second): what the clients show. */
    private List<UUID> linkedPages = List.of();
    /** The comparator output, checked again every second. */
    private int output;

    public StepControllerBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.STEP_CONTROLLER_ENTITY, pos, state);
    }

    @Override
    public void writeNbt(NbtCompound nbt, RegistryWrapper.WrapperLookup wrapper) {
        nbt.putInt("mode", mode.ordinal());
        nbt.putBoolean("wasPowered", wasPowered);
        if (link != null) {
            nbt.putString("LinkDimension", link.dimension().getValue().toString());
            nbt.putLong("LinkPos", link.pos().asLong());
        }
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
        mode = StepPulse.Mode.of(nbt.getInt("mode"));
        wasPowered = nbt.getBoolean("wasPowered");
        Identifier dimension = nbt.contains("LinkPos") ? Identifier.tryParse(nbt.getString("LinkDimension")) : null;
        link = dimension == null ? null : GlobalPos.create(RegistryKey.of(RegistryKeys.WORLD, dimension), BlockPos.fromLong(nbt.getLong("LinkPos")));
        List<UUID> pages = new ArrayList<>();
        for (NbtElement element : nbt.getList("LinkedPages", NbtElement.INT_ARRAY_TYPE)) pages.add(NbtHelper.toUuid(element));
        linkedPages = List.copyOf(pages);
    }

    @Override
    public void registerControllers(AnimatableManager.ControllerRegistrar controllerRegistrar) {
        controllerRegistrar.add(new AnimationController<>(this, "idle", 0, state -> state.setAndContinue(IDLE)));
        controllerRegistrar.add(new AnimationController<>(this, "mode", 5,
                state -> state.setAndContinue(mode == StepPulse.Mode.REPLAY ? SIDE : UP)));
        controllerRegistrar.add(new AnimationController<>(this, "activation", 5, this::activationAnimController));
    }

    private PlayState activationAnimController(AnimationState<StepControllerBlockEntity> state) {
        if (wasPowered || (!state.getController().hasAnimationFinished() && state.getController().getCurrentRawAnimation() == ACTIVATED)) {
            return state.setAndContinue(ACTIVATED);
        }
        return state.setAndContinue(IDLE);
    }

    @Override
    public AnimatableInstanceCache getAnimatableInstanceCache() {
        return cache;
    }

    public StepPulse.Mode getMode() {
        return mode;
    }

    /** The party element it drives (Tile Linker Brush), null for none. */
    public @Nullable GlobalPos getLink() {
        return link;
    }

    /** Links it to a party element (null: none), told to the clients (the brush draws it). */
    public void setLink(@Nullable GlobalPos link) {
        this.link = link;
        markDirty();
        syncToClients();
    }

    @Override
    public void tick() {
        if (!(this.world instanceof ServerWorld serverWorld)) return;
        if (serverWorld.getTime() % CHECK_TICKS == 0) {
            refreshLinkedPages();
            refreshOutput(serverWorld);
        }
        boolean isPowered = serverWorld.isReceivingRedstonePower(this.pos);
        if (isPowered == wasPowered) return;
        wasPowered = isPowered;
        if (isPowered) trigger();
        markDirty();
        syncToClients();
    }

    /** The pages this controller is linked to (server: as the pages say now; client: as last told). */
    public List<UUID> getLinkedPages() {
        if (this.world instanceof ServerWorld serverWorld) return MiniGamePages.pageIdsAt(serverWorld, this.pos);
        return linkedPages;
    }

    /** Tells the clients the pages it is linked to, if they changed (a link made or removed, here or in a page's editor). */
    public void refreshLinkedPages() {
        if (!(this.world instanceof ServerWorld)) return;
        List<UUID> pages = getLinkedPages();
        if (pages.equals(linkedPages)) return;
        linkedPages = List.copyOf(pages);
        markDirty();
        syncToClients();
    }

    /**
     * What it drives now: a party, or the pages of a mini-game (whoever plays it). Server side; null party and no
     * page: nothing (a link to an element whose party or pages are gone, or no party near).
     */
    public record Driven(@Nullable PartyControllerEntity party, List<UUID> pages) {
        static final Driven NONE = new Driven(null, List.of());

        /** The mini-game being played on its pages now, null for none. */
        public @Nullable MiniGameSession session() {
            return MiniGameSession.playing(pages);
        }
    }

    /** See {@link Driven}. */
    public Driven driven() {
        return driven(true);
    }

    /** @param load whether the chunk of its element may be loaded to find it out (a pulse), or not (its comparator) */
    private Driven driven(boolean load) {
        if (!(this.world instanceof ServerWorld serverWorld)) return Driven.NONE;
        if (link != null) return element(serverWorld, link, load);
        List<UUID> pages = getLinkedPages();
        if (!pages.isEmpty()) return new Driven(null, pages);
        // « next » never acts on an ended party: only running ones are found
        return new Driven(PartyControllerEntity.getClosestSteppablePartyControllerEntity(serverWorld, this.pos, RANGE, false).orElse(null), List.of());
    }

    /** What the element at {@code at} belongs to. */
    private static Driven element(ServerWorld here, GlobalPos at, boolean load) {
        ServerWorld world = here.getServer().getWorld(at.dimension());
        BlockPos pos = at.pos();
        if (world == null || (!load && !world.isChunkLoaded(pos))) return Driven.NONE;
        BlockEntity blockEntity = world.getBlockEntity(pos);
        if (blockEntity instanceof PartyControllerEntity party) return new Driven(party, List.of());
        if (blockEntity instanceof CartridgeContainerBlockEntity) {
            return new Driven(PartyControllerEntity.getControllerOfBoardSpace(world, pos, PartyControllerEntity.BOARD_NEARBY_RADIUS).orElse(null), List.of());
        }
        if (blockEntity instanceof MiniGameControllerBlockEntity controller) {
            UUID page = controller.getPageId();
            return page == null ? Driven.NONE : new Driven(null, List.of(page));
        }
        BlockState state = world.getBlockState(pos);
        if (state.getBlock() instanceof PodiumBlock) return new Driven(null, MiniGamePages.pageIdsAt(world, PodiumBlock.bottomOf(world, pos)));
        if (MiniGamePipes.linkable(state)) {
            List<UUID> pages = new ArrayList<>();
            for (MiniGamePagesState.Linked linked : MiniGamePages.linksAt(world.getServer(), at)) pages.add(linked.page().id());
            return new Driven(null, pages);
        }
        return new Driven(null, MiniGamePages.pageIdsAt(world, pos));
    }

    /** A redstone pulse: what it drives moves by its mode, said by its sound (a low one: nothing could be done). */
    public boolean trigger() {
        if (!(this.world instanceof ServerWorld serverWorld)) return false;
        Driven driven = driven();
        boolean done;
        if (driven.party() != null) {
            done = StepPulse.apply(driven.party(), mode);
            if (done) ModCriteria.fireNear(serverWorld, this.pos.toCenterPos(), 16, "step_controller_pulse", mode.ordinal() + 1, null);
        } else {
            MiniGameSession session = driven.session();
            done = session != null && session.step(mode);
        }
        serverWorld.playSound(null, this.pos, done ? SoundEvents.BLOCK_TRIAL_SPAWNER_OPEN_SHUTTER : SoundEvents.EVENT_MOB_EFFECT_TRIAL_OMEN,
                SoundCategory.BLOCKS, 1.0F, 1.0F);
        refreshOutput(serverWorld);
        return done;
    }

    /** The comparator output: the phase of the party it drives (a test of its mini-game: the mini-game phase), 0 for none. */
    public int getComparatorOutput() {
        return output;
    }

    private void refreshOutput(ServerWorld serverWorld) {
        Driven driven = driven(false);
        int phase;
        if (driven.party() != null) {
            phase = driven.party().getPhase();
        } else {
            MiniGameSession session = driven.session();
            phase = session instanceof PartyMiniGameSession played ? played.controller().getPhase()
                    : session != null ? PartyControllerEntity.PHASE_MINIGAME : 0;
        }
        if (phase == output) return;
        output = phase;
        serverWorld.updateComparators(this.pos, getCachedState().getBlock());
    }

    /** A click: the other mode (« next » up, « replay » sideways). */
    public void cycleMode() {
        if (this.world == null) return;
        mode = mode.cycle();
        this.world.playSound(null, this.getPos(), SoundEvents.BLOCK_COPPER_TRAPDOOR_OPEN, SoundCategory.BLOCKS, 1.0F, 2.0F);
        markDirty();
        syncToClients();
    }
}
