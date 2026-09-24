package fr.lordfinn.steveparty.blocks.custom.PartyController;

import fr.lordfinn.steveparty.entities.TokenStatus;
import fr.lordfinn.steveparty.entities.TokenizedEntityInterface;
import fr.lordfinn.steveparty.blocks.ModBlockEntities;
import fr.lordfinn.steveparty.blocks.custom.PartyBellBlockEntity;
import fr.lordfinn.steveparty.blocks.custom.PartyController.steps.*;
import fr.lordfinn.steveparty.blocks.custom.boardspaces.ABoardSpaceBlock;
import fr.lordfinn.steveparty.blocks.custom.boardspaces.BoardSpaceBlockEntity;
import fr.lordfinn.steveparty.blocks.custom.boardspaces.BoardSpaceType;
import fr.lordfinn.steveparty.entities.custom.DiceEntity;
import fr.lordfinn.steveparty.items.custom.MiniGamesCatalogueItem;
import fr.lordfinn.steveparty.payloads.custom.PartyDataPayload;
import fr.lordfinn.steveparty.utils.MessageUtils;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.networking.v1.PacketSender;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.block.BlockState;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.component.ComponentMap;
import net.minecraft.entity.Entity;
import net.minecraft.entity.mob.MobEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.inventory.Inventories;
import net.minecraft.inventory.SimpleInventory;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.registry.Registries;
import net.minecraft.nbt.NbtElement;
import net.minecraft.nbt.NbtList;
import net.minecraft.nbt.NbtString;
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
import net.minecraft.util.Formatting;
import net.minecraft.util.ItemScatterer;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.ChunkSectionPos;
import net.minecraft.util.math.GlobalPos;
import net.minecraft.world.World;
import net.minecraft.world.chunk.WorldChunk;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.*;

import static fr.lordfinn.steveparty.components.ModComponents.*;

public class PartyControllerEntity extends BlockEntity {
    public ItemStack catalogue = ItemStack.EMPTY;
    private PartyData partyData = new PartyData();
    /** Server-side only registry of the loaded controllers, keyed by dimension + position. */
    private static final Map<GlobalPos, PartyControllerEntity> ACTIVE_PARTY_CONTROLLERS = new LinkedHashMap<>();
    private static final int START_TILES_SEARCH_RADIUS = 100;
    private final Set<UUID> interestedPlayers = new HashSet<>(); // New field
    /** Set once the step that was running when this controller was saved has been resumed. */
    private boolean resumeDone = false;
    /** Tokens that left the party (excluded / party over) while not loaded: released as soon as they are loaded. */
    private final Set<UUID> tokensToRelease = new LinkedHashSet<>();
    /** Radius around the controller in which the players are told about the party (absent turns, exclusions...). */
    public static final int PARTY_AUDIENCE_RADIUS = 100;
    /** Last phase given to comparators, to notify them only when it changes. */
    private int lastPhase = -1;
    /** Players who won the last mini-game (piggy banks may reward them). */
    private final List<UUID> lastWinners = new ArrayList<>();

    /** Settings slots (wrench + right-click): the coin and star items, then the party program cards. */
    public static final int SLOT_COIN = 0;
    public static final int SLOT_STAR = 1;
    public static final int PROGRAM_FIRST_SLOT = 2;
    public static final int PROGRAM_SLOTS = 18;
    public static final int SETTINGS_SIZE = PROGRAM_FIRST_SLOT + PROGRAM_SLOTS;
    private static final int SCORE_REFRESH_INTERVAL_TICKS = 20;
    private final SimpleInventory settings = new SimpleInventory(SETTINGS_SIZE) {
        @Override
        public void markDirty() {
            super.markDirty();
            PartyControllerEntity.this.markDirty();
        }
    };
    /** Last known coins / stars of each player of the party, kept for the players who went offline. */
    private final Map<UUID, int[]> cachedScores = new LinkedHashMap<>();

    static {
        ServerLifecycleEvents.SERVER_STOPPED.register(server -> ACTIVE_PARTY_CONTROLLERS.clear());
    }

