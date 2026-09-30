package fr.lordfinn.steveparty.blocks.custom;

import fr.lordfinn.steveparty.blocks.ModBlockEntities;
import fr.lordfinn.steveparty.components.TileStampComponent;
import fr.lordfinn.steveparty.utils.MessageUtils;
import net.minecraft.nbt.NbtOps;
import net.minecraft.network.listener.ClientPlayPacketListener;
import net.minecraft.network.packet.Packet;
import net.minecraft.network.packet.s2c.play.BlockEntityUpdateS2CPacket;
import net.minecraft.util.Formatting;
import fr.lordfinn.steveparty.blocks.custom.PartyController.PartyControllerEntity;
import fr.lordfinn.steveparty.blocks.custom.PartyController.steps.MiniGamePartyStep;
import fr.lordfinn.steveparty.blocks.custom.PartyController.steps.TeamDisposition;
import fr.lordfinn.steveparty.items.custom.MiniGamesCatalogueItem;
import fr.lordfinn.steveparty.blocks.custom.boardspaces.CartridgeContainerBlockEntity;
import fr.lordfinn.steveparty.screen_handlers.custom.LootingBoxScreenHandler;
import fr.lordfinn.steveparty.utils.TickableBlockEntity;
import net.minecraft.block.BlockState;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.registry.RegistryWrapper;
import net.minecraft.screen.ScreenHandler;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.text.Text;
import net.minecraft.util.StringIdentifiable;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import org.jetbrains.annotations.Nullable;

import java.util.*;

/**
 * Podium: ends the mini-game being played and names its winners. A column of podiums is one podium: its top block
 * watches the players standing on it, its bottom block keeps the settings (mode, cartridge, stamped banner).
 * <ul>
 *     <li>"First arrived" mode (default): the first participant to step on a gold or classic podium wins; a silver or
 *     bronze podium gives the 2nd or 3rd place.</li>
 *     <li>"On signal" mode: stepping on it does nothing.</li>
 * </ul>
 * In both modes a redstone pulse ends the mini-game, its power designating the winners (see
 * {@link #designateWinners}): the participants standing on the podium, else a team (1, 2) or the player of that rank,
 * else the nearest participant (and his team). The winners receive the items of its inventory cartridge (from the
 * linked chest). Out of a party it is a finish line: a pulse each time a player steps on it; the comparator gives the
 * number of players standing on it.
 */
public class PodiumBlockEntity extends CartridgeContainerBlockEntity implements TickableBlockEntity {
    private static final int CHECK_INTERVAL_TICKS = 2;

    public enum Mode implements StringIdentifiable {
        FIRST_ARRIVED("first_arrived"),
        ON_SIGNAL("on_signal");

        private final String name;

        Mode(String name) {
            this.name = name;
        }

        @Override
        public String asString() {
            return name;
        }

        public Text getText() {
            return Text.translatable("podium_mode.steveparty." + name);
        }
    }

    private Mode mode = Mode.FIRST_ARRIVED;
    private final Set<UUID> playersOn = new LinkedHashSet<>();
    private boolean inputPowered = false;
    private int cycleIndex = 0;
    /** The look stamped on the banner (bottom block of the column; drawn on its top block). */
    private @Nullable TileStampComponent bannerStamp;

