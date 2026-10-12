package fr.lordfinn.steveparty.items.custom.cartridges;

import fr.lordfinn.steveparty.blocks.custom.MagpieNestBlock;
import fr.lordfinn.steveparty.blocks.custom.boardspaces.BoardSpaceType;
import fr.lordfinn.steveparty.components.ModComponents;
import fr.lordfinn.steveparty.items.ModItems;
import fr.lordfinn.steveparty.items.custom.cartridges.menu.CartridgeModule;
import fr.lordfinn.steveparty.items.custom.cartridges.menu.InfoModule;
import fr.lordfinn.steveparty.items.tooltip.Tooltips;
import net.minecraft.item.ItemStack;
import net.minecraft.text.Text;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.GlobalPos;
import net.minecraft.world.World;
import org.jetbrains.annotations.Nullable;

import java.util.List;

/**
 * The Common pot Cartridge: every token passing over its space puts a stake of coins into the pot, the token stopping
 * exactly on it wins the whole pot (see CommonPots). The pot is the Magpie Nest the cartridge is linked to
 * ({@link ModComponents#POT_NEST}: a click of the cartridge on the nest, or the Tile Linker Brush): its coins, real
 * items in the nest, and the shiny things given to it. No nest linked: the pot does nothing.
 * <p>
 * Settings ({@link ModComponents#BOARD_CARTRIDGE_SETTINGS}): {@link #STAKE} (coins put in by a passing token, 1 by
 * default, 0 to 100), {@link #START} (the base:
 * the nest is filled up to it from the space's storage, never made from nothing, when new and after each win; 0 by
 * default), {@link #CAP} (the most coins the nest holds as a pot, 0 for no cap). Remembered
 * ({@link ModComponents#BOARD_CARTRIDGE_STATE}): {@link #BASE_DUE}, its base still to be taken from a storage (a pot
 * never filled yet: due).
 */
public class PotCartridgeItem extends BoardRuleCartridgeItem {
    /** Its tile's straw (the nest's). */
    public static final int COLOR = 0xC0A060;
    public static final String STAKE = "stake", START = "start", CAP = "cap", BASE_DUE = "base_due";
    /** Every number of the menu goes from 0 to 100 (a stake of 0: the pot only grows by what its nest is given). */
    public static final int MAX = 100;

    private static final String K = MENU_KEY + "pot.";
    private static final Setting STAKE_SETTING = new Setting(STAKE, 1, 0, MAX), START_SETTING = new Setting(START, 0, 0, MAX),
            CAP_SETTING = new Setting(CAP, 0, 0, MAX);
    /** Its nest (linked or not), then each number its own stepper, two a row (its meaning in its label's tooltip). */
    private static final List<CartridgeModule> MODULES = List.of(
            description("pot_cartridge", 2),
            new InfoModule("nest", K + "nest", 1, PotCartridgeItem::nestLine, stack -> new ItemStack(ModItems.MAGPIE_NEST)).tipped(),
            STAKE_SETTING.number(STAKE, K + STAKE, stack -> COLOR).half().tipped(),
            START_SETTING.number(START, K + START, stack -> COLOR).half().tipped(),
            CAP_SETTING.number(CAP, K + CAP, stack -> COLOR).half().tipped());

    public PotCartridgeItem(Settings settings) {
        super(settings);
    }

    @Override
    public BoardSpaceType getBoardSpaceType() {
        return BoardSpaceType.TILE_POT;
    }

    @Override
    public List<CartridgeModule> modules() {
        return MODULES;
    }

    @Override
    public int tileColor() {
        return COLOR;
    }

    private static List<InfoModule.Line> nestLine(InfoModule.Context context) {
        BlockPos nest = linkedNest(context.stack(), context.world());
        if (nest == null) return List.of(new InfoModule.Line(Text.translatable(K + "nest.none"), InfoModule.Tone.BAD));
        return List.of(new InfoModule.Line(Text.translatable(K + "nest.linked", nest.getX(), nest.getY(), nest.getZ()), InfoModule.Tone.GOOD));
    }

    // ---------------------------------------------------------------- its nest

    /** Its nest's position in {@code world}'s dimension, linked (whatever stands there now), null for none. */
    public static @Nullable BlockPos linkedNest(ItemStack stack, World world) {
        GlobalPos pos = stack.get(ModComponents.POT_NEST);
        return pos == null || !pos.dimension().equals(world.getRegistryKey()) ? null : pos.pos();
    }

    /** Its nest if it is one now (loaded, still a Magpie Nest), null otherwise. */
    @SuppressWarnings("deprecation") // isChunkLoaded(BlockPos): an unloaded nest is not loaded for this
    public static @Nullable BlockPos nest(ItemStack stack, World world) {
        BlockPos pos = linkedNest(stack, world);
        if (pos == null || !world.isChunkLoaded(pos) || !(world.getBlockState(pos).getBlock() instanceof MagpieNestBlock)) return null;
        return pos;
    }

    /** Links the nest at {@code pos} (the one before, if any, is forgotten); null: none. */
    public static void setNest(ItemStack stack, World world, @Nullable BlockPos pos) {
        if (pos == null) stack.remove(ModComponents.POT_NEST);
        else stack.set(ModComponents.POT_NEST, GlobalPos.create(world.getRegistryKey(), pos.toImmutable()));
    }

    // ---------------------------------------------------------------- its settings

    public static int stake(ItemStack stack) {
        return STAKE_SETTING.get(stack);
    }

    public static int start(ItemStack stack) {
        return START_SETTING.get(stack);
    }

    /** The most coins the pot holds, 0: no cap. */
    public static int cap(ItemStack stack) {
        return CAP_SETTING.get(stack);
    }

    /** Whether its base is still to be taken from a storage: a pot never filled yet, or won since. */
    public static boolean baseDue(ItemStack stack) {
        return state(stack, BASE_DUE, 1) == 1;
    }

    public static void setBaseDue(ItemStack stack, boolean due) {
        putState(stack, BASE_DUE, due ? 1 : 0);
    }

    @Override
    protected void appendState(ItemStack stack, Tooltips tips) {
        tips.state(Text.translatable("tooltip.steveparty.pot_cartridge.stake", Tooltips.coins(stake(stack))));
        if (!stack.contains(ModComponents.POT_NEST)) tips.warn(Text.translatable("tooltip.steveparty.pot_cartridge.no_nest"));
    }

    @Override
    protected void appendMore(ItemStack stack, Tooltips.More more) {
        more.note("tooltip.steveparty.cartridge.pot_cartridge.rules");
    }
}
