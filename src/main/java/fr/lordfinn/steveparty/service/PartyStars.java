package fr.lordfinn.steveparty.service;

import fr.lordfinn.steveparty.blocks.custom.BoardSpaceRedstoneRouterBlockEntity;
import fr.lordfinn.steveparty.blocks.custom.PartyController.PartyBank;
import fr.lordfinn.steveparty.blocks.custom.PartyController.PartyControllerEntity;
import fr.lordfinn.steveparty.blocks.custom.PartyController.PartyCurrency;
import fr.lordfinn.steveparty.blocks.custom.PartyController.steps.PartyStep;
import fr.lordfinn.steveparty.blocks.custom.PartyController.steps.TokenTurnPartyStep;
import fr.lordfinn.steveparty.blocks.custom.boardspaces.ABoardSpaceBlock;
import fr.lordfinn.steveparty.blocks.custom.boardspaces.BoardSpaceBlockEntity;
import fr.lordfinn.steveparty.blocks.custom.boardspaces.BoardSpaceType;
import fr.lordfinn.steveparty.blocks.custom.boardspaces.BoardSpaces;
import fr.lordfinn.steveparty.blocks.custom.boardspaces.behaviors.InventoryInteractorTileBehavior;
import fr.lordfinn.steveparty.board.BoardText;
import fr.lordfinn.steveparty.components.StarSettingsComponent;
import fr.lordfinn.steveparty.entities.TokenizedEntityInterface;
import fr.lordfinn.steveparty.items.custom.cartridges.StarCartridgeItem;
import fr.lordfinn.steveparty.payloads.custom.StarSpacesPayload;
import fr.lordfinn.steveparty.utils.InventoryUtils;
import fr.lordfinn.steveparty.utils.MessageUtils;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.block.BlockState;
import net.minecraft.entity.mob.MobEntity;
import net.minecraft.inventory.Inventory;
import net.minecraft.item.ItemStack;
import net.minecraft.util.ItemScatterer;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.text.ClickEvent;
import net.minecraft.text.HoverEvent;
import net.minecraft.text.MutableText;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.World;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static net.minecraft.server.command.CommandManager.literal;

/**
 * The star of a party, Mario Party style, on the star spaces given by the {@link StarCartridgeItem Star Cartridge}:
 * <ul>
 *     <li>when the party starts, the star stands on one of the active star spaces of its board (those within
 *     {@link PartyControllerEntity#START_TILES_SEARCH_RADIUS} blocks of the controller), at random;</li>
 *     <li>a token reaching it (passing over it, or only stopping on it, as its cartridge says; on a check point,
 *     always passing) pauses there if its owner has the coins: the owner chooses in the chat to buy it or not
 *     ({@value #DECISION_SECONDS} s, then it is bought); the Skeleton Key never walks past it;</li>
 *     <li>bought: the coins are paid (the party's coin, from the buyer's inventory, into the party's bank: what does
 *     not fit falls by the controller), the buyer gets one of the party's stars taken from that bank (none left: no sale,
 *     the token goes on), everyone is told, and the star goes to another active star space at random (the same one only if it is
 *     the board's only one);</li>
 *     <li>its space switched off (redstone, its cartridge taken out, broken): the star goes to another active star
 *     space, unless its Star Cartridge (still in the space) says to wait there; no active star space: it waits, hidden,
 *     for one to be switched on.</li>
 * </ul>
 * Where the star stands belongs to the party: saved with its controller ({@link PartyControllerEntity#getStarSpace}).
 * The choices in progress live in memory (a server stop forgets them, like the shop stops). The clients draw the stars
 * from {@link StarSpacesPayload}.
 */
public final class PartyStars {
    /** Seconds a player gets to buy the star or not; then it is bought. */
    public static final int DECISION_SECONDS = 15;
    /** How often a party checks its star's space is still on; how often a hidden star looks for a space; the clients' refresh. */
    private static final int CHECK_INTERVAL = 20, HIDDEN_SCAN_INTERVAL = 100, SYNC_INTERVAL = 100;
    /** How often the player choosing is reminded of the time left. */
    private static final int REMINDER_INTERVAL = 40;

    /** A token waiting on the star while its owner chooses. */
    private record Offer(UUID token, UUID owner, ServerWorld world, BlockPos space, PartyControllerEntity party, int price,
                         boolean passing, long deadline, Text tokenName) {
    }

