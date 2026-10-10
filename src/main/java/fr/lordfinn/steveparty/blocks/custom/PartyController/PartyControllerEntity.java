package fr.lordfinn.steveparty.blocks.custom.PartyController;

import fr.lordfinn.steveparty.blocks.ModBlockEntities;
import fr.lordfinn.steveparty.blocks.SyncedBlockEntity;
import fr.lordfinn.steveparty.blocks.custom.GoalPoleNetwork;
import fr.lordfinn.steveparty.blocks.custom.PartyBellBlockEntity;
import fr.lordfinn.steveparty.blocks.custom.PartyController.steps.BasicGameGeneratorStep;
import fr.lordfinn.steveparty.blocks.custom.PartyController.steps.PartyStep;
import fr.lordfinn.steveparty.blocks.custom.PartyController.steps.StartRollsStep;
import fr.lordfinn.steveparty.blocks.custom.PartyController.steps.TokenTurnPartyStep;
import fr.lordfinn.steveparty.blocks.custom.PartyController.steps.MiniGamePartyStep;
import fr.lordfinn.steveparty.blocks.custom.boardspaces.BoardSpaceBlockEntity;
import fr.lordfinn.steveparty.blocks.custom.boardspaces.BoardSpaceType;
import fr.lordfinn.steveparty.board.BoardValidator;
import fr.lordfinn.steveparty.commands.PartyCommands;
import fr.lordfinn.steveparty.entities.TokenStatus;
import fr.lordfinn.steveparty.entities.TokenizedEntityInterface;
import fr.lordfinn.steveparty.entities.custom.DiceEntity;
import fr.lordfinn.steveparty.items.custom.MiniGamesCatalogueItem;
import fr.lordfinn.steveparty.payloads.custom.BlockPosPayload;
import fr.lordfinn.steveparty.powerups.effects.TrapEffect;
import fr.lordfinn.steveparty.powerups.effects.TrapState;
import fr.lordfinn.steveparty.screen_handlers.ScreenHandlerChecks;
import fr.lordfinn.steveparty.screen_handlers.custom.PartyControllerScreenHandler;
import fr.lordfinn.steveparty.service.PartyStars;
import fr.lordfinn.steveparty.service.ShopStops;
import fr.lordfinn.steveparty.utils.InventoryUtils;
import fr.lordfinn.steveparty.utils.MessageUtils;
import net.fabricmc.fabric.api.networking.v1.PacketSender;
import net.fabricmc.fabric.api.screenhandler.v1.ExtendedScreenHandlerFactory;
import net.minecraft.inventory.Inventories;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.component.ComponentMap;
import net.minecraft.entity.Entity;
import net.minecraft.entity.mob.MobEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.inventory.SimpleInventory;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtElement;
import net.minecraft.nbt.NbtList;
import net.minecraft.nbt.NbtString;
import net.minecraft.registry.RegistryWrapper;
import net.minecraft.screen.ScreenHandler;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayNetworkHandler;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.text.Text;
import net.minecraft.util.ActionResult;
import net.minecraft.util.Formatting;
import net.minecraft.util.ItemScatterer;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.*;

import static fr.lordfinn.steveparty.components.ModComponents.*;

/**
 * The Party Controller: runs a party on the board around it. It holds the party ({@link PartyData}) and the catalogue,
 * and hands the rest to its collaborators: its rules ({@link PartySettings}), its program ({@link PartyProgram}), the
 * course of its steps ({@link PartyFlow}), who follows it ({@link PartyAudience}), where its tokens come from and go
 * back to ({@link PartyTokenHomes}), the loaded controllers ({@link PartyControllers}) and the board around
 * ({@link PartyBoard}).
 */
