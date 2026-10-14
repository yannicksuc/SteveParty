package fr.lordfinn.steveparty.items.custom.cartridges;

import fr.lordfinn.steveparty.items.tooltip.Tooltips;
import fr.lordfinn.steveparty.blocks.custom.boardspaces.BoardSpaceType;
import fr.lordfinn.steveparty.components.ModComponents;
import fr.lordfinn.steveparty.items.custom.cartridges.menu.CartridgeModule;
import fr.lordfinn.steveparty.items.custom.cartridges.menu.NumberModule;
import net.minecraft.item.ItemStack;
import net.minecraft.item.tooltip.TooltipType;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;

import java.util.List;

/**
 * The Glandouille Cartridge: a token stopping on its tile is pushed {@link #distance} spaces on along the path by a
 * tower of {@link #tower} Glandouilles, with every token the tower meets on the way (see GlandouilleTileBehavior). Each
 * token it meets on the way makes one fall off the top; out of Glandouilles, it stops there, short of its destination.
 * Its destination is that number of spaces ahead, or back along the path if negative (the way a Reversed die moves
 * a token): 0 is no destination, and nothing happens. A tower of 1 is a lone Glandouille that tries to push, can't, and
 * sulks (the tokens stay put). Its tile is brown; a dye on it changes that.
 */
public class GlandouilleCartridgeItem extends CartridgeItem implements MobSpawnCartridge {
    /** Its tile's brown. */
    public static final int COLOR = 0x6C370F;
    public static final int MIN_DISTANCE = -50, MAX_DISTANCE = 50;
    public static final int DEFAULT_DISTANCE = -5;
    /** How many Glandouilles in its tower. */
    public static final int MIN_TOWER = 1, MAX_TOWER = 25, DEFAULT_TOWER = 5;

    private static final IntSetting DISTANCE = new IntSetting(ModComponents.GLANDOUILLE_DISTANCE, MIN_DISTANCE, MAX_DISTANCE, DEFAULT_DISTANCE);
    private static final IntSetting TOWER = new IntSetting(ModComponents.GLANDOUILLE_TOWER, MIN_TOWER, MAX_TOWER, DEFAULT_TOWER);

    private static final String K = MENU_KEY + "glandouille.";
    private static final List<CartridgeModule> MODULES = List.of(
            description("glandouille_cartridge", 3),
            new NumberModule("distance", K + "distance", MIN_DISTANCE, MAX_DISTANCE, GlandouilleCartridgeItem::distance,
                    (edit, value) -> DISTANCE.set(edit.stack(), value), stack -> COLOR),
            new NumberModule("tower", K + "tower_size", MIN_TOWER, MAX_TOWER, GlandouilleCartridgeItem::tower,
                    (edit, value) -> setTower(edit.stack(), value), stack -> COLOR));

    public GlandouilleCartridgeItem(Settings settings) {
        super(settings);
    }

    @Override
    public net.minecraft.entity.EntityType<?> spawnedMob() {
        return fr.lordfinn.steveparty.entities.ModEntities.GLANDOUILLE;
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
    public int tileColor() {
        return COLOR;
    }

    /** Spaces ahead its tower pushes the tokens, back if negative: {@link #MIN_DISTANCE} to {@link #MAX_DISTANCE}, 0 none. */
    public static int distance(ItemStack stack) {
        // No cartridge: no push (an unset one pushes back by default)
        return stack == null || stack.isEmpty() ? 0 : DISTANCE.get(stack);
    }

    /** Glandouilles in its tower: {@link #MIN_TOWER} to {@link #MAX_TOWER} (the old lone flag reads as 1). */
    public static int tower(ItemStack stack) {
        if (stack != null && !stack.isEmpty() && stack.getOrDefault(ModComponents.GLANDOUILLE_LONE, false)) return 1;
        return TOWER.get(stack);
    }

    public static void setTower(ItemStack stack, int value) {
        stack.remove(ModComponents.GLANDOUILLE_LONE);
        TOWER.set(stack, value);
    }

    /** A tower of 1: the lone Glandouille, who can't push. */
    public static boolean lone(ItemStack stack) {
        return tower(stack) == 1;
    }

    @Override
    protected void appendState(ItemStack stack, Tooltips tips) {
        int distance = distance(stack);
        tips.state(distance == 0 ? Text.translatable("tooltip.steveparty.glandouille_cartridge.none")
                : Text.translatable(distance < 0 ? "tooltip.steveparty.glandouille_cartridge.distance_back"
                : "tooltip.steveparty.glandouille_cartridge.distance", Tooltips.rgb(Math.abs(distance), 0xD9A066)));
    }

    @Override
    protected void appendMore(ItemStack stack, Tooltips.More more) {
        more.note("tooltip.steveparty.cartridge.glandouille_cartridge.rules");
    }
}
