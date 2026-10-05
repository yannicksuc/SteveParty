package fr.lordfinn.steveparty.blocks.custom.PartyController;

import fr.lordfinn.steveparty.blocks.SyncedBlockEntity;
import fr.lordfinn.steveparty.utils.InventoryUtils;
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
import fr.lordfinn.steveparty.payloads.custom.BlockPosPayload;
import fr.lordfinn.steveparty.payloads.custom.PartyDataPayload;
import fr.lordfinn.steveparty.screen_handlers.custom.PartyControllerScreenHandler;
import net.fabricmc.fabric.api.screenhandler.v1.ExtendedScreenHandlerFactory;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.screen.ScreenHandler;
import fr.lordfinn.steveparty.payloads.custom.PartyLivePayload;
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
import net.minecraft.inventory.Inventories;
import net.minecraft.inventory.SimpleInventory;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.registry.Registries;
import net.minecraft.nbt.NbtElement;
import net.minecraft.nbt.NbtList;
import net.minecraft.nbt.NbtString;
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

public class PartyControllerEntity extends SyncedBlockEntity implements ExtendedScreenHandlerFactory<BlockPosPayload> {
    public ItemStack catalogue = ItemStack.EMPTY;
    private PartyData partyData = new PartyData();
    /** Server-side only registry of the loaded controllers, keyed by dimension + position. */
    private static final Map<GlobalPos, PartyControllerEntity> ACTIVE_PARTY_CONTROLLERS = fr.lordfinn.steveparty.utils.ServerMemory.forgetOnStop(new LinkedHashMap<>());
    /** The board of a party: its start tiles and star spaces are looked for this far from the controller. */
    public static final int START_TILES_SEARCH_RADIUS = 100;
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
    /** The party program: party cards read in order (dashboard, Program page). Empty: the default party. */
    /** 2 rows of 12 (the 18 of the 9 x 2 grid first: a saved program keeps its cards in their order). */
    public static final int PROGRAM_SLOTS = 24;
    private final SimpleInventory program = new SimpleInventory(PROGRAM_SLOTS) {
        @Override
        public void markDirty() {
            super.markDirty();
            PartyControllerEntity.this.markDirty();
        }
    };
    /** How often the live state of the party (current turn, standings: see {@link PartyLiveData}) is checked. */
    public static final int LIVE_SYNC_INTERVAL_TICKS = 5;
    /** The live state last sent to the interested players (only a change is sent). */
    private PartyLiveData lastLiveData = PartyLiveData.EMPTY;
    /** The items counted as stars and coins by this party (one of each, never empty): see {@link PartyCurrency}. */
    private ItemStack starItem = PartyCurrency.STAR.defaultStack();
    private ItemStack coinItem = PartyCurrency.COIN.defaultStack();
    /** What the party pays at the end of each mini-game, by place (Gains page). */
    private MiniGameGains gains = MiniGameGains.DEFAULT;
    /** The Inventory Cartridge whose chest the gains are taken from (Gains page), empty for none: see {@link PartyBank}. */
    private ItemStack bank = ItemStack.EMPTY;
    /** Rounds a party may have (Settings page). */
    public static final int MIN_ROUNDS = 1, MAX_ROUNDS = 50;
    /** A practice round before each mini-game whose page has a Mini-game Controller (Settings page). */
    private boolean practiceRound = true;
    /**
     * The star space holding the party's star (see {@link fr.lordfinn.steveparty.service.PartyStars}); null while the
     * party has no star yet, or while it waits, hidden, for a star space to be switched on. Saved with the party.
     */
    private @Nullable BlockPos starSpace;

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
        if (this.world instanceof ServerWorld)
            ACTIVE_PARTY_CONTROLLERS.remove(GlobalPos.create(this.world.getRegistryKey(), this.pos), this);
    }

    /**
     * A token was renamed (a name tag, /data, any change of its custom name): the parties it plays remember the new
     * name (for while it is unloaded) and send it to their players right away (the standings, the turn bar, the
     * notice, the dashboard), not at the next step.
     */
    public static void onTokenRenamed(ServerWorld world, UUID token, @Nullable Text name) {
        for (PartyControllerEntity controller : ACTIVE_PARTY_CONTROLLERS.values()) {
            if (controller.isRemoved() || controller.getWorld() != world) continue;
            PartyData data = controller.getPartyData();
            if (!data.isStarted() || !data.getTokens().contains(token)) continue;
            for (PartyStep step : data.getSteps()) {
                if (step instanceof TokenTurnPartyStep turn && token.equals(turn.getTokenUUID()))
                    turn.setTokenName(name == null ? null : name.getString());
            }
            controller.markDirty();
            controller.syncLiveData(world);
        }
    }

    /** Snapshot of the loaded server-side controllers (safe to iterate while steps change). */
    public static List<PartyControllerEntity> getActivePartyControllers() { return List.copyOf(ACTIVE_PARTY_CONTROLLERS.values()); }
    public static PartyControllerEntity getPartyControllerEntity(World world, BlockPos pos) {
        return ACTIVE_PARTY_CONTROLLERS.get(GlobalPos.create(world.getRegistryKey(), pos));
    }

    /**
     * The loaded controller of the party playing right now the mini-game of one of {@code pages} (its practice round
     * or its real round), in any dimension and at any distance (what the podiums and the step controllers linked to
     * a page act on).
     */
    public static Optional<PartyControllerEntity> getPartyPlayingPage(Collection<UUID> pages) {
        if (pages.isEmpty()) return Optional.empty();
        for (PartyControllerEntity entity : ACTIVE_PARTY_CONTROLLERS.values()) {
            if (entity.isRemoved() || !(entity.getPartyData().getCurrentStep() instanceof MiniGamePartyStep miniGame)
                    || !miniGame.isOnArena()) continue;
            UUID page = fr.lordfinn.steveparty.minigame.MiniGamePages.idOf(MiniGamesCatalogueItem.getCurrentMiniGame(entity.catalogue));
            if (page != null && pages.contains(page)) return Optional.of(entity);
        }
        return Optional.empty();
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
        // Serialize interestedPlayers list
        NbtCompound playersNbt = new NbtCompound();
        int i = 0;
        for (UUID playerUUID : interestedPlayers) {
            playersNbt.putString("player_" + i, playerUUID.toString());
            i++;
        }
        nbt.put("interestedPlayers", playersNbt);

        if (!catalogue.isEmpty()) {
            NbtElement item = catalogue.encode(wrapper, new NbtCompound());
            nbt.put("catalogue", item);
            nbt.putBoolean("isCatalogued", true);
        } else {
            nbt.putBoolean("isCatalogued", false);
        }
        partyData.toNbt(nbt);
        nbt.put(PartyCurrency.STAR.nbtKey(), starItem.encode(wrapper));
        nbt.put(PartyCurrency.COIN.nbtKey(), coinItem.encode(wrapper));
        nbt.put("MiniGameGains", gains.toNbt());
        if (!bank.isEmpty()) nbt.put("BankCartridge", bank.encode(wrapper));
        nbt.putBoolean("PracticeRound", practiceRound);
        if (starSpace != null) nbt.putLong("StarSpace", starSpace.asLong());
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
        if (!program.isEmpty()) {
            NbtCompound programNbt = new NbtCompound();
            Inventories.writeNbt(programNbt, program.getHeldStacks(), wrapper);
            nbt.put("PartyProgram", programNbt);
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
        starItem = readCurrency(nbt, wrapper, PartyCurrency.STAR);
        coinItem = readCurrency(nbt, wrapper, PartyCurrency.COIN);
        gains = MiniGameGains.fromNbt(nbt.getCompound("MiniGameGains"));
        NbtElement bankElement = nbt.get("BankCartridge");
        bank = bankElement == null ? ItemStack.EMPTY : ItemStack.fromNbt(wrapper, bankElement).orElse(ItemStack.EMPTY);
        practiceRound = !nbt.contains("PracticeRound") || nbt.getBoolean("PracticeRound");
        starSpace = nbt.contains("StarSpace") ? BlockPos.fromLong(nbt.getLong("StarSpace")) : null;
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
        program.getHeldStacks().clear();
        Inventories.readNbt(nbt.getCompound("PartyProgram"), program.getHeldStacks(), wrapper);
        resumeDone = false;
    }

    /** What the clients get: everything saved but the hidden traps (Trap power-up), which only their owner may know. */
    @Override
    public NbtCompound toInitialChunkDataNbt(RegistryWrapper.WrapperLookup registries) {
        NbtCompound nbt = super.toInitialChunkDataNbt(registries);
        nbt.remove(fr.lordfinn.steveparty.powerups.effects.TrapState.NBT_KEY);
        return nbt;
    }

    private static ItemStack readCurrency(NbtCompound nbt, RegistryWrapper.WrapperLookup wrapper, PartyCurrency currency) {
        NbtElement element = nbt.get(currency.nbtKey());
        ItemStack stack = element == null ? ItemStack.EMPTY : ItemStack.fromNbt(wrapper, element).orElse(ItemStack.EMPTY);
        return currency.template(stack);
    }

    // ------------------------------------------------------------------ the star

    /** The star space holding the party's star, null for none (not placed yet, or hidden: no star space is on). */
    public @Nullable BlockPos getStarSpace() {
        return starSpace;
    }

    public void setStarSpace(@Nullable BlockPos starSpace) {
        if (java.util.Objects.equals(this.starSpace, starSpace)) return;
        this.starSpace = starSpace == null ? null : starSpace.toImmutable();
        super.markDirty(); // saved; nothing to sync (the star has its own packet: see PartyStars)
    }

    // ------------------------------------------------------------------ settings (the dashboard's Settings page)

    /** The item counted as this currency (a copy, count 1). */
    public ItemStack getCurrency(PartyCurrency currency) {
        return (currency == PartyCurrency.STAR ? starItem : coinItem).copy();
    }

    /**
     * Picks the item counted as a currency: {@code picked} (its item and components, whatever its count), or the
     * default item for an empty stack. Refused if the other currency already uses that very item.
     *
     * @return false if refused
     */
    public boolean setCurrency(PartyCurrency currency, ItemStack picked) {
        ItemStack template = currency.template(picked);
        if (ItemStack.areItemsAndComponentsEqual(template, currency == PartyCurrency.STAR ? coinItem : starItem)) return false;
        if (currency == PartyCurrency.STAR) starItem = template;
        else coinItem = template;
        markDirty();
        return true;
    }

    /** What the party pays at the end of each of its mini-games, by place. */
    public MiniGameGains getGains() {
        return gains;
    }

    public void setGains(MiniGameGains gains) {
        if (gains == null || gains.equals(this.gains)) return;
        this.gains = gains;
        markDirty();
    }

    /** The Inventory Cartridge of the bank (Gains page), empty for none. */
    public ItemStack getBank() {
        return bank;
    }

    public void setBank(ItemStack bank) {
        this.bank = bank == null ? ItemStack.EMPTY : bank;
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
     * what it has, an absent bank (null) pays nothing.
     *
     * @param place 1 for the winners, 2, 3, 4; 0 (or more than 4) for the participants
     * @param bank  the bank's chest ({@link PartyBank#inventory}), null for none
     */
    public Paid payGains(ServerPlayerEntity player, int place, @Nullable net.minecraft.inventory.Inventory bank) {
        int[] paid = new int[2];
        boolean full = true;
        for (PartyCurrency currency : PartyCurrency.values()) {
            int amount = gains.forPlace(currency, place);
            ItemStack template = getCurrency(currency);
            int taken = InventoryUtils.take(bank, template, amount);
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
        return practiceRound;
    }

    public void setPracticeRound(boolean practiceRound) {
        if (this.practiceRound == practiceRound) return;
        this.practiceRound = practiceRound;
        markDirty();
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
        if (world == null || !fr.lordfinn.steveparty.screen_handlers.ScreenHandlerChecks.canBuildAt(player, pos)) return false;
        if (!partyData.isStarted()) return true;
        return player.hasPermissionLevel(2) || fr.lordfinn.steveparty.commands.PartyCommands.holdsGameMasterWand(player);
    }

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
                world.setBlockState(pos, state.with(PartyController.CATALOGUED, !stack.isEmpty()), net.minecraft.block.Block.NOTIFY_ALL);
        }
    }

    /** The redstone power locks the catalogue in (it can't be taken out while the controller is powered). */
    public boolean isCatalogueLocked() {
        return world != null && world.isReceivingRedstonePower(pos);
    }

    /** The tokens that would play if a party started now: the ones bound to the start tiles around, in their order. */
    public List<UUID> findStartTokens(ServerWorld serverWorld) {
        List<UUID> tokens = new ArrayList<>();
        for (BlockPos tilePos : findStartTiles(serverWorld, this.getPos())) {
            if (!(serverWorld.getBlockEntity(tilePos) instanceof BoardSpaceBlockEntity tile)) continue;
            String potentialUuid = tile.getActiveCartridgeItemStack().get(TB_START_BOUND_ENTITY);
            if (potentialUuid == null) continue;
            try {
                UUID uuid = UUID.fromString(potentialUuid);
                if (!tokens.contains(uuid)) tokens.add(uuid);
            } catch (IllegalArgumentException ignored) {
            }
        }
        return tokens;
    }

    /**
     * Server tick. Scheduled tasks are not saved, so after a load the step that was running is resumed
     * once the party is really playable again (at least one token loaded and one owner online, or any player
     * for an ownerless token), otherwise
     * a step could wrongly consider the tokens as missing.
     */
    public void serverTick(ServerWorld serverWorld) {
        fr.lordfinn.steveparty.service.PartyStars.tick(this, serverWorld);
        if (!tokensToRelease.isEmpty() && serverWorld.getTime() % 20 == 0)
            releasePendingTokens(serverWorld);
        if (serverWorld.getTime() % 20 == 0) syncChunkHold();
        if (serverWorld.getTime() % LIVE_SYNC_INTERVAL_TICKS == 0)
            syncLiveData(serverWorld);
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
    public void markDirty() {
        syncToClients();
        super.markDirty();
        updatePhase();
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

    public void boot() {
        if (!(this.world instanceof ServerWorld serverWorld)) return;
        if (partyData.isStarted()) return;

        // A previous (ended) party may still have a step holding scheduled tasks
        endCurrentStep();
        resumeDone = true;
        clearInterestedPlayers();
        getTokenFromStartTiles(serverWorld);
        setTokensStatus(serverWorld);
        // A warning for everyone around if the board has problems (the party still starts)
        fr.lordfinn.steveparty.board.BoardValidator.warnAtStart(serverWorld, pos);

        addInterestedPlayersFromTokens(serverWorld);

        partyData.addStep(new StartRollsStep());
        partyData.addStep(new BasicGameGeneratorStep());

        sendStartGameInfos();
        // The star stands on one of the board's star spaces, at random
        fr.lordfinn.steveparty.service.PartyStars.onPartyStarted(this, serverWorld);
        nextStep();
        markDirty();
        // Goal pole bases linked to this party start again from 0
        fr.lordfinn.steveparty.blocks.custom.GoalPoleNetwork.onPartyStarted(this);
    }

    private void setTokensStatus(ServerWorld serverWorld) {
        for (UUID tokenUUID : partyData.getTokens()) {
            if (serverWorld.getEntity(tokenUUID) instanceof TokenizedEntityInterface token) {
                token.steveparty$setStatus(TokenStatus.setStatus(0, TokenStatus.IN_GAME));
            }
        }
    }

    private void getTokenFromStartTiles(ServerWorld serverWorld) {
        List<UUID> tokens = findStartTokens(serverWorld);
        partyData.reset();
        tokens.forEach(partyData::addToken);
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

    /**
     * Finds the start tiles within {@link #START_TILES_SEARCH_RADIUS} blocks. A start tile always has a board space
     * block entity, so only the block entities of the already loaded chunks are checked (no chunk is loaded).
     */
    private List<BlockPos> findStartTiles(ServerWorld world, BlockPos center) {
        return findBoardSpaces(world, center, BoardSpaceType.TILE_START);
    }

    /**
     * The board spaces of the type {@code type} within {@link #START_TILES_SEARCH_RADIUS} blocks of {@code center}, in
     * the loaded chunks (none is loaded for this), sorted by z, then y, then x.
     */
    public static List<BlockPos> findBoardSpaces(ServerWorld world, BlockPos center, BoardSpaceType type) {
        List<BlockPos> spaces = new ArrayList<>();
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
                    if (state.getBlock() instanceof ABoardSpaceBlock && state.get(ABoardSpaceBlock.TILE_TYPE) == type) {
                        spaces.add(pos.toImmutable());
                    }
                }
            }
        }
        // Same order as the former full scan (x first, then y, then z)
        spaces.sort(Comparator.comparingInt(BlockPos::getZ).thenComparingInt(BlockPos::getY).thenComparingInt(BlockPos::getX));
        return spaces;
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

    // ---------------------------------------------------------------- party program (dashboard, Program page)

    public SimpleInventory getProgram() {
        return program;
    }

    /** The cards of the party program, in reading order (copies). Empty: the default party. */
    public List<ItemStack> getProgramCards() {
        List<ItemStack> cards = new ArrayList<>();
        for (int i = 0; i < program.size(); i++) {
            ItemStack stack = program.getStack(i);
            if (!stack.isEmpty()) cards.add(stack.copy());
        }
        return cards;
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
        // A mini-game ended from outside (a step controller): its results are read on its podiums and paid all the same
        if (currentStep instanceof MiniGamePartyStep miniGame) miniGame.concludeIfPlaying(this);
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

    public static boolean isTokenInRunningParty(UUID tokenUUID) {
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

    /**
     * The controller block is broken / replaced: the connected interested players (in any dimension) get an empty
     * party so their steps HUD is cleared.
     */
    public void onControllerRemoved() {
        if (!(this.world instanceof ServerWorld serverWorld)) return;
        PartyChunkHolds.release(serverWorld, pos);
        for (UUID playerUUID : interestedPlayers) {
            ServerPlayerEntity player = serverWorld.getServer().getPlayerManager().getPlayer(playerUUID);
            if (player != null)
                this.sendClearPacketToPlayer(player);
        }
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
            // The steps changed: the live state goes with them, up to date
            lastLiveData = partyData.isStarted() ? PartyLiveData.capture(this, serverWorld) : PartyLiveData.EMPTY;
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
        if (partyData.isStarted() && this.world instanceof ServerWorld serverWorld) {
            if (lastLiveData == PartyLiveData.EMPTY) lastLiveData = PartyLiveData.capture(this, serverWorld);
            ServerPlayNetworking.send(player, new PartyLivePayload(lastLiveData));
        }
    }

    /**
     * Sends the live state of the party (see {@link PartyLiveData}) to the connected interested players when it
     * changed. Checked every {@value #LIVE_SYNC_INTERVAL_TICKS} ticks while a party is running.
     */
    public void syncLiveData(ServerWorld serverWorld) {
        if (!partyData.isStarted() || interestedPlayers.isEmpty()) {
            lastLiveData = PartyLiveData.EMPTY;
            return;
        }
        PartyLiveData live = PartyLiveData.capture(this, serverWorld);
        if (live.sameAs(lastLiveData)) return;
        lastLiveData = live;
        PartyLivePayload payload = new PartyLivePayload(live);
        for (ServerPlayerEntity player : getInterestedPlayersEntities())
            ServerPlayNetworking.send(player, payload);
    }

    /** The live state last sent to the interested players ({@link PartyLiveData#EMPTY} while no party runs). */
    public PartyLiveData getLastLiveData() {
        return lastLiveData;
    }

    public void sendClearPacketToPlayer(ServerPlayerEntity player) {
        sendPacketToInterestedPlayer(player, new PartyData());
    }

    public void sendPacketToInterestedPlayer(ServerPlayerEntity player, PartyData partyData) {
        PartyDataPayload payload = PartyDataPayload.fromPartyData(partyData);
        ServerPlayNetworking.send(player, payload);
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
