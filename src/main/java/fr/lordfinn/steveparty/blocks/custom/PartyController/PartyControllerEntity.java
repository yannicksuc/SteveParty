package fr.lordfinn.steveparty.blocks.custom.PartyController;

import fr.lordfinn.steveparty.entities.TokenStatus;
import fr.lordfinn.steveparty.entities.TokenizedEntityInterface;
import fr.lordfinn.steveparty.blocks.ModBlockEntities;
import fr.lordfinn.steveparty.blocks.custom.PartyController.steps.*;
import fr.lordfinn.steveparty.blocks.custom.boardspaces.ABoardSpaceBlock;
import fr.lordfinn.steveparty.blocks.custom.boardspaces.BoardSpaceBlockEntity;
import fr.lordfinn.steveparty.blocks.custom.boardspaces.BoardSpaceType;
import fr.lordfinn.steveparty.entities.custom.DiceEntity;
import fr.lordfinn.steveparty.items.custom.MiniGamesCatalogueItem;
import fr.lordfinn.steveparty.payloads.custom.PartyDataPayload;
import fr.lordfinn.steveparty.utils.MessageUtils;
import net.fabricmc.fabric.api.networking.v1.PacketSender;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.block.BlockState;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.component.ComponentMap;
import net.minecraft.entity.Entity;
import net.minecraft.entity.mob.MobEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtElement;
import net.minecraft.network.listener.ClientPlayPacketListener;
import net.minecraft.network.packet.Packet;
import net.minecraft.network.packet.s2c.play.BlockEntityUpdateS2CPacket;
import net.minecraft.registry.RegistryWrapper;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayNetworkHandler;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.text.Text;
import net.minecraft.util.ActionResult;
import net.minecraft.util.ItemScatterer;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.*;

import static fr.lordfinn.steveparty.components.ModComponents.*;

public class PartyControllerEntity extends BlockEntity {
    public ItemStack catalogue = ItemStack.EMPTY;
    private PartyData partyData = new PartyData();
    private static final Set<PartyControllerEntity> ACTIVE_PARTY_CONTROLLERS = new HashSet<>();
    private final Set<UUID> interestedPlayers = new HashSet<>(); // New field


