package fr.lordfinn.steveparty.blocks.custom.PartyController;

import fr.lordfinn.steveparty.blocks.custom.PodiumBlock;
import fr.lordfinn.steveparty.blocks.custom.PodiumBlockEntity;
import fr.lordfinn.steveparty.blocks.custom.boardspaces.ABoardSpaceBlock;
import fr.lordfinn.steveparty.blocks.custom.boardspaces.BoardSpaceType;
import fr.lordfinn.steveparty.components.ModComponents;
import fr.lordfinn.steveparty.items.custom.PartyCardItem;
import fr.lordfinn.steveparty.minigame.MiniGamePipeLink;
import fr.lordfinn.steveparty.minigame.MiniGamePipeRole;
import fr.lordfinn.steveparty.minigame.MiniGamePipes;
import fr.lordfinn.steveparty.podium.PodiumGroup;
import net.minecraft.block.BlockState;
import net.minecraft.entity.mob.MobEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.GlobalPos;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/**
 * Where everyone goes once a party is over (its END step):
 * <ul>
 *     <li>the pawns, best place first: onto the podiums linked to the Party Controller (one per column, the tallest
 *     first, facing the podium's front), then onto its Finish spaces linked to it (several on one space: small and
 *     turning, see {@link PartyParking}), the others back onto their start tiles;</li>
 *     <li>the players: out of the pipe the End party card of its program is linked to, if any;</li>
 *     <li>the ranking stays on the controller, shown over its podiums until the next party starts.</li>
 * </ul>
 */
public final class PartyEnding {
    private PartyEnding() {
    }

    /** The party of {@code controller} is over, ranked {@code places}: its pawns {@code tokens} and its players go to their places. */
    public static void partyOver(PartyControllerEntity controller, ServerWorld world, List<PartyResults.Place> places, Collection<UUID> tokens) {
        List<UUID> ranked = ranked(controller, places, tokens);
        PartyParking parking = controller.tokenHomes().parking();
        List<UUID> home = new ArrayList<>();
        int next = 0;
        // The podiums: one pawn a column, the best place on the tallest
        for (PodiumGroup.Column column : podiumColumns(controller, world)) {
            if (next >= ranked.size()) break;
            UUID token = ranked.get(next++);
            PodiumBlockEntity master = column.master();
            // A figure left by a mini-game would stand in the pawn
            if (master != null && master.getOccupant() != null) master.setOccupant(null);
            parking.park(world, token, column.top());
            if (world.getEntity(token) instanceof MobEntity mob) face(mob, world.getBlockState(column.top()));
        }
        // The Finish spaces linked to the controller: the others, in turn
        List<BlockPos> arrivals = arrivalSpaces(controller, world);
        for (int i = 0; next < ranked.size() && !arrivals.isEmpty(); i++) {
            parking.park(world, ranked.get(next++), arrivals.get(i % arrivals.size()));
        }
        while (next < ranked.size()) home.add(ranked.get(next++));
        controller.sendTokensHome(world, home);
        // The players out of the End card's pipe
        EndPipe pipe = endPipe(controller);
        if (pipe != null) {
            MiniGamePipeLink link = new MiniGamePipeLink(pipe.mouth(), pipe.opening(), MiniGamePipeRole.PLAYERS);
            for (PartyResults.Place place : places) {
                ServerPlayerEntity player = place.owner() == null ? null : world.getServer().getPlayerManager().getPlayer(place.owner());
                if (player != null) MiniGamePipes.emerge(world.getServer(), link, player);
            }
        }
        controller.setEndResults(places);
    }

    /** The pawns in the order of their places (a player's pawns together), those of no place last. */
    static List<UUID> ranked(PartyControllerEntity controller, List<PartyResults.Place> places, Collection<UUID> tokens) {
        Set<UUID> ranked = new LinkedHashSet<>();
        for (PartyResults.Place place : places) {
            if (place.token() != null && tokens.contains(place.token())) ranked.add(place.token());
            if (place.owner() == null) continue;
            for (UUID token : tokens) if (place.owner().equals(controller.getTokenOwner(token))) ranked.add(token);
        }
        ranked.addAll(tokens);
        return new ArrayList<>(ranked);
    }

