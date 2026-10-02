package fr.lordfinn.steveparty.podium;

import fr.lordfinn.steveparty.blocks.custom.GoalPoleBaseBlockEntity;
import fr.lordfinn.steveparty.blocks.custom.GoalPoleBlockEntity;
import fr.lordfinn.steveparty.blocks.custom.PartyController.PartyControllerEntity;
import fr.lordfinn.steveparty.blocks.custom.PartyController.steps.MiniGamePartyStep;
import fr.lordfinn.steveparty.blocks.custom.PartyController.steps.TeamDisposition;
import fr.lordfinn.steveparty.blocks.custom.PodiumBlock;
import fr.lordfinn.steveparty.blocks.custom.PodiumBlockEntity;
import fr.lordfinn.steveparty.items.custom.MiniGamesCatalogueItem;
import fr.lordfinn.steveparty.minigame.MiniGamePageData;
import fr.lordfinn.steveparty.minigame.MiniGamePageNetworking;
import fr.lordfinn.steveparty.minigame.MiniGamePages;
import fr.lordfinn.steveparty.minigame.MiniGamePipeRole;
import fr.lordfinn.steveparty.minigame.MiniGameSession;
import fr.lordfinn.steveparty.minigame.MiniGamePodiumLink;
import fr.lordfinn.steveparty.podium.PodiumGroup.Column;
import fr.lordfinn.steveparty.sounds.ModSounds;
import fr.lordfinn.steveparty.utils.MessageUtils;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.text.MutableText;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.Hand;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.ChunkSectionPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.GlobalPos;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.chunk.WorldChunk;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * What the podiums do (server side). A mini-game only says who won and who lost: the podiums record it, the party
 * controller pays the gains (its Gains page).
 * <ul>
 *     <li><b>Registering</b> ({@link #toggle}): sneaking on a podium or right-clicking it registers the player on it,
 *     doing it again on the podium showing him unregisters him. A podium held by someone else can be taken (nothing
 *     ever blocks the game). One podium per player, per team in a team mini-game ({@link #register}).</li>
 *     <li><b>Redstone</b> ({@link #onSignal}): a pulse into a column does what its {@link PodiumSignal} says.</li>
 *     <li><b>In order</b> ({@link #fill}): a winner takes the highest free place of the group: a pulse into a column
 *     set to {@link PodiumSignal#FILL}, or a per-player goal reached on a goal pole linked to the group
 *     ({@link #onGoalReached}).</li>
 *     <li><b>Reset</b> ({@link #reset}): the columns of the group are emptied and the points of its goal pole bases go
 *     back to 0; resetting such a base empties the group too ({@link #onBaseReset}). The party currencies are never
 *     touched.</li>
 *     <li><b>Links</b>: a podium or a goal pole base clicked with a mini-game page is linked to it
 *     ({@link #clickLink}); a goal pole touching a podium of the group is linked to the group too.</li>
 * </ul>
 * While a page's mini-game is played (by a party, or as a test), its group only takes the players of that mini-game.
 */
public final class Podiums {
    /** Out of a party, a pulse registers the nearest player within this many blocks. */
    public static final double NEAREST_RADIUS = 64;
    /** A player's click and his sneak in the same moment are one gesture. */
    private static final int TOGGLE_COOLDOWN_TICKS = 6;
    /** How far the reset command looks for a podium. */
    public static final int COMMAND_RADIUS = 48;

    private static final Map<UUID, Long> LAST_TOGGLE = new HashMap<>();
    /** Set while a reset spreads between podium groups and goal pole bases, so that it stops there. */
    private static boolean resetting;

    private Podiums() {
    }

    public static void initialize() {
        ServerLifecycleEvents.SERVER_STOPPED.register(server -> LAST_TOGGLE.clear());
    }

    // ------------------------------------------------------------------ the mini-game being played

    /** The mini-game being played on a page the group is linked to (a party's, or a test), null for none. */
    public static @Nullable MiniGameSession played(PodiumGroup group) {
        return MiniGameSession.playing(group.pages());
    }

    /** « 1st », « 2nd »... */
    public static MutableText placeText(int place) {
        return place >= 1 && place <= 9 ? Text.translatable("hud.steveparty.party.rank." + place) : Text.literal(String.valueOf(place));
    }

    /** The name of a team, in its colour. */
    public static MutableText teamText(int team) {
        MiniGamePipeRole role = MiniGamePipeRole.ofTeam(team);
        return role.text().copy().styled(style -> style.withColor(role.color()));
    }

    // ------------------------------------------------------------------ registering

    /**
     * {@code player} sneaks on the podium at {@code pos} or clicks it: registered on it, or unregistered if it
     * already shows him.
     *
     * @return true if something changed
     */
    public static boolean toggle(ServerPlayerEntity player, ServerWorld world, BlockPos pos) {
        long now = world.getTime();
        Long last = LAST_TOGGLE.get(player.getUuid());
        if (last != null && now >= last && now - last < TOGGLE_COOLDOWN_TICKS) return false;
        PodiumGroup group = PodiumGroup.of(world, pos);
        Column column = group.columnAt(world, pos);
        if (column == null) return false;
        LAST_TOGGLE.put(player.getUuid(), now);
        PodiumOccupant occupant = column.occupant();
        if (occupant != null && occupant.player().equals(player.getUuid())) {
            unregister(group, column, player);
            return true;
        }
        return register(group, column, player);
    }

    /**
     * Registers {@code player} on a column of the group: whoever held it leaves it, and the player (his team in a team
     * mini-game) leaves the column of the group he held. In a party's mini-game only its players register.
     *
     * @return false if nothing changed (not a player of the mini-game, or already there)
     */
    public static boolean register(PodiumGroup group, Column column, ServerPlayerEntity player) {
        PodiumBlockEntity master = column.master();
        if (master == null) return false;
        MiniGameSession played = played(group);
        UUID uuid = player.getUuid();
        if (played != null && !played.isParticipant(uuid)) {
            player.sendMessage(Text.translatable("message.steveparty.podium.not_playing").formatted(Formatting.RED), true);
            return false;
        }
        int team = played == null ? -1 : played.teamOf(uuid);
        PodiumOccupant previous = master.getOccupant();
        if (previous != null && previous.player().equals(uuid) && previous.team() == team) return false;
        // One podium per player, per team in a team mini-game
        for (Column other : group.columns()) {
            PodiumOccupant there = other.occupant();
            if (other != column && there != null && there.sameSide(uuid, team) && other.master() != null) {
                other.master().setOccupant(null);
                poof(other);
            }
        }
        ServerWorld world = column.world();
        int place = group.placeOf(column);
        master.setOccupant(new PodiumOccupant(uuid, player.getGameProfile().getName(), team, place, world.getTime()));
        refreshPlaces(group);
        Vec3d at = column.standPos();
        world.spawnParticles(ParticleTypes.POOF, at.x, at.y + 0.4, at.z, 10, 0.2, 0.25, 0.2, 0.02);
        world.spawnParticles(ParticleTypes.END_ROD, at.x, at.y + 0.6, at.z, 6, 0.2, 0.3, 0.2, 0.03);
        world.playSound(null, at.x, at.y, at.z, SoundEvents.BLOCK_NOTE_BLOCK_PLING.value(), SoundCategory.BLOCKS, 0.8f,
                Math.max(0.8f, 1.7f - 0.2f * (place - 1)));
        world.playSound(null, at.x, at.y, at.z, SoundEvents.ENTITY_ITEM_PICKUP, SoundCategory.BLOCKS, 0.6f, 1.2f);

        player.sendMessage(Text.translatable("message.steveparty.podium.registered", placeText(place)).formatted(Formatting.GOLD), true);
        if (previous != null && !previous.player().equals(uuid)) {
            ServerPlayerEntity ousted = world.getServer().getPlayerManager().getPlayer(previous.player());
            if (ousted != null && ousted != player)
                ousted.sendMessage(Text.translatable("message.steveparty.podium.taken", player.getName()).formatted(Formatting.RED), true);
        }
        if (played != null) {
            Text line = team < 0
                    ? Text.translatable("message.steveparty.podium.place", player.getName(), placeText(place))
                    : Text.translatable("message.steveparty.podium.place.team", teamText(team), player.getName(), placeText(place));
            MessageUtils.sendToPlayers(played.audience(), line.copy().formatted(Formatting.GOLD), MessageUtils.MessageType.CHAT);
            played.onPodiumsChanged();
        }
        return true;
    }

    /** The places of the group changed: the mini-game being played with it may end, or its end be called off. */
    private static void notifyPlayed(PodiumGroup group) {
        MiniGameSession played = played(group);
        if (played != null) played.onPodiumsChanged();
    }

    private static void unregister(PodiumGroup group, Column column, ServerPlayerEntity player) {
        PodiumBlockEntity master = column.master();
        if (master == null || master.getOccupant() == null) return;
        master.setOccupant(null);
        poof(column);
        player.sendMessage(Text.translatable("message.steveparty.podium.unregistered").formatted(Formatting.GRAY), true);
        MiniGameSession played = played(group);
        if (played != null) {
            MessageUtils.sendToPlayers(played.audience(),
                    Text.translatable("message.steveparty.podium.left", player.getName(), placeText(group.placeOf(column))).formatted(Formatting.GRAY),
                    MessageUtils.MessageType.CHAT);
            played.onPodiumsChanged();
        }
    }

    /** The figure of a column goes away in a puff. */
    private static void poof(Column column) {
        Vec3d at = column.standPos();
        column.world().spawnParticles(ParticleTypes.POOF, at.x, at.y + 0.4, at.z, 12, 0.2, 0.25, 0.2, 0.03);
        column.world().playSound(null, at.x, at.y, at.z, SoundEvents.ENTITY_ITEM_FRAME_REMOVE_ITEM, SoundCategory.BLOCKS, 0.8f, 0.9f);
    }

    /** The place shown with each figure follows the heights (a column built or broken since). */
    private static void refreshPlaces(PodiumGroup group) {
        for (Column column : group.columns()) {
            PodiumBlockEntity master = column.master();
            if (master != null && master.getOccupant() != null) master.setOccupant(master.getOccupant().withPlace(group.placeOf(column)));
        }
    }

    /**
     * A winner arrives: {@code player} takes the highest free place of the group. A player already placed (or whose
     * team is) is not placed again.
     *
     * @return true if he took a place
     */
    public static boolean fill(PodiumGroup group, ServerPlayerEntity player) {
        MiniGameSession played = played(group);
        if (played != null && !played.isParticipant(player.getUuid())) return false;
        if (group.columnOf(player.getUuid(), played == null ? -1 : played.teamOf(player.getUuid())) != null) return false;
        Column free = group.highestFree();
        return free != null && register(group, free, player);
    }

    // ------------------------------------------------------------------ redstone

    /** A redstone pulse into the column at {@code pos}: what its signal setting says. */
    public static void onSignal(ServerWorld world, BlockPos pos) {
        PodiumGroup group = PodiumGroup.of(world, pos);
        Column column = group.columnAt(world, pos);
        PodiumBlockEntity master = column == null ? null : column.master();
        if (master == null) return;
        switch (master.getSignal()) {
            case REGISTER -> {
                ServerPlayerEntity nearest = nearest(group, column, false);
                if (nearest != null) register(group, column, nearest);
            }
            case FILL -> {
                ServerPlayerEntity nearest = nearest(group, column, true);
                if (nearest != null) fill(group, nearest);
            }
            case CLEAR -> {
                if (master.getOccupant() != null) {
                    master.setOccupant(null);
                    poof(column);
                    notifyPlayed(group);
                }
            }
            case RESET -> reset(group);
        }
    }

    /**
     * The player the nearest to a column: a player of the mini-game being played with the group, else any player
     * within {@link #NEAREST_RADIUS} blocks.
     *
     * @param unplacedOnly only among those who have no place in the group yet (nor their team)
     */
    public static @Nullable ServerPlayerEntity nearest(PodiumGroup group, Column column, boolean unplacedOnly) {
        MiniGameSession played = played(group);
        Vec3d at = column.standPos();
        ServerPlayerEntity best = null;
        double bestDistance = played == null ? NEAREST_RADIUS * NEAREST_RADIUS : Double.MAX_VALUE;
        for (ServerPlayerEntity player : column.world().getPlayers()) {
            if (player.isSpectator() || !player.isAlive()) continue;
            UUID uuid = player.getUuid();
            if (played != null && !played.isParticipant(uuid)) continue;
            if (unplacedOnly && group.columnOf(uuid, played == null ? -1 : played.teamOf(uuid)) != null) continue;
            double distance = player.squaredDistanceTo(at);
            if (distance <= bestDistance) {
                best = player;
                bestDistance = distance;
            }
        }
        return best;
    }

    // ------------------------------------------------------------------ reset

    /** Empties the columns of a group and puts the points of its goal pole bases back to 0. */
    public static void reset(PodiumGroup group) {
        clear(group);
        if (resetting) return;
        resetting = true;
        try {
            for (GoalPoleBaseBlockEntity base : basesOf(group)) base.reset();
        } finally {
            resetting = false;
        }
    }

    private static void clear(PodiumGroup group) {
        boolean changed = false;
        for (Column column : group.columns()) {
            PodiumBlockEntity master = column.master();
            if (master == null || master.getOccupant() == null) continue;
            master.setOccupant(null);
            poof(column);
            changed = true;
        }
        if (changed) notifyPlayed(group);
    }

    /** A goal pole base was reset: the podium groups it is linked to are emptied, their other bases reset too. */
    public static void onBaseReset(GoalPoleBaseBlockEntity base) {
        if (resetting || !(base.getWorld() instanceof ServerWorld)) return;
        resetting = true;
        try {
            for (PodiumGroup group : groupsOf(base)) {
                clear(group);
                for (GoalPoleBaseBlockEntity other : basesOf(group)) if (other != base) other.reset();
            }
        } finally {
            resetting = false;
        }
    }

    /** A mini-game starts on {@code page}: its podiums are emptied, its counters back to 0. */
    public static void resetForMiniGame(MinecraftServer server, MiniGamePageData page) {
        PodiumGroup group = PodiumGroup.ofPage(server, page);
        if (group != null) reset(group);
        for (MiniGamePodiumLink link : page.podiumLinks()) {
            if (link.kind() != MiniGamePodiumLink.Kind.COUNTER) continue;
            GoalPoleBaseBlockEntity base = baseAt(server.getWorld(link.pos().dimension()), link.pos().pos());
            if (base != null && base.getTotal() != 0) base.reset();
        }
    }

    // ------------------------------------------------------------------ goal poles

    private static @Nullable GoalPoleBaseBlockEntity baseAt(@Nullable ServerWorld world, BlockPos pos) {
        if (world == null || !world.isChunkLoaded(ChunkSectionPos.getSectionCoord(pos.getX()), ChunkSectionPos.getSectionCoord(pos.getZ()))) return null;
        BlockEntity entity = world.getBlockEntity(pos);
        if (entity instanceof GoalPoleBaseBlockEntity base) return base;
        return entity instanceof GoalPoleBlockEntity pole ? pole.getCachedBase() : null;
    }

    /** The goal pole bases linked to a group: touching one of its podiums (the base or its pole), or linked to its page. */
    public static Set<GoalPoleBaseBlockEntity> basesOf(PodiumGroup group) {
        Set<GoalPoleBaseBlockEntity> bases = new LinkedHashSet<>();
        for (Column column : group.columns()) {
            ServerWorld world = column.world();
            for (BlockPos block = column.bottom(); block.getY() <= column.top().getY(); block = block.up()) {
                for (Direction direction : Direction.Type.HORIZONTAL) {
                    GoalPoleBaseBlockEntity base = baseAt(world, block.offset(direction));
                    if (base != null) bases.add(base);
                }
            }
        }
        if (group.isEmpty()) return bases;
        MinecraftServer server = group.columns().getFirst().world().getServer();
        for (UUID page : group.pages()) {
            for (MiniGamePodiumLink link : MiniGamePages.get(server, page).podiumLinks()) {
                if (link.kind() != MiniGamePodiumLink.Kind.COUNTER) continue;
                GoalPoleBaseBlockEntity base = baseAt(server.getWorld(link.pos().dimension()), link.pos().pos());
                if (base != null) bases.add(base);
            }
        }
        return bases;
    }

    /** The podium groups a goal pole base is linked to: touching it or its pole, or linked to a page it is linked to. */
    public static List<PodiumGroup> groupsOf(GoalPoleBaseBlockEntity base) {
        if (!(base.getWorld() instanceof ServerWorld world)) return List.of();
        Map<GlobalPos, PodiumGroup> groups = new LinkedHashMap<>();
        BlockPos.Mutable cursor = base.getPos().mutableCopy();
        do {
            for (Direction direction : Direction.Type.HORIZONTAL) {
                BlockPos side = cursor.offset(direction);
                if (PodiumBlock.isPodium(world.getBlockState(side))) addGroup(groups, PodiumGroup.of(world, side));
            }
            cursor.move(Direction.UP);
        } while (!world.isOutOfHeightLimit(cursor) && world.getBlockEntity(cursor) instanceof GoalPoleBlockEntity);
        MinecraftServer server = world.getServer();
        for (MiniGamePageData page : MiniGamePages.pagesAt(server, GlobalPos.create(world.getRegistryKey(), base.getPos()))) {
            PodiumGroup group = PodiumGroup.ofPage(server, page);
            if (group != null) addGroup(groups, group);
        }
        return new ArrayList<>(groups.values());
    }

    private static void addGroup(Map<GlobalPos, PodiumGroup> groups, PodiumGroup group) {
        if (group.id() != null) groups.putIfAbsent(group.id(), group);
    }

    /**
     * A player reached his own goal on a pole of {@code base} (a per-player goal): he takes the highest free place of
     * the podium groups the base is linked to.
     */
    public static void onGoalReached(GoalPoleBaseBlockEntity base, String holder) {
        if (!(base.getWorld() instanceof ServerWorld world)) return;
        ServerPlayerEntity player = world.getServer().getPlayerManager().getPlayer(holder);
        if (player == null) return;
        for (PodiumGroup group : groupsOf(base)) fill(group, player);
    }

    // ------------------------------------------------------------------ links to a mini-game page

    /**
     * A click on a podium, a goal pole base or a step controller with the page held in {@code hand}: it is linked to
     * the page, or unlinked if it was. The player is told what happened.
     *
     * @return true if the page changed
     */
    public static boolean clickLink(ServerPlayerEntity player, Hand hand, ServerWorld world, BlockPos pos, MiniGamePodiumLink.Kind kind) {
        ItemStack stack = player.getStackInHand(hand);
        if (!MiniGamePages.isPage(stack)) return false;
        if (!MiniGamePages.canEdit(player)) {
            player.sendMessage(Text.translatable("gui.steveparty.mini_game_page.status.not_allowed").formatted(Formatting.RED), true);
            return false;
        }
        UUID id = MiniGamePageNetworking.ensureSinglePage(player, hand);
        MinecraftServer server = player.server;
        // A podium is its whole column: linked by its bottom block, unlinked whatever block of it was linked
        List<GlobalPos> blocks = new ArrayList<>();
        BlockPos anchor = pos.toImmutable();
        if (kind == MiniGamePodiumLink.Kind.PODIUM) {
            anchor = PodiumBlock.bottomOf(world, pos);
            BlockPos top = PodiumBlock.topOf(world, pos);
            for (BlockPos block = anchor; block.getY() <= top.getY(); block = block.up()) blocks.add(GlobalPos.create(world.getRegistryKey(), block));
        } else {
            blocks.add(GlobalPos.create(world.getRegistryKey(), anchor));
        }
        String key = "message.steveparty.mini_game_page." + kind.key();
        if (MiniGamePages.removePodiumLinks(server, id, blocks)) {
            player.sendMessage(Text.translatable(key + ".unlinked", anchor.getX(), anchor.getY(), anchor.getZ()), true);
            world.playSound(null, pos, ModSounds.CANCEL_SOUND_EVENT, SoundCategory.BLOCKS, 1.0F, 1.0F);
        } else if (MiniGamePages.addPodiumLink(server, id, new MiniGamePodiumLink(GlobalPos.create(world.getRegistryKey(), anchor), kind))) {
            player.sendMessage(Text.translatable(key + ".linked", anchor.getX(), anchor.getY(), anchor.getZ()), true);
            world.playSound(null, pos, ModSounds.SELECT_SOUND_EVENT, SoundCategory.BLOCKS, 1.0F, 1.0F);
        } else {
            player.sendMessage(Text.translatable("message.steveparty.mini_game_page.podium.full", MiniGamePageData.MAX_PODIUM_LINKS)
                    .formatted(Formatting.RED), true);
            return false;
        }
        MiniGamePages.refresh(server, player.getStackInHand(hand));
        return true;
    }

    // ------------------------------------------------------------------ Wrench and command

    /** Right click with the Wrench: what a redstone pulse into the column does. */
    public static void cycleSignal(ServerPlayerEntity player, ServerWorld world, BlockPos pos) {
        PodiumBlockEntity master = PodiumBlock.master(world, pos);
        if (master == null) return;
        if (!MiniGamePages.canEdit(player)) {
            player.sendMessage(Text.translatable("message.steveparty.podium.not_allowed").formatted(Formatting.RED), true);
            return;
        }
        master.setSignal(master.getSignal().next());
        world.playSound(null, pos, SoundEvents.BLOCK_COPPER_TRAPDOOR_OPEN, SoundCategory.BLOCKS, 1.0f, 1.5f);
        player.sendMessage(Text.translatable("message.steveparty.podium.signal", master.getSignal().text().copy().formatted(Formatting.YELLOW)), true);
    }

    /** Sneak + right click with the Wrench: the group of the podium is reset. */
    public static void wrenchReset(ServerPlayerEntity player, ServerWorld world, BlockPos pos) {
        if (!MiniGamePages.canEdit(player)) {
            player.sendMessage(Text.translatable("message.steveparty.podium.not_allowed").formatted(Formatting.RED), true);
            return;
        }
        PodiumGroup group = PodiumGroup.of(world, pos);
        reset(group);
        world.playSound(null, pos, SoundEvents.BLOCK_COMPARATOR_CLICK, SoundCategory.BLOCKS, 0.8f, 0.6f);
        player.sendMessage(Text.translatable("message.steveparty.podium.reset", group.columns().size()).formatted(Formatting.GOLD), true);
    }

    /** The podium the nearest to {@code center} within {@code radius} blocks (loaded chunks only), null for none. */
    public static @Nullable BlockPos nearestPodium(ServerWorld world, Vec3d center, int radius) {
        BlockPos best = null;
        double bestDistance = (double) radius * radius;
        int minX = ChunkSectionPos.getSectionCoord(center.x - radius), maxX = ChunkSectionPos.getSectionCoord(center.x + radius);
        int minZ = ChunkSectionPos.getSectionCoord(center.z - radius), maxZ = ChunkSectionPos.getSectionCoord(center.z + radius);
        for (int chunkX = minX; chunkX <= maxX; chunkX++) {
            for (int chunkZ = minZ; chunkZ <= maxZ; chunkZ++) {
                WorldChunk chunk = world.getChunkManager().getWorldChunk(chunkX, chunkZ);
                if (chunk == null) continue;
                for (BlockEntity entity : chunk.getBlockEntities().values()) {
                    if (!(entity instanceof PodiumBlockEntity)) continue;
                    double distance = entity.getPos().toCenterPos().squaredDistanceTo(center);
                    if (distance <= bestDistance) {
                        best = entity.getPos();
                        bestDistance = distance;
                    }
                }
            }
        }
        return best;
    }
}