public class PartyControllerEntity extends SyncedBlockEntity implements ExtendedScreenHandlerFactory<BlockPosPayload> {
    private static final String BANK_ITEMS = "BankItems";
    public ItemStack catalogue = ItemStack.EMPTY;
    private PartyData partyData = new PartyData();
    /** The board of a party: its start tiles and star spaces are looked for this far from the controller. */
    public static final int START_TILES_SEARCH_RADIUS = 100;
    /** Radius around the controller in which the players are told about the party (absent turns, exclusions...). */
    public static final int PARTY_AUDIENCE_RADIUS = 100;
    /** Players who won the last mini-game (piggy banks may reward them). */
    private final List<UUID> lastWinners = new ArrayList<>();
    /** The party program: party cards read in order (dashboard, Program page). Empty: the default party. */
    /** 2 rows of 12 (the 18 of the 9 x 2 grid first: a saved program keeps its cards in their order). */
    public static final int PROGRAM_SLOTS = 24;
    /** How often the live state of the party (current turn, standings: see {@link PartyLiveData}) is checked. */
    public static final int LIVE_SYNC_INTERVAL_TICKS = 5;
    /** Rounds a party may have (Settings page). */
    public static final int MIN_ROUNDS = 1, MAX_ROUNDS = 50;
    /** The most dice the « Allowed dice » setting lists. */
    public static final int MAX_ALLOWED_DICE = 27;
    /** The slots of its own bank. */
    public static final int BANK_SIZE = 27;
    /**
     * The star space holding the party's star (see {@link PartyStars}); null while the party has no star yet, or
     * while it waits, hidden, for a star space to be switched on. Saved with the party.
     */
    private @Nullable BlockPos starSpace;

    private final PartySettings settings = new PartySettings();
    private final PartyProgram program = new PartyProgram(this::markDirty);
    private final PartyAudience audience = new PartyAudience(this);
    private final PartyTokenHomes tokenHomes = new PartyTokenHomes();
    private final PartyFlow flow = new PartyFlow(this);
    /** Its own bank, 27 slots like a chest (see {@link PartyBank}): saved, never sent to the clients. */
    private final SimpleInventory bankItems = new SimpleInventory(BANK_SIZE) {
        @Override
        public boolean canPlayerUse(PlayerEntity player) {
            return !isRemoved() && canEdit(player) && ScreenHandlerChecks.canUseBlockEntity(PartyControllerEntity.this, player);
        }
    };
    {
        bankItems.addListener(inventory -> saveOnly());
    }

