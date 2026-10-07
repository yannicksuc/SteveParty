package fr.lordfinn.steveparty.items.custom.cartridges;
import fr.lordfinn.steveparty.blocks.custom.boardspaces.BoardSpaceType;
import fr.lordfinn.steveparty.items.custom.AbstractDestinationsSelectorItem;
import fr.lordfinn.steveparty.items.custom.CartridgeContainerOpener;
import fr.lordfinn.steveparty.components.ModComponents;
import fr.lordfinn.steveparty.components.TileStampComponent;
import fr.lordfinn.steveparty.items.custom.cartridges.menu.CartridgeMenus;
import fr.lordfinn.steveparty.items.custom.cartridges.menu.CartridgeModule;
import fr.lordfinn.steveparty.items.custom.cartridges.menu.ColorModule;
import fr.lordfinn.steveparty.items.custom.cartridges.menu.InfoModule;
import fr.lordfinn.steveparty.board.BoardLinks;
import fr.lordfinn.steveparty.components.DestinationsComponent;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemUsageContext;
import net.minecraft.text.Style;
import net.minecraft.util.math.BlockPos;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.util.ActionResult;
import net.minecraft.util.Hand;
import net.minecraft.util.TypedActionResult;
import net.minecraft.world.World;
import net.minecraft.item.ItemStack;
import net.minecraft.item.tooltip.TooltipData;
import net.minecraft.item.tooltip.TooltipType;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;

import java.util.List;
import java.util.Optional;

import static fr.lordfinn.steveparty.utils.RaycastUtils.isTargetingBlock;

/**
 * A cartridge gives a role to the board space it is inserted in.
 * Each cartridge item declares that role: adding a new kind of board space is a new cartridge item
 * returning a new {@link BoardSpaceType}, plus its behavior in the behavior factory.
 * <p>
 * It also declares its settings as the modules of its menu ({@link #modules()}, see {@link CartridgeModule}): the
 * « cartridge shell » opened by a right click in the air with it, and shown next to its tile's slots in the tile's
 * interface. This base class is the Simple cartridge: a short description and the tile's colour.
 */
public class CartridgeItem extends AbstractDestinationsSelectorItem implements CartridgeContainerOpener {
    public CartridgeItem(Settings settings) {
        super(settings);
    }

    public BoardSpaceType getBoardSpaceType() {
        return BoardSpaceType.DEFAULT;
    }

    // ---------------------------------------------------------------- its menu

    public static final String MENU_KEY = "gui.steveparty.cartridge_menu.";
    /** The colour of the Simple and Start tiles when not dyed. */
    public static final int PLAIN_COLOR = 0xFFFFFF;

    private static final List<CartridgeModule> SIMPLE_MODULES = List.of(
            description("board_space_behavior", 3), colorModule(PLAIN_COLOR));

    /**
     * The modules of its menu, top to bottom (the same list for every stack of the item: the layout depends on it).
     * A new cartridge declares its settings here; the menu, the checks and the network are shared.
     */
    public List<CartridgeModule> modules() {
        return SIMPLE_MODULES;
    }

    /** The colour of the label of its menu (and of its tile, most of the time). */
    public int menuColor(ItemStack stack) {
        Integer color = stack.get(ModComponents.COLOR);
        return color != null ? color & 0xFFFFFF : 0xE8E8E8;
    }

    /** A short description of what the cartridge does ({@code gui.steveparty.cartridge_menu.desc.<id>}). */
    public static InfoModule description(String id, int lines) {
        Text text = Text.translatable(MENU_KEY + "desc." + id);
        List<InfoModule.Line> content = List.of(InfoModule.Line.of(text));
        return new InfoModule("description", null, lines, context -> content);
    }

    /** The tile's colour, like a dye on the tile (see {@link ColorModule}). */
    public static ColorModule colorModule(int defaultColor) {
        return new ColorModule("color", MENU_KEY + "color", defaultColor);
    }

    /**
     * Right click on a block the block did not take (the ground, any block while sneaking): a board space or a router
     * is added to its links (or removed, the former way of linking); a spot it is linked to although no board space is
     * there any more is unlinked; anything else opens its menu, as in the air.
     */
    @Override
    public ActionResult useOnBlock(ItemUsageContext context) {
        if (context.getHand() == Hand.OFF_HAND) return ActionResult.PASS;
        World world = context.getWorld();
        BlockPos pos = context.getBlockPos();
        if (BoardLinks.container(world, pos) != null || linksTo(context.getStack(), pos)) return toggleDestination(context);
        if (context.getPlayer() instanceof ServerPlayerEntity serverPlayer) CartridgeMenus.openInHand(serverPlayer, context.getHand());
        return ActionResult.success(world.isClient());
    }

