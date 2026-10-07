package fr.lordfinn.steveparty.board;

import fr.lordfinn.steveparty.blocks.custom.PartyController.PartyController;
import fr.lordfinn.steveparty.blocks.custom.boardspaces.BoardSpaceBlockEntity;
import fr.lordfinn.steveparty.blocks.custom.boardspaces.BoardSpaces;
import fr.lordfinn.steveparty.blocks.custom.boardspaces.CartridgeContainer;
import fr.lordfinn.steveparty.blocks.custom.boardspaces.CartridgeContainerBlockEntity;
import fr.lordfinn.steveparty.components.ShopLinkComponent;
import fr.lordfinn.steveparty.entities.custom.BoxedTraderEntity;
import fr.lordfinn.steveparty.persistent_state.VendorLinkPersistentState;
import fr.lordfinn.steveparty.components.ModComponents;
import fr.lordfinn.steveparty.items.custom.WrenchItem;
import fr.lordfinn.steveparty.items.custom.cartridges.CartridgeItem;
import fr.lordfinn.steveparty.sounds.ModSounds;
import net.minecraft.block.BlockState;
import net.minecraft.item.ItemStack;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvent;
import net.minecraft.sound.SoundEvents;
import net.minecraft.text.Text;
import net.minecraft.util.ActionResult;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * The board tools, server side.
 * <ul>
 *     <li>The Wrench (« Clé ») opens and works what is otherwise locked: a right click on a board space or a router
 *     opens its interface (no sneaking), or, with another kind of cartridge in the off hand, swaps its cartridge (links
 *     kept); on the Party Controller it checks the board; sneaking on a podium resets its group. It takes plastic apart
 *     in one hit.</li>
 *     <li>The link edits shared by the tools (the Tile Linker Brush paints the links, see {@link TileLinkerBrush}):
 *     adding / removing a link, the undo history ({@link #recorded}), the cartridge supplied to a tile.</li>
 *     <li>What the brush's anchor (the last board space it painted) gives: a chest clicked joins its inventory tile, a
 *     stall, cash register or Boxed Trader clicked becomes its shop, a board space placed with the brush in the off
 *     hand is linked from it.</li>
 * </ul>
 */
public final class WrenchActions {
    /** Board spaces can be linked this far away. */
    public static final double LONG_REACH = 32;
    /** Vanilla repeats the use every 4 ticks while the button is held: the same block again is not a new click. */
    private static final int REPEAT_TICKS = 4;

    private record LastUse(BlockPos pos, long tick) {
    }

    private static final Map<UUID, LastUse> LAST_USES = fr.lordfinn.steveparty.utils.ServerMemory.forgetOnStop(new HashMap<>());
    private static final Map<UUID, Text> LAST_LABELS = fr.lordfinn.steveparty.utils.ServerMemory.forgetOnStop(new HashMap<>());

    private WrenchActions() {
    }

    // ---------------------------------------------------------------- the Wrench

    /** Right click on a block with the Wrench. */
    public static ActionResult useOnBlock(ServerPlayerEntity player, ItemStack wrench, ServerWorld world, BlockPos clicked) {
        // A podium (sneaking: a plain click is the podium's, it changes what a signal does): its group is reset
        if (fr.lordfinn.steveparty.blocks.custom.PodiumBlock.isPodium(world.getBlockState(clicked))) {
            if (player.isSneaking() && !isRepeat(player, clicked, world.getTime()))
                fr.lordfinn.steveparty.podium.Podiums.wrenchReset(player, world, clicked);
            return ActionResult.SUCCESS;
        }
        BlockPos pos = BoardSpaces.resolve(world, clicked);
        BlockState state = world.getBlockState(pos);
        if (state.getBlock() instanceof PartyController) {
            // The board this controller plays: checked, the report in the chat
            if (!isRepeat(player, pos, world.getTime())) BoardValidator.send(player, BoardValidator.check(world, pos));
            return ActionResult.SUCCESS;
        }
        // Same answer as the client's prediction: the hand swings, the off hand item is not used instead
        if (!(state.getBlock() instanceof CartridgeContainer block) || isRepeat(player, pos, world.getTime())) return ActionResult.SUCCESS;
        // A board space or a router with another kind of cartridge in the off hand: it replaces the space's, links
        // kept; else (any cartridge block, sneaking or not) the interface opens
        CartridgeContainerBlockEntity container = BoardLinks.container(world, pos);
        if (container != null) {
            boolean[] swapped = {false};
            recorded(player, world, null, () -> swapped[0] = swapCartridge(player, world, pos, container, TileLinkerBrush.POWERED));
            if (swapped[0]) return ActionResult.SUCCESS;
        }
        block.openContainerScreen(state, world, pos, player);
        return ActionResult.SUCCESS;
    }

    /** Runs a link edit, recording what it changes for undo (labelled by its last message); {@code tool}'s anchor too. */
    public static void recorded(ServerPlayerEntity player, World world, @Nullable ItemStack tool, Runnable action) {
        LAST_LABELS.remove(player.getUuid());
        LinkHistory.begin(player, world, tool);
        try {
            action.run();
        } finally {
            LinkHistory.commit(player, LAST_LABELS.getOrDefault(player.getUuid(), Text.empty()), tool);
        }
    }

    /** The action bar message of an action (also its label in the undo history). */
    public static void say(ServerPlayerEntity player, Text text) {
        LAST_LABELS.put(player.getUuid(), text);
        player.sendMessage(text, true);
    }

    /** The board space (or router) the player aims at, up to {@link #LONG_REACH} blocks away, or null (see {@link BrushAim}). */
    public static @Nullable BlockPos aimedBoardSpace(net.minecraft.entity.player.PlayerEntity player, World world) {
        return BrushAim.aimed(player, world, 1f);
    }

    private static boolean isRepeat(ServerPlayerEntity player, BlockPos pos, long tick) {
        LastUse last = LAST_USES.put(player.getUuid(), new LastUse(pos, tick));
        return last != null && last.pos().equals(pos) && tick - last.tick() <= REPEAT_TICKS && tick >= last.tick();
    }

    // ---------------------------------------------------------------- cartridge type swap (links kept)

    /**
     * Clicking a board space holding a cartridge while holding another kind of cartridge in the off hand: that one
     * replaces it and takes its links (the replaced cartridge goes back to the inventory, except in creative).
     *
     * @return true if the cartridge was swapped
     */
    private static boolean swapCartridge(ServerPlayerEntity player, ServerWorld world, BlockPos pos,
                                         CartridgeContainerBlockEntity container, int requestedSlot) {
        if (!(player.getOffHandStack().getItem() instanceof CartridgeItem)) return false;
        return swapCartridge(player, world, pos, container, BoardLinks.slotOf(container, requestedSlot), false);
    }

    /**
     * Swaps the cartridge in {@code slot} of a board space for one of the kind the player supplies (see
     * {@link BoardLinks#cartridgeSource}: the off hand, else the kind picked on the brush), which takes its links; the
     * replaced cartridge goes back to the inventory, except in creative. Nothing when the slot is empty or already
     * holds that kind.
     *
     * @param tellMissing whether to say so when the player has none of the kind left
     * @return true if the cartridge was swapped
     */
    public static boolean swapCartridge(ServerPlayerEntity player, ServerWorld world, BlockPos pos,
                                        CartridgeContainerBlockEntity container, int slot, boolean tellMissing) {
        ItemStack current = container.getStack(slot);
        if (current.isEmpty()) return false;
        net.minecraft.item.Item kind = player.getOffHandStack().getItem() instanceof CartridgeItem
                ? player.getOffHandStack().getItem() : BoardLinks.cartridgeKind(player);
        if (current.getItem() == kind) return false;
        ItemStack source = BoardLinks.cartridgeSource(player);
        if (source.isEmpty()) {
            if (tellMissing) say(player, Text.translatable("message.steveparty.tile_linker_brush.cartridge.none_left",
                    new ItemStack(kind).getName(), BoardText.pos(pos)));
            return false;
        }
        ItemStack replacement = source.copyWithCount(1);
        BoardLinks.setLinks(replacement, BoardLinks.links(current), world);
        boolean creative = player.getAbilities().creativeMode;
        if (!creative) source.decrement(1);
        ItemStack removed = container.removeStack(slot);
        container.setStack(slot, replacement);
        BoardLinks.sync(container);
        BoardLinks.linkNearestChest(player, container, slot);
        if (!creative && !removed.isEmpty()) {
            removed.remove(ModComponents.DESTINATIONS_COMPONENT); // its links stay with the space
            player.getInventory().offerOrDrop(removed);
        }
        say(player, Text.translatable("message.steveparty.wrench.cartridge_swapped", BoardText.pos(pos),
                current.getName(), replacement.getName()));
        playSound(world, player, ModSounds.SELECT_SOUND_EVENT, 1.3f);
        return true;
    }

    // ---------------------------------------------------------------- link writes

    /**
     * Adds the link {@code origin → target} (inserting a cartridge in the origin if needed) and turns the origin toward
     * its target when it is its first link to a board space.
     *
     * @return false if the origin has no cartridge and none could be supplied
     */
    static boolean addLink(ServerPlayerEntity player, ServerWorld world, BlockPos origin,
                                   CartridgeContainerBlockEntity originContainer, int slot, BlockPos target) {
        ItemStack cartridge = BoardLinks.ensureCartridge(player, originContainer, slot);
        if (cartridge.isEmpty()) {
            say(player, Text.translatable("message.steveparty.wrench.no_cartridge"));
            playSound(world, player, ModSounds.CANCEL_SOUND_EVENT, 0.7f);
            return false;
        }
        List<BlockPos> links = new ArrayList<>(BoardLinks.links(cartridge));
        if (links.contains(target)) return true;
        links.add(target);
        // The tile keeps the way it was placed: a link never turns it
        writeLinks(player, world, originContainer, slot, links);
        BoardLinks.trail(world, origin, target, BoardLinks.LINK_COLOR);
        starPop(world, target);
        return true;
    }

    static void removeLink(ServerPlayerEntity player, ServerWorld world, CartridgeContainerBlockEntity container, int slot, BlockPos target) {
        List<BlockPos> links = new ArrayList<>(BoardLinks.links(container, slot));
        links.remove(target);
        writeLinks(player, world, container, slot, links);
        BoardLinks.trail(world, container.getPos(), target, BoardLinks.CUT_COLOR);
    }

    /** Writes the links of a cartridge, recorded for undo. */
    static void writeLinks(@Nullable ServerPlayerEntity player, ServerWorld world, CartridgeContainerBlockEntity container, int slot, List<BlockPos> links) {
        List<BlockPos> before = List.copyOf(BoardLinks.links(container, slot));
        if (BoardLinks.setLinks(world, container, slot, links)) {
            LinkHistory.record(player, new LinkHistory.LinksChange(container.getPos().toImmutable(), slot, before, List.copyOf(links)));
        }
    }

    // ---------------------------------------------------------------- linked on placement

    /** A board space placed farther than this from the anchor is not linked automatically. */
    public static final double AUTO_LINK_DISTANCE = 10;

    /**
     * A board space was placed by {@code placer}: with the Tile Linker Brush in the off hand, it is linked from the
     * brush's anchor (the last board space it painted or placed), the anchor turns toward it and it becomes the new
     * anchor; without anchor yet, it becomes the anchor. So a path is built by placing its tiles only.
     */
    public static void onBoardSpacePlaced(World world, BlockPos pos, @Nullable net.minecraft.entity.LivingEntity placer, ItemStack placedFrom) {
        if (!(world instanceof ServerWorld serverWorld)) return;
        dropCopiedLinks(serverWorld, pos, placer, placedFrom);
        if (!(placer instanceof ServerPlayerEntity player)) return;
        ItemStack brush = player.getOffHandStack();
        if (!TileLinkerBrush.isBrush(brush)) return;
        CartridgeContainerBlockEntity placed = BoardLinks.container(world, pos);
        if (!(placed instanceof BoardSpaceBlockEntity)) return;
        BlockPos anchor = TileLinkerBrush.anchor(brush, world);
        CartridgeContainerBlockEntity anchorContainer = anchor == null ? null : BoardLinks.container(world, anchor);
        recorded(player, world, brush, () -> {
            if (anchorContainer == null) {
                TileLinkerBrush.setAnchor(brush, world, pos);
                say(player, Text.translatable("message.steveparty.tile_linker_brush.start", BoardText.pos(pos)));
                return;
            }
            double distance = Math.sqrt(anchor.getSquaredDistance(pos));
            if (distance > AUTO_LINK_DISTANCE) {
                say(player, Text.translatable("message.steveparty.tile_linker_brush.too_far", (int) Math.round(distance), (int) AUTO_LINK_DISTANCE));
                return;
            }
            int slot = BoardLinks.slotOf(anchorContainer, TileLinkerBrush.level(brush));
            if (!addLink(player, serverWorld, anchor, anchorContainer, slot, pos)) return;
            TileLinkerBrush.setAnchor(brush, world, pos);
            say(player, Text.translatable("message.steveparty.tile_linker_brush.linked", BoardText.pos(anchor), BoardText.pos(pos)));
            playChainSound(world, player, 3);
        });
    }

    /**
     * A board space placed from an item holding its data (creative pick block with Ctrl, a copied item...): its
     * cartridges come without their links, which pointed at the neighbours of the original.
     */
    private static void dropCopiedLinks(ServerWorld world, BlockPos pos, @Nullable net.minecraft.entity.LivingEntity placer, ItemStack placedFrom) {
        if (!placedFrom.contains(net.minecraft.component.DataComponentTypes.BLOCK_ENTITY_DATA)) return;
        if (!(world.getBlockEntity(pos) instanceof CartridgeContainerBlockEntity container)) return;
        int dropped = 0;
        for (int slot = 0; slot < container.size(); slot++) {
            List<BlockPos> links = BoardLinks.links(container, slot);
            if (links.isEmpty()) continue;
            dropped += links.size();
            BoardLinks.setLinks(world, container, slot, List.of());
        }
        if (dropped > 0 && placer instanceof ServerPlayerEntity player) {
            player.sendMessage(Text.translatable("message.steveparty.wrench.copy_without_links", dropped), false);
        }
    }

    // ---------------------------------------------------------------- chests of inventory tiles

    public static void initialize() {
        // A click on a trading stall or a cash register with the Tile Linker Brush whose anchor holds a Shop Cartridge:
        // the shop of that stall / register (its Boxed Trader) is the cartridge's shop, instead of the nearest merchant
        net.fabricmc.fabric.api.event.player.UseBlockCallback.EVENT.register((player, world, hand, hit) -> {
            if (hand != net.minecraft.util.Hand.MAIN_HAND || player.isSpectator()) return ActionResult.PASS;
            ItemStack wrench = player.getMainHandStack();
            if (!TileLinkerBrush.isBrush(wrench)) return ActionResult.PASS;
            net.minecraft.block.Block block = world.getBlockState(hit.getBlockPos()).getBlock();
            if (!(block instanceof fr.lordfinn.steveparty.blocks.custom.TradingStallBlock)
                    && !(block instanceof fr.lordfinn.steveparty.blocks.custom.CashRegisterBlock)) return ActionResult.PASS;
            ShopOrigin shop = shopOrigin(wrench, world);
            if (shop == null) return ActionResult.PASS;
            if (world.isClient) return ActionResult.SUCCESS;
            ServerPlayerEntity serverPlayer = (ServerPlayerEntity) player;
            BlockPos clicked = hit.getBlockPos().toImmutable();
            if (isRepeat(serverPlayer, clicked, world.getTime())) return ActionResult.SUCCESS;
            recorded(serverPlayer, world, wrench, () -> linkShopFromBlock(serverPlayer, (ServerWorld) world, shop, clicked));
            return ActionResult.SUCCESS;
        });
        // A click on a Boxed Trader with the same brush: that trader is the cartridge's shop
        net.fabricmc.fabric.api.event.player.UseEntityCallback.EVENT.register((player, world, hand, entity, hit) -> {
            if (hand != net.minecraft.util.Hand.MAIN_HAND || player.isSpectator()) return ActionResult.PASS;
            ItemStack wrench = player.getMainHandStack();
            if (!TileLinkerBrush.isBrush(wrench) || !(entity instanceof BoxedTraderEntity trader)) return ActionResult.PASS;
            ShopOrigin shop = shopOrigin(wrench, world);
            if (shop == null) return ActionResult.PASS;
            if (world.isClient) return ActionResult.SUCCESS;
            ServerPlayerEntity serverPlayer = (ServerPlayerEntity) player;
            if (isRepeat(serverPlayer, trader.getBlockPos(), world.getTime())) return ActionResult.SUCCESS;
            recorded(serverPlayer, world, wrench, () -> linkShop(serverPlayer, (ServerWorld) world, shop,
                    new ShopLinkComponent(trader.getUuid(), trader.getBlockPos().toImmutable())));
            return ActionResult.SUCCESS;
        });
        // A click on a chest with the brush whose anchor is an inventory tile: that tile's chest (even without sneaking)
        net.fabricmc.fabric.api.event.player.UseBlockCallback.EVENT.register((player, world, hand, hit) -> {
            if (hand != net.minecraft.util.Hand.MAIN_HAND || player.isSpectator()) return ActionResult.PASS;
            ItemStack wrench = player.getMainHandStack();
            if (!TileLinkerBrush.isBrush(wrench) || !BoardLinks.isChest(world, hit.getBlockPos())) return ActionResult.PASS;
            BlockPos origin = TileLinkerBrush.anchor(wrench, world);
            CartridgeContainerBlockEntity container = origin == null ? null : BoardLinks.container(world, origin);
            if (container == null) return ActionResult.PASS;
            int slot = BoardLinks.slotOf(container, TileLinkerBrush.level(wrench));
            if (!(container.getStack(slot).getItem() instanceof fr.lordfinn.steveparty.items.custom.cartridges.InventoryCartridgeItem)) {
                return ActionResult.PASS;
            }
            if (world.isClient) return ActionResult.SUCCESS;
            ServerPlayerEntity serverPlayer = (ServerPlayerEntity) player;
            BlockPos chest = hit.getBlockPos().toImmutable();
            // Held down, the use repeats: the same chest is not toggled back and forth
            if (isRepeat(serverPlayer, chest, world.getTime())) return ActionResult.SUCCESS;
            recorded(serverPlayer, world, wrench, () -> linkChest(serverPlayer, (ServerWorld) world, container, slot, chest));
            return ActionResult.SUCCESS;
        });
    }

    /**
     * Adds {@code chest} to the containers of the inventory cartridge in {@code slot} (at the end), or removes it if
     * it is one of them; a full list takes no more.
     */
    private static void linkChest(ServerPlayerEntity player, ServerWorld world, CartridgeContainerBlockEntity container, int slot, BlockPos chest) {
        ItemStack cartridge = container.getStack(slot);
        java.util.List<net.minecraft.util.math.GlobalPos> before = fr.lordfinn.steveparty.items.custom.cartridges.CartridgeContainers.of(cartridge, world.getRegistryKey());
        var toggle = fr.lordfinn.steveparty.items.custom.cartridges.CartridgeContainers.toggle(cartridge, world, chest);
        if (toggle == fr.lordfinn.steveparty.items.custom.cartridges.CartridgeContainers.Toggle.FULL) {
            say(player, Text.translatable("message.steveparty.inventory_cartridge.full", fr.lordfinn.steveparty.items.custom.cartridges.CartridgeContainers.MAX));
            playSound(world, player, ModSounds.CANCEL_SOUND_EVENT, 0.7f);
            return;
        }
        BoardLinks.sync(container);
        LinkHistory.record(player, new LinkHistory.ChestChange(container.getPos().toImmutable(), slot, before,
                fr.lordfinn.steveparty.items.custom.cartridges.CartridgeContainers.of(cartridge, world.getRegistryKey())));
        if (toggle == fr.lordfinn.steveparty.items.custom.cartridges.CartridgeContainers.Toggle.ADDED) {
            BoardLinks.trail(world, container.getPos(), chest, 0x3C8CFF);
            say(player, Text.translatable("message.steveparty.wrench.chest.linked", BoardText.pos(chest), BoardText.pos(container.getPos())));
            playSound(world, player, ModSounds.SELECT_SOUND_EVENT, 1.1f);
        } else {
            say(player, Text.translatable("message.steveparty.wrench.chest.unlinked", BoardText.pos(chest), BoardText.pos(container.getPos())));
            playSound(world, player, ModSounds.CANCEL_SOUND_EVENT, 1f);
        }
    }

    // ---------------------------------------------------------------- shops of Shop Cartridges

    /** The Shop Cartridge the brush edits: its anchor's cartridge in the slot of its level. */
    public record ShopOrigin(CartridgeContainerBlockEntity container, int slot) {
        ItemStack cartridge() {
            return container.getStack(slot);
        }
    }

    /** The Shop Cartridge of the brush's anchor (the slot of its level), or null. */
    public static @Nullable ShopOrigin shopOrigin(ItemStack brush, World world) {
        BlockPos origin = TileLinkerBrush.anchor(brush, world);
        CartridgeContainerBlockEntity container = origin == null ? null : BoardLinks.container(world, origin);
        if (container == null) return null;
        int slot = BoardLinks.slotOf(container, TileLinkerBrush.level(brush));
        return container.getStack(slot).getItem() instanceof fr.lordfinn.steveparty.items.custom.cartridges.ShopCartridgeItem
                ? new ShopOrigin(container, slot) : null;
    }

    /** A trading stall or cash register clicked: the Boxed Trader it belongs to (Shopkeeper Key links) becomes the shop. */
    private static void linkShopFromBlock(ServerPlayerEntity player, ServerWorld world, ShopOrigin origin, BlockPos clicked) {
        VendorLinkPersistentState links = VendorLinkPersistentState.get(world.getServer());
        java.util.Set<UUID> traders = links == null ? java.util.Set.of()
                : links.getVendorsLinkedTo(net.minecraft.util.math.GlobalPos.create(world.getRegistryKey(), clicked));
        if (traders.isEmpty()) {
            say(player, Text.translatable("message.steveparty.wrench.shop.no_trader", BoardText.pos(clicked)));
            playSound(world, player, ModSounds.CANCEL_SOUND_EVENT, 0.7f);
            return;
        }
        // Several traders sharing the block: the loaded one first, else any (sorted: the same one every time)
        UUID trader = traders.stream().filter(uuid -> world.getEntity(uuid) instanceof BoxedTraderEntity)
                .findFirst().orElse(traders.stream().sorted().findFirst().orElseThrow());
        linkShop(player, world, origin, new ShopLinkComponent(trader, clicked));
    }

    /**
     * Makes {@code shop} the cartridge's shop, or, if it was already, goes back to the nearest merchant.
     */
    private static void linkShop(ServerPlayerEntity player, ServerWorld world, ShopOrigin origin, ShopLinkComponent shop) {
        ItemStack cartridge = origin.cartridge();
        ShopLinkComponent before = cartridge.get(ModComponents.SHOP_LINK);
        BlockPos pos = origin.container().getPos().toImmutable();
        if (before != null && before.trader().equals(shop.trader())) {
            cartridge.remove(ModComponents.SHOP_LINK);
            BoardLinks.sync(origin.container());
            LinkHistory.record(player, new LinkHistory.ShopChange(pos, origin.slot(), before, null));
            BoardLinks.trail(world, pos, before.anchor(), BoardLinks.CUT_COLOR);
            say(player, Text.translatable("message.steveparty.wrench.shop.unlinked", BoardText.pos(pos)));
            playSound(world, player, ModSounds.CANCEL_SOUND_EVENT, 1f);
            return;
        }
        cartridge.set(ModComponents.SHOP_LINK, shop);
        BoardLinks.sync(origin.container());
        LinkHistory.record(player, new LinkHistory.ShopChange(pos, origin.slot(), before, shop));
        BoardLinks.trail(world, pos, shop.anchor(), SHOP_COLOR);
        net.minecraft.entity.Entity trader = world.getEntity(shop.trader());
        say(player, Text.translatable("message.steveparty.wrench.shop.linked", BoardText.pos(pos),
                trader != null ? trader.getDisplayName() : Text.translatable("entity.steveparty.boxed_trader")));
        world.playSound(null, player.getBlockPos(), SoundEvents.ENTITY_VILLAGER_TRADE, SoundCategory.PLAYERS, 0.6f, 1.2f);
    }

    /** Colour of a shop link (particles, board view): the Shop Cartridge's lime green. */
    public static final int SHOP_COLOR = fr.lordfinn.steveparty.items.custom.cartridges.ShopCartridgeItem.COLOR;

    // ---------------------------------------------------------------- sounds

    /** Semitones of the major pentatonic scale: the chain climbs it, one note per board space. */
    private static final int[] PENTATONIC = {0, 2, 4, 7, 9};

    private static void playChainSound(World world, ServerPlayerEntity player, int length) {
        // From half pitch (the first space) up two octaves of the pentatonic scale, then it stays on the top note
        int step = Math.min(Math.max(length - 1, 0), 10);
        int semitones = 12 * (step / 5) + PENTATONIC[step % 5] - 12;
        float pitch = (float) Math.pow(2, semitones / 12.0);
        world.playSound(null, player.getBlockPos(), SoundEvents.BLOCK_NOTE_BLOCK_BELL.value(), SoundCategory.PLAYERS, 0.45f, pitch);
    }

    /** A new link: a small star pop on the linked space (few particles, short). */
    private static void starPop(ServerWorld world, BlockPos target) {
        net.minecraft.util.math.Vec3d at = BoardSpaces.standPos(world, target).add(0, 0.35, 0);
        world.spawnParticles(fr.lordfinn.steveparty.particles.KamekShapeEffect.sparkle(2.4F, 0.8F, 9, fr.lordfinn.steveparty.particles.KamekShapeEffect.YELLOW),
                at.x, at.y, at.z, 3, 0.15, 0.1, 0.15, 0.02);
    }

    private static void playSound(World world, ServerPlayerEntity player, SoundEvent sound, float pitch) {
        world.playSound(null, player.getBlockPos(), sound, SoundCategory.PLAYERS, 0.6f, pitch);
    }

    /** @return whether {@code stack} is a wrench */
    public static boolean isWrench(ItemStack stack) {
        return stack.getItem() instanceof WrenchItem;
    }
}
