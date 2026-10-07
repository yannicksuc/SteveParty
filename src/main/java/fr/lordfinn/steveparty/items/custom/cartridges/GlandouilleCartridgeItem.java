package fr.lordfinn.steveparty.items.custom.cartridges;

import fr.lordfinn.steveparty.blocks.custom.boardspaces.BoardSpaceType;
import fr.lordfinn.steveparty.components.ModComponents;
import fr.lordfinn.steveparty.items.custom.cartridges.menu.CartridgeModule;
import fr.lordfinn.steveparty.items.custom.cartridges.menu.ChoiceModule;
import fr.lordfinn.steveparty.items.custom.cartridges.menu.InfoModule;
import fr.lordfinn.steveparty.items.custom.cartridges.menu.NumberModule;
import net.minecraft.item.ItemStack;
import net.minecraft.item.tooltip.TooltipType;
import net.minecraft.text.Text;
import net.minecraft.text.TextColor;
import net.minecraft.util.Formatting;

import java.util.List;

/**
 * The Glandouille Cartridge: a token stopping on its tile is pushed {@link #distance} spaces on along the path by a
 * tower of Glandouilles, with every token the tower meets on the way (see GlandouilleTileBehavior). Its destination is
 * that number of spaces ahead: 0 is no destination, and nothing happens. Its menu also has the crew: the tower, or a
 * lone Glandouille that tries to push, can't, and sulks (the tokens stay put).
 */
public class GlandouilleCartridgeItem extends CartridgeItem {
    /** Its tile's brown. */
    public static final int COLOR = 0x9A5A2A;
    public static final int MAX_DISTANCE = 12;
    public static final int DEFAULT_DISTANCE = 3;

    private static final String K = MENU_KEY + "glandouille.";
    private static final List<CartridgeModule> MODULES = List.of(
            description("glandouille_cartridge", 3),
            new NumberModule("distance", K + "distance", 0, MAX_DISTANCE, GlandouilleCartridgeItem::distance,
                    (edit, value) -> edit.stack().set(ModComponents.GLANDOUILLE_DISTANCE, value), stack -> COLOR),
            new InfoModule("hint", null, 1, context -> List.of(
                    new InfoModule.Line(Text.translatable(K + "hint"), InfoModule.Tone.SOFT))),
            new ChoiceModule("crew", K + "crew",
                    List.of(new ChoiceModule.Option(K + "tower"), new ChoiceModule.Option(K + "lone")),
                    stack -> lone(stack) ? 1 : 0,
                    (edit, value) -> edit.stack().set(ModComponents.GLANDOUILLE_LONE, value == 1)),
            colorModule(COLOR));

    public GlandouilleCartridgeItem(Settings settings) {
        super(settings);
    }

    @Override
    public BoardSpaceType getBoardSpaceType() {
        return BoardSpaceType.TILE_GLANDOUILLE;
    }

    @Override
    public List<CartridgeModule> modules() {
        return MODULES;
    }

    @Override
    public int menuColor(ItemStack stack) {
        return stack.getOrDefault(ModComponents.COLOR, COLOR) & 0xFFFFFF;
    }

    /** Spaces ahead its tower pushes the tokens: 0 (no destination) to {@link #MAX_DISTANCE}. */
    public static int distance(ItemStack stack) {
        if (stack == null || stack.isEmpty()) return 0;
        return Math.max(0, Math.min(MAX_DISTANCE, stack.getOrDefault(ModComponents.GLANDOUILLE_DISTANCE, DEFAULT_DISTANCE)));
    }

    /** The lone Glandouille (it can't push) rather than the tower. */
    public static boolean lone(ItemStack stack) {
        return stack != null && stack.getOrDefault(ModComponents.GLANDOUILLE_LONE, false);
    }

    @Override
    public void appendTooltip(ItemStack stack, TooltipContext context, List<Text> tooltip, TooltipType type) {
        int distance = distance(stack);
        tooltip.add((distance == 0 ? Text.translatable("tooltip.steveparty.glandouille_cartridge.none")
                : Text.translatable("tooltip.steveparty.glandouille_cartridge.distance", distance))
                .styled(style -> style.withColor(TextColor.fromRgb(0xD9A066)).withBold(true)));
        if (lone(stack)) tooltip.add(Text.translatable("tooltip.steveparty.glandouille_cartridge.lone").formatted(Formatting.GRAY));
        super.appendTooltip(stack, context, tooltip, type);
    }
}