    public PodiumBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.PODIUM_ENTITY, pos, state, 1);
    }

    public Mode getMode() {
        return mode;
    }

    public void cycleMode() {
        mode = Mode.values()[(mode.ordinal() + 1) % Mode.values().length];
        markDirty();
    }

    public int getPlayersOnCount() {
        return playersOn.size();
    }

    @Override
    public void tick() {
        if (!(this.world instanceof ServerWorld world) || world.getTime() % CHECK_INTERVAL_TICKS != 0) return;
        Set<UUID> now = new LinkedHashSet<>();
        List<ServerPlayerEntity> arrived = new ArrayList<>();
        for (ServerPlayerEntity player : getPlayersStandingOn(world)) {
            now.add(player.getUuid());
            if (!playersOn.contains(player.getUuid())) arrived.add(player);
        }
        boolean changed = !now.equals(playersOn);
        playersOn.clear();
        playersOn.addAll(now);
        if (changed) world.updateComparators(this.pos, getCachedState().getBlock());
        for (ServerPlayerEntity player : arrived) onArrival(world, player);
    }

    /** Players standing on the top of the column this block belongs to. */
    private List<ServerPlayerEntity> getPlayersStandingOn(ServerWorld world) {
        BlockPos top = PodiumBlock.topOf(world, pos);
        BlockState state = world.getBlockState(top);
        double surface = top.getY() + (PodiumBlock.isPodium(state) && state.get(PodiumBlock.FULL) ? 1.0 : 0.5);
        Box above = new Box(top.getX(), surface - 0.1, top.getZ(), top.getX() + 1, surface + 1.1, top.getZ() + 1);
        return world.getEntitiesByClass(ServerPlayerEntity.class, above, player -> !player.isSpectator() && player.isAlive());
    }

    private void onArrival(ServerWorld world, ServerPlayerEntity player) {
        PodiumBlock.pulse(world, pos);
        PodiumBlockEntity master = PodiumBlock.master(world, pos);
        if (master == null || master.mode != Mode.FIRST_ARRIVED) return;
        if (!(getCachedState().getBlock() instanceof PodiumBlock block)) return;
        PodiumBlock.Place place = block.getPlace();
        findPlayedMiniGame(world, player.getUuid()).ifPresent(controller -> {
            if (place.endsTheMiniGame()) {
                master.finish(world, controller, List.of(player.getUuid()));
            } else {
                // 2nd / 3rd place: told to everybody, the mini-game goes on until the gold podium is reached
                MessageUtils.sendToPlayers(controller.getPartyAudience(), Text.translatableWithFallback("message.steveparty.podium.place",
                        "%1$s takes the place %2$s", player.getName(), place.rank()).formatted(Formatting.GOLD), MessageUtils.MessageType.CHAT);
            }
        });
    }

    public @Nullable TileStampComponent getBannerStamp() {
        return bannerStamp;
    }

    public void setBannerStamp(@Nullable TileStampComponent stamp) {
        if (java.util.Objects.equals(bannerStamp, stamp)) return;
        bannerStamp = stamp;
        markDirty();
        if (world != null && !world.isClient)
            world.updateListeners(pos, getCachedState(), getCachedState(), net.minecraft.block.Block.NOTIFY_ALL);
    }

    @Override
    public @Nullable Packet<ClientPlayPacketListener> toUpdatePacket() {
        return BlockEntityUpdateS2CPacket.create(this);
    }

    @Override
    public NbtCompound toInitialChunkDataNbt(RegistryWrapper.WrapperLookup registries) {
        return createNbt(registries);
    }

    /**
     * Neighbor update: a rising edge ends the mini-game, its winners designated by {@link #designateWinners} from the
     * received power.
     */
    public void onRedstoneInput(int power) {
        boolean powered = power > 0;
        boolean risingEdge = powered && !inputPowered;
        if (powered != inputPowered) {
            inputPowered = powered;
            markDirty();
        }
        if (!risingEdge || !(this.world instanceof ServerWorld world)) return;
        findPlayedMiniGame(world, null).ifPresent(controller -> {
            if (!(controller.getPartyData().getCurrentStep() instanceof MiniGamePartyStep miniGame)) return;
            List<UUID> participants = new ArrayList<>();
            controller.getPlayersInOrder().stream().filter(miniGame.getParticipants()::contains).forEach(participants::add);
            miniGame.getParticipants().stream().filter(uuid -> !participants.contains(uuid)).forEach(participants::add);
            List<UUID> standing = getPlayersStandingOn(world).stream().map(PlayerEntity::getUuid).toList();
            TeamDisposition teams = MiniGamesCatalogueItem.getCurrentMiniGameTeamDisposition(controller.getCatalogue());
            UUID nearest = world.getPlayers().stream().filter(player -> participants.contains(player.getUuid()))
                    .min(Comparator.comparingDouble(player -> player.squaredDistanceTo(pos.toCenterPos())))
                    .map(PlayerEntity::getUuid).orElse(null);
            finish(world, controller, designateWinners(participants, standing, teams, power, nearest));
        });
    }

    /**
     * Who wins when the podium receives a signal of {@code power}:
     * <ol>
     *     <li>participants standing on the podium: they win, whatever the power;</li>
     *     <li>else, a team mini-game: 1 = team A, 2 = team B;</li>
     *     <li>else, a mini-game without teams: the player of rank {@code power} in the play order (1 = the first);</li>
     *     <li>else (power greater than the number of players, or a team game with another power): the participant the
     *     nearest to the podium, with his team in a team mini-game.</li>
     * </ol>
     *
     * @param participants the participants in the play order
     */
    public static List<UUID> designateWinners(List<UUID> participants, List<UUID> standing, @Nullable TeamDisposition teams,
                                              int power, @Nullable UUID nearest) {
        List<UUID> onPodium = standing.stream().filter(participants::contains).distinct().toList();
        if (!onPodium.isEmpty()) return onPodium;
        boolean teamGame = teams != null && !teams.getTeamA().isEmpty() && !teams.getTeamB().isEmpty();
        if (teamGame && (power == 1 || power == 2)) {
            Set<UUID> team = power == 1 ? teams.getTeamA() : teams.getTeamB();
            return participants.stream().filter(team::contains).toList();
        }
        if (!teamGame && power >= 1 && power <= participants.size()) return List.of(participants.get(power - 1));
        if (nearest == null) return List.of();
        if (teamGame) {
            Set<UUID> team = teams.getTeamA().contains(nearest) ? teams.getTeamA()
                    : teams.getTeamB().contains(nearest) ? teams.getTeamB() : Set.of(nearest);
            return participants.stream().filter(team::contains).toList();
        }
        return List.of(nearest);
    }

    public void initRedstoneInput(boolean powered) {
        this.inputPowered = powered;
    }

    /**
     * The party whose mini-game is being played: the one of the participant if given, else the closest one of
     * this world (a mini-game arena may be far from its party controller).
     */
    private Optional<PartyControllerEntity> findPlayedMiniGame(ServerWorld world, @Nullable UUID participant) {
        return PartyControllerEntity.getActivePartyControllers().stream()
                .filter(controller -> !controller.isRemoved() && controller.getWorld() == world)
                .filter(controller -> controller.getPartyData().getCurrentStep() instanceof MiniGamePartyStep miniGame
                        && miniGame.isPlaying() && (participant == null || miniGame.getParticipants().contains(participant)))
                .min(Comparator.comparingDouble(controller -> controller.getPos().getSquaredDistance(this.pos)));
    }

    private void finish(ServerWorld world, PartyControllerEntity controller, List<UUID> winnerIds) {
        if (!(controller.getPartyData().getCurrentStep() instanceof MiniGamePartyStep miniGame)) return;
        List<UUID> winners = winnerIds.stream().filter(miniGame.getParticipants()::contains).toList();
        if (!miniGame.finish(controller, winners)) return;
        for (UUID uuid : winners) {
            ServerPlayerEntity winner = world.getServer().getPlayerManager().getPlayer(uuid);
            if (winner != null)
                CartridgeTransfers.apply(world, getStack(0), winner, () -> cycleIndex, index -> cycleIndex = index);
        }
        markDirty();
    }

    @Override
    public @Nullable ScreenHandler createMenu(int syncId, PlayerInventory playerInventory, PlayerEntity player) {
        return new LootingBoxScreenHandler(syncId, playerInventory, this);
    }

    @Override
    public Text getDisplayName() {
        return Text.translatable("block.steveparty.podium");
    }

    @Override
    public void writeNbt(NbtCompound nbt, RegistryWrapper.WrapperLookup wrapper) {
        super.writeNbt(nbt, wrapper);
        nbt.putString("Mode", mode.asString());
        nbt.putBoolean("InputPowered", inputPowered);
        nbt.putInt("CycleIndex", cycleIndex);
        if (bannerStamp != null)
            TileStampComponent.CODEC.encodeStart(wrapper.getOps(NbtOps.INSTANCE), bannerStamp)
                    .ifSuccess(element -> nbt.put("BannerStamp", element));
    }

    @Override
    public void readNbt(NbtCompound nbt, RegistryWrapper.WrapperLookup wrapper) {
        super.readNbt(nbt, wrapper);
        mode = Mode.FIRST_ARRIVED;
        for (Mode value : Mode.values()) {
            if (value.asString().equals(nbt.getString("Mode"))) mode = value;
        }
        inputPowered = nbt.getBoolean("InputPowered");
        cycleIndex = nbt.getInt("CycleIndex");
        bannerStamp = nbt.contains("BannerStamp")
                ? TileStampComponent.CODEC.parse(wrapper.getOps(NbtOps.INSTANCE), nbt.get("BannerStamp")).result().orElse(null)
                : null;
    }
}
