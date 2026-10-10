package fr.lordfinn.steveparty.blocks.custom.PartyController;

import fr.lordfinn.steveparty.blocks.custom.PartyController.steps.MiniGamePartyStep;
import fr.lordfinn.steveparty.blocks.custom.PartyController.steps.PartyStep;
import fr.lordfinn.steveparty.blocks.custom.boardspaces.BoardSpaceBlockEntity;
import fr.lordfinn.steveparty.entities.custom.DiceEntity;
import fr.lordfinn.steveparty.items.custom.MiniGamesCatalogueItem;
import fr.lordfinn.steveparty.minigame.MiniGamePages;
import fr.lordfinn.steveparty.utils.ServerMemory;
import net.fabricmc.fabric.api.networking.v1.PacketSender;
import net.minecraft.entity.mob.MobEntity;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayNetworkHandler;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.ActionResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.GlobalPos;
import net.minecraft.world.World;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Collection;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.HashMap;
import java.util.Optional;
import java.util.UUID;

/**
 * The loaded server-side Party Controllers, keyed by dimension + position: the lookups (which party plays a page, a
 * token, which one is closest) and the events every party hears (a player joins, a die is rolled, a tile is
 * reached). {@link PartyControllerEntity} keeps its static methods, which delegate here.
 */
public final class PartyControllers {
    private static final Map<GlobalPos, PartyControllerEntity> ACTIVE = ServerMemory.forgetOnStop(new LinkedHashMap<>());

    /** The board spaces of the loaded controllers' boards (as they remember them), each with its controller. */
    private static final Map<GlobalPos, PartyControllerEntity> BOARD_SPACES = ServerMemory.forgetOnStop(new HashMap<>());

    private PartyControllers() {}

    static void register(PartyControllerEntity controller) {
        if (controller.getWorld() instanceof ServerWorld && !controller.isRemoved()) {
            ACTIVE.put(GlobalPos.create(controller.getWorld().getRegistryKey(), controller.getPos()), controller);
            indexBoard(controller, Set.of(), controller.rememberedBoardSpaces());
        }
    }

    static void unregister(PartyControllerEntity controller) {
        if (controller.getWorld() instanceof ServerWorld) {
            ACTIVE.remove(GlobalPos.create(controller.getWorld().getRegistryKey(), controller.getPos()), controller);
            indexBoard(controller, controller.rememberedBoardSpaces(), Set.of());
        }
    }

    /** Its board was {@code before}, it is {@code now}. */
    static void indexBoard(PartyControllerEntity controller, Collection<BlockPos> before, Collection<BlockPos> now) {
        if (controller.getWorld() == null) return;
        var dimension = controller.getWorld().getRegistryKey();
        for (BlockPos pos : before) BOARD_SPACES.remove(GlobalPos.create(dimension, pos), controller);
        for (BlockPos pos : now) BOARD_SPACES.put(GlobalPos.create(dimension, pos), controller);
    }

    /**
     * The party of the board space at {@code pos}: the loaded controller whose board holds it, else the closest one
     * within {@code fallbackRadius} blocks; only a running party (or one standing on its end if {@code includeEnded}).
     */
    static Optional<PartyControllerEntity> ofBoardSpace(World world, BlockPos pos, int fallbackRadius, boolean includeEnded) {
        PartyControllerEntity owner = BOARD_SPACES.get(GlobalPos.create(world.getRegistryKey(), pos));
        if (owner != null && !owner.isRemoved() && owner.getWorld() == world) {
            boolean steppable = owner.getPartyData().isStarted() || (includeEnded && owner.getPartyData().isAtEnd());
            return steppable ? Optional.of(owner) : Optional.empty();
        }
        return closestSteppable(world, pos, fallbackRadius, includeEnded);
    }

    /**
     * The controller of the board holding the board space at {@code pos}, whether its party runs or not: the loaded
     * controller whose board holds it, else the closest one within {@code fallbackRadius} blocks.
     */
    static Optional<PartyControllerEntity> ofBoard(World world, BlockPos pos, int fallbackRadius) {
        PartyControllerEntity owner = BOARD_SPACES.get(GlobalPos.create(world.getRegistryKey(), pos));
        if (owner != null && !owner.isRemoved() && owner.getWorld() == world) return Optional.of(owner);
        return ACTIVE.values().stream()
                .filter(entity -> !entity.isRemoved() && entity.getWorld() == world)
                .filter(entity -> fallbackRadius <= 0 || entity.getPos().getSquaredDistance(pos) < (double) fallbackRadius * fallbackRadius)
                .min(Comparator.comparingDouble(entity -> entity.getPos().getSquaredDistance(pos)));
    }

    /** Snapshot of the loaded server-side controllers (safe to iterate while steps change). */
    public static List<PartyControllerEntity> all() {
        return List.copyOf(ACTIVE.values());
    }

