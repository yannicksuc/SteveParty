package fr.lordfinn.steveparty.items.custom.cartridges;

import fr.lordfinn.steveparty.blocks.custom.boardspaces.BoardSpaceBlockEntity;
import fr.lordfinn.steveparty.blocks.custom.boardspaces.BoardSpaceType;
import fr.lordfinn.steveparty.board.BoardLinks;
import fr.lordfinn.steveparty.components.ModComponents;
import fr.lordfinn.steveparty.components.TeleportNetwork;
import fr.lordfinn.steveparty.components.TeleportSettingsComponent;
import fr.lordfinn.steveparty.blocks.custom.boardspaces.TileTeleport;
import fr.lordfinn.steveparty.items.custom.cartridges.menu.CartridgeEdit;
import fr.lordfinn.steveparty.items.custom.cartridges.menu.CartridgeModule;
import fr.lordfinn.steveparty.items.custom.cartridges.menu.CartridgeRef;
import fr.lordfinn.steveparty.items.custom.cartridges.menu.ChoiceModule;
import fr.lordfinn.steveparty.items.custom.cartridges.menu.InfoModule;
import net.minecraft.item.ItemStack;
import net.minecraft.item.tooltip.TooltipType;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.math.BlockPos;

import java.util.List;

/**
 * « Cartouche Téléportation »: a token landing on its tile is sent to another Teleport tile of the same network (its
 * colour: violet, green, orange or blue) on the same board, like warp pipes. Its settings are its menu's modules
 * ({@link #modules()}): right click in the air with it, or its tile's interface (also opened by an empty hand on it).
 */
public class TeleportCartridgeItem extends CartridgeItem {
    public TeleportCartridgeItem(Settings settings) {
        super(settings);
    }

    @Override
    public BoardSpaceType getBoardSpaceType() {
        return BoardSpaceType.TILE_TELEPORT;
    }

    public static TeleportSettingsComponent settings(ItemStack stack) {
        return stack.getOrDefault(ModComponents.TELEPORT_SETTINGS, TeleportSettingsComponent.DEFAULT);
    }

    /** A Teleport Cartridge in {@code network} (for the creative tab, the test worlds...). */
    public static ItemStack withNetwork(ItemStack stack, TeleportNetwork network) {
        stack.set(ModComponents.TELEPORT_SETTINGS, settings(stack).withNetwork(network));
        return stack;
    }

    // ---------------------------------------------------------------- the menu

    private static final String K = "gui.steveparty.cartridge_menu.teleport.";

    private static void write(CartridgeEdit edit, TeleportSettingsComponent settings) {
        edit.stack().set(ModComponents.TELEPORT_SETTINGS, settings);
    }

    private static final List<CartridgeModule> MODULES = List.of(
            new ChoiceModule("network", K + "network",
                    java.util.Arrays.stream(TeleportNetwork.values())
                            .map(network -> new ChoiceModule.Option("teleport_network.steveparty." + network.asString(), network.color()))
                            .toList(),
                    stack -> settings(stack).network().ordinal(),
                    (edit, value) -> write(edit, settings(edit.stack()).withNetwork(TeleportNetwork.values()[value]))),
            new ChoiceModule("arrival", K + "arrival",
                    List.of(new ChoiceModule.Option(K + "stay", -1, K + "stay.tooltip"),
                            new ChoiceModule.Option(K + "push", -1, K + "push.tooltip")),
                    stack -> settings(stack).push() ? 1 : 0,
                    (edit, value) -> write(edit, settings(edit.stack()).withPush(value == 1))),
            new ChoiceModule("triggers", K + "triggers",
                    List.of(new ChoiceModule.Option("gui.steveparty.cartridge_menu.yes"), new ChoiceModule.Option("gui.steveparty.cartridge_menu.no")),
                    stack -> settings(stack).pushTriggers() ? 0 : 1,
                    (edit, value) -> write(edit, settings(edit.stack()).withPushTriggers(value == 0)),
                    stack -> settings(stack).push()),
            new ChoiceModule("pick", K + "pick",
                    List.of(new ChoiceModule.Option(K + "random"), new ChoiceModule.Option(K + "cycle")),
                    stack -> settings(stack).cycle() ? 1 : 0,
                    (edit, value) -> write(edit, settings(edit.stack()).withCycle(value == 1))),
            new InfoModule("partners", null, 1, TeleportCartridgeItem::partnersLine));

    /** On a tile: how many other tiles of its network are on its board (a warning when none); in hand: a hint. */
    private static List<InfoModule.Line> partnersLine(InfoModule.Context context) {
        if (context.pos() == null) return List.of(new InfoModule.Line(Text.translatable(K + "hand_hint"), InfoModule.Tone.SOFT));
        int partners = TileTeleport.partners(context.world(), context.pos()).size();
        if (partners == 0) return List.of(new InfoModule.Line(Text.translatable(K + "alone"), InfoModule.Tone.BAD));
        return List.of(new InfoModule.Line(partners == 1 ? Text.translatable(K + "partners.one")
                : Text.translatable(K + "partners", partners), InfoModule.Tone.GOOD));
    }

    @Override
    public List<CartridgeModule> modules() {
        return MODULES;
    }

    @Override
    public int menuColor(ItemStack stack) {
        return settings(stack).network().color();
    }

    /**
     * May {@code player} change the Teleport Cartridge of the tile at {@code pos} (a dye on the tile)? Like the other
     * board edits (see {@link CartridgeRef#mayEdit}).
     */
    public static boolean mayEdit(ServerPlayerEntity player, BlockPos pos) {
        return CartridgeRef.slot(pos, 0).mayEdit(player);
    }

    /** Writes the settings of the tile's Teleport Cartridge, then saves and sends the tile (its colour follows). */
    public static void apply(BoardSpaceBlockEntity tile, ItemStack cartridge, TeleportSettingsComponent settings) {
        cartridge.set(ModComponents.TELEPORT_SETTINGS, settings);
        BoardLinks.sync(tile);
    }

    // ---------------------------------------------------------------- tooltip

    private static Text yesNo(boolean yes) {
        return Text.translatable(yes ? "tooltip.steveparty.teleport_cartridge.yes" : "tooltip.steveparty.teleport_cartridge.no");
    }

    /** The settings in plain words, one line each (the tooltip, the menu's summary). */
    public static List<Text> describe(TeleportSettingsComponent settings) {
        List<Text> lines = new java.util.ArrayList<>();
        lines.add(Text.translatable("tooltip.steveparty.teleport_cartridge.network", settings.network().displayName()));
        lines.add(Text.translatable(settings.push() ? "tooltip.steveparty.teleport_cartridge.arrival.push"
                : "tooltip.steveparty.teleport_cartridge.arrival.stay"));
        if (settings.push()) {
            lines.add(Text.translatable("tooltip.steveparty.teleport_cartridge.triggers", yesNo(settings.pushTriggers())));
        }
        lines.add(Text.translatable("tooltip.steveparty.teleport_cartridge.pick", Text.translatable(settings.cycle()
                ? "tooltip.steveparty.teleport_cartridge.pick.cycle" : "tooltip.steveparty.teleport_cartridge.pick.random")));
        return lines;
    }

    @Override
    public void appendTooltip(ItemStack stack, TooltipContext context, List<Text> tooltip, TooltipType type) {
        super.appendTooltip(stack, context, tooltip, type);
        for (Text line : describe(settings(stack))) tooltip.add(line.copy().formatted(Formatting.GRAY));
        addWrapped(tooltip, Text.translatable("tooltip.steveparty.teleport_cartridge.controls"), Formatting.DARK_GRAY);
    }
}
