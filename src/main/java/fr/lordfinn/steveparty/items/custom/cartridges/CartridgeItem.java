package fr.lordfinn.steveparty.items.custom.cartridges;
import net.minecraft.util.math.GlobalPos;
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
import fr.lordfinn.steveparty.items.tooltip.Tooltips;
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

    /** A setting shown in the action bar, in {@code rgb}, bold. */
    public static UnaryOperator<Style> headline(int rgb) {
        return style -> style.withColor(TextColor.fromRgb(rgb)).withBold(true);
    }

    /** A stamped cartridge shows its look (drawn by the client's tooltip component). */
    @Override
    public Optional<TooltipData> getTooltipData(ItemStack stack) {
        TileStampComponent stamp = stack.get(ModComponents.TILE_STAMP);
        return stamp == null ? Optional.empty() : Optional.of(stamp);
    }

    /**
     * The same layout for every cartridge (see Tooltips): its tags, its settings ({@link #appendState}), its
     * destinations and look, what it does; behind Shift what a cartridge is, its controls ({@link #appendMore}).
     */
    @Override
    public final void appendTooltip(ItemStack stack, TooltipContext context, List<Text> tooltip, TooltipType type) {
        TileStampComponent stamp = stack.get(ModComponents.TILE_STAMP);
        Tooltips tips = Tooltips.of(tooltip).tags(Tooltips.Tag.CONFIGURABLE);
        if (stamp != null) tips.tags(Tooltips.Tag.STAMPED);
        appendState(stack, tips);
        // A mob space's cartridge: its Spawn Marker, linked with the Tile Linker Brush
        GlobalPos marker = stack.get(ModComponents.SPAWN_MARKER);
        if (marker != null && CartridgeSpawnMarker.spawnsMobs(stack)) tips.state(Text.translatable("tooltip.steveparty.cartridge.spawn_marker",
                Tooltips.value(marker.pos().getX() + " " + marker.pos().getY() + " " + marker.pos().getZ())));
        appendDestinations(stack, tips);
        if (hasSummary()) tips.summary("tooltip.steveparty.cartridge." + Registries.ITEM.getId(this).getPath());
        tips.more(more -> {
            appendDestinationList(stack, more);
            more.use(Tooltips.Keys.use(), "tooltip.steveparty.cartridge.use.tile");
            more.use(Tooltips.Keys.use(), "tooltip.steveparty.cartridge.use.air");
            more.use(Tooltips.Keys.use(), "tooltip.steveparty.cartridge.use.block");
            more.use(Tooltips.Keys.sneakUse(), "tooltip.steveparty.cartridge.use.destination");
            appendMore(stack, more);
            if (CartridgeSpawnMarker.spawnsMobs(stack)) more.note("tooltip.steveparty.cartridge.spawn_marker.how");
            if (CartridgeContainers.linksContainers(stack)) more.note("tooltip.steveparty.cartridge.party_bank");
        });
    }

    /** Whether its name doesn't say it all: a one-line « what it does » under its settings. */
    protected boolean hasSummary() {
        return true;
    }

    /** This stack's settings, first lines of its tooltip: a neutral label, a coloured value; none at their default. */
    protected void appendState(ItemStack stack, Tooltips tips) {
    }

    /** Its own controls and rules, behind Shift. */
    protected void appendMore(ItemStack stack, Tooltips.More more) {
    }
}
