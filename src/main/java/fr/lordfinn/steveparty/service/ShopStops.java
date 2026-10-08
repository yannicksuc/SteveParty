package fr.lordfinn.steveparty.service;

import fr.lordfinn.steveparty.blocks.custom.PartyController.PartyControllerEntity;
import fr.lordfinn.steveparty.blocks.custom.PartyController.steps.PartyStep;
import fr.lordfinn.steveparty.blocks.custom.PartyController.steps.TokenTurnPartyStep;
import fr.lordfinn.steveparty.blocks.custom.TradingStallBlockEntity;
import fr.lordfinn.steveparty.blocks.custom.boardspaces.BoardSpaceBlockEntity;
import fr.lordfinn.steveparty.blocks.custom.boardspaces.ABoardSpaceBlock;
import fr.lordfinn.steveparty.blocks.custom.boardspaces.BoardSpaces;
import fr.lordfinn.steveparty.blocks.custom.boardspaces.TileFeedback;
import fr.lordfinn.steveparty.components.ModComponents;
import fr.lordfinn.steveparty.components.ShopLinkComponent;
import fr.lordfinn.steveparty.entities.TokenizedEntityInterface;
import fr.lordfinn.steveparty.entities.custom.BoxedTraderEntity;
import fr.lordfinn.steveparty.items.custom.cartridges.ShopCartridgeItem;
import fr.lordfinn.steveparty.persistent_state.VendorLinkPersistentState;
import fr.lordfinn.steveparty.screen_handlers.custom.ShopStopScreenHandler;
import fr.lordfinn.steveparty.utils.MessageUtils;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.gamerule.v1.GameRuleFactory;
import net.fabricmc.fabric.api.gamerule.v1.GameRuleRegistry;
import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.entity.effect.StatusEffects;
import net.minecraft.entity.mob.MobEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.GameRules;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Shop stops, as in party board games, given by the {@link ShopCartridgeItem Shop Cartridge}:
 * <ul>
 *     <li>a check point holding it stops a token passing through ({@link #onTileReached});</li>
 *     <li>a tile holding it opens the shop when a token ends its move there ({@link #onLanding}); passing tokens go on.</li>
 * </ul>
 * The token's owner gets the shop's trade screen wherever they are, with « Buy nothing » and a countdown; the merchant
 * glows meanwhile. The stop ends when the purchases allowed by the cartridge are made, on « Buy nothing » or when the
 * screen is closed, when the time runs out (game rule {@code stevepartyShopStopSeconds}, {@value #DEFAULT_SECONDS} s
 * by default) or when the owner leaves: a paused token walks its remaining steps, a landing ends the turn.
 * <p>
 * The shop is the merchant chosen with the Tile Linker Brush (cartridge {@link ModComponents#SHOP_LINK}), else the nearest one
 * within {@value #SHOP_RADIUS} blocks: a Boxed Trader with trading stalls, the distance counted to him or to his
 * nearest stall. No shop, a token without owner or whose owner is offline: nothing happens, the token goes on. Only the
 * owner shops; the players around get a notice. Stops live in memory (a server stop ends them).
 */
public final class ShopStops {
    public static final int DEFAULT_SECONDS = 60;
    /** Seconds a player gets to shop at a shop stop. */
    public static final GameRules.Key<GameRules.IntRule> SHOP_SECONDS = GameRuleRegistry.register(
            "stevepartyShopStopSeconds", GameRules.Category.MISC, GameRuleFactory.createIntRule(DEFAULT_SECONDS, 5, 600));
    /** The nearest merchant is looked for this far from the space (to him or to one of his trading stalls). */
    public static final int SHOP_RADIUS = 32;
    /** The players this far from the space hear about the stop. */
    public static final double AUDIENCE_RADIUS = 100;
    /** How often the others are reminded that someone is shopping (action bar), and the merchant sparkles. */
    private static final int NOTICE_INTERVAL = 40, SPARKLE_INTERVAL = 10;

    /** How a stop ended. */
    public enum End {
        /** The purchases allowed were made, or the screen was closed after buying. */
        BOUGHT,
        /** « Buy nothing », or closed without buying. */
        NOTHING,
        /** The time ran out. */
        TIMEOUT,
        /** The owner left (disconnected, died, changed world), or the token or the merchant went away. */
        LEFT
    }

    private static final class Stop {
        final UUID token;
        final UUID owner;
        final ServerWorld world;
        final BlockPos space;
        final UUID trader;
        final int limit;
        final long deadline;
        /** The party whose turn waits for the stop (a landing during a party), else null. */
        final @Nullable PartyControllerEntity party;
        final Text tokenName;
        @Nullable ShopStopScreenHandler handler;

        Stop(UUID token, UUID owner, ServerWorld world, BlockPos space, UUID trader, int limit, long deadline,
             @Nullable PartyControllerEntity party, Text tokenName) {
            this.token = token;
            this.owner = owner;
            this.world = world;
            this.space = space;
            this.trader = trader;
            this.limit = limit;
            this.deadline = deadline;
            this.party = party;
            this.tokenName = tokenName;
        }
    }

    /** Current stops, by token. */
    private static final Map<UUID, Stop> STOPS = new LinkedHashMap<>();

    private ShopStops() {
    }

    public static void initialize() {
        ServerTickEvents.END_SERVER_TICK.register(ShopStops::tick);
        ServerLifecycleEvents.SERVER_STOPPED.register(server -> STOPS.clear());
    }

    // ---------------------------------------------------------------- queries

    /** True while this token waits for its owner to shop. */
    public static boolean isShopping(UUID token) {
        return STOPS.containsKey(token);
    }

    /** Seconds before the stop of this screen ends by itself (0 if it is not a current stop's). */
    public static int secondsLeft(ShopStopScreenHandler handler) {
        Stop stop = stopOf(handler);
        return stop == null ? 0 : (int) Math.max(0, (stop.deadline - stop.world.getTime() + 19) / 20);
    }

    /** The Shop Cartridge giving {@code space} its role, or null. */
    public static @Nullable ItemStack shopCartridge(BoardSpaceBlockEntity space) {
        ItemStack stack = space.getActiveCartridgeItemStack();
        return stack.getItem() instanceof ShopCartridgeItem ? stack : null;
    }

    /**
     * The shop of a Shop Cartridge on the space at {@code space}: the merchant chosen with the Tile Linker Brush (if he is here),
     * else the nearest Boxed Trader with trading stalls, the distance counted to him or to his nearest stall.
     */
    public static @Nullable BoxedTraderEntity findShop(ServerWorld world, BlockPos space, ItemStack cartridge) {
        ShopLinkComponent link = cartridge.get(ModComponents.SHOP_LINK);
        if (link != null) {
            return world.getEntity(link.trader()) instanceof BoxedTraderEntity trader && trader.isAlive() ? trader : null;
        }
        Vec3d at = Vec3d.ofCenter(space);
        VendorLinkPersistentState links = VendorLinkPersistentState.get(world.getServer());
        BoxedTraderEntity best = null;
        double bestDistance = (double) SHOP_RADIUS * SHOP_RADIUS;
        for (BoxedTraderEntity trader : world.getEntitiesByClass(BoxedTraderEntity.class, new Box(space).expand(SHOP_RADIUS + 16),
                trader -> trader.isAlive() && !fr.lordfinn.steveparty.entities.TokenBase.isToken(trader))) {
            List<BlockPos> stalls = stallsOf(world, links, trader);
            if (stalls.isEmpty()) continue; // a merchant sells what his stalls offer
            double distance = trader.squaredDistanceTo(at);
            for (BlockPos stall : stalls) distance = Math.min(distance, Vec3d.ofCenter(stall).squaredDistanceTo(at));
            if (distance <= bestDistance) {
                bestDistance = distance;
                best = trader;
            }
        }
        return best;
    }

    /** The trading stalls of {@code trader} in its world. */
    public static List<BlockPos> stallsOf(ServerWorld world, @Nullable VendorLinkPersistentState links, BoxedTraderEntity trader) {
        List<BlockPos> stalls = new ArrayList<>();
        if (links == null) return stalls;
        for (BlockPos pos : links.getLinkedPositionsIn(trader.getUuid(), world.getRegistryKey())) {
            if (world.isChunkLoaded(pos) && world.getBlockEntity(pos) instanceof TradingStallBlockEntity) stalls.add(pos);
        }
        return stalls;
    }

    // ---------------------------------------------------------------- start

    /**
     * A token reached {@code space} (TileReachedEvent): a shop check point stops it if it is walking forward (steps
     * left; a backward move, with negative steps, never stops); a shop tile opens the shop when a token ends its move
     * there outside a party (during a party the landing goes through
     * {@link fr.lordfinn.steveparty.blocks.custom.boardspaces.behaviors.ShopBoardSpaceBehavior#onDestinationReached}).
     *
     * @return true if the token stops here (its move goes on when the stop ends)
     */
    public static boolean onTileReached(MobEntity mob, BoardSpaceBlockEntity space) {
        if (!(mob instanceof TokenizedEntityInterface token) || shopCartridge(space) == null) return false;
        int steps = token.steveparty$getNbSteps();
        // A check point: the token walking forward stops to shop, unless its die carries the Skeleton Key module
        if (!ABoardSpaceBlock.countsAsStep(space.getCachedState().getBlock()))
            return steps > 0 && !DiceRollEffects.ignoresStops(mob) && start(mob, space, null);
        if (steps == 0 && !TileFeedback.isInRunningParty(mob.getUuid())) start(mob, space, null);
        return false;
    }

    /**
     * {@code mob} ended its move on the tile {@code space}: its owner shops. During a party, {@code party}'s turn goes on
     * when the stop ends (see {@link fr.lordfinn.steveparty.blocks.custom.boardspaces.behaviors.ABoardSpaceBehavior#keepsTurn}).
     *
     * @return true if a stop started
     */
    public static boolean onLanding(MobEntity mob, BoardSpaceBlockEntity space, @Nullable PartyControllerEntity party) {
        return start(mob, space, party);
    }

    private static boolean start(MobEntity mob, BoardSpaceBlockEntity space, @Nullable PartyControllerEntity party) {
        if (!(mob.getWorld() instanceof ServerWorld world) || !(mob instanceof TokenizedEntityInterface token)) return false;
        if (STOPS.containsKey(mob.getUuid())) return true;
        ItemStack cartridge = shopCartridge(space);
        if (cartridge == null) return false;
        UUID ownerUuid = token.steveparty$getTokenOwner();
        if (ownerUuid == null) return false;
        ServerPlayerEntity owner = world.getServer().getPlayerManager().getPlayer(ownerUuid);
        if (owner == null || owner.isDisconnected() || !owner.isAlive() || owner.getWorld() != world) return false;
        for (Stop other : STOPS.values()) {
            if (other.owner.equals(ownerUuid)) return false; // already shopping with another token
        }
        BoxedTraderEntity trader = findShop(world, space.getPos(), cartridge);
        if (trader == null) {
            MessageUtils.sendToPlayer(owner, Text.translatable("message.steveparty.shop_stop.closed").formatted(Formatting.GRAY),
                    MessageUtils.MessageType.ACTION_BAR);
            return false;
        }
        int seconds = world.getGameRules().getInt(SHOP_SECONDS);
        int limit = ShopCartridgeItem.purchases(cartridge);
        Text tokenName = mob.getDisplayName();
        Stop stop = new Stop(mob.getUuid(), ownerUuid, world, space.getPos().toImmutable(), trader.getUuid(), limit,
                world.getTime() + 20L * seconds, party, tokenName);
        STOPS.put(mob.getUuid(), stop); // before opening: the screen reads its time left
        ShopStopScreenHandler handler = trader.openShopStop(owner, limit);
        if (handler == null) {
            STOPS.remove(mob.getUuid());
            MessageUtils.sendToPlayer(owner, Text.translatable("message.steveparty.shop_stop.closed").formatted(Formatting.GRAY),
                    MessageUtils.MessageType.ACTION_BAR);
            return false;
        }
        stop.handler = handler;
        // A shop check point holds the token: its Router tells a comparator (a shop tile did when the token landed)
        if (!ABoardSpaceBlock.countsAsStep(space.getCachedState().getBlock()))
            fr.lordfinn.steveparty.blocks.custom.BoardSpaceRedstoneRouterBlockEntity.onTokenStopped(world, space);
        // The merchant glows for everyone while the stop lasts
        trader.addStatusEffect(new StatusEffectInstance(StatusEffects.GLOWING, 20 * seconds + 40, 0, false, false));
        sparkle(world, stop, trader);
        Vec3d at = BoardSpaces.standPos(world, space.getPos());
        world.playSound(null, at.x, at.y, at.z, SoundEvents.BLOCK_NOTE_BLOCK_BELL.value(), SoundCategory.BLOCKS, 0.8F, 1.2F);
        world.playSound(null, trader.getX(), trader.getY(), trader.getZ(), SoundEvents.ENTITY_VILLAGER_TRADE, SoundCategory.NEUTRAL, 0.8F, 1.0F);
        MessageUtils.sendToPlayer(owner, Text.translatable("message.steveparty.shop_stop.welcome", tokenName, limit, seconds)
                .formatted(Formatting.GOLD), MessageUtils.MessageType.CHAT);
        for (ServerPlayerEntity player : audience(stop)) {
            MessageUtils.sendToPlayer(player, Text.translatable("message.steveparty.shop_stop.stopped", owner.getDisplayName(), tokenName)
                    .formatted(Formatting.GOLD), MessageUtils.MessageType.CHAT);
        }
        return true;
    }

    // ---------------------------------------------------------------- the screen

    /** « Buy nothing » (or « Done ») pressed. */
    public static void buyNothing(ShopStopScreenHandler handler) {
        Stop stop = stopOf(handler);
        if (stop != null) end(stop, handler.getPurchases() > 0 ? End.BOUGHT : End.NOTHING);
    }

    /** The shop screen was closed (by its player, or the server). */
    public static void onScreenClosed(ShopStopScreenHandler handler) {
        Stop stop = stopOf(handler);
        if (stop != null) end(stop, handler.getPurchases() > 0 ? End.BOUGHT : End.NOTHING);
    }

    private static @Nullable Stop stopOf(ShopStopScreenHandler handler) {
        for (Stop stop : STOPS.values()) {
            if (stop.handler == handler) return stop;
        }
        return null;
    }

    // ---------------------------------------------------------------- time

    private static void tick(MinecraftServer server) {
        if (STOPS.isEmpty()) return;
        for (Stop stop : new ArrayList<>(STOPS.values())) {
            ServerWorld world = stop.world;
            ServerPlayerEntity owner = server.getPlayerManager().getPlayer(stop.owner);
            if (owner == null || owner.isDisconnected() || !owner.isAlive() || owner.getWorld() != world
                    || !(world.getEntity(stop.token) instanceof MobEntity mob) || !mob.isAlive()
                    || !(world.getEntity(stop.trader) instanceof BoxedTraderEntity trader) || !trader.isAlive()) {
                end(stop, End.LEFT);
                continue;
            }
            ShopStopScreenHandler handler = stop.handler;
            if (handler == null || owner.currentScreenHandler != handler) {
                end(stop, handler != null && handler.getPurchases() > 0 ? End.BOUGHT : End.NOTHING);
                continue;
            }
            if (handler.getPurchases() >= stop.limit) {
                // Ended a tick after the last purchase: never under the trade being made
                end(stop, End.BOUGHT);
                continue;
            }
            long left = stop.deadline - world.getTime();
            if (left <= 0) {
                end(stop, End.TIMEOUT);
                continue;
            }
            if (left % SPARKLE_INTERVAL == 0) sparkle(world, stop, trader);
            if (left % NOTICE_INTERVAL == 0) {
                Text notice = Text.translatable("message.steveparty.shop_stop.shopping", owner.getDisplayName(), (left + 19) / 20)
                        .formatted(Formatting.GOLD);
                for (ServerPlayerEntity player : audience(stop)) {
                    MessageUtils.sendToPlayer(player, notice, MessageUtils.MessageType.ACTION_BAR);
                }
            }
        }
    }

    /** Gold sparkles on the merchant and his stalls: where the shop is. */
    private static void sparkle(ServerWorld world, Stop stop, BoxedTraderEntity trader) {
        world.spawnParticles(ParticleTypes.HAPPY_VILLAGER, trader.getX(), trader.getY() + trader.getHeight() + 0.3, trader.getZ(),
                4, 0.4, 0.3, 0.4, 0.0);
        for (BlockPos stall : stallsOf(world, VendorLinkPersistentState.get(world.getServer()), trader)) {
            world.spawnParticles(ParticleTypes.HAPPY_VILLAGER, stall.getX() + 0.5, stall.getY() + 1.2, stall.getZ() + 0.5,
                    2, 0.35, 0.2, 0.35, 0.0);
        }
    }

    // ---------------------------------------------------------------- end

    /** Ends the stop: the screen closes, everyone around is told, the token goes on. */
    private static void end(Stop stop, End how) {
        if (STOPS.remove(stop.token) == null) return;
        ServerWorld world = stop.world;
        ServerPlayerEntity owner = world.getServer().getPlayerManager().getPlayer(stop.owner);
        if (owner != null && stop.handler != null && owner.currentScreenHandler == stop.handler) {
            owner.closeHandledScreen(); // back in onScreenClosed: nothing more, the stop is gone
        }
        if (world.getEntity(stop.trader) instanceof BoxedTraderEntity trader) trader.removeStatusEffect(StatusEffects.GLOWING);
        Text who = owner != null ? owner.getDisplayName() : stop.tokenName;
        int purchases = stop.handler == null ? 0 : stop.handler.getPurchases();
        Text message = switch (how) {
            case BOUGHT -> Text.translatable("message.steveparty.shop_stop.bought", who, purchases).formatted(Formatting.GREEN);
            case NOTHING -> Text.translatable("message.steveparty.shop_stop.nothing", who).formatted(Formatting.GRAY);
            case TIMEOUT -> Text.translatable("message.steveparty.shop_stop.timeout", who).formatted(Formatting.GOLD);
            case LEFT -> Text.translatable("message.steveparty.shop_stop.left", stop.tokenName).formatted(Formatting.GRAY);
        };
        Vec3d at = Vec3d.ofCenter(stop.space);
        MessageUtils.sendToNearby(world, at, AUDIENCE_RADIUS, message, MessageUtils.MessageType.CHAT);
        if (owner != null && !owner.getPos().isInRange(at, AUDIENCE_RADIUS)) {
            MessageUtils.sendToPlayer(owner, message, MessageUtils.MessageType.CHAT);
        }
        goOn(stop);
    }

    /**
     * The party of the token was stopped: its stop ends at once, silently, and nothing goes on after it (the screen
     * closes, the merchant stops glowing).
     */
    public static void cancel(UUID token) {
        Stop stop = STOPS.remove(token);
        if (stop == null) return;
        ServerPlayerEntity owner = stop.world.getServer().getPlayerManager().getPlayer(stop.owner);
        if (owner != null && stop.handler != null && owner.currentScreenHandler == stop.handler) owner.closeHandledScreen();
        if (stop.world.getEntity(stop.trader) instanceof BoxedTraderEntity trader) trader.removeStatusEffect(StatusEffects.GLOWING);
    }

    /** A paused token walks its remaining steps; a landing during a party ends the turn. */
    private static void goOn(Stop stop) {
        ServerWorld world = stop.world;
        if (world.getEntity(stop.token) instanceof MobEntity mob && mob.isAlive()
                && mob instanceof TokenizedEntityInterface token && token.steveparty$isTokenized()
                && token.steveparty$getNbSteps() > 0 && BoardSpaces.boardSpaceOf(mob) != null) {
            world.playSound(null, mob.getX(), mob.getY(), mob.getZ(), SoundEvents.BLOCK_NOTE_BLOCK_CHIME.value(), SoundCategory.BLOCKS, 0.6F, 1.4F);
            TokenMovementService.moveEntityOnBoard(mob, token.steveparty$getNbSteps());
            return;
        }
        PartyControllerEntity party = stop.party;
        if (party == null || party.isRemoved()) return;
        PartyStep step = party.getPartyData().getCurrentStep();
        // Only the turn that waited for this stop, if nothing moved the party on meanwhile
        if (step instanceof TokenTurnPartyStep turn && step.getStatus() == PartyStep.Status.IN_PROGRESS
                && stop.token.equals(turn.getTokenUUID())) {
            party.nextStep();
        }
    }

    /** The players around the space, the shopper excepted. */
    private static List<ServerPlayerEntity> audience(Stop stop) {
        Vec3d at = Vec3d.ofCenter(stop.space);
        return stop.world.getPlayers(player -> !player.getUuid().equals(stop.owner) && player.getPos().isInRange(at, AUDIENCE_RADIUS));
    }
}
