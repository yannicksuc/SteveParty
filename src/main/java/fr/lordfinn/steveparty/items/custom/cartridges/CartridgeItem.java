package fr.lordfinn.steveparty.items.custom.cartridges;
import fr.lordfinn.steveparty.blocks.custom.boardspaces.BoardSpaceType;
import fr.lordfinn.steveparty.board.DestinationSwap;
import fr.lordfinn.steveparty.items.custom.AbstractDestinationsSelectorItem;
import fr.lordfinn.steveparty.items.custom.CartridgeContainerOpener;
import fr.lordfinn.steveparty.components.ModComponents;
import fr.lordfinn.steveparty.components.TileStampComponent;
import fr.lordfinn.steveparty.items.custom.cartridges.menu.CartridgeMenus;
import fr.lordfinn.steveparty.items.custom.cartridges.menu.CartridgeModule;
import fr.lordfinn.steveparty.items.custom.cartridges.menu.ColorModule;
import fr.lordfinn.steveparty.items.custom.cartridges.menu.InfoModule;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemUsageContext;
import net.minecraft.text.Style;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.util.ActionResult;
import net.minecraft.util.Hand;
import net.minecraft.util.TypedActionResult;
import net.minecraft.world.World;
import net.minecraft.item.ItemStack;
import net.minecraft.item.tooltip.TooltipData;
import net.minecraft.item.tooltip.TooltipType;
import net.minecraft.registry.Registries;
import net.minecraft.screen.slot.Slot;
import net.minecraft.text.Text;
import net.minecraft.text.TextColor;
import net.minecraft.util.ClickType;
import net.minecraft.util.Formatting;

import java.util.List;
import java.util.Optional;
import java.util.function.UnaryOperator;

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

    /** The menu's label colour of a cartridge without colour of its own. */
    private static final int UNTINTED_MENU_COLOR = 0xE8E8E8;

    /**
     * The colour of its tile until dyed ({@code 0xRRGGBB}), -1 for none of its own: the plain tile, or a colour it
     * works out itself (it then overrides {@link #menuColor} and its behaviour's colour). A tile holding it shows it
     * while its cartridge has no {@link ModComponents#COLOR} (see ABoardSpaceBehavior#updateBoardSpaceColor).
     */
    public int tileColor() {
        return -1;
    }

    /** The colour of the label of its menu (and of its tile, most of the time): its dye, else its tile's colour. */
    public int menuColor(ItemStack stack) {
        Integer color = stack.get(ModComponents.COLOR);
        if (color != null) return color & 0xFFFFFF;
        return tileColor() >= 0 ? tileColor() : UNTINTED_MENU_COLOR;
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
     * Right click on a block the block did not take (the ground, any plain block, a board space while sneaking): that
     * block is added to its destinations, or removed if it is one (a spot under a destination removes it too). Its
     * menu opens with a right click in the air (see {@link #use}).
     */
    @Override
    public ActionResult useOnBlock(ItemUsageContext context) {
        if (context.getHand() == Hand.OFF_HAND) return ActionResult.PASS;
        // The client swings and stops there (the server decides)
        if (context.getWorld().isClient) return ActionResult.SUCCESS;
        return toggleDestination(context);
    }

    /** Right click in the air, sneaking or not: its menu, for the cartridge in that hand (on a block: see {@link #useOnBlock}). */
    @Override
    public TypedActionResult<ItemStack> use(World world, PlayerEntity player, Hand hand) {
        if (isTargetingBlock(player)) return super.use(world, player, hand);
        if (player instanceof ServerPlayerEntity serverPlayer) CartridgeMenus.openInHand(serverPlayer, hand);
        return TypedActionResult.success(player.getStackInHand(hand), world.isClient());
    }

    /** Clicked on another cartridge or a tile item: their destinations swap too (see DestinationSwap). */
    @Override
    public boolean onStackClicked(ItemStack stack, Slot slot, ClickType clickType, PlayerEntity player) {
        return DestinationSwap.onCartridgeClicked(stack, slot, clickType, player)
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

    /** A tooltip line's style in {@code rgb} (its settings in a few words). */
    public static UnaryOperator<Style> tint(int rgb) {
        return style -> style.withColor(TextColor.fromRgb(rgb));
    }

    /** The style of the tooltip's first line, what it is set to: {@code rgb}, bold. */
    public static UnaryOperator<Style> headline(int rgb) {
        return style -> style.withColor(TextColor.fromRgb(rgb)).withBold(true);
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
        String id = Registries.ITEM.getId(this).getPath();
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