    /** Current choices, by token. */
    private static final Map<UUID, Offer> OFFERS = new LinkedHashMap<>();

    private PartyStars() {
    }

    public static void initialize() {
        ServerTickEvents.END_SERVER_TICK.register(PartyStars::tickOffers);
        ServerLifecycleEvents.SERVER_STOPPED.register(server -> OFFERS.clear());
        CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) -> dispatcher.register(
                literal("steveparty").then(literal("star")
                        .then(literal("buy").executes(context -> decide(context.getSource(), true)))
                        .then(literal("pass").executes(context -> decide(context.getSource(), false))))));
    }

    // ---------------------------------------------------------------- queries

    /** True while this token waits on the star for its owner to choose. */
    public static boolean isDeciding(UUID token) {
        return OFFERS.containsKey(token);
    }

    /** The party of the token was stopped: its choice is dropped (nothing is bought, nothing goes on). */
    public static void cancelOffer(UUID token) {
        OFFERS.remove(token);
    }

    /** True if {@code pos} is a star space that is on (its active cartridge is a Star Cartridge); false if not loaded. */
    @SuppressWarnings("deprecation") // isChunkLoaded(BlockPos): nothing is loaded for this
    public static boolean isActiveStarSpace(World world, BlockPos pos) {
        if (!world.isChunkLoaded(pos)) return false;
        BlockState state = world.getBlockState(pos);
        return state.getBlock() instanceof ABoardSpaceBlock && state.get(ABoardSpaceBlock.TILE_TYPE) == BoardSpaceType.TILE_STAR;
    }

    /** The active star spaces of the party's board (loaded chunks only), sorted by position. */
    public static List<BlockPos> activeStarSpaces(ServerWorld world, PartyControllerEntity party) {
        return PartyControllerEntity.findBoardSpaces(world, party.getPos(), BoardSpaceType.TILE_STAR);
    }

    /**
     * The settings of the Star Cartridge of the space at {@code pos}: its active one, else one in another of its slots
     * (a space switched off by redstone keeps it); null for none (taken out, or no board space any more).
     */
    public static @Nullable StarSettingsComponent settingsAt(World world, BlockPos pos) {
        if (!(world.getBlockEntity(pos) instanceof BoardSpaceBlockEntity space)) return null;
        ItemStack active = space.getActiveCartridgeItemStack();
        if (active.getItem() instanceof StarCartridgeItem) return StarCartridgeItem.settings(active);
        for (ItemStack stack : space.getItems()) {
            if (stack.getItem() instanceof StarCartridgeItem) return StarCartridgeItem.settings(stack);
        }
        return null;
    }

    // ---------------------------------------------------------------- where the star stands

    /** A party starts: its star stands on one of its board's active star spaces, at random (hidden if none). */
    public static void onPartyStarted(PartyControllerEntity party, ServerWorld world) {
        party.setStarSpace(null);
        place(party, world, null);
    }

    /**
     * Server tick of a party controller: while its party runs, a star whose space was switched off moves on, a star
     * without a space looks for one, and the clients are reminded where the stars are. A party over loses its star.
     */
    public static void tick(PartyControllerEntity party, ServerWorld world) {
        long time = world.getTime();
        if (time % CHECK_INTERVAL != 0) return;
        if (!party.getPartyData().isStarted()) {
            if (party.getStarSpace() != null) {
                party.setStarSpace(null);
                sync(world);
            }
            return;
        }
        BlockPos star = party.getStarSpace();
        if (star == null) {
            if (time % HIDDEN_SCAN_INTERVAL == 0) place(party, world, null);
        } else if (world.isChunkLoaded(star) && !isActiveStarSpace(world, star)) {
            onSwitchedOff(party, world, star);
        }
        if (time % SYNC_INTERVAL == 0) sync(world);
    }

    /**
     * A board space took another role (BoardSpaceBlockEntity, when its active cartridge changes): the stars on it move
     * on if it is no star space any more, and the hidden stars of the parties around come to it if it became one.
     */
    public static void onRoleChanged(ServerWorld world, BlockPos pos, BoardSpaceType from, BoardSpaceType to) {
        for (PartyControllerEntity party : PartyControllerEntity.getActivePartyControllers()) {
            if (party.isRemoved() || party.getWorld() != world || !party.getPartyData().isStarted()) continue;
            if (from == BoardSpaceType.TILE_STAR && pos.equals(party.getStarSpace())) {
                onSwitchedOff(party, world, pos);
            } else if (to == BoardSpaceType.TILE_STAR && party.getStarSpace() == null
                    && pos.isWithinDistance(party.getPos(), PartyControllerEntity.START_TILES_SEARCH_RADIUS * 1.75)) {
                place(party, world, null);
            }
        }
    }

    /** The star's space is no star space any more: the star moves on, unless its cartridge says to wait there. */
    private static void onSwitchedOff(PartyControllerEntity party, ServerWorld world, BlockPos star) {
        StarSettingsComponent settings = settingsAt(world, star);
        if (settings != null && !settings.relocate()) return;
        place(party, world, star);
    }

    /**
     * Puts the party's star on one of its board's active star spaces at random, another one than {@code avoid} when
     * there is another; hidden (no space) if there is none. Everyone around is told where it goes.
     *
     * @return the space it stands on now, null if hidden
     */
    public static @Nullable BlockPos place(PartyControllerEntity party, ServerWorld world, @Nullable BlockPos avoid) {
        List<BlockPos> spaces = new ArrayList<>(activeStarSpaces(world, party));
        BlockPos previous = party.getStarSpace();
        if (avoid != null && spaces.size() > 1) spaces.remove(avoid);
        if (spaces.isEmpty()) {
            party.setStarSpace(null);
            if (previous != null) {
                MessageUtils.sendToPlayers(party.getPartyAudience(),
                        Text.translatable("message.steveparty.star.hidden").formatted(Formatting.GRAY), MessageUtils.MessageType.CHAT);
                sync(world);
            }
            return null;
        }
        BlockPos chosen = spaces.get(world.getRandom().nextInt(spaces.size()));
        party.setStarSpace(chosen);
        MessageUtils.sendToPlayers(party.getPartyAudience(), Text.translatable(previous == null
                ? "message.steveparty.star.appeared" : "message.steveparty.star.moved", BoardText.pos(chosen))
                .formatted(Formatting.GOLD), MessageUtils.MessageType.CHAT);
        Vec3d at = BoardSpaces.standPos(world, chosen);
        world.playSound(null, at.x, at.y, at.z, SoundEvents.BLOCK_AMETHYST_BLOCK_RESONATE, SoundCategory.BLOCKS, 1.0F, 1.2F);
        world.spawnParticles(ParticleTypes.END_ROD, at.x, at.y + 1.2, at.z, 20, 0.3, 0.8, 0.3, 0.02);
        sync(world);
        return chosen;
    }

    /** Tells the players of {@code world} where the stars of its running parties stand. */
    public static void sync(ServerWorld world) {
        StarSpacesPayload payload = new StarSpacesPayload(shownStarSpaces(world));
        for (ServerPlayerEntity player : world.getPlayers()) {
            if (ServerPlayNetworking.canSend(player, StarSpacesPayload.ID)) ServerPlayNetworking.send(player, payload);
        }
    }

    /**
     * The star spaces the clients draw a star and its light beam over: those holding the star of a running party of
     * {@code world}, each once. A party over (or reset) shows none.
     */
    public static List<BlockPos> shownStarSpaces(ServerWorld world) {
        List<BlockPos> stars = new ArrayList<>();
        for (PartyControllerEntity party : PartyControllerEntity.getActivePartyControllers()) {
            BlockPos star = party.getStarSpace();
            if (!party.isRemoved() && party.getWorld() == world && party.getPartyData().isStarted() && star != null
                    && !stars.contains(star)) stars.add(star);
        }
        return stars;
    }

    // ---------------------------------------------------------------- reaching the star

    /**
     * A token reached {@code space} with steps left (TileReachedEvent, from TokenMovementService): if its party's star
     * is there and the cartridge sells it to the tokens passing (a check point always does), its owner may buy it. The
     * Skeleton Key does not walk past it: it only opens the Stop spaces and the shop check points.
     *
     * @return true if the token waits here while its owner chooses (its move goes on after)
     */
    public static boolean onTileReached(MobEntity mob, BoardSpaceBlockEntity space) {
        if (!(mob instanceof TokenizedEntityInterface token) || token.steveparty$getNbSteps() <= 0) return false;
        PartyControllerEntity party = PartyControllerEntity.getRunningPartyOf(mob.getUuid()).orElse(null);
        if (party == null || party.getWorld() != mob.getWorld() || !space.getPos().equals(party.getStarSpace())) return false;
        ItemStack cartridge = space.getActiveCartridgeItemStack();
        if (!(cartridge.getItem() instanceof StarCartridgeItem)) return false;
        StarSettingsComponent settings = StarCartridgeItem.settings(cartridge);
        if (!settings.onPass() && ABoardSpaceBlock.countsAsStep(space.getCachedState().getBlock())) return false;
        return offer(mob, space, party, settings.price(), true);
    }

    /**
     * {@code mob} ended its move on the star space {@code space} during {@code party}'s turn: if the star is there, its
     * owner may buy it, and the turn waits for the choice (see StarBoardSpaceBehavior#keepsTurn).
     *
     * @return true if a choice started
     */
    public static boolean onLanding(MobEntity mob, BoardSpaceBlockEntity space, @Nullable PartyControllerEntity party) {
        if (party == null || party.isRemoved() || !party.getPartyData().isStarted()
                || !space.getPos().equals(party.getStarSpace())) return false;
        ItemStack cartridge = space.getActiveCartridgeItemStack();
        if (!(cartridge.getItem() instanceof StarCartridgeItem)) return false;
        return offer(mob, space, party, StarCartridgeItem.settings(cartridge).price(), false);
    }

    private static boolean offer(MobEntity mob, BoardSpaceBlockEntity space, PartyControllerEntity party, int price, boolean passing) {
        if (!(mob.getWorld() instanceof ServerWorld world) || !(mob instanceof TokenizedEntityInterface token)) return false;
        if (OFFERS.containsKey(mob.getUuid())) return true;
        UUID ownerUuid = token.steveparty$getTokenOwner();
        if (ownerUuid == null) return false;
        ServerPlayerEntity owner = world.getServer().getPlayerManager().getPlayer(ownerUuid);
        if (owner == null || owner.isDisconnected() || !owner.isAlive()) return false;
        int coins = InventoryUtils.count(owner.getInventory(), party.getCurrency(PartyCurrency.COIN));
        Text tokenName = mob.getDisplayName();
        if (coins < price) {
            MessageUtils.sendToPlayers(party.getPartyAudience(), Text.translatable("message.steveparty.star.too_poor",
                    owner.getDisplayName(), price, coins).formatted(Formatting.GRAY), MessageUtils.MessageType.CHAT);
            return false;
        }
        // The star given comes from the party's bank: none left there, nothing to sell (the token goes on)
        if (bankStars(party, world) <= 0) {
            MessageUtils.sendToPlayers(party.getPartyAudience(), Text.translatable("message.steveparty.star.bank_empty")
                    .formatted(Formatting.RED), MessageUtils.MessageType.CHAT);
            return false;
        }
        Offer offer = new Offer(mob.getUuid(), ownerUuid, world, space.getPos().toImmutable(), party, price, passing,
                world.getTime() + 20L * DECISION_SECONDS, tokenName);
        OFFERS.put(mob.getUuid(), offer);
        // A star check point holds the token: its Router tells a comparator (a star tile did when the token landed)
        if (!ABoardSpaceBlock.countsAsStep(space.getCachedState().getBlock())) BoardSpaceRedstoneRouterBlockEntity.onTokenStopped(world, space);
        Vec3d at = BoardSpaces.standPos(world, space.getPos());
        world.playSound(null, at.x, at.y, at.z, SoundEvents.BLOCK_NOTE_BLOCK_CHIME.value(), SoundCategory.BLOCKS, 0.8F, 1.5F);
        MutableText prompt = Text.translatable("message.steveparty.star.offer", price, coins).formatted(Formatting.GOLD)
                .append(" ")
                .append(button(Text.translatable("message.steveparty.star.buy_button"), Formatting.YELLOW, "/steveparty star buy",
                        Text.translatable("message.steveparty.star.buy_hover", price)))
                .append(" ")
                .append(button(Text.translatable("message.steveparty.star.pass_button"), Formatting.GRAY, "/steveparty star pass",
                        Text.translatable("message.steveparty.star.pass_hover")))
                .append(" ")
                .append(Text.translatable("message.steveparty.star.auto", DECISION_SECONDS).formatted(Formatting.DARK_GRAY));
        MessageUtils.sendToPlayer(owner, prompt, MessageUtils.MessageType.CHAT);
        Text notice = Text.translatable("message.steveparty.star.reached", owner.getDisplayName(), tokenName).formatted(Formatting.GOLD);
        for (ServerPlayerEntity player : party.getPartyAudience()) {
            if (!player.getUuid().equals(ownerUuid)) MessageUtils.sendToPlayer(player, notice, MessageUtils.MessageType.CHAT);
        }
        return true;
    }

    private static Text button(MutableText label, Formatting color, String command, Text hover) {
        return label.styled(style -> style.withColor(color).withBold(true)
                .withClickEvent(new ClickEvent(ClickEvent.Action.RUN_COMMAND, command))
                .withHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT, hover)));
    }

    /** {@code /steveparty star buy|pass}: the chat buttons of a choice. */
    private static int decide(ServerCommandSource source, boolean buy) {
        ServerPlayerEntity player = source.getPlayer();
        if (player != null && decide(player, buy)) return 1;
        source.sendError(Text.translatable("message.steveparty.star.no_offer"));
        return 0;
    }

    /**
     * The player chose to buy the star the token waits on, or not.
     *
     * @return false if none of their tokens waits on a star
     */
    public static boolean decide(ServerPlayerEntity player, boolean buy) {
        for (Offer offer : new ArrayList<>(OFFERS.values())) {
            if (offer.owner().equals(player.getUuid())) {
                end(offer, buy);
                return true;
            }
        }
        return false;
    }

    private static void tickOffers(MinecraftServer server) {
        if (OFFERS.isEmpty()) return;
        for (Offer offer : new ArrayList<>(OFFERS.values())) {
            ServerWorld world = offer.world();
            ServerPlayerEntity owner = server.getPlayerManager().getPlayer(offer.owner());
            if (owner == null || owner.isDisconnected() || !owner.isAlive() || offer.party().isRemoved()
                    || !(world.getEntity(offer.token()) instanceof MobEntity mob) || !mob.isAlive()) {
                end(offer, false);
                continue;
            }
            long left = offer.deadline() - world.getTime();
            if (left <= 0) {
                end(offer, true); // the time ran out: bought
                continue;
            }
            if (left % REMINDER_INTERVAL == 0) {
                MessageUtils.sendToPlayer(owner, Text.translatable("message.steveparty.star.waiting", (left + 19) / 20)
                        .formatted(Formatting.GOLD), MessageUtils.MessageType.ACTION_BAR);
            }
        }
    }

    /** Ends the choice: the star is bought or not, then the token goes on (or the turn ends). */
    private static void end(Offer offer, boolean buy) {
        if (OFFERS.remove(offer.token()) == null) return;
        ServerWorld world = offer.world();
        ServerPlayerEntity owner = world.getServer().getPlayerManager().getPlayer(offer.owner());
        if (buy && owner != null) {
            buy(offer.party(), world, owner, offer.space(), offer.price());
        } else if (owner != null) {
            MessageUtils.sendToPlayers(offer.party().getPartyAudience(), Text.translatable("message.steveparty.star.passed",
                    owner.getDisplayName()).formatted(Formatting.GRAY), MessageUtils.MessageType.CHAT);
        }
        goOn(offer);
    }

    /**
     * {@code buyer} buys the star of {@code party} standing on {@code space} for {@code price} coins: the coins leave
     * their inventory for the party's bank ({@link #deposit}), one of the party's stars comes from that bank into their
     * inventory (nothing is created), everyone is told, and the star goes to another star space.
     *
     * @return false if the star is not there, the buyer is short of coins or the bank has no star left (nothing changes)
     */
    public static boolean buy(PartyControllerEntity party, ServerWorld world, ServerPlayerEntity buyer, BlockPos space, int price) {
        if (!space.equals(party.getStarSpace())) return false;
        ItemStack coin = party.getCurrency(PartyCurrency.COIN);
        int coins = InventoryUtils.count(buyer.getInventory(), coin);
        if (coins < price) {
            MessageUtils.sendToPlayer(buyer, Text.translatable("message.steveparty.star.too_poor", buyer.getDisplayName(), price, coins)
                    .formatted(Formatting.GRAY), MessageUtils.MessageType.CHAT);
            return false;
        }
        Inventory bank = bank(party, world);
        ItemStack star = party.getCurrency(PartyCurrency.STAR);
        if (bank == null || InventoryUtils.take(bank, star, 1) < 1) {
            MessageUtils.sendToPlayers(audienceWith(party, buyer), Text.translatable("message.steveparty.star.bank_empty")
                    .formatted(Formatting.RED), MessageUtils.MessageType.CHAT);
            return false;
        }
        bank.markDirty();
        InventoryUtils.take(buyer.getInventory(), coin, price);
        deposit(party, world, coin.copyWithCount(price));
        InventoryUtils.giveOrDrop(buyer, star, 1);
        List<ServerPlayerEntity> audience = party.getPartyAudience();
        if (!audience.contains(buyer)) audience.add(buyer);
        MessageUtils.sendToPlayers(audience, Text.translatable("message.steveparty.star.bought", buyer.getDisplayName(), price)
                .formatted(Formatting.GOLD, Formatting.BOLD), MessageUtils.MessageType.CHAT);
        MessageUtils.sendToPlayers(audience, Text.translatable("message.steveparty.star.bought_title", buyer.getDisplayName())
                .formatted(Formatting.GOLD), MessageUtils.MessageType.TITLE);
        Vec3d at = BoardSpaces.standPos(world, space);
        world.playSound(null, at.x, at.y, at.z, SoundEvents.UI_TOAST_CHALLENGE_COMPLETE, SoundCategory.PLAYERS, 0.8F, 1.0F);
        world.spawnParticles(ParticleTypes.FIREWORK, at.x, at.y + 1.0, at.z, 40, 0.4, 0.6, 0.4, 0.15);
        world.spawnParticles(ParticleTypes.WAX_OFF, at.x, at.y + 1.0, at.z, 30, 0.6, 0.6, 0.6, 0.5);
        place(party, world, space);
        return true;
    }

    /**
     * The coins paid for the star go back to the party: into its bank (the chests of the Gains page's Inventory
     * Cartridge, in their order), like the mini-game gains come from it. With no bank, or for what does not fit, they
     * fall by the Party Controller: nothing is lost, and the purchase is never refused for it.
     */
    static void deposit(PartyControllerEntity party, ServerWorld world, ItemStack coins) {
        ItemStack rest = coins.copy();
        Inventory bank = bank(party, world);
        if (bank != null) {
            InventoryInteractorTileBehavior.insertLinked(rest, bank);
            bank.markDirty();
        }
        if (rest.isEmpty()) return;
        BlockPos pos = party.getPos();
        ItemScatterer.spawn(world, pos.getX() + 0.5, pos.getY() + 1.1, pos.getZ() + 0.5, rest);
    }

    /** The party's bank (the chests of its Gains page's Inventory Cartridge), null for none (or none loaded). */
    private static @Nullable Inventory bank(PartyControllerEntity party, ServerWorld world) {
        return world.getServer() == null ? null : PartyBank.inventory(world.getServer(), party.getBank());
    }

    /** The party's stars left in its bank: what the star spaces can still sell. */
    public static int bankStars(PartyControllerEntity party, ServerWorld world) {
        Inventory bank = bank(party, world);
        return bank == null ? 0 : InventoryUtils.count(bank, party.getCurrency(PartyCurrency.STAR));
    }

    private static List<ServerPlayerEntity> audienceWith(PartyControllerEntity party, ServerPlayerEntity player) {
        List<ServerPlayerEntity> audience = new ArrayList<>(party.getPartyAudience());
        if (!audience.contains(player)) audience.add(player);
        return audience;
    }

    /** A token that was passing walks its remaining steps; a landing during a party ends the turn. */
    private static void goOn(Offer offer) {
        ServerWorld world = offer.world();
        if (offer.passing() && world.getEntity(offer.token()) instanceof MobEntity mob && mob.isAlive()
                && mob instanceof TokenizedEntityInterface token && token.steveparty$isTokenized()
                && token.steveparty$getNbSteps() > 0 && BoardSpaces.boardSpaceOf(mob) != null) {
            world.playSound(null, mob.getX(), mob.getY(), mob.getZ(), SoundEvents.BLOCK_NOTE_BLOCK_CHIME.value(), SoundCategory.BLOCKS, 0.6F, 1.4F);
            TokenMovementService.moveEntityOnBoard(mob, token.steveparty$getNbSteps());
            return;
        }
        if (offer.passing()) return;
        PartyControllerEntity party = offer.party();
        if (party.isRemoved()) return;
        PartyStep step = party.getPartyData().getCurrentStep();
        // Only the turn that waited for this choice, if nothing moved the party on meanwhile
        if (step instanceof TokenTurnPartyStep turn && step.getStatus() == PartyStep.Status.IN_PROGRESS
                && offer.token().equals(turn.getTokenUUID())) {
            party.nextStep();
        }
    }
}
