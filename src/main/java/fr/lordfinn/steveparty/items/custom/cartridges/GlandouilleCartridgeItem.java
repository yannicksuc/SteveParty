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
 * tower of {@link #tower} Glandouilles, with every token the tower meets on the way (see GlandouilleTileBehavior). Each
 * token it meets on the way makes one fall off the top; out of Glandouilles, it stops there, short of its destination.
 * Its destination is that number of spaces ahead, or back along the path if negative (the way a Reversed die moves
 * a token): 0 is no destination, and nothing happens. Its menu also has the crew: the tower, or a
 * lone Glandouille that tries to push, can't, and sulks (the tokens stay put). Its tile is brown; a dye on it changes
 * that.
 */
public class GlandouilleCartridgeItem extends CartridgeItem {
    /** Its tile's brown. */
    public static final int COLOR = 0x9A5A2A;
    public static final int MIN_DISTANCE = -50, MAX_DISTANCE = 50;
    public static final int DEFAULT_DISTANCE = -5;
    /** How many Glandouilles in its tower. */
    public static final int MIN_TOWER = 1, MAX_TOWER = 25, DEFAULT_TOWER = 5;

    private static final String K = MENU_KEY + "glandouille.";
    private static final List<CartridgeModule> MODULES = List.of(
            description("glandouille_cartridge", 3),
            new NumberModule("distance", K + "distance", MIN_DISTANCE, MAX_DISTANCE, GlandouilleCartridgeItem::distance,
                    (edit, value) -> edit.stack().set(ModComponents.GLANDOUILLE_DISTANCE, value), stack -> COLOR),
            new InfoModule("hint", null, 1, context -> List.of(
                    new InfoModule.Line(Text.translatable(K + "hint"), InfoModule.Tone.SOFT))),
            new NumberModule("tower", K + "tower_size", MIN_TOWER, MAX_TOWER, GlandouilleCartridgeItem::tower,
                    (edit, value) -> edit.stack().set(ModComponents.GLANDOUILLE_TOWER, value), stack -> COLOR),
            new ChoiceModule("crew", K + "crew",
                    List.of(new ChoiceModule.Option(K + "tower"), new ChoiceModule.Option(K + "lone")),
                    stack -> lone(stack) ? 1 : 0,
                    (edit, value) -> edit.stack().set(ModComponents.GLANDOUILLE_LONE, value == 1)));

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

    /** Spaces ahead its tower pushes the tokens, back if negative: {@link #MIN_DISTANCE} to {@link #MAX_DISTANCE}, 0 none. */
    public static int distance(ItemStack stack) {
        if (stack == null || stack.isEmpty()) return 0;
        return Math.max(MIN_DISTANCE, Math.min(MAX_DISTANCE, stack.getOrDefault(ModComponents.GLANDOUILLE_DISTANCE, DEFAULT_DISTANCE)));
    }

    /** Glandouilles in its tower: {@link #MIN_TOWER} to {@link #MAX_TOWER}. */
    public static int tower(ItemStack stack) {
        if (stack == null || stack.isEmpty()) return DEFAULT_TOWER;
        return Math.max(MIN_TOWER, Math.min(MAX_TOWER, stack.getOrDefault(ModComponents.GLANDOUILLE_TOWER, DEFAULT_TOWER)));
    }

    /** The lone Glandouille (it can't push) rather than the tower. */
    public static boolean lone(ItemStack stack) {
        return stack != null && stack.getOrDefault(ModComponents.GLANDOUILLE_LONE, false);
    }

    @Override
    public void appendTooltip(ItemStack stack, TooltipContext context, List<Text> tooltip, TooltipType type) {
        int distance = distance(stack);
        tooltip.add((distance == 0 ? Text.translatable("tooltip.steveparty.glandouille_cartridge.none")
                : distance < 0 ? Text.translatable("tooltip.steveparty.glandouille_cartridge.distance_back", -distance)
                : Text.translatable("tooltip.steveparty.glandouille_cartridge.distance", distance))
                .styled(style -> style.withColor(TextColor.fromRgb(0xD9A066)).withBold(true)));
        if (lone(stack)) tooltip.add(Text.translatable("tooltip.steveparty.glandouille_cartridge.lone").formatted(Formatting.GRAY));
        else if (distance > 0) tooltip.add(Text.translatable("tooltip.steveparty.glandouille_cartridge.tower", tower(stack)).formatted(Formatting.GRAY));
        super.appendTooltip(stack, context, tooltip, type);
    }
}
