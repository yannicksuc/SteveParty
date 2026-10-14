package fr.lordfinn.steveparty.board;

import fr.lordfinn.steveparty.blocks.custom.MiniGameControllerBlockEntity;
import fr.lordfinn.steveparty.blocks.custom.PodiumBlock;
import fr.lordfinn.steveparty.blocks.custom.GoalPoleBaseBlock;
import fr.lordfinn.steveparty.blocks.custom.pipe.MiniGamePipeBlock;
import fr.lordfinn.steveparty.blocks.custom.pipe.MiniGamePipeBlockEntity;
import fr.lordfinn.steveparty.minigame.MiniGamePageData;
import fr.lordfinn.steveparty.minigame.MiniGamePages;
import fr.lordfinn.steveparty.minigame.MiniGamePipeLink;
import fr.lordfinn.steveparty.minigame.MiniGamePipeRole;
import fr.lordfinn.steveparty.minigame.MiniGamePipes;
import fr.lordfinn.steveparty.minigame.MiniGamePodiumLink;
import fr.lordfinn.steveparty.podium.Podiums;
import fr.lordfinn.steveparty.sounds.ModSounds;
import net.minecraft.block.BlockState;
import net.minecraft.item.ItemStack;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.GlobalPos;
import net.minecraft.world.World;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import java.util.function.Function;

/**
 * The Tile Linker Brush's links from a Mini-game Controller to the elements of its mini-game: painting from the
 * controller to a block writes it on the page the controller holds, exactly as a click on it with that page would.
 * <ul>
 *     <li>{@link Pipes}: a pipe mouth (the role of its colour), drawn in the colour of its role;</li>
 *     <li>{@link Results}: a podium column, a goal pole base, a Step Controller;</li>
 *     <li>{@link Entrances}: a mini-game pipe, printed with the page (painting over it again wipes it off).</li>
 * </ul>
 * A controller without page links nothing (the player is told). Same answers on both sides: the client reads the
 * page from its cache ({@link #clientPages}, set by the client).
 */
public final class MiniGameControllerLinks {
    /** The colour of the links to the podiums, goal pole bases and Step Controllers. */
    public static final int RESULTS_COLOR = 0xF2C230;
    /** The colour of the links to the mini-game pipes. */
    public static final int ENTRANCE_COLOR = 0xD08A4A;

    /** Client side: the content of a page, null while not known (the client asks for it). */
    public static volatile Function<UUID, MiniGamePageData> clientPages = id -> null;

    static final BrushLinks.Provider PROVIDER = (world, holder, held, out) -> {
        if (!(holder instanceof MiniGameControllerBlockEntity controller) || controller.getPageId() == null) return;
        out.add(new Pipes(controller));
        out.add(new Results(controller));
        out.add(new Entrances(controller));
    };

    private MiniGameControllerLinks() {
    }

    /** Whether the block at {@code pos} is a Mini-game Controller holding no page (the brush says so). */
    public static boolean emptyController(World world, BlockPos pos) {
        return world.getBlockEntity(pos) instanceof MiniGameControllerBlockEntity controller && controller.getPageId() == null;
    }

    /** The page of {@code controller} as its side knows it, null if it has none or it is not known yet. */
    static @Nullable MiniGamePageData page(MiniGameControllerBlockEntity controller) {
        UUID id = controller.getPageId();
        World world = controller.getWorld();
        if (id == null || world == null) return null;
        if (world instanceof ServerWorld server) return MiniGamePages.get(server.getServer(), id);
        return clientPages.apply(id);
    }

    private static boolean canEdit(ServerPlayerEntity player) {
        if (MiniGamePages.canEdit(player)) return true;
        player.sendMessage(Text.translatable("gui.steveparty.mini_game_page.status.not_allowed").formatted(Formatting.RED), true);
        return false;
    }

    /** The links of the page before a change, for undo. */
    private static LinkHistory.PageLinksChange change(BlockPos controller, MiniGamePageData before, MiniGamePageData after) {
        return new LinkHistory.PageLinksChange(controller, before.id(), before.pipeLinks(), before.podiumLinks(), after.pipeLinks(), after.podiumLinks());
    }

    /** A kind of link of a Mini-game Controller: to elements written on its page. */
    private abstract static sealed class PageKind implements BrushLinkable permits Pipes, Results, Entrances {
        protected final MiniGameControllerBlockEntity controller;

        PageKind(MiniGameControllerBlockEntity controller) {
            this.controller = controller;
        }

        @Override
        public BlockPos holder() {
            return controller.getPos();
        }

        @Override
        public boolean equals(Object other) {
            return other != null && other.getClass() == getClass() && ((PageKind) other).controller == controller;
        }