    /** Whether it is linked to {@code pos} or the block above (a link is to the board space, a click on what holds it). */
    private static boolean linksTo(ItemStack stack, BlockPos pos) {
        DestinationsComponent links = stack.get(ModComponents.DESTINATIONS_COMPONENT);
        return links != null && (links.destinations().contains(pos) || links.destinations().contains(pos.up()));
    }

    /** A cartridge's links lead to board spaces: one with none there any more is shown, with how to fix it. */
    @Override
    protected boolean showsMissingDestinations() {
        return true;
    }

    /** Right click in the air: its menu, for the cartridge in that hand (on a block: see {@link #useOnBlock}). */
    @Override
    public TypedActionResult<ItemStack> use(World world, PlayerEntity player, Hand hand) {
        if (isTargetingBlock(player)) return super.use(world, player, hand);
        if (player instanceof ServerPlayerEntity serverPlayer) CartridgeMenus.openInHand(serverPlayer, hand);
        return TypedActionResult.success(player.getStackInHand(hand), world.isClient());
    }

    /** Clicked on another cartridge or a tile item: their destinations swap too (see DestinationSwap). */
    @Override
    public boolean onStackClicked(ItemStack stack, net.minecraft.screen.slot.Slot slot, net.minecraft.util.ClickType clickType, PlayerEntity player) {
        return fr.lordfinn.steveparty.board.DestinationSwap.onCartridgeClicked(stack, slot, clickType, player)
                || super.onStackClicked(stack, slot, clickType, player);
    }

    private static final int LINE_WIDTH = 46;
    /** The colour of the « Configurable » tag of the tooltip. */
    private static final int CONFIGURABLE_COLOR = 0xFCB017;

    /** Adds {@code text} as lines of at most {@link #LINE_WIDTH} characters (a tooltip line doesn't wrap by itself). */
    public static void addWrapped(List<Text> tooltip, Text text, Formatting formatting) {
        StringBuilder line = new StringBuilder();
        for (String word : text.getString().split(" ")) {
            if (line.length() > 0 && line.length() + 1 + word.length() > LINE_WIDTH) {
                tooltip.add(Text.literal(line.toString()).formatted(formatting));
                line.setLength(0);
            }
            if (line.length() > 0) line.append(' ');
            line.append(word);
        }
        if (line.length() > 0) tooltip.add(Text.literal(line.toString()).formatted(formatting));
    }

    /** A stamped cartridge shows its look (drawn by the client's tooltip component). */
    @Override
    public Optional<TooltipData> getTooltipData(ItemStack stack) {
        TileStampComponent stamp = stack.get(ModComponents.TILE_STAMP);
        return stamp == null ? Optional.empty() : Optional.of(stamp);
    }

    @Override
    public void appendTooltip(ItemStack stack, TooltipContext context, List<Text> tooltip, TooltipType type) {
        // What a cartridge is, what this one does, how to use it
        String id = net.minecraft.registry.Registries.ITEM.getId(this).getPath();
        addWrapped(tooltip, Text.translatable("tooltip.steveparty.cartridge.what"), Formatting.GRAY);
        addWrapped(tooltip, Text.translatable("tooltip.steveparty.cartridge." + id), Formatting.GRAY);
        // A tag: it has settings, and where to find them
        tooltip.add(Text.translatable("tooltip.steveparty.cartridge.configurable.tag").setStyle(Style.EMPTY.withColor(CONFIGURABLE_COLOR).withBold(true))
                .append(Text.translatable("tooltip.steveparty.cartridge.configurable").setStyle(Style.EMPTY.withColor(Formatting.GRAY).withBold(false))));
        addWrapped(tooltip, Text.translatable("tooltip.steveparty.cartridge.use"), Formatting.DARK_GRAY);
        super.appendTooltip(stack, context, tooltip, type);
        TileStampComponent stamp = stack.get(ModComponents.TILE_STAMP);
        if (stamp != null) {
            tooltip.add(Text.translatable("tooltip.steveparty.stamped").formatted(Formatting.LIGHT_PURPLE));
            tooltip.add(stamp.describe().copy().formatted(Formatting.GRAY));
        }
    }
}