    public static PartyControllerEntity at(World world, BlockPos pos) {
        return ACTIVE.get(GlobalPos.create(world.getRegistryKey(), pos));
    }

    /** See {@link PartyControllerEntity#onTokenRenamed}. */
    static void onTokenRenamed(ServerWorld world, UUID token, @Nullable String name) {
        for (PartyControllerEntity controller : ACTIVE.values()) {
            if (controller.isRemoved() || controller.getWorld() != world) continue;
            if (controller.rememberTokenName(token, name)) controller.syncLiveData(world);
        }
    }

    /** See {@link PartyControllerEntity#getPartyPlayingPage}. */
    static Optional<PartyControllerEntity> playingPage(Collection<UUID> pages) {
        if (pages.isEmpty()) return Optional.empty();
        for (PartyControllerEntity entity : ACTIVE.values()) {
            if (entity.isRemoved() || !(entity.getPartyData().getCurrentStep() instanceof MiniGamePartyStep miniGame)
                    || !miniGame.isOnArena()) continue;
            UUID page = MiniGamePages.idOf(MiniGamesCatalogueItem.getCurrentMiniGame(entity.catalogue));
            if (page != null && pages.contains(page)) return Optional.of(entity);
        }
        return Optional.empty();
    }

    /** See {@link PartyControllerEntity#getClosestSteppablePartyControllerEntity}. */
    static Optional<PartyControllerEntity> closestSteppable(@Nullable World world, BlockPos pos, int radius, boolean includeEnded) {
        return ACTIVE.values().stream()
                .filter(entity -> !entity.isRemoved())
                .filter(entity -> world == null || entity.getWorld() == world)
                .filter(entity -> entity.getPartyData().isStarted() || (includeEnded && entity.getPartyData().isAtEnd()))
                .filter(entity -> radius <= 0 || entity.getPos().getSquaredDistance(pos) < (double) radius * radius)
                .min(Comparator.comparingDouble(entity -> entity.getPos().getSquaredDistance(pos)));
    }

    /** The loaded controller whose running party contains the token, if any. */
    static Optional<PartyControllerEntity> runningPartyOf(UUID tokenUUID) {
        return ACTIVE.values().stream()
                .filter(entity -> !entity.isRemoved() && entity.getPartyData().isStarted()
                        && entity.getPartyData().getTokens().contains(tokenUUID))
                .findFirst();
    }

    static boolean isTokenInRunningParty(UUID tokenUUID) {
        return ACTIVE.values().stream()
                .anyMatch(entity -> !entity.isRemoved() && entity.getPartyData().isStarted()
                        && entity.getPartyData().getTokens().contains(tokenUUID));
    }

    static void onPlayerJoin(ServerPlayNetworkHandler handler, PacketSender sender, MinecraftServer server) {
        for (PartyControllerEntity entity : all()) {
            if (!entity.isRemoved()) entity.audience().onPlayerJoin(handler.player);
        }
    }

    static ActionResult onDiceRoll(DiceEntity dice, UUID ownerUUID, int rollValue) {
        ActionResult actionResult = ActionResult.PASS;
        for (PartyControllerEntity entity : all()) {
            if (entity.isRemoved()) continue;
            ActionResult result = isGone(entity) ? ActionResult.PASS : diceRolled(entity, dice, ownerUUID, rollValue);
            if (result != ActionResult.SUCCESS) actionResult = result;
        }
        return actionResult;
    }

    private static ActionResult diceRolled(PartyControllerEntity entity, DiceEntity dice, UUID ownerUUID, int rollValue) {
        PartyStep currentStep = entity.getPartyData().getCurrentStep();
        if (currentStep == null) return ActionResult.PASS;
        return currentStep.onDiceRoll(dice, ownerUUID, rollValue, entity);
    }

    static ActionResult onTileReached(@NotNull MobEntity token, @NotNull BoardSpaceBlockEntity boardSpaceEntity) {
        ActionResult actionResult = ActionResult.PASS;
        for (PartyControllerEntity entity : all()) {
            if (entity.isRemoved()) continue;
            ActionResult result = isGone(entity) ? ActionResult.PASS : tileReached(entity, token, boardSpaceEntity);
            if (result != ActionResult.SUCCESS) actionResult = result;
        }
        return actionResult;
    }

    private static ActionResult tileReached(PartyControllerEntity entity, MobEntity token, BoardSpaceBlockEntity boardSpaceEntity) {
        PartyStep currentStep = entity.getPartyData().getCurrentStep();
        if (currentStep == null) return ActionResult.PASS;
        if (entity.getPartyData().getTokens().contains(token.getUuid()))
            return currentStep.onTileReached(token, boardSpaceEntity, entity);
        return ActionResult.PASS;
    }

    /** A controller removed (or without a world) meanwhile hears nothing. */
    private static boolean isGone(PartyControllerEntity entity) {
        return entity.isRemoved() || entity.getWorld() == null;
    }
}