        @Override
        public int hashCode() {
            return Objects.hash(getClass(), controller);
        }
    }

    /** The pipe mouths of the page. */
    public static final class Pipes extends PageKind {
        Pipes(MiniGameControllerBlockEntity controller) {
            super(controller);
        }

        @Override
        public int color() {
            return MiniGamePipeRole.PLAYERS.color();
        }

        @Override
        public boolean accepts(World world, BlockPos target) {
            return MiniGamePipes.linkable(world.getBlockState(target));
        }

        @Override
        public List<BlockPos> targets(World world) {
            MiniGamePageData page = page(controller);
            if (page == null) return List.of();
            List<BlockPos> out = new ArrayList<>();
            for (MiniGamePipeLink link : page.pipeLinks()) if (link.mouth().dimension().equals(world.getRegistryKey())) out.add(link.mouth().pos());
            return out;
        }

        @Override
        public boolean link(ServerPlayerEntity player, ServerWorld world, BlockPos target) {
            UUID id = controller.getPageId();
            if (id == null || !canEdit(player)) return false;
            BlockState state = world.getBlockState(target);
            Direction opening = MiniGamePipes.mouthOf(state, target, player.getEyePos());
            if (opening == null) {
                player.sendMessage(Text.translatable("message.steveparty.mini_game_page.pipe.no_mouth").formatted(Formatting.RED), true);
                return false;
            }
            MiniGamePageData before = MiniGamePages.get(world.getServer(), id);
            MiniGamePipeLink link = MiniGamePages.toggleLink(world.getServer(), id,
                    MiniGamePipeLink.of(GlobalPos.create(world.getRegistryKey(), target.toImmutable()), opening, state));
            if (link == null) {
                player.sendMessage(Text.translatable("message.steveparty.mini_game_page.pipe.full", MiniGamePageData.MAX_PIPE_LINKS)
                        .formatted(Formatting.RED), true);
                return false;
            }
            LinkHistory.record(player, change(holder(), before, MiniGamePages.get(world.getServer(), id)));
            WrenchActions.say(player, Text.translatable("message.steveparty.mini_game_page.pipe.linked", target.getX(), target.getY(), target.getZ(),
                    link.role().text()));
            ModSounds.playSelect(world, target);
            return true;
        }

        @Override
        public boolean unlink(ServerPlayerEntity player, ServerWorld world, BlockPos target) {
            UUID id = controller.getPageId();
            if (id == null || !canEdit(player)) return false;
            MiniGamePageData before = MiniGamePages.get(world.getServer(), id);
            if (!MiniGamePages.removeLink(world.getServer(), id, GlobalPos.create(world.getRegistryKey(), target))) return false;
            LinkHistory.record(player, change(holder(), before, MiniGamePages.get(world.getServer(), id)));
            WrenchActions.say(player, Text.translatable("message.steveparty.mini_game_page.pipe.unlinked", target.getX(), target.getY(), target.getZ()));
            ModSounds.playCancel(world, target);
            return true;
        }
    }

    /** The podiums, goal pole bases and Step Controllers of the page (what ends the mini-game and gives its places). */
    public static final class Results extends PageKind {
        Results(MiniGameControllerBlockEntity controller) {
            super(controller);
        }

        @Override
        public int color() {
            return RESULTS_COLOR;
        }

        @Override
        public boolean accepts(World world, BlockPos target) {
            return Podiums.linkable(world.getBlockState(target));
        }

        /** The block a target is written as: a podium by the bottom of its column. */
        private static BlockPos anchor(World world, BlockPos target) {
            return PodiumBlock.isPodium(world.getBlockState(target)) ? PodiumBlock.bottomOf(world, target) : target.toImmutable();
        }

        @Override
        public List<BlockPos> targets(World world) {
            MiniGamePageData page = page(controller);
            if (page == null) return List.of();
            List<BlockPos> out = new ArrayList<>();
            for (MiniGamePodiumLink link : page.podiumLinks()) if (link.pos().dimension().equals(world.getRegistryKey())) out.add(link.pos().pos());
            return out;
        }

        @Override
        public boolean linked(World world, BlockPos target) {
            return targets(world).contains(anchor(world, target));
        }

        private static MiniGamePodiumLink.Kind kindOf(BlockState state) {
            if (PodiumBlock.isPodium(state)) return MiniGamePodiumLink.Kind.PODIUM;
            return state.getBlock() instanceof GoalPoleBaseBlock ? MiniGamePodiumLink.Kind.COUNTER : MiniGamePodiumLink.Kind.STEP_CONTROLLER;
        }