    public PartyControllerEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.PARTY_CONTROLLER_ENTITY, pos, state);
    }

    PartyAudience audience() {
        return audience;
    }

    PartyTokenHomes tokenHomes() {
        return tokenHomes;
    }

    @Override
    public void setWorld(World world) {
        super.setWorld(world);
        PartyControllers.register(this);
    }

    @Override
    public void cancelRemoval() {
        super.cancelRemoval();
        PartyControllers.register(this);
    }

    @Override
    public void markRemoved() {
        super.markRemoved();
        PartyControllers.unregister(this);
    }

    // ------------------------------------------------------------------ the loaded controllers (see PartyControllers)

    /**
     * A token was renamed (a name tag, /data, any change of its custom name): the parties it plays remember the new
     * name (for while it is unloaded) and send it to their players right away (the standings, the turn bar, the
     * notice, the dashboard), not at the next step.
     */
    public static void onTokenRenamed(ServerWorld world, UUID token, @Nullable Text name) {
        PartyControllers.onTokenRenamed(world, token, name == null ? null : name.getString());
    }

    /** @return true if this party plays the token: its turns remember its new name. */
    boolean rememberTokenName(UUID token, @Nullable String name) {
        PartyData data = getPartyData();
        if (!data.isStarted() || !data.getTokens().contains(token)) return false;
        for (PartyStep step : data.getSteps()) {
            if (step instanceof TokenTurnPartyStep turn && token.equals(turn.getTokenUUID()))
                turn.setTokenName(name);
        }
        markDirty();
        return true;
    }

    /** Snapshot of the loaded server-side controllers (safe to iterate while steps change). */
    public static List<PartyControllerEntity> getActivePartyControllers() { return PartyControllers.all(); }
    public static PartyControllerEntity getPartyControllerEntity(World world, BlockPos pos) {
        return PartyControllers.at(world, pos);
    }

    /**
     * The loaded controller of the party playing right now the mini-game of one of {@code pages} (its practice round
     * or its real round), in any dimension and at any distance (what the podiums and the step controllers linked to
     * a page act on).
     */
    public static Optional<PartyControllerEntity> getPartyPlayingPage(Collection<UUID> pages) {
        return PartyControllers.playingPage(pages);
    }

    /**
     * Closest party controller a step controller can act on: a started party or, if {@code includeEnded},
     * a party standing on its END step (so it can be brought back to the previous step).
     */
    public static Optional<PartyControllerEntity> getClosestSteppablePartyControllerEntity(@Nullable World world, BlockPos pos, int radius, boolean includeEnded) {
        return PartyControllers.closestSteppable(world, pos, radius, includeEnded);
    }

    /** The loaded controller whose running party contains the token, if any. */
    public static Optional<PartyControllerEntity> getRunningPartyOf(UUID tokenUUID) {
        return PartyControllers.runningPartyOf(tokenUUID);
    }

    public static boolean isTokenInRunningParty(UUID tokenUUID) {
        return PartyControllers.isTokenInRunningParty(tokenUUID);
    }

    public static void handlePlayerJoin(ServerPlayNetworkHandler handler, PacketSender sender, MinecraftServer server) {
        PartyControllers.onPlayerJoin(handler, sender, server);
    }

    public static ActionResult handleDiceRoll(DiceEntity dice, UUID ownerUUID, int rollValue) {
        return PartyControllers.onDiceRoll(dice, ownerUUID, rollValue);
    }

    public static ActionResult handleTileReached(@NotNull MobEntity token, @NotNull BoardSpaceBlockEntity boardSpaceEntity) {
        return PartyControllers.onTileReached(token, boardSpaceEntity);
    }

    // ------------------------------------------------------------------ save

    @Override
    protected void readComponents(ComponentsAccess components) {
        super.readComponents(components);
        this.catalogue = components.getOrDefault(CATALOGUE, ItemStack.EMPTY);
    }

    @Override
    protected void addComponents(ComponentMap.Builder componentMapBuilder) {
        super.addComponents(componentMapBuilder);
        // Its codec refuses an empty stack: an empty controller copied (pick block) would give an item that can be
        // neither sent nor saved
        if (!catalogue.isEmpty()) componentMapBuilder.add(CATALOGUE, this.catalogue);
    }

    @Override
    public void removeFromCopiedStackNbt(NbtCompound nbt) {
        super.removeFromCopiedStackNbt(nbt);
        // Carried by the component on a copied item, not twice
        nbt.remove("catalogue");
    }

    @Override
    public void writeNbt(NbtCompound nbt, RegistryWrapper.WrapperLookup wrapper) {
        super.writeNbt(nbt, wrapper);
        audience.writeNbt(nbt);
        if (!catalogue.isEmpty()) {
            NbtElement item = catalogue.encode(wrapper, new NbtCompound());
            nbt.put("catalogue", item);
            nbt.putBoolean("isCatalogued", true);
        } else {
            nbt.putBoolean("isCatalogued", false);
        }
        partyData.toNbt(nbt);
        settings.writeNbt(nbt, wrapper);
        if (starSpace != null) nbt.putLong("StarSpace", starSpace.asLong());
        tokenHomes.writeNbt(nbt);
        if (!lastWinners.isEmpty()) {
            NbtList winnersNbt = new NbtList();
            lastWinners.forEach(uuid -> winnersNbt.add(NbtString.of(uuid.toString())));
            nbt.put("LastWinners", winnersNbt);
        }
        program.writeNbt(nbt, wrapper);
        if (!bankItems.isEmpty()) nbt.put(BANK_ITEMS, Inventories.writeNbt(new NbtCompound(), bankItems.getHeldStacks(), wrapper));
    }

    @Override
    public void readNbt(NbtCompound nbt, RegistryWrapper.WrapperLookup wrapper) {
        super.readNbt(nbt, wrapper);
        audience.readNbt(nbt);
        NbtElement catalogueElem = nbt.get("catalogue");
        if (catalogueElem != null) {
            Optional<ItemStack> socketedStoryNbt = ItemStack.fromNbt(wrapper, nbt.get("catalogue"));
            socketedStoryNbt.ifPresentOrElse(stack -> catalogue = stack, () -> catalogue = ItemStack.EMPTY);
        }
        if (!nbt.getBoolean("isCatalogued"))
            catalogue = ItemStack.EMPTY;
        partyData = new PartyData(nbt);
        settings.readNbt(nbt, wrapper);
        starSpace = nbt.contains("StarSpace") ? BlockPos.fromLong(nbt.getLong("StarSpace")) : null;
        tokenHomes.readNbt(nbt);
        lastWinners.clear();
        nbt.getList("LastWinners", NbtElement.STRING_TYPE).forEach(element -> {
            try {
                lastWinners.add(UUID.fromString(element.asString()));
            } catch (IllegalArgumentException ignored) {
            }
        });
        program.readNbt(nbt, wrapper);
        // A controller saved before it had its own bank: an empty one (its linked chests are kept)
        bankItems.getHeldStacks().clear();
        if (nbt.contains(BANK_ITEMS)) Inventories.readNbt(nbt.getCompound(BANK_ITEMS), bankItems.getHeldStacks(), wrapper);
        flow.loaded();
    }

    /** What the clients get: everything saved but the traps (Trap power-up): the board spaces show them. */
    @Override
    public NbtCompound toInitialChunkDataNbt(RegistryWrapper.WrapperLookup registries) {
        NbtCompound nbt = super.toInitialChunkDataNbt(registries);
        nbt.remove(TrapState.NBT_KEY);
        nbt.remove(BANK_ITEMS);
        return nbt;
    }

    // ------------------------------------------------------------------ the star

    /** The star space holding the party's star, null for none (not placed yet, or hidden: no star space is on). */
    public @Nullable BlockPos getStarSpace() {
        return starSpace;
    }

    public void setStarSpace(@Nullable BlockPos starSpace) {
        if (Objects.equals(this.starSpace, starSpace)) return;
        this.starSpace = starSpace == null ? null : starSpace.toImmutable();
        super.markDirty(); // saved; nothing to sync (the star has its own packet: see PartyStars)
    }

    // ------------------------------------------------------------------ settings (the dashboard's Settings page, see PartySettings)

    /** The item counted as this currency (a copy, count 1). */
    public ItemStack getCurrency(PartyCurrency currency) {
        return settings.getCurrency(currency);
    }

    /**
     * Picks the item counted as a currency: {@code picked} (its item and components, whatever its count), or the
     * default item for an empty stack. Refused if the other currency already uses that very item.
     *
     * @return false if refused
     */
    public boolean setCurrency(PartyCurrency currency, ItemStack picked) {
        if (!settings.setCurrency(currency, picked)) return false;
        markDirty();
        return true;
    }

    /** What the party pays at the end of each of its mini-games, by place. */
    public MiniGameGains getGains() {
        return settings.getGains();
    }

    public void setGains(MiniGameGains gains) {
        if (settings.setGains(gains)) markDirty();
    }

    /**
     * Its own bank: 27 slots, the first place the party's coins and stars are taken from and put in (see
     * {@link PartyBank}); hoppers fill and empty it, broken it drops its content.
     */
    public SimpleInventory getBankItems() {
        return bankItems;
    }

    /** Saved, not sent (its bank's content is the server's). */
    private void saveOnly() {
        super.markDirty();
    }

    /** The Inventory Cartridge of the bank (Gains page), empty for none. */
    public ItemStack getBank() {
        return settings.getBank();
    }

    public void setBank(ItemStack bank) {
        settings.setBank(bank);
        markDirty();
    }

    /**
     * What a player was paid for his place in a mini-game.
     *
     * @param coins the coins he received
     * @param stars the stars he received
     * @param full  he received the whole gain of his place
     */
    public record Paid(int coins, int stars, boolean full) {
        public int of(PartyCurrency currency) {
            return currency == PartyCurrency.STAR ? stars : coins;
        }
    }

    /**
     * Pays a player what his place in a mini-game is worth, taken from the bank: the party's coins and stars, given
     * as items (what does not fit in his inventory falls at his feet). Nothing is created: a bank short of them pays
     * what it has, no bank ({@link PartyResources#NONE}) pays nothing.
     *
     * @param place 1 for the winners, 2, 3, 4; 0 (or more than 4) for the participants
     * @param bank  the party's source ({@link PartyResources#of(PartyControllerEntity)})
     */
    public Paid payGains(ServerPlayerEntity player, int place, PartyResources bank) {
        int[] paid = new int[2];
        boolean full = true;
        for (PartyCurrency currency : PartyCurrency.values()) {
            int amount = settings.getGains().forPlace(currency, place);
            ItemStack template = getCurrency(currency);
            int taken = bank.take(template, amount);
            if (taken < amount) full = false;
            paid[currency == PartyCurrency.STAR ? 1 : 0] = taken;
            InventoryUtils.giveOrDrop(player, template, taken);
        }
        return new Paid(paid[0], paid[1], full);
    }

    /**
     * @return true if the mini-games of this party start with a practice round (those whose page has a Mini-game
     * Controller: see {@code MiniGamePartyStep})
     */
    public boolean hasPracticeRound() {
        return settings.hasPracticeRound();
    }

    public void setPracticeRound(boolean practiceRound) {
        if (settings.setPracticeRound(practiceRound)) markDirty();
    }

    /**
     * The power-ups a player may carry during this party (Power-up dice included), 0 for no limit.
     * See {@link fr.lordfinn.steveparty.powerups.PowerUpLimit}.
     */
    public int getMaxPowerUps() {
        return settings.getMaxPowerUps();
    }

    public void setMaxPowerUps(int maxPowerUps) {
        if (settings.setMaxPowerUps(maxPowerUps)) markDirty();
    }

    /** Whether only the allowed dice may be thrown during this party (off: every die). */
    public boolean isRestrictDice() {
        return settings.isRestrictDice();
    }

    /** « Infinite bank »: the party's bank never runs out (see {@link PartyResources#of(PartyControllerEntity)}). */
    public boolean isInfiniteBank() {
        return settings.isInfiniteBank();
    }

    public void setInfiniteBank(boolean infiniteBank) {
        if (settings.setInfiniteBank(infiniteBank)) markDirty();
    }

    /** Who may switch the « Infinite bank »: a player in creative mode or an operator (permission level 2). */
    public static boolean canSwitchInfiniteBank(PlayerEntity player) {
        return player.isCreative() || player.hasPermissionLevel(2);
    }

    public void setRestrictDice(boolean restrictDice) {
        if (settings.setRestrictDice(restrictDice)) markDirty();
    }

    /** The dice a player may throw during this party while the dice are restricted, empty for every die (read-only). */
    public List<ItemStack> getAllowedDice() {
        return settings.getAllowedDice();
    }

    /**
     * Puts a copy of {@code die} (one) at {@code index} of the allowed dice, replacing the one there, or after the
     * last one when {@code index} is past it. False if it is not a die, is already listed elsewhere, or the list is full.
     */
    public boolean setAllowedDie(int index, ItemStack die) {
        if (!settings.setAllowedDie(index, die)) return false;
        markDirty();
        return true;
    }

    /** Takes the die at {@code index} off the allowed dice (the next ones move up). False if there is none. */
    public boolean removeAllowedDie(int index) {
        if (!settings.removeAllowedDie(index)) return false;
        markDirty();
        return true;
    }

    /** Sets the number of rounds of the next party: only while no party runs (the rounds are generated at its start). */
    public boolean setRounds(int rounds) {
        if (partyData.isStarted()) return false;
        partyData.setNbTurn(Math.clamp(rounds, MIN_ROUNDS, MAX_ROUNDS));
        markDirty();
        return true;
    }

    /**
     * Who may change the settings of this controller and start a party from its dashboard: a player allowed to build
     * here (not in Adventure / Spectator mode, not in a protected area); once a party runs, only an operator or a
     * Game Master (a Tokenizer Wand enchanted with Game Master in hand), so that no player changes the rules mid-game.
     */
    public boolean canEdit(PlayerEntity player) {
        if (world == null || !ScreenHandlerChecks.canBuildAt(player, pos)) return false;
        if (!partyData.isStarted()) return true;
        return player.hasPermissionLevel(2) || PartyCommands.holdsGameMasterWand(player);
    }

    // ------------------------------------------------------------------ the catalogue

    /**
     * Puts a catalogue in (or takes it out with an empty stack) as a slot does: nothing is given back, the caller
     * moves the stacks.
     */
    public void putCatalogue(ItemStack stack) {
        catalogue = stack;
        markDirty();
        if (world != null && !world.isClient && world.getBlockState(pos).getBlock() instanceof PartyController) {
            BlockState state = world.getBlockState(pos);
            if (state.get(PartyController.CATALOGUED) != !stack.isEmpty())
                world.setBlockState(pos, state.with(PartyController.CATALOGUED, !stack.isEmpty()), Block.NOTIFY_ALL);
        }
    }

    /** The redstone power locks the catalogue in (it can't be taken out while the controller is powered). */
    public boolean isCatalogueLocked() {
        return world != null && world.isReceivingRedstonePower(pos);
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
                holderPlayer.getInventory().offerOrDrop(catalogue);
            } else {
                ItemScatterer.spawn(world, pos.getX() + 0.5f, pos.getY() + 0.5f, pos.getZ() + 0.5f, catalogue);
            }
        }
        if (itemStack.isEmpty() || itemStack.getItem() instanceof MiniGamesCatalogueItem)
            catalogue = itemStack.copy();
        this.markDirty();
        return !catalogue.isEmpty();
    }

    public List<ItemStack> getMiniGames() {
       return MiniGamesCatalogueItem.getStoredPages(catalogue);
    }

    public ItemStack getCatalogue() {
        return catalogue;
    }

    // ------------------------------------------------------------------ the board (see PartyBoard)

    /** The tokens that would play if a party started now: the ones bound to the start tiles around, in their order. */
    public List<UUID> findStartTokens(ServerWorld serverWorld) {
        return new ArrayList<>(findStartTokenTiles(serverWorld).keySet());
    }

    /** The tokens bound to the start tiles around, in their order, each with its start tile (the first one bound to it). */
    public Map<UUID, BlockPos> findStartTokenTiles(ServerWorld serverWorld) {
        return PartyBoard.startTokenTiles(serverWorld, this.getPos());
    }

    /**
     * The board spaces of the type {@code type} within {@link #START_TILES_SEARCH_RADIUS} blocks of {@code center}, in
     * the loaded chunks (none is loaded for this), sorted by z, then y, then x.
     */
    public static List<BlockPos> findBoardSpaces(ServerWorld world, BlockPos center, BoardSpaceType type) {
        return PartyBoard.boardSpaces(world, center, type);
    }

    /** The start tile a token of the running party started from, null if unknown. */
    public @Nullable BlockPos getStartTile(UUID token) {
        return tokenHomes.startTile(token);
    }

    // ------------------------------------------------------------------ tick and sync

    /**
     * Server tick. Scheduled tasks are not saved, so after a load the step that was running is resumed
     * once the party is really playable again (see {@link PartyFlow#tick}).
     */
    public void serverTick(ServerWorld serverWorld) {
        PartyStars.tick(this, serverWorld);
        if (tokenHomes.hasPending() && serverWorld.getTime() % 20 == 0 && tokenHomes.processPending(serverWorld))
            markDirty();
        if (serverWorld.getTime() % 20 == 0) syncChunkHold();
        if (serverWorld.getTime() % LIVE_SYNC_INTERVAL_TICKS == 0)
            syncLiveData(serverWorld);
        flow.tick(serverWorld);
    }

    @Override
    public void markDirty() {
        syncToClients();
        super.markDirty();
        flow.updatePhase();
        syncChunkHold();
    }

    /** @return true while the party is on a mini-game step: the controller must stay loaded (see {@link PartyChunkHolds}). */
    public boolean wantsChunk() {
        return !isRemoved() && partyData.isStarted() && partyData.getCurrentStep() instanceof MiniGamePartyStep;
    }

    /**
     * Keeps the controller's chunk loaded for as long as its party is on a mini-game step (the players are away, maybe
     * in another dimension: the mini-game must still end, pay and bring them back), and lets it go after.
     */
    private void syncChunkHold() {
        if (!(this.world instanceof ServerWorld serverWorld) || isRemoved()) return;
        boolean wanted = wantsChunk();
        if (wanted == PartyChunkHolds.isHeld(serverWorld, pos)) return;
        if (wanted) PartyChunkHolds.hold(serverWorld, pos);
        else PartyChunkHolds.release(serverWorld, pos);
    }

    // ------------------------------------------------------------------ start and stop

    public void boot() {
        if (!(this.world instanceof ServerWorld serverWorld)) return;
        if (partyData.isStarted()) return;

        // A previous (ended) party may still have a step holding scheduled tasks
        flow.endCurrentStep();
        flow.started();
        clearInterestedPlayers();
        getTokenFromStartTiles(serverWorld);
        setTokensStatus(serverWorld);
        // A warning for everyone around if the board has problems (the party still starts)
        BoardValidator.warnAtStart(serverWorld, pos);

        audience.addFromTokens(serverWorld);

        partyData.addStep(new StartRollsStep());
        partyData.addStep(new BasicGameGeneratorStep());

        sendStartGameInfos();
        // The star stands on one of the board's star spaces, at random
        PartyStars.onPartyStarted(this, serverWorld);
        nextStep();
        markDirty();
        // Goal pole bases linked to this party start again from 0
        GoalPoleNetwork.onPartyStarted(this);
    }

    private void setTokensStatus(ServerWorld serverWorld) {
        for (UUID tokenUUID : partyData.getTokens()) {
            if (serverWorld.getEntity(tokenUUID) instanceof TokenizedEntityInterface token) {
                token.steveparty$setStatus(TokenStatus.setStatus(0, TokenStatus.IN_GAME));
            }
        }
    }

    private void getTokenFromStartTiles(ServerWorld serverWorld) {
        Map<UUID, BlockPos> tokens = findStartTokenTiles(serverWorld);
        TrapEffect.clearAll(this);
        partyData.reset();
        tokenHomes.startFrom(tokens);
        for (UUID tokenUUID : tokens.keySet()) partyData.addToken(tokenUUID);
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

    /**
     * Stops the running party at once, without a winner: the current step ends (a mini-game sends its players back
     * and gives its zone back, nothing is paid), the shop stops, star choices, teleports, pipe travels and dice of its
     * tokens are dropped, every token goes back onto the start tile it started from, out of the game, and the
     * controller is back as before the party (a new one can start). Everyone around is told.
     *
     * @param stoppedBy who stopped it, null when its controller is broken or replaced
     * @return false if no party runs (nothing done)
     */
    public boolean stopParty(@Nullable Text stoppedBy) {
        if (!(this.world instanceof ServerWorld serverWorld) || !partyData.isStarted()) return false;
        List<ServerPlayerEntity> audience = getPartyAudience();
        flow.endCurrentStep();
        Set<UUID> tokens = new LinkedHashSet<>(partyData.getTokens());
        Map<UUID, BlockPos> homes = new HashMap<>(tokenHomes.startTiles());
        // A party started without them (an older save): the start tiles still bound to its tokens
        if (!homes.keySet().containsAll(tokens)) findStartTokenTiles(serverWorld).forEach(homes::putIfAbsent);
        PartyTokenHomes.dropDiceOf(serverWorld, pos, tokens, getPlayersInOrder());
        for (UUID tokenUUID : tokens) {
            ShopStops.cancel(tokenUUID);
            PartyStars.cancelOffer(tokenUUID);
            BlockPos home = homes.get(tokenUUID);
            if (home != null) tokenHomes.sendHome(serverWorld, tokenUUID, home);
            releaseToken(serverWorld, tokenUUID);
        }
        TrapEffect.clearAll(this);
        partyData.reset();
        tokenHomes.forgetStartTiles();
        setStarSpace(null);
        PartyStars.sync(serverWorld);
        // The players' HUDs go (steps, standings)
        clearInterestedPlayers();
        this.audience.forgetLiveData();
        Text message = stoppedBy != null
                ? Text.translatableWithFallback("message.steveparty.party_stopped", "The party was stopped by %s.", stoppedBy)
                : Text.translatableWithFallback("message.steveparty.party_stopped.removed", "The party was stopped: its Party Controller is gone.");
        MessageUtils.sendToPlayers(audience, message.copy().formatted(Formatting.RED), MessageUtils.MessageType.CHAT);
        markDirty();
        return true;
    }

    /**
     * The controller block is broken / replaced: the connected interested players (in any dimension) get an empty
     * party so their steps HUD is cleared.
     */
    public void onControllerRemoved() {
        if (!(this.world instanceof ServerWorld serverWorld)) return;
        // A running party stops with it (already done if the player breaking it is known: see PartyController#onBreak)
        stopParty(null);
        PartyChunkHolds.release(serverWorld, pos);
        audience.clearHuds(serverWorld);
    }

    // ------------------------------------------------------------------ the tokens and the players

    /**
     * Takes a token out of the game (clears IN_GAME / CAN_MOVE). A token that is not loaded is released
     * as soon as it is loaded again, unless it joined a running party meanwhile.
     */
    public void releaseToken(ServerWorld serverWorld, UUID tokenUUID) {
        tokenHomes.release(serverWorld, tokenUUID);
        markDirty();
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

    /** The players of the party (owners of its tokens), in play order. */
    public List<UUID> getPlayersInOrder() {
        List<UUID> players = new ArrayList<>();
        for (UUID token : partyData.getTokens()) {
            UUID owner = getTokenOwner(token);
            if (owner != null && !players.contains(owner)) players.add(owner);
        }
        return players;
    }

    /**
     * @return true if the player takes part in this party: interested player, or owner of one of its tokens
     */
    public boolean isParticipant(ServerPlayerEntity player) {
        UUID playerUUID = player.getUuid();
        if (audience.ids().contains(playerUUID)) return true;
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

    public List<UUID> getLastWinners() {
        return Collections.unmodifiableList(lastWinners);
    }

    public void setLastWinners(List<UUID> winners) {
        lastWinners.clear();
        lastWinners.addAll(winners);
        markDirty();
    }

    public PartyData getPartyData() {
        return partyData;
    }

    public void setPartyData(PartyData partyData) {
        this.partyData = partyData;
        markDirty();
    }

    // ---------------------------------------------------------------- party program (dashboard, Program page; see PartyProgram)

    public SimpleInventory getProgram() {
        return program.inventory();
    }

    /** The cards of the party program, in reading order (copies). Empty: the default party. */
    public List<ItemStack> getProgramCards() {
        return program.cards();
    }

    // ---------------------------------------------------------------- the steps (see PartyFlow)

    public void nextStep() {
        flow.nextStep();
    }

    public void restartStep() {
        flow.restartStep();
    }

    public void previousStep() {
        flow.previousStep();
    }

    /**
     * A party bell in waiting mode received its signal: the step waiting for {@code moment} goes on.
     *
     * @return true if a step was waiting for it
     */
    public boolean releaseMoment(PartyMoment moment) {
        return flow.releaseMoment(moment);
    }

    /**
     * Rings the bells of a moment happening inside a step (dice rolled, mini-game chosen...).
     *
     * @return true if a bell waits at this moment (the step must pause until {@link PartyStep#onMomentReleased})
     */
    public boolean ringMoment(PartyMoment moment, int value) {
        return flow.ringMoment(moment, value);
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

    /**
     * The roll of {@code token} moved nothing (coin, debt or swap face): its turn ends where it stands, without
     * landing on its tile.
     *
     * @return false if it is not the turn of that token (nothing done)
     */
    public boolean endTurnOf(UUID token) {
        if (!(partyData.getCurrentStep() instanceof TokenTurnPartyStep turn) || turn.getStatus() != PartyStep.Status.IN_PROGRESS
                || !token.equals(turn.getTokenUUID())) return false;
        nextStep();
        return true;
    }

    /** The roll of the turn of {@code token} gave or took coins: the party HUD shows it. */
    public void noteRollCoins(UUID token, int coins) {
        if (partyData.getCurrentStep() instanceof TokenTurnPartyStep turn && token.equals(turn.getTokenUUID())) turn.noteCoins(coins);
    }

    /** The roll of the turn of {@code token} swapped it with the token named {@code with}: the party HUD shows it. */
    public void noteRollSwap(UUID token, String with) {
        if (partyData.getCurrentStep() instanceof TokenTurnPartyStep turn && token.equals(turn.getTokenUUID())) turn.noteSwap(with);
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
        return flow.phase();
    }

    // ---------------------------------------------------------------- who follows the party (see PartyAudience)

    /**
     * Players told about the party events: the connected interested players and the players near the controller.
     */
    public List<ServerPlayerEntity> getPartyAudience() {
        return audience.partyAudience();
    }

    public void addInterestedPlayer(ServerPlayerEntity player) {
        audience.add(player);
    }

    public void removeInterestedPlayer(ServerPlayerEntity player) {
        audience.remove(player);
    }

    // Clear the interestedPlayers list
    public void clearInterestedPlayers() {
        audience.clear();
    }

    public Set<UUID> getInterestedPlayers() {
        return audience.ids();
    }

    public List<ServerPlayerEntity> getInterestedPlayersEntities() {
        return audience.entities();
    }

    public void sendPacketToInterestedPlayers() {
        audience.sendToAll();
    }

    void sendPacketToInterestedPlayer(ServerPlayerEntity player) {
        audience.sendTo(player);
    }

    /**
     * Sends the live state of the party (see {@link PartyLiveData}) to the connected interested players when it
     * changed. Checked every {@value #LIVE_SYNC_INTERVAL_TICKS} ticks while a party is running.
     */
    public void syncLiveData(ServerWorld serverWorld) {
        audience.syncLiveData(serverWorld);
    }

    /** The live state last sent to the interested players ({@link PartyLiveData#EMPTY} while no party runs). */
    public PartyLiveData getLastLiveData() {
        return audience.lastLiveData();
    }

    public void sendPacketToInterestedPlayer(ServerPlayerEntity player, PartyData partyData) {
        PartyAudience.send(player, partyData);
    }

    // ------------------------------------------------------------------ dashboard (right click)

    @Override
    public BlockPosPayload getScreenOpeningData(ServerPlayerEntity player) {
        return new BlockPosPayload(pos);
    }

    @Override
    public Text getDisplayName() {
        return Text.translatable("block.steveparty.party_controller");
    }

    @Override
    public ScreenHandler createMenu(int syncId, PlayerInventory playerInventory, PlayerEntity player) {
        return new PartyControllerScreenHandler(syncId, playerInventory, this);
    }
}