    /** The columns of the podiums linked to the controller (each group once), the best place first. */
    static List<PodiumGroup.Column> podiumColumns(PartyControllerEntity controller, ServerWorld world) {
        List<PodiumGroup.Column> columns = new ArrayList<>();
        Set<GlobalPos> seen = new LinkedHashSet<>();
        for (BlockPos linked : controller.getPodiumLinks()) {
            if (!world.isChunkLoaded(linked) || !PodiumBlock.isPodium(world.getBlockState(linked))) continue;
            PodiumGroup group = PodiumGroup.of(world, linked);
            for (PodiumGroup.Column column : group.columns()) if (seen.add(column.id())) columns.add(column);
        }
        // Several groups: the tallest columns first all the same
        columns.sort((a, b) -> Integer.compare(b.height(), a.height()));
        return columns;
    }

    /** The Finish spaces linked to the controller that still are (loaded). */
    static List<BlockPos> arrivalSpaces(PartyControllerEntity controller, ServerWorld world) {
        List<BlockPos> spaces = new ArrayList<>();
        for (BlockPos linked : controller.getArrivalLinks()) if (world.isChunkLoaded(linked) && isFinish(world, linked)) spaces.add(linked);
        return spaces;
    }

    /** Whether the block at {@code pos} is a Finish space (a board space with a Finish Cartridge active). */
    public static boolean isFinish(net.minecraft.world.World world, BlockPos pos) {
        BlockState state = world.getBlockState(pos);
        return state.getBlock() instanceof ABoardSpaceBlock && state.get(ABoardSpaceBlock.TILE_TYPE) == BoardSpaceType.TILE_END;
    }

    /** The pipe the End card of the controller's program is linked to, null for none. */
    public static @Nullable EndPipe endPipe(PartyControllerEntity controller) {
        for (ItemStack card : controller.getProgramCards()) {
            if (card.getItem() instanceof PartyCardItem item && item.getCardType() == PartyCardItem.CardType.END) {
                EndPipe pipe = card.get(ModComponents.END_PIPE);
                if (pipe != null) return pipe;
            }
        }
        return null;
    }

    /**
     * A right click on a pipe with an End party card: the card is linked to that pipe's mouth (the side looked at),
     * or unlinked if it was already.
     */
    public static void clickPipe(ServerPlayerEntity player, net.minecraft.util.Hand hand, ServerWorld world, BlockPos pos) {
        ItemStack card = player.getStackInHand(hand);
        net.minecraft.util.math.Direction opening = MiniGamePipes.mouthOf(world.getBlockState(pos), pos, player.getEyePos());
        if (opening == null) {
            player.sendMessage(net.minecraft.text.Text.translatable("message.steveparty.mini_game_page.pipe.no_mouth")
                    .formatted(net.minecraft.util.Formatting.RED), true);
            return;
        }
        GlobalPos mouth = GlobalPos.create(world.getRegistryKey(), pos.toImmutable());
        EndPipe linked = card.get(ModComponents.END_PIPE);
        if (linked != null && linked.mouth().equals(mouth)) {
            card.remove(ModComponents.END_PIPE);
            player.sendMessage(net.minecraft.text.Text.translatable("message.steveparty.mini_game_page.pipe.unlinked", pos.getX(), pos.getY(), pos.getZ()), true);
            fr.lordfinn.steveparty.sounds.ModSounds.playCancel(world, pos);
        } else {
            card.set(ModComponents.END_PIPE, new EndPipe(mouth, opening));
            player.sendMessage(net.minecraft.text.Text.translatable("message.steveparty.party_card_end.pipe.linked", pos.getX(), pos.getY(), pos.getZ()), true);
            fr.lordfinn.steveparty.sounds.ModSounds.playSelect(world, pos);
        }
    }

    /** A pawn on a podium faces its front. */
    private static void face(MobEntity mob, BlockState podium) {
        if (!PodiumBlock.isPodium(podium)) return;
        float yaw = podium.get(PodiumBlock.FACING).asRotation();
        mob.setYaw(yaw);
        mob.setBodyYaw(yaw);
        mob.setHeadYaw(yaw);
    }
}