        @Override
        public boolean link(ServerPlayerEntity player, ServerWorld world, BlockPos target) {
            UUID id = controller.getPageId();
            if (id == null || !canEdit(player)) return false;
            MiniGamePodiumLink.Kind kind = kindOf(world.getBlockState(target));
            BlockPos anchor = anchor(world, target);
            MiniGamePageData before = MiniGamePages.get(world.getServer(), id);
            if (!MiniGamePages.addPodiumLink(world.getServer(), id, new MiniGamePodiumLink(GlobalPos.create(world.getRegistryKey(), anchor), kind))) {
                player.sendMessage(Text.translatable("message.steveparty.mini_game_page.podium.full", MiniGamePageData.MAX_PODIUM_LINKS)
                        .formatted(Formatting.RED), true);
                return false;
            }
            LinkHistory.record(player, change(holder(), before, MiniGamePages.get(world.getServer(), id)));
            WrenchActions.say(player, Text.translatable("message.steveparty.mini_game_page." + kind.key() + ".linked", anchor.getX(), anchor.getY(), anchor.getZ()));
            ModSounds.playSelect(world, target);
            return true;
        }

        @Override
        public boolean unlink(ServerPlayerEntity player, ServerWorld world, BlockPos target) {
            UUID id = controller.getPageId();
            if (id == null || !canEdit(player)) return false;
            MiniGamePodiumLink.Kind kind = kindOf(world.getBlockState(target));
            BlockPos anchor = anchor(world, target);
            // A podium is its whole column: unlinked whatever block of it was linked
            List<GlobalPos> blocks = new ArrayList<>();
            if (kind == MiniGamePodiumLink.Kind.PODIUM) {
                BlockPos top = PodiumBlock.topOf(world, target);
                for (BlockPos block = anchor; block.getY() <= top.getY(); block = block.up()) blocks.add(GlobalPos.create(world.getRegistryKey(), block));
            } else {
                blocks.add(GlobalPos.create(world.getRegistryKey(), anchor));
            }
            MiniGamePageData before = MiniGamePages.get(world.getServer(), id);
            if (!MiniGamePages.removePodiumLinks(world.getServer(), id, blocks)) return false;
            LinkHistory.record(player, change(holder(), before, MiniGamePages.get(world.getServer(), id)));
            WrenchActions.say(player, Text.translatable("message.steveparty.mini_game_page." + kind.key() + ".unlinked", anchor.getX(), anchor.getY(), anchor.getZ()));
            ModSounds.playCancel(world, target);
            return true;
        }
    }

    /** The mini-game pipes printed with the page: the ways into the mini-game. */
    public static final class Entrances extends PageKind {
        Entrances(MiniGameControllerBlockEntity controller) {
            super(controller);
        }

        @Override
        public int color() {
            return ENTRANCE_COLOR;
        }

        @Override
        public boolean accepts(World world, BlockPos target) {
            return world.getBlockState(target).getBlock() instanceof MiniGamePipeBlock;
        }

        /** Only the loaded mini-game pipes near are drawn: the client knows no index of them. */
        @Override
        public List<BlockPos> targets(World world) {
            return List.of();
        }

        @Override
        public boolean linked(World world, BlockPos target) {
            UUID id = controller.getPageId();
            return id != null && id.equals(MiniGamePages.idOf(MiniGamePipeBlock.pageAt(world, target)));
        }

        @Override
        public boolean link(ServerPlayerEntity player, ServerWorld world, BlockPos target) {
            if (!(world.getBlockEntity(target) instanceof MiniGamePipeBlockEntity pipe) || controller.getPage().isEmpty() || !canEdit(player)) return false;
            ItemStack before = pipe.getPage().copy();
            ItemStack page = controller.getPage().copyWithCount(1);
            MiniGamePages.refresh(world.getServer(), page);
            pipe.setPage(page);
            LinkHistory.record(player, new LinkHistory.PipePageChange(target.toImmutable(), before, page.copy()));
            WrenchActions.say(player, Text.translatable("message.steveparty.minigame_pipe.programmed", page.getName()));
            ModSounds.playSelect(world, target);
            return true;
        }

        @Override
        public boolean unlink(ServerPlayerEntity player, ServerWorld world, BlockPos target) {
            if (!(world.getBlockEntity(target) instanceof MiniGamePipeBlockEntity pipe) || pipe.getPage().isEmpty() || !canEdit(player)) return false;
            ItemStack before = pipe.getPage().copy();
            pipe.setPage(ItemStack.EMPTY);
            LinkHistory.record(player, new LinkHistory.PipePageChange(target.toImmutable(), before, ItemStack.EMPTY));
            WrenchActions.say(player, Text.translatable("message.steveparty.minigame_pipe.page_taken"));
            ModSounds.playCancel(world, target);
            return true;
        }
    }
}