    public PartyControllerEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.PARTY_CONTROLLER_ENTITY, pos, state);
    }

    @Override
    public void setWorld(World world) {
        super.setWorld(world);
        register();
    }

    @Override
    public void cancelRemoval() {
        super.cancelRemoval();
        register();
    }

    private void register() {
        if (this.world instanceof ServerWorld && !this.isRemoved())
            ACTIVE_PARTY_CONTROLLERS.put(GlobalPos.create(this.world.getRegistryKey(), this.pos), this);
    }

    private void unregister() {
        if (this.world != null)
            ACTIVE_PARTY_CONTROLLERS.remove(GlobalPos.create(this.world.getRegistryKey(), this.pos), this);
    }

    /** Snapshot of the loaded server-side controllers (safe to iterate while steps change). */
    public static List<PartyControllerEntity> getActivePartyControllers() { return List.copyOf(ACTIVE_PARTY_CONTROLLERS.values()); }
    public static PartyControllerEntity getPartyControllerEntity(World world, BlockPos pos) {
        return ACTIVE_PARTY_CONTROLLERS.get(GlobalPos.create(world.getRegistryKey(), pos));
    }

    /**
     * Closest started party controller in the given world, within {@code radius} blocks ({@code radius <= 0}: no limit).
     */
    public static Optional<PartyControllerEntity> getClosestActivePartyControllerEntity(@Nullable World world, BlockPos pos, int radius) {
        return ACTIVE_PARTY_CONTROLLERS.values().stream()
                .filter(entity -> !entity.isRemoved())
                .filter(entity -> world == null || entity.getWorld() == world)
                .filter(entity -> entity.getPartyData().isStarted())
                .filter(entity -> radius <= 0 || entity.getPos().getSquaredDistance(pos) < (double) radius * radius)
                .min(Comparator.comparingDouble(entity -> entity.getPos().getSquaredDistance(pos)));
    }

    /**
     * Closest party controller a step controller can act on: a started party or, if {@code includeEnded},
     * a party standing on its END step (so it can be brought back to the previous step).
     */
    public static Optional<PartyControllerEntity> getClosestSteppablePartyControllerEntity(@Nullable World world, BlockPos pos, int radius, boolean includeEnded) {
        return ACTIVE_PARTY_CONTROLLERS.values().stream()
                .filter(entity -> !entity.isRemoved())
                .filter(entity -> world == null || entity.getWorld() == world)
                .filter(entity -> entity.getPartyData().isStarted() || (includeEnded && entity.getPartyData().isAtEnd()))
                .filter(entity -> radius <= 0 || entity.getPos().getSquaredDistance(pos) < (double) radius * radius)
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
        if (!tokensToRelease.isEmpty()) {
            NbtList releaseNbt = new NbtList();
            tokensToRelease.forEach(uuid -> releaseNbt.add(NbtString.of(uuid.toString())));
            nbt.put("TokensToRelease", releaseNbt);
        }
        if (!lastWinners.isEmpty()) {
            NbtList winnersNbt = new NbtList();
            lastWinners.forEach(uuid -> winnersNbt.add(NbtString.of(uuid.toString())));
            nbt.put("LastWinners", winnersNbt);
        }
        if (!settings.isEmpty()) {
            NbtCompound settingsNbt = new NbtCompound();
            Inventories.writeNbt(settingsNbt, settings.getHeldStacks(), wrapper);
            nbt.put("PartySettings", settingsNbt);
        }
        if (!cachedScores.isEmpty()) {
            NbtCompound scoresNbt = new NbtCompound();
            cachedScores.forEach((uuid, score) -> scoresNbt.putIntArray(uuid.toString(), score));
            nbt.put("Scores", scoresNbt);
        }
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
        tokensToRelease.clear();
        nbt.getList("TokensToRelease", NbtElement.STRING_TYPE).forEach(element -> {
            try {
                tokensToRelease.add(UUID.fromString(element.asString()));
            } catch (IllegalArgumentException ignored) {
            }
        });
        lastWinners.clear();
        nbt.getList("LastWinners", NbtElement.STRING_TYPE).forEach(element -> {
            try {
                lastWinners.add(UUID.fromString(element.asString()));
            } catch (IllegalArgumentException ignored) {
            }
        });
        settings.getHeldStacks().clear();
        Inventories.readNbt(nbt.getCompound("PartySettings"), settings.getHeldStacks(), wrapper);
        cachedScores.clear();
        NbtCompound scoresNbt = nbt.getCompound("Scores");
        for (String key : scoresNbt.getKeys()) {
            int[] score = scoresNbt.getIntArray(key);
            try {
                if (score.length == 2) cachedScores.put(UUID.fromString(key), score);
            } catch (IllegalArgumentException ignored) {
            }
        }
        partyData.setScores(buildScoreBoard());
        resumeDone = false;
    }

    /**
     * Server tick. Scheduled tasks are not saved, so after a load the step that was running is resumed
     * once the party is really playable again (at least one token loaded and one owner online, or any player
     * for an ownerless token), otherwise
     * a step could wrongly consider the tokens as missing.
     */
    public void serverTick(ServerWorld serverWorld) {
        if (!tokensToRelease.isEmpty() && serverWorld.getTime() % 20 == 0)
            releasePendingTokens(serverWorld);
        if (partyData.isStarted() && serverWorld.getTime() % SCORE_REFRESH_INTERVAL_TICKS == 0)
            refreshScores();
        PartyStep currentStep = partyData.getCurrentStep();
        if (resumeDone) {
            // Once resumed (or started), the current step gets ticked (e.g. the countdown of an absent turn)
            if (currentStep != null && currentStep.getStatus() == PartyStep.Status.IN_PROGRESS)
                currentStep.tick(this, serverWorld);
            return;
        }
        if (!partyData.isStarted() || currentStep == null || currentStep.getStatus() != PartyStep.Status.IN_PROGRESS) {
            resumeDone = true;
            return;
        }
        if (serverWorld.getTime() % 20 != 0) return;
        List<TokenizedEntityInterface> loadedTokens = partyData.getTokens(serverWorld);
        if (loadedTokens.isEmpty()) return;
        // An ownerless token can be played by anyone: a connected player is enough
        boolean playable = !partyData.getOwners(serverWorld).isEmpty()
                || (!serverWorld.getPlayers().isEmpty() && loadedTokens.stream().anyMatch(token -> token.steveparty$getTokenOwner() == null));
        if (!playable) return;
        resumeDone = true;
        currentStep.resume(this);
        markDirty();
        sendPacketToInterestedPlayers();
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
        updatePhase();
    }

    public void boot() {
        if (!(this.world instanceof ServerWorld serverWorld)) return;
        if (partyData.isStarted()) return;

        // A previous (ended) party may still have a step holding scheduled tasks
        endCurrentStep();
        resumeDone = true;
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
                world,
                this.getPos().toCenterPos(), 100,
                Text.translatable("message.steveparty.game_started"), MessageUtils.MessageType.CHAT);
        for (UUID tokenUUID : partyData.getTokens()) {
            if (world.getEntity(tokenUUID) instanceof TokenizedEntityInterface token) {
                UUID ownerUUID = token.steveparty$getTokenOwner();
                if (world.getEntity(ownerUUID) instanceof PlayerEntity player) {
                    MessageUtils.sendToNearby(
                            world,
                            this.getPos().toCenterPos(), 100,
                            Text.translatable("message.steveparty.join_game", ((Entity)token).getCustomName(), player.getName()),
                            MessageUtils.MessageType.CHAT);
                }
            }
        }
    }

    public boolean setCatalogue(ItemStack itemStack) {
        return setCatalogue(itemStack, null);
    }

    /**
     * @param player when a new catalogue is inserted, the player inserting it: the replaced catalogue goes back to them
     */
    public boolean setCatalogue(ItemStack itemStack, @Nullable PlayerEntity player) {
        if (world == null || world.isClient) return !catalogue.isEmpty();

        if (!catalogue.isEmpty()) {
            Entity holder = itemStack.getHolder();
            if (!itemStack.isEmpty() && player != null) {
                player.getInventory().offerOrDrop(catalogue);
            } else if (holder instanceof ServerPlayerEntity holderPlayer) {
                holderPlayer.giveOrDropStack(catalogue);
            } else {
                ItemScatterer.spawn(world, pos.getX() + 0.5f, pos.getY() + 0.5f, pos.getZ() + 0.5f, catalogue);
            }
        }
        if (itemStack.isEmpty() || itemStack.getItem() instanceof MiniGamesCatalogueItem)
            catalogue = itemStack.copy();
        this.markDirty();
        return !catalogue.isEmpty();
    }

    /**
     * Finds the start tiles within {@link #START_TILES_SEARCH_RADIUS} blocks. A start tile always has a board space
     * block entity, so only the block entities of the already loaded chunks are checked (no chunk is loaded).
     */
    private List<BlockPos> findStartTiles(ServerWorld world, BlockPos center) {
        List<BlockPos> startTiles = new ArrayList<>();
        int minX = center.getX() - START_TILES_SEARCH_RADIUS, maxX = center.getX() + START_TILES_SEARCH_RADIUS;
        int minY = center.getY() - START_TILES_SEARCH_RADIUS, maxY = center.getY() + START_TILES_SEARCH_RADIUS;
        int minZ = center.getZ() - START_TILES_SEARCH_RADIUS, maxZ = center.getZ() + START_TILES_SEARCH_RADIUS;

        for (int chunkX = ChunkSectionPos.getSectionCoord(minX); chunkX <= ChunkSectionPos.getSectionCoord(maxX); chunkX++) {
            for (int chunkZ = ChunkSectionPos.getSectionCoord(minZ); chunkZ <= ChunkSectionPos.getSectionCoord(maxZ); chunkZ++) {
                WorldChunk chunk = world.getChunkManager().getWorldChunk(chunkX, chunkZ);
                if (chunk == null) continue;
                for (BlockEntity blockEntity : chunk.getBlockEntities().values()) {
                    if (!(blockEntity instanceof BoardSpaceBlockEntity)) continue;
                    BlockPos pos = blockEntity.getPos();
                    if (pos.getX() < minX || pos.getX() > maxX || pos.getY() < minY || pos.getY() > maxY
                            || pos.getZ() < minZ || pos.getZ() > maxZ) continue;
                    BlockState state = chunk.getBlockState(pos);
                    if (state.getBlock() instanceof ABoardSpaceBlock && state.get(ABoardSpaceBlock.TILE_TYPE) == BoardSpaceType.TILE_START) {
                        startTiles.add(pos.toImmutable());
                    }
                }
            }
        }
        // Same order as the former full scan (x first, then y, then z)
        startTiles.sort(Comparator.comparingInt(BlockPos::getZ).thenComparingInt(BlockPos::getY).thenComparingInt(BlockPos::getX));
        return startTiles;
    }


    public static void handlePlayerJoin(ServerPlayNetworkHandler handler, PacketSender sender, MinecraftServer server) {
        for (PartyControllerEntity entity : getActivePartyControllers()) {
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
        for (PartyControllerEntity entity : getActivePartyControllers()) {
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
        for (PartyControllerEntity entity : getActivePartyControllers()) {
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
        unregister();
    }

    public PartyData getPartyData() {
        return partyData;
    }

    // ---------------------------------------------------------------- settings: currency and program

    public SimpleInventory getSettings() {
        return settings;
    }

    public ItemStack getCoinTemplate() {
        return settings.getStack(SLOT_COIN);
    }

    public ItemStack getStarTemplate() {
        return settings.getStack(SLOT_STAR);
    }

    /** @return true if a coin or a star item was chosen: scores are shown and the party ends with a ranking. */
    public boolean hasCurrency() {
        return !getCoinTemplate().isEmpty() || !getStarTemplate().isEmpty();
    }

    /** The cards of the party program, in reading order (copies). Empty: the default party. */
    public List<ItemStack> getProgram() {
        List<ItemStack> program = new ArrayList<>();
        for (int i = PROGRAM_FIRST_SLOT; i < SETTINGS_SIZE; i++) {
            ItemStack stack = settings.getStack(i);
            if (!stack.isEmpty()) program.add(stack.copy());
        }
        return program;
    }

    /** Number of items like {@code template} (same item and components) in the player's inventory. */
    public static int countItems(PlayerEntity player, ItemStack template) {
        if (template.isEmpty()) return 0;
        int count = 0;
        for (int i = 0; i < player.getInventory().size(); i++) {
            ItemStack stack = player.getInventory().getStack(i);
            if (!stack.isEmpty() && ItemStack.areItemsAndComponentsEqual(stack, template)) count += stack.getCount();
        }
        return count;
    }

    /** Owner of a token of the party: read on the loaded token, else on its turns. */
    public @Nullable UUID getTokenOwner(UUID tokenUUID) {
        if (this.world instanceof ServerWorld serverWorld && serverWorld.getEntity(tokenUUID) instanceof TokenizedEntityInterface token)
            return token.steveparty$getTokenOwner();
        for (PartyStep step : partyData.getSteps()) {
            if (step instanceof TokenTurnPartyStep turn && tokenUUID.equals(turn.getTokenUUID()) && turn.getOwnerUUID() != null)
                return turn.getOwnerUUID();
        }
        return null;
    }

    /** The players of the party (owners of its tokens), in play order. Ownerless tokens have no score. */
    public List<UUID> getPlayersInOrder() {
        List<UUID> players = new ArrayList<>();
        for (UUID token : partyData.getTokens()) {
            UUID owner = getTokenOwner(token);
            if (owner != null && !players.contains(owner)) players.add(owner);
        }
        return players;
    }

    /** Coins and stars of a player: counted in their inventory if online, else the last known ones. */
    public int[] getScore(UUID playerUUID) {
        if (this.world instanceof ServerWorld serverWorld) {
            ServerPlayerEntity player = serverWorld.getServer().getPlayerManager().getPlayer(playerUUID);
            if (player != null) {
                int[] score = {countItems(player, getCoinTemplate()), countItems(player, getStarTemplate())};
                cachedScores.put(playerUUID, score);
                return score;
            }
        }
        return cachedScores.getOrDefault(playerUUID, new int[]{0, 0});
    }

    private String playerName(UUID playerUUID) {
        if (this.world instanceof ServerWorld serverWorld) {
            MinecraftServer server = serverWorld.getServer();
            ServerPlayerEntity player = server.getPlayerManager().getPlayer(playerUUID);
            if (player != null) return player.getName().getString();
            if (server.getUserCache() != null) {
                var profile = server.getUserCache().getByUuid(playerUUID);
                if (profile.isPresent()) return profile.get().getName();
            }
        }
        return playerUUID.toString().substring(0, 8);
    }

    private PartyData.ScoreBoard buildScoreBoard() {
        if (!hasCurrency()) return PartyData.ScoreBoard.EMPTY;
        List<PartyData.ScoreEntry> entries = new ArrayList<>();
        boolean server = this.world instanceof ServerWorld;
        for (UUID player : server ? getPlayersInOrder() : List.copyOf(cachedScores.keySet())) {
            int[] score = server ? getScore(player) : cachedScores.get(player);
            entries.add(new PartyData.ScoreEntry(player, server ? playerName(player) : "", score[0], score[1]));
        }
        return new PartyData.ScoreBoard(itemId(getCoinTemplate()), itemId(getStarTemplate()), entries);
    }

    private static String itemId(ItemStack stack) {
        return stack.isEmpty() ? "" : Registries.ITEM.getId(stack.getItem()).toString();
    }

    /** Recounts the scores; the players following the party get them when they change. */
    private void refreshScores() {
        PartyData.ScoreBoard scores = buildScoreBoard();
        if (scores.equals(partyData.getScores())) return;
        partyData.setScores(scores);
        super.markDirty();
        sendPacketToInterestedPlayers();
    }

    /** Players sorted by stars, then coins (best first). */
    public List<PartyData.ScoreEntry> computeRanking() {
        List<PartyData.ScoreEntry> ranking = new ArrayList<>(buildScoreBoard().entries());
        ranking.sort(Comparator.comparingInt(PartyData.ScoreEntry::stars).thenComparingInt(PartyData.ScoreEntry::coins).reversed());
        return ranking;
    }

    /** End of the party: announces the ranking (if a coin or star item was chosen). */
    public void announceRanking() {
        if (!hasCurrency()) return;
        List<PartyData.ScoreEntry> ranking = computeRanking();
        if (ranking.isEmpty()) return;
        List<ServerPlayerEntity> audience = getPartyAudience();
        MessageUtils.sendToPlayers(audience, Text.translatableWithFallback("message.steveparty.ranking.title", "Ranking")
                .formatted(Formatting.GOLD, Formatting.BOLD), MessageUtils.MessageType.CHAT);
        int rank = 0;
        PartyData.ScoreEntry previous = null;
        for (int i = 0; i < ranking.size(); i++) {
            PartyData.ScoreEntry entry = ranking.get(i);
            // Same stars and coins: same rank
            if (previous == null || previous.stars() != entry.stars() || previous.coins() != entry.coins()) rank = i + 1;
            previous = entry;
            MessageUtils.sendToPlayers(audience, Text.translatableWithFallback("message.steveparty.ranking.line",
                    "%1$s. %2$s: %3$s stars, %4$s coins", rank, entry.name(), entry.stars(), entry.coins())
                    .formatted(rank == 1 ? Formatting.YELLOW : Formatting.WHITE), MessageUtils.MessageType.CHAT);
        }
        PartyData.ScoreEntry best = ranking.getFirst();
        List<String> winners = ranking.stream()
                .filter(entry -> entry.stars() == best.stars() && entry.coins() == best.coins())
                .map(PartyData.ScoreEntry::name).toList();
        MessageUtils.sendToPlayers(audience, Text.translatableWithFallback("message.steveparty.ranking.winner",
                "%s wins the party!", String.join(", ", winners)).styled(style -> style.withColor(0xFFD700).withBold(true)),
                MessageUtils.MessageType.TITLE);
    }

    public List<UUID> getLastWinners() {
        return Collections.unmodifiableList(lastWinners);
    }

    public void setLastWinners(List<UUID> winners) {
        lastWinners.clear();
        lastWinners.addAll(winners);
        markDirty();
    }

    public void setPartyData(PartyData partyData) {
        this.partyData = partyData;
        markDirty();
    }

    public void nextStep() {
        PartyStep currentStep = partyData.getCurrentStep();
        int index = partyData.getStepIndex();
        endCurrentStep();
        if (currentStep instanceof EventPartyStep event && event.isTransition() && index >= 0) {
            // Its moments rang: go on with the step it was inserted before (already announced)
            partyData.getSteps().remove(index);
            startStep(index);
            return;
        }
        goToStep(index + 1, currentStep);
    }

    /**
     * Moves to the step at {@code target} coming from {@code from}, ringing the party bells of the moments of this
     * transition. If a bell waits at one of them, a transition step is inserted first: it rings the moments one
     * after the other and waits for the bells.
     */
    private void goToStep(int target, @Nullable PartyStep from) {
        List<PartyStep> steps = partyData.getSteps();
        PartyStep to = target >= 0 && target < steps.size() ? steps.get(target) : null;
        List<PartyMoment> moments = new ArrayList<>();
        List<Integer> values = new ArrayList<>();
        collectTransitionMoments(from, to, target, moments, values);
        // The index first: the bells listen to started parties (or parties standing on their end)
        partyData.setStepIndex(target);
        if (moments.stream().anyMatch(moment -> PartyBellBlockEntity.hasWaitingBell(this, moment))) {
            steps.add(target, EventPartyStep.transition(moments, values));
        } else {
            for (int i = 0; i < moments.size(); i++)
                PartyBellBlockEntity.ring(this, moments.get(i), values.get(i));
        }
        startStep(target);
    }

    private void collectTransitionMoments(@Nullable PartyStep from, @Nullable PartyStep to, int target,
                                          List<PartyMoment> moments, List<Integer> values) {
        if (from instanceof TokenTurnPartyStep turn) {
            moments.add(PartyMoment.TURN_END);
            values.add(partyData.getTokenRank(turn.getTokenUUID()));
        } else if (from instanceof MiniGamePartyStep miniGame) {
            moments.add(PartyMoment.MINIGAME_END);
            values.add(miniGame.getWinners().size());
        }
        if (to == null) return;
        if (from == null && target == 0) {
            moments.add(PartyMoment.PARTY_START);
            values.add(partyData.getTokens().size());
        }
        if (to instanceof TokenTurnPartyStep turn) {
            if (partyData.isRoundStart(target)) {
                moments.add(PartyMoment.ROUND_START);
                values.add(partyData.getRoundAt(target));
            }
            moments.add(PartyMoment.TURN_START);
            values.add(partyData.getTokenRank(turn.getTokenUUID()));
        } else if (to.getType() == PartyStepType.END) {
            moments.add(PartyMoment.PARTY_END);
            values.add(partyData.getTokens().size());
        }
    }

    /**
     * A party bell in waiting mode received its signal: the step waiting for {@code moment} goes on.
     *
     * @return true if a step was waiting for it
     */
    public boolean releaseMoment(PartyMoment moment) {
        PartyStep currentStep = partyData.getCurrentStep();
        if (currentStep == null || currentStep.getStatus() != PartyStep.Status.IN_PROGRESS) return false;
        if (!currentStep.onMomentReleased(moment, this)) return false;
        markDirty();
        sendPacketToInterestedPlayers();
        return true;
    }

    /**
     * Rings the bells of a moment happening inside a step (dice rolled, mini-game chosen...).
     *
     * @return true if a bell waits at this moment (the step must pause until {@link PartyStep#onMomentReleased})
     */
    public boolean ringMoment(PartyMoment moment, int value) {
        boolean waiting = PartyBellBlockEntity.ring(this, moment, value);
        updatePhase();
        return waiting;
    }

    /**
     * A dice is about to move a token: rings the "dice rolled" bells of its party, or the free play bells around it.
     */
    public static void onTokenDiceRolled(ServerWorld world, Entity token, int rollValue) {
        Optional<PartyControllerEntity> party = getRunningPartyOf(token.getUuid());
        if (party.isPresent()) party.get().ringMoment(PartyMoment.DICE_ROLLED, rollValue);
        else PartyBellBlockEntity.ringFreePlay(world, token.getBlockPos(), PartyMoment.DICE_ROLLED, rollValue);
    }

    /** Free play: a token that is not in a running party finished its movement. */
    public static void onFreeTokenArrived(ServerWorld world, Entity token) {
        if (getRunningPartyOf(token.getUuid()).isEmpty())
            PartyBellBlockEntity.ringFreePlay(world, token.getBlockPos(), PartyMoment.TURN_END, 1);
    }

    /** The loaded controller whose running party contains the token, if any. */
    public static Optional<PartyControllerEntity> getRunningPartyOf(UUID tokenUUID) {
        return ACTIVE_PARTY_CONTROLLERS.values().stream()
                .filter(entity -> !entity.isRemoved() && entity.getPartyData().isStarted()
                        && entity.getPartyData().getTokens().contains(tokenUUID))
                .findFirst();
    }

    // ---------------------------------------------------------------- phase (comparator output)

    public static final int PHASE_IDLE = 0;
    public static final int PHASE_PREPARING = 1;
    public static final int PHASE_TURN = 2;
    public static final int PHASE_MINIGAME = 3;
    public static final int PHASE_WAITING = 4;
    public static final int PHASE_END = 5;

    /**
     * Phase read by a comparator: 0 idle, 1 start rolls / preparation, 2 token turn, 3 mini-game,
     * 4 waiting for a party bell, 5 party over.
     */
    public int getPhase() {
        PartyStep currentStep = partyData.getCurrentStep();
        if (currentStep == null || (!partyData.isStarted() && !partyData.isAtEnd())) return PHASE_IDLE;
        if (currentStep.isWaitingForBell()) return PHASE_WAITING;
        return switch (currentStep.getType()) {
            case TOKEN_TURN -> PHASE_TURN;
            case MINI_GAME -> PHASE_MINIGAME;
            case END -> PHASE_END;
            default -> PHASE_PREPARING;
        };
    }

    private void updatePhase() {
        if (this.world == null || this.world.isClient) return;
        int phase = getPhase();
        if (phase == lastPhase) return;
        lastPhase = phase;
        this.world.updateComparators(this.pos, getCachedState().getBlock());
    }

    public void restartStep() {
        endCurrentStep();
        startStep(partyData.getStepIndex());
    }

    public void previousStep() {
        PartyStep currentStep = partyData.getCurrentStep();
        boolean leavingEnd = currentStep != null && currentStep.getType() == PartyStepType.END && partyData.getStepIndex() > 0;
        endCurrentStep();
        if (currentStep instanceof EventPartyStep event && event.isTransition()) {
            // Not a real step: drop it, then go back to the step before it
            partyData.getSteps().remove(partyData.getStepIndex());
        }
        // The END step released the tokens: going back into the game puts them in game again
        // (the step being resumed grants CAN_MOVE as usual)
        if (leavingEnd)
            restoreTokensInGame();
        startStep(partyData.getStepIndex() - 1);
    }

    private void restoreTokensInGame() {
        if (!(this.world instanceof ServerWorld serverWorld)) return;
        for (UUID tokenUUID : partyData.getTokens()) {
            tokensToRelease.remove(tokenUUID);
            if (serverWorld.getEntity(tokenUUID) instanceof TokenizedEntityInterface token)
                token.steveparty$setStatus(TokenStatus.setStatus(token.steveparty$getStatus(), TokenStatus.IN_GAME));
        }
    }

    /**
     * Takes a token out of the game (clears IN_GAME / CAN_MOVE). A token that is not loaded is released
     * as soon as it is loaded again, unless it joined a running party meanwhile.
     */
    public void releaseToken(ServerWorld serverWorld, UUID tokenUUID) {
        if (serverWorld.getEntity(tokenUUID) instanceof TokenizedEntityInterface token) {
            token.steveparty$setStatus(TokenStatus.clearStatuses(token.steveparty$getStatus(), TokenStatus.IN_GAME, TokenStatus.CAN_MOVE));
            tokensToRelease.remove(tokenUUID);
        } else {
            tokensToRelease.add(tokenUUID);
        }
        markDirty();
    }

    private void releasePendingTokens(ServerWorld serverWorld) {
        boolean changed = false;
        Iterator<UUID> iterator = tokensToRelease.iterator();
        while (iterator.hasNext()) {
            UUID tokenUUID = iterator.next();
            if (isTokenInRunningParty(tokenUUID)) {
                iterator.remove(); // it plays again: nothing to release
                changed = true;
            } else if (serverWorld.getEntity(tokenUUID) instanceof TokenizedEntityInterface token) {
                token.steveparty$setStatus(TokenStatus.clearStatuses(token.steveparty$getStatus(), TokenStatus.IN_GAME, TokenStatus.CAN_MOVE));
                iterator.remove();
                changed = true;
            }
        }
        if (changed) markDirty();
    }

    private static boolean isTokenInRunningParty(UUID tokenUUID) {
        return ACTIVE_PARTY_CONTROLLERS.values().stream()
                .anyMatch(entity -> !entity.isRemoved() && entity.getPartyData().isStarted()
                        && entity.getPartyData().getTokens().contains(tokenUUID));
    }

    /**
     * Excludes a token from the party: it is removed from the tokens, its next turns are removed (only the
     * steps after the current one, so the step index stays valid), it is taken out of the game and everybody
     * is told. If it was the token's turn, the party goes on with the next step.
     *
     * @return false if the token is not part of this party
     */
    public boolean excludeToken(UUID tokenUUID) {
        if (!(this.world instanceof ServerWorld serverWorld)) return false;
        if (!partyData.getTokens().contains(tokenUUID)) return false;
        Text tokenName = getTokenDisplayName(serverWorld, tokenUUID);

        partyData.removeToken(tokenUUID);
        int stepIndex = partyData.getStepIndex();
        PartyStep currentStep = partyData.getCurrentStep();
        boolean isItsTurn = currentStep instanceof TokenTurnPartyStep turn && tokenUUID.equals(turn.getTokenUUID())
                && currentStep.getStatus() == PartyStep.Status.IN_PROGRESS;

        List<PartyStep> steps = partyData.getSteps();
        for (int i = steps.size() - 1; i > stepIndex; i--) {
            if (steps.get(i) instanceof TokenTurnPartyStep turn && tokenUUID.equals(turn.getTokenUUID()))
                steps.remove(i);
        }
        for (PartyStep step : List.copyOf(steps.subList(Math.min(Math.max(stepIndex, 0), steps.size()), steps.size())))
            step.onTokenExcluded(tokenUUID, this);

        releaseToken(serverWorld, tokenUUID);
        MessageUtils.sendToPlayers(getPartyAudience(),
                Text.translatableWithFallback("message.steveparty.token_excluded",
                        "%s has been excluded from the party.", tokenName).formatted(Formatting.RED),
                MessageUtils.MessageType.CHAT);

        if (isItsTurn) {
            nextStep();
        } else {
            markDirty();
            sendPacketToInterestedPlayers();
        }
        return true;
    }

    /**
     * Name of a token of the party: its current name if loaded, else the name remembered by one of its turns,
     * else the beginning of its UUID.
     */
    public Text getTokenDisplayName(ServerWorld serverWorld, UUID tokenUUID) {
        if (serverWorld.getEntity(tokenUUID) instanceof Entity entity)
            return entity.getCustomName() != null ? entity.getCustomName() : entity.getName();
        for (PartyStep step : partyData.getSteps()) {
            if (step instanceof TokenTurnPartyStep turn && tokenUUID.equals(turn.getTokenUUID()))
                return turn.getTokenDisplayName(null);
        }
        return Text.literal(tokenUUID.toString().substring(0, 8));
    }

    /**
     * @return true if the player takes part in this party: interested player, or owner of one of its tokens
     */
    public boolean isParticipant(ServerPlayerEntity player) {
        UUID playerUUID = player.getUuid();
        if (interestedPlayers.contains(playerUUID)) return true;
        for (UUID tokenUUID : partyData.getTokens()) {
            if (isTokenOwnedBy(tokenUUID, playerUUID)) return true;
        }
        return false;
    }

    /**
     * @return true if the player owns the token (read on the loaded token, else on the turns of the party)
     */
    public boolean isTokenOwnedBy(UUID tokenUUID, UUID playerUUID) {
        if (this.world instanceof ServerWorld serverWorld && serverWorld.getEntity(tokenUUID) instanceof TokenizedEntityInterface token)
            return playerUUID.equals(token.steveparty$getTokenOwner());
        for (PartyStep step : partyData.getSteps()) {
            if (step instanceof TokenTurnPartyStep turn && tokenUUID.equals(turn.getTokenUUID()) && playerUUID.equals(turn.getOwnerUUID()))
                return true;
        }
        return false;
    }

    /**
     * Players told about the party events: the connected interested players and the players near the controller.
     */
    public List<ServerPlayerEntity> getPartyAudience() {
        List<ServerPlayerEntity> audience = new ArrayList<>();
        if (this.world instanceof ServerWorld serverWorld) {
            for (UUID playerUUID : interestedPlayers) {
                ServerPlayerEntity player = serverWorld.getServer().getPlayerManager().getPlayer(playerUUID);
                if (player != null) audience.add(player);
            }
            for (ServerPlayerEntity player : serverWorld.getPlayers()) {
                if (!audience.contains(player) && player.getPos().isInRange(this.pos.toCenterPos(), PARTY_AUDIENCE_RADIUS))
                    audience.add(player);
            }
        }
        return audience;
    }

    private void endCurrentStep() {
        PartyStep currentStep = partyData.getCurrentStep();
        if (currentStep != null) {
            currentStep.setStatus(PartyStep.Status.FINISHED);
            currentStep.end(this);
        }
    }

    private void startStep(int stepIndex) {
        // The party is live: nothing left to resume from a previous load
        resumeDone = true;
        partyData.setStepIndex(stepIndex);
        PartyStep currentStep = partyData.getCurrentStep();
        if (currentStep != null)
            currentStep.start(this);
        markDirty();
        sendPacketToInterestedPlayers();
    }

    public void addInterestedPlayer(ServerPlayerEntity player) {
        interestedPlayers.add(player.getUuid());
        sendPacketToInterestedPlayer(player);
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
            if (this.world.getPlayerByUuid(playerUUID) instanceof ServerPlayerEntity player)
                this.sendClearPacketToPlayer(player);
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

    public void sendPacketToInterestedPlayers() {
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