    public PartyControllerEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.PARTY_CONTROLLER_ENTITY, pos, state);
        if (ACTIVE_PARTY_CONTROLLERS.stream().anyMatch(entity -> entity.getPos().equals(pos))) return;
        ACTIVE_PARTY_CONTROLLERS.add(this);
    }

    public static Set<PartyControllerEntity> getActivePartyControllers() { return ACTIVE_PARTY_CONTROLLERS; }
    public static PartyControllerEntity getPartyControllerEntity(BlockPos pos) {
        return ACTIVE_PARTY_CONTROLLERS.stream().filter(entity -> entity.getPos().equals(pos)).findFirst().orElse(null);
    }

    public static Optional<PartyControllerEntity> getClosestActivePartyControllerEntity(BlockPos pos, int radius) {
        return PartyControllerEntity.getActivePartyControllers().stream()
                .filter(entity -> entity.getPartyData().isStarted())
                .filter(entity -> radius <= 0 || entity.getPos().getSquaredDistance(pos) < radius)
                .min(Comparator.comparingDouble(entity -> entity.getPos().getSquaredDistance(pos)));
    }

    @Override
    protected void readComponents(ComponentsAccess components) {
        super.readComponents(components);
        this.catalogue = components.getOrDefault(CATALOGUE, ItemStack.EMPTY);
    }

    @Override
    protected void addComponents(ComponentMap.Builder componentMapBuilder) {
        super.addComponents(componentMapBuilder);
        componentMapBuilder.add(CATALOGUE, this.catalogue);
    }

    @Override
    public void writeNbt(NbtCompound nbt, RegistryWrapper.WrapperLookup wrapper) {
        super.writeNbt(nbt, wrapper);
        // Serialize interestedPlayers list
        NbtCompound playersNbt = new NbtCompound();
        int i = 0;
        for (UUID playerUUID : interestedPlayers) {
            playersNbt.putString("player_" + i, playerUUID.toString());
            i++;
        }
        nbt.put("interestedPlayers", playersNbt);

        if (!catalogue.isEmpty()) {
            NbtElement item = catalogue.toNbt(wrapper, new NbtCompound());
            nbt.put("catalogue", item);
            nbt.putBoolean("isCatalogued", true);
        } else {
            nbt.putBoolean("isCatalogued", false);
        }
        partyData.toNbt(nbt);
        sendPacketToInterestedPlayers();
    }

    @Override
    public void readNbt(NbtCompound nbt, RegistryWrapper.WrapperLookup wrapper) {
        super.readNbt(nbt, wrapper);

        // Deserialize interestedPlayers list
        interestedPlayers.clear();
        NbtCompound playersNbt = nbt.getCompound("interestedPlayers");
        for (String key : playersNbt.getKeys()) {
            interestedPlayers.add(UUID.fromString(playersNbt.getString(key)));
        }

        NbtElement catalogueElem = nbt.get("catalogue");
        if (catalogueElem != null) {
            Optional<ItemStack> socketedStoryNbt = ItemStack.fromNbt(wrapper, nbt.get("catalogue"));
            socketedStoryNbt.ifPresentOrElse(stack -> catalogue = stack, () -> catalogue = ItemStack.EMPTY);
        }
        if (!nbt.getBoolean("isCatalogued"))
            catalogue = ItemStack.EMPTY;
        partyData = new PartyData(nbt);
    }

    @Override
    public NbtCompound toInitialChunkDataNbt(RegistryWrapper.WrapperLookup registryLookup) {
        return createNbt(registryLookup);
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

    public void boot() {
        if (!(this.world instanceof ServerWorld serverWorld)) return;
        if (partyData.isStarted()) return;

        clearInterestedPlayers();
        getTokenFromStartTiles(serverWorld);
        setTokensStatus(serverWorld);

        addInterestedPlayersFromTokens(serverWorld);

        partyData.addStep(new StartRollsStep());
        partyData.addStep(new BasicGameGeneratorStep());

        sendStartGameInfos();
        nextStep();
        markDirty();
    }

    private void setTokensStatus(ServerWorld serverWorld) {
        for (UUID tokenUUID : partyData.getTokens()) {
            if (serverWorld.getEntity(tokenUUID) instanceof TokenizedEntityInterface token) {
                token.steveparty$setStatus(TokenStatus.setStatus(0, TokenStatus.IN_GAME));
            }
        }
    }

    private void getTokenFromStartTiles(ServerWorld serverWorld) {
        BlockPos pos = this.getPos();
        List<BlockPos> startTiles = findStartTiles(serverWorld, pos);

        partyData.reset();

        for (BlockPos tilePos : startTiles) {
            BlockEntity tileEntity = serverWorld.getBlockEntity(tilePos);
            if (tileEntity instanceof BoardSpaceBlockEntity tile) {
                String potentialUuid = tile.getActiveCartridgeItemStack().get(TB_START_BOUND_ENTITY);
                if (potentialUuid == null) continue;
                partyData.addToken(UUID.fromString(potentialUuid));
            }
        }
    }

    private void sendStartGameInfos() {
        ServerWorld world = (ServerWorld) this.getWorld();
        if (world == null) return;
        MessageUtils.sendToNearby(
                world.getServer(),
                this.getPos().toCenterPos(), 100,
                Text.translatable("message.steveparty.game_started"), MessageUtils.MessageType.CHAT);
        for (UUID tokenUUID : partyData.getTokens()) {
            if (world.getEntity(tokenUUID) instanceof TokenizedEntityInterface token) {
                UUID ownerUUID = token.steveparty$getTokenOwner();
                if (world.getEntity(ownerUUID) instanceof PlayerEntity player) {
                    MessageUtils.sendToNearby(
                            world.getServer(),
                            this.getPos().toCenterPos(), 100,
                            Text.translatable("message.steveparty.join_game", ((Entity)token).getCustomName(), player.getName()),
                            MessageUtils.MessageType.CHAT);
                }
            }
        }
    }

    public boolean setCatalogue(ItemStack itemStack) {
        if (world == null || world.isClient) return !catalogue.isEmpty();

        if (!catalogue.isEmpty()) {
            Entity holder = itemStack.getHolder();
            if (holder instanceof ServerPlayerEntity player) {
                player.giveOrDropStack(catalogue);
            } else {
                ItemScatterer.spawn(world, pos.getX() + 0.5f, pos.getY() + 0.5f, pos.getZ() + 0.5f, catalogue);
            }
        }
        if (itemStack.isEmpty() || itemStack.getItem() instanceof MiniGamesCatalogueItem)
            catalogue = itemStack.copy();
        this.markDirty();
        return !catalogue.isEmpty();
    }

    private List<BlockPos> findStartTiles(ServerWorld world, BlockPos center) {
        List<BlockPos> startTiles = new ArrayList<>();
        int START_TILES_SEARCH_RADIUS = 100;
        Box searchBox = new Box(center.add(-START_TILES_SEARCH_RADIUS, -START_TILES_SEARCH_RADIUS, -START_TILES_SEARCH_RADIUS).toCenterPos(),
                center.add(START_TILES_SEARCH_RADIUS, START_TILES_SEARCH_RADIUS, START_TILES_SEARCH_RADIUS).toCenterPos());

        for (BlockPos pos : BlockPos.iterate((int) searchBox.minX, (int) searchBox.minY, (int) searchBox.minZ,
                (int) searchBox.maxX, (int) searchBox.maxY, (int) searchBox.maxZ)) {
            BlockState state = world.getBlockState(pos);
            if (state.getBlock() instanceof ABoardSpaceBlock && state.get(ABoardSpaceBlock.TILE_TYPE) == BoardSpaceType.TILE_START) {
                startTiles.add(pos.toImmutable());
            }
        }
        return startTiles;
    }


    public static void handlePlayerJoin(ServerPlayNetworkHandler handler, PacketSender sender, MinecraftServer server) {
        for (PartyControllerEntity entity : ACTIVE_PARTY_CONTROLLERS) {
            if (!entity.isRemoved()) {
                entity.onPlayerJoin(handler, sender, server);
            }
        }
    }

    private void onPlayerJoin(ServerPlayNetworkHandler handler, PacketSender sender, MinecraftServer server) {
        ServerPlayerEntity player = handler.player;
        boolean isInterested = interestedPlayers.contains(player.getUuid());
        if (isInterested) {
            this.sendPacketToInterestedPlayer(player);
        }
    }

    public static ActionResult handleDiceRoll(DiceEntity dice, UUID ownerUUID, int rollValue) {
        ActionResult actionResult = ActionResult.PASS;
        for (PartyControllerEntity entity : ACTIVE_PARTY_CONTROLLERS) {
            if (!entity.isRemoved()) {
                ActionResult result = entity.onDiceRoll(dice, ownerUUID, rollValue);
                if (result != ActionResult.SUCCESS)
                    actionResult = result;
            }
        }
        return actionResult;
    }

    private ActionResult onDiceRoll(DiceEntity dice, UUID ownerUUID, int rollValue) {
        if (this.isRemoved() || this.world == null) {
            return ActionResult.PASS;
        }
        PartyStep currentStep = partyData.getCurrentStep();
        if (currentStep == null) return ActionResult.PASS;
        return currentStep.onDiceRoll(dice,ownerUUID, rollValue, this);
    }


    public static ActionResult handleTileReached(@NotNull MobEntity token,@NotNull BoardSpaceBlockEntity boardSpaceEntity) {
        ActionResult actionResult = ActionResult.PASS;
        for (PartyControllerEntity entity : ACTIVE_PARTY_CONTROLLERS) {
            if (!entity.isRemoved()) {
                ActionResult result = entity.onTileReached(token, boardSpaceEntity);
                if (result != ActionResult.SUCCESS)
                    actionResult = result;
            }
        }
        return actionResult;
    }

    private ActionResult onTileReached(@NotNull MobEntity token,@NotNull BoardSpaceBlockEntity boardSpaceEntity) {
        if (this.isRemoved() || this.world == null) {
            return ActionResult.PASS;
        }
        PartyStep currentStep = partyData.getCurrentStep();
        if (currentStep == null) return ActionResult.PASS;
        if (getPartyData().getTokens().contains(token.getUuid()))
            return currentStep.onTileReached(token, boardSpaceEntity, this);
        return ActionResult.PASS;
    }

    @Override
    public void markRemoved() {
        super.markRemoved();
        ACTIVE_PARTY_CONTROLLERS.remove(this);
    }

    public PartyData getPartyData() {
        return partyData;
    }

    public void setPartyData(PartyData partyData) {
        this.partyData = partyData;
    }

    public void nextStep() {
           endCurrentStep();
        startStep(partyData.getStepIndex() + 1);
    }

    public void restartStep() {
        endCurrentStep();
        startStep(partyData.getStepIndex());
    }

    public void previousStep() {
        endCurrentStep();
        startStep(partyData.getStepIndex() - 1);
    }

    private void endCurrentStep() {
        PartyStep currentStep = partyData.getCurrentStep();
        if (currentStep != null) {
            currentStep.setStatus(PartyStep.Status.FINISHED);
            currentStep.end(this);
        }
    }

    private void startStep(int stepIndex) {
        partyData.setStepIndex(stepIndex);
        PartyStep currentStep = partyData.getCurrentStep();
        if (currentStep != null)
            currentStep.start(this);
        sendPacketToInterestedPlayers();
    }

    public void addInterestedPlayer(ServerPlayerEntity player) {
        interestedPlayers.add(player.getUuid());
        markDirty();
    }

    public void removeInterestedPlayer(ServerPlayerEntity player) {
        interestedPlayers.remove(player.getUuid());
        this.sendClearPacketToPlayer(player);
        markDirty();
    }

    // Clear the interestedPlayers list
    public void clearInterestedPlayers() {
        if (this.world == null) return;
        for (UUID playerUUID : interestedPlayers) {
            this.sendClearPacketToPlayer((ServerPlayerEntity) this.world.getPlayerByUuid(playerUUID));
        }
        interestedPlayers.clear();
        markDirty();
    }

    private void addInterestedPlayersFromTokens(ServerWorld serverWorld) {
        // Add playing players to the interestedPlayers list
        for (UUID tokenUUID : partyData.getTokens()) {
            if (serverWorld.getEntity(tokenUUID) instanceof TokenizedEntityInterface token) {
                UUID ownerUUID = token.steveparty$getTokenOwner();
                if (serverWorld.getEntity(ownerUUID) instanceof ServerPlayerEntity player) {
                    addInterestedPlayer(player);
                }
            }
        }
    }

    void sendPacketToInterestedPlayers() {
        if (this.world instanceof ServerWorld serverWorld) {
            for (UUID playerUUID : interestedPlayers) {
                PlayerEntity player = serverWorld.getPlayerByUuid(playerUUID);
                if (player instanceof ServerPlayerEntity serverPlayer) {
                    sendPacketToInterestedPlayer(serverPlayer);
                }
            }
        }
    }

    public List<ServerPlayerEntity> getInterestedPlayersEntities() {
        List<ServerPlayerEntity> interestedPlayers = new ArrayList<>();
        if (this.world instanceof ServerWorld serverWorld) {
            for (UUID playerUUID : this.interestedPlayers) {
                PlayerEntity player = serverWorld.getPlayerByUuid(playerUUID);
                if (player instanceof ServerPlayerEntity serverPlayer) {
                    interestedPlayers.add(serverPlayer);
                }
            }
        }
        return interestedPlayers;
    }

    void sendPacketToInterestedPlayer(ServerPlayerEntity player) {
        sendPacketToInterestedPlayer(player, partyData);
    }

    public void sendClearPacketToPlayer(ServerPlayerEntity player) {
        sendPacketToInterestedPlayer(player, new PartyData());
    }

    public void sendPacketToInterestedPlayer(ServerPlayerEntity player, PartyData partyData) {
        PartyDataPayload payload = PartyDataPayload.fromPartyData(partyData);
        ServerPlayNetworking.send(player, payload);
    }

    public void printPartyInfo(PlayerEntity player) {
        PartyStep currentStep = partyData.getCurrentStep();
        ServerWorld world = (ServerWorld) this.world;
        printGameStatus((ServerPlayerEntity) player);
        if (!getPartyData().isStarted())
            return;
        //Print list of player with their tokens :
        printListOfParticipants(world, (ServerPlayerEntity) player);
        //Print game info
        MessageUtils.sendToPlayer((ServerPlayerEntity) player, Text.translatable("message.steveparty.game_info", partyData.getStepIndex(), partyData.getSteps().size()), MessageUtils.MessageType.CHAT);


        //Print current step info
        if (currentStep != null) {
            MessageUtils.sendToPlayer((ServerPlayerEntity) player, Text.translatable("message.steveparty.current_step"), MessageUtils.MessageType.CHAT);
            currentStep.printInfo((ServerPlayerEntity) player);
        }
    }

    private void printGameStatus(ServerPlayerEntity player) {
        if (!this.partyData.isStarted())
            MessageUtils.sendToPlayer(player, Text.translatable("message.steveparty.game_status_off"), MessageUtils.MessageType.CHAT);
    }

    public void printListOfParticipants(ServerWorld world, ServerPlayerEntity player) {
        Text participants = partyData.getParticipantsAsString(world);
        MessageUtils.sendToPlayer(player, Text.translatable("message.steveparty.participants", partyData.getTokens().size()).append(participants), MessageUtils.MessageType.CHAT);
    }

    public Set<UUID> getInterestedPlayers() {
        return interestedPlayers;
    }

    public List<ItemStack> getMiniGames() {
       return MiniGamesCatalogueItem.getStoredPages(catalogue);
    }

    public ItemStack getCatalogue() {
        return catalogue;
    }
}
