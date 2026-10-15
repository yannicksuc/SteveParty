package fr.lordfinn.steveparty.blocks.custom;

import fr.lordfinn.steveparty.advancement.ModCriteria;
import fr.lordfinn.steveparty.blocks.ModBlockEntities;
import fr.lordfinn.steveparty.blocks.SyncedBlockEntity;
import fr.lordfinn.steveparty.blocks.custom.PartyController.PartyControllerEntity;
import fr.lordfinn.steveparty.blocks.custom.PartyController.StepPulse;
import fr.lordfinn.steveparty.board.LinkedParty;
import fr.lordfinn.steveparty.board.PartyLink;
import fr.lordfinn.steveparty.board.PartyLinked;
import fr.lordfinn.steveparty.minigame.MiniGamePages;
import fr.lordfinn.steveparty.minigame.MiniGameSession;
import fr.lordfinn.steveparty.minigame.PartyMiniGameSession;
import fr.lordfinn.steveparty.utils.TickableBlockEntity;
import net.minecraft.block.BlockState;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtElement;
import net.minecraft.nbt.NbtHelper;
import net.minecraft.nbt.NbtList;
import net.minecraft.registry.RegistryWrapper;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.text.Text;
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
 * The Party Rewinder (« Magnéto de Party », registry id {@code step_controller}): a tape deck whose rising edge of
 * redstone moves a party on, by its mode, the button pushed in on its top (see {@link StepPulse}: « next », the party
 * stops waiting for what it waits for; « replay », the step is played again, a turn undone first; « previous », back to
 * the turn before). A pulse presses its button and spins its reels (back, back then play, forward), with their sound; a
 * pulse that can't act shakes it, with the dispenser's fail click. What it drives:
 * <ul>
 *     <li>linked with the Tile Linker Brush to an element of a party ({@link #getLink}, see
 *     {@link fr.lordfinn.steveparty.board.PartyLinks}): a board space or any block of a board (its board's
 *     party), the Party Controller (its party), a Mini-game Controller, a podium, a goal pole base or a pipe of a page
 *     (the mini-game of that page, played by a party or as a test), however far and in whatever dimension;</li>
 *     <li>else, linked to mini-game pages (page in hand, click: see {@code Podiums#clickLink}, the links kept by the
 *     pages and only mirrored here for the clients): the mini-game of one of those pages;</li>
 *     <li>else the nearest running party within {@value #RANGE} blocks.</li>
 * </ul>
 * A comparator reads the phase of the party it drives (see {@link PartyControllerEntity#getPhase}; a test of its
 * mini-game: the mini-game phase), 0 for none.
 */
public class StepControllerBlockEntity extends SyncedBlockEntity implements GeoBlockEntity, TickableBlockEntity, PartyLinked {
    private final AnimatableInstanceCache cache = GeckoLibUtil.createInstanceCache(this);
    /** The animations played on a pulse (see {@link #triggerAnim}), in the controller {@value #ACTION}. */
    public static final String ACTION = "action", REFUSED = "refused";
    /** How long the reels' sound lasts after a pulse (ticks), one tick of it every 2 ticks. */
    private static final int TAPE_TICKS = 16;
    /** Its chosen button pushed in, by mode. */
    private static final RawAnimation[] MODE_ANIMATIONS = java.util.Arrays.stream(StepPulse.Mode.values())
            .map(each -> RawAnimation.begin().thenLoop("mode." + each.id())).toArray(RawAnimation[]::new);
    public static final int RANGE = 64;
    private static final int CHECK_TICKS = 20;
    private StepPulse.Mode mode = StepPulse.Mode.NEXT;
    private boolean wasPowered = false;
    /** The party element it drives (Tile Linker Brush), null for none. */
    private final PartyLink link = new PartyLink(this, this::syncToClients);
    /** The pages it is linked to, as the pages say (checked again every second): what the clients show. */
    private List<UUID> linkedPages = List.of();
    /** The comparator output, checked again every second. */
    private int output;
    /** Ticks since the pulse whose tape sound is playing, -1 for none (server). */
    private int tapeTicks = -1;
    private StepPulse.Mode tapeMode = StepPulse.Mode.NEXT;

    public StepControllerBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.STEP_CONTROLLER_ENTITY, pos, state);
    }

    @Override
    public void writeNbt(NbtCompound nbt, RegistryWrapper.WrapperLookup wrapper) {
        nbt.putInt("mode", mode.ordinal());
        nbt.putBoolean("wasPowered", wasPowered);
        link.write(nbt);
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
        link.read(nbt);
        List<UUID> pages = new ArrayList<>();
        for (NbtElement element : nbt.getList("LinkedPages", NbtElement.INT_ARRAY_TYPE)) pages.add(NbtHelper.toUuid(element));
        linkedPages = List.copyOf(pages);
    }

    /** Its chosen button stays pushed in ({@code mode.<id>}); a pulse plays {@code act.<id>}, or {@value #REFUSED}. */
    @Override
    public void registerControllers(AnimatableManager.ControllerRegistrar controllerRegistrar) {
        controllerRegistrar.add(new AnimationController<>(this, "mode", 2, state -> state.setAndContinue(MODE_ANIMATIONS[mode.ordinal()])));
        AnimationController<StepControllerBlockEntity> action = new AnimationController<>(this, ACTION, 0, state -> PlayState.STOP);
        for (StepPulse.Mode each : StepPulse.Mode.values()) {
            action.triggerableAnim(actName(each), RawAnimation.begin().thenPlay(actName(each)));
        }
        controllerRegistrar.add(action.triggerableAnim(REFUSED, RawAnimation.begin().thenPlay(REFUSED)));
    }

    private static String actName(StepPulse.Mode mode) {
        return "act." + mode.id();
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
        return link.get();
    }

    /** Links it to a party element (null: none), told to the clients (the brush draws it). */
    public void setLink(@Nullable GlobalPos link) {
        this.link.set(link);
    }

    @Override
    public PartyLink partyLink() {
        return link;
    }

    @Override
    public void tick() {
        if (!(this.world instanceof ServerWorld serverWorld)) return;
        if (tapeTicks >= 0) playTape(serverWorld);
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
        if (link.get() != null) return element(serverWorld, link.get(), load);
        List<UUID> pages = getLinkedPages();
        if (!pages.isEmpty()) return new Driven(null, pages);
        // « next » never acts on an ended party: only running ones are found
        return new Driven(PartyControllerEntity.getClosestSteppablePartyControllerEntity(serverWorld, this.pos, RANGE, false).orElse(null), List.of());
    }

    /** What the element at {@code at} belongs to (see {@link LinkedParty}). */
    private static Driven element(ServerWorld here, GlobalPos at, boolean load) {
        LinkedParty linked = LinkedParty.of(here, at, load);
        return linked.party() == null && linked.pages().isEmpty() ? Driven.NONE : new Driven(linked.party(), linked.pages());
    }

    /** A redstone pulse: what it drives moves by its mode, shown by its button and reels (a shake: nothing could be done). */
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
        if (done) {
            triggerAnim(ACTION, actName(mode));
            serverWorld.playSound(null, this.pos, SoundEvents.BLOCK_STONE_BUTTON_CLICK_ON, SoundCategory.BLOCKS, 0.6F, 0.8F);
            tapeMode = mode;
            tapeTicks = 0;
        } else {
            triggerAnim(ACTION, REFUSED);
            serverWorld.playSound(null, this.pos, SoundEvents.BLOCK_DISPENSER_FAIL, SoundCategory.BLOCKS, 1.0F, 1.2F);
        }
        refreshOutput(serverWorld);
        return done;
    }

    /**
     * The reels' whirr after a pulse, a tick of a hi-hat every 2 ticks: falling for « previous » (rewinding), rising for
     * « next » (fast-forwarding); « replay » rewinds a little then clicks back into play.
     */
    private void playTape(ServerWorld serverWorld) {
        int tick = tapeTicks++;
        if (tick >= TAPE_TICKS) {
            tapeTicks = -1;
            return;
        }
        if (tick % 2 != 0) return;
        float progress = tick / (float) TAPE_TICKS;
        if (tapeMode == StepPulse.Mode.REPLAY && tick >= TAPE_TICKS / 2) {
            if (tick == TAPE_TICKS / 2 + 2) serverWorld.playSound(null, this.pos, SoundEvents.BLOCK_LEVER_CLICK, SoundCategory.BLOCKS, 0.5F, 1.4F);
            return;
        }
        float pitch = switch (tapeMode) {
            case PREVIOUS -> 2.0F - progress;
            case NEXT -> 1.0F + progress;
            case REPLAY -> 2.0F - 2 * progress;
        };
        serverWorld.playSound(null, this.pos, SoundEvents.BLOCK_NOTE_BLOCK_HAT.value(), SoundCategory.BLOCKS, 0.35F, pitch);
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

    /** A click elsewhere than on a button: the next mode (« next », « replay », « previous »). */
    public void cycleMode() {
        cycleMode(null);
    }

    /** {@link #cycleMode()}, said to {@code player} over his hotbar. */
    public void cycleMode(@Nullable PlayerEntity player) {
        selectMode(mode.cycle(), player);
    }

    /** A click on the button of {@code mode}: it is pushed in, and what a pulse would do is said to {@code player}. */
    public void selectMode(StepPulse.Mode mode, @Nullable PlayerEntity player) {
        if (this.world == null) return;
        boolean changed = mode != this.mode;
        this.mode = mode;
        this.world.playSound(null, this.getPos(), SoundEvents.BLOCK_STONE_BUTTON_CLICK_ON, SoundCategory.BLOCKS, 0.5F, changed ? 1.3F : 1.6F);
        if (player != null) player.sendMessage(describe(mode), true);
        if (!changed) return;
        markDirty();
        syncToClients();
    }

    /** « ⏮ Précédent : 2 tours possibles »: the mode, and what a pulse would do now to what it drives. */
    public Text describe(StepPulse.Mode mode) {
        Text hint;
        Driven driven = driven(false);
        if (driven.party() != null) {
            hint = StepPulse.preview(driven.party(), mode);
        } else if (driven.session() != null) {
            hint = Text.translatable("block.steveparty.step_controller.hint."
                    + (mode == StepPulse.Mode.PREVIOUS ? "nothing" : mode.id() + ".mini_game"));
        } else {
            hint = Text.translatable("block.steveparty.step_controller.hint.none");
        }
        return Text.translatable("block.steveparty.step_controller.mode", mode.label(), hint.copy().styled(style -> style.withColor(HINT_COLOR)));
    }

    /** The hint after the mode's name: a light grey, readable over any ground under the action bar. */
    private static final int HINT_COLOR = 0xE0E0E0;
}
