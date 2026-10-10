package fr.lordfinn.steveparty.items.custom.cartridges;

import fr.lordfinn.steveparty.blocks.custom.CartridgeTransfers;
import fr.lordfinn.steveparty.blocks.custom.PartyController.PartyControllerEntity;
import fr.lordfinn.steveparty.blocks.custom.PartyController.PartyResources;
import fr.lordfinn.steveparty.blocks.custom.boardspaces.BoardSpaceType;
import fr.lordfinn.steveparty.components.InventoryComponent;
import fr.lordfinn.steveparty.components.ModComponents;
import fr.lordfinn.steveparty.items.custom.cartridges.menu.CartridgeModule;
import fr.lordfinn.steveparty.items.custom.cartridges.menu.ChoiceModule;
import fr.lordfinn.steveparty.items.custom.cartridges.menu.ContainersModule;
import fr.lordfinn.steveparty.items.custom.cartridges.menu.GhostSlotsModule;
import fr.lordfinn.steveparty.items.tooltip.Tooltips;
import net.minecraft.entity.Entity;
import net.minecraft.inventory.SimpleInventory;
import net.minecraft.item.ItemStack;
import net.minecraft.registry.Registries;
import net.minecraft.text.Text;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.GlobalPos;
import net.minecraft.world.World;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import static fr.lordfinn.steveparty.components.ModComponents.INVENTORY_COMPONENT;
import static fr.lordfinn.steveparty.components.ModComponents.IS_NEGATIVE;

/**
 * The Trichaudron Cartridge (« Cartouche Trichaudron »): its prizes are what its linked chests really hold, linked
 * like the Inventory Cartridge's ({@link CartridgeContainers}: a click on a chest adds or removes it). Its menu only
 * picks which ones: up to {@link #PRIZES} ghost items (an item and how many make a prize, capped by what the chests
 * hold); without any, the first {@link #PRIZES} different items found in the chests, as they lie. A prize won is taken
 * out of the chests, and (by default) no longer offered for the rest of the party ({@link #offered}; option: always the
 * same prizes). The Trichaudron shows one head per prize on offer: a blind pick by default (option: a true choice, each
 * head showing its prize). A token stopping on it meets the Trichaudron (see TrichaudronPrizes); nothing to give (no chest,
 * empty chests, none of the items set) and the space sleeps. Nothing is ever made from nothing. Its tile is the dark
 * red of the beast's crust.
 */
public class TrichaudronCartridgeItem extends CartridgeItem implements ContainerCartridge, MobSpawnCartridge {
    /** Its tile's colour: a dark magma crust. */
    public static final int COLOR = 0x64200C;
    /** The most prizes on offer (its menu's slots). */
    public static final int PRIZES = 5;

    private static final String K = MENU_KEY + "trichaudron.";
    /** Off (default): a prize won is not offered again for the rest of the party; on: always the same prizes. */
    private static final BoolSetting SAME_PRIZES = new BoolSetting(ModComponents.TRICHAUDRON_SAME_PRIZES);
    /** Off (default): a blind pick, the heads show nothing; on: a true choice, each head shows its prize. */
    private static final BoolSetting TRUE_CHOICE = new BoolSetting(ModComponents.TRICHAUDRON_TRUE_CHOICE);
    private static final List<CartridgeModule> MODULES = List.of(
            new GhostSlotsModule("prizes", K + "prizes", PRIZES, K + "wheel"),
            SAME_PRIZES.choice("restock", K + "restock", ChoiceModule.Option.tipped(K + "consumed"),
                    ChoiceModule.Option.tipped(K + "same")),
            TRUE_CHOICE.choice("pick", K + "pick", ChoiceModule.Option.tipped(K + "blind"),
                    ChoiceModule.Option.tipped(K + "true_choice")),
            // No title: its hint row says what it is; no description: it fits beside a tile with all its rows
            new ContainersModule("chests", null));

    public TrichaudronCartridgeItem(Settings settings) {
        super(settings);
    }

    @Override
    public net.minecraft.entity.EntityType<?> spawnedMob() {
        return fr.lordfinn.steveparty.entities.ModEntities.TRICHAUDRON;
    }

    @Override
    public BoardSpaceType getBoardSpaceType() {
        return BoardSpaceType.TILE_TRICHAUDRON;
    }

    @Override
    public List<CartridgeModule> modules() {
        return MODULES;
    }

    @Override
    public int tileColor() {
        return COLOR;
    }

    /** The prizes set in its menu, in their slots' order (copies; empty slots left out): an item and how many. */
    public static List<ItemStack> filters(ItemStack stack) {
        List<ItemStack> filters = new ArrayList<>();
        InventoryComponent set = stack == null ? null : stack.get(INVENTORY_COMPONENT);
        if (set == null) return filters;
        for (int i = 0; i < Math.min(PRIZES, set.size()); i++) {
            ItemStack prize = set.getStack(i);
            if (prize.isEmpty()) continue;
            prize = prize.copy();
            prize.remove(IS_NEGATIVE); // a prize is given, never taken
            filters.add(prize);
        }
        return filters;
    }

    /** Sets the prizes of its menu to {@code prizes} (the first {@link #PRIZES}; none: whatever the chests hold). */
    public static void setFilters(ItemStack stack, List<ItemStack> prizes) {
        SimpleInventory slots = new SimpleInventory(GhostSlotsModule.COUNT);
        int slot = 0;
        for (ItemStack prize : prizes) {
            if (prize.isEmpty()) continue;
            if (slot >= PRIZES) break;
            slots.setStack(slot++, prize.copy());
        }
        if (slot == 0) stack.remove(INVENTORY_COMPONENT);
        else InventoryComponent.writeToStack(stack, slots);
    }

    /**
     * Where the space at {@code space} takes its prizes: its linked chests that are there now (loaded), in their order,
     * or without any, the party running on its board through its one source (CartridgeContainers.sourceFor);
     * {@link PartyResources#NONE} for none.
     */
    public static PartyResources chests(ItemStack stack, World world, @Nullable BlockPos space) {
        return CartridgeContainers.sourceFor(stack, world, space);
    }

    /**
     * The prizes its chests can give now (server side), at most {@link #PRIZES}: each prize of its menu its chests
     * hold, as many as set or as they hold if fewer; without prizes set, the first different items found in the
     * chests, as many as there are of each.
     */
    public static List<ItemStack> available(ItemStack stack, World world, @Nullable BlockPos space) {
        List<ItemStack> prizes = new ArrayList<>();
        PartyResources chests = chests(stack, world, space);
        if (chests.isNone()) return prizes;
        List<ItemStack> filters = filters(stack);
        if (!filters.isEmpty()) {
            for (ItemStack filter : filters) {
                int held = chests.available(CartridgeTransfers.pattern(filter));
                if (held > 0) prizes.add(filter.copyWithCount(Math.min(filter.getCount(), held)));
            }
            return prizes;
        }
        for (ItemStack found : chests.contents()) {
            if (prizes.size() >= PRIZES) break;
            prizes.add(found);
        }
        return prizes;
    }

    /**
     * The prizes a token stopping here is offered now (server side; at most {@link #PRIZES}, one head each): what its
     * chests can give ({@link #available}), less, unless it always offers the same prizes ({@link #samePrizes}), those
     * already won at {@code space} during {@code party}'s game (TrichaudronPrizes.Won). No party: all of them.
     */
    public static List<ItemStack> offered(ItemStack stack, World world, @Nullable BlockPos space,
                                          @Nullable PartyControllerEntity party) {
        List<ItemStack> prizes = available(stack, world, space);
        if (party == null || space == null || samePrizes(stack)) return prizes;
        Set<String> won = party.getPartyData().getTrichaudronWon().wonAt(space);
        if (!won.isEmpty()) prizes.removeIf(prize -> won.contains(prizeKey(prize)));
        return prizes;
    }

    /** What makes two prizes the same one (consumed together): the item and its components, never the count. */
    public static String prizeKey(ItemStack prize) {
        return Registries.ITEM.getId(prize.getItem()) + "#" + prize.getComponentChanges().hashCode();
    }

    /** Always the same prizes: a prize won stays on offer (off: consumed for the rest of the party). */
    public static boolean samePrizes(ItemStack stack) {
        return SAME_PRIZES.get(stack);
    }

    public static void setSamePrizes(ItemStack stack, boolean same) {
        SAME_PRIZES.set(stack, same);
    }

    /** A true choice: each head shows its prize (off: a blind pick, the prizes shuffled into the heads unseen). */
    public static boolean trueChoice(ItemStack stack) {
        return TRUE_CHOICE.get(stack);
    }

    public static void setTrueChoice(ItemStack stack, boolean trueChoice) {
        TRUE_CHOICE.set(stack, trueChoice);
    }

    /** Whether it has a chest linked (in any dimension). */
    public static boolean hasChests(ItemStack stack) {
        return !CartridgeContainers.isEmpty(stack);
    }

    /** Asleep, as last seen by the server (see TrichaudronTileBehavior): its tile's face dimmed. */
    public static boolean isAsleep(ItemStack stack) {
        return Boolean.TRUE.equals(stack.get(ModComponents.TRICHAUDRON_ASLEEP));
    }

    /** Records whether its space sleeps; true if that changed. */
    public static boolean setAsleep(ItemStack stack, boolean asleep) {
        if (stack.isEmpty() || isAsleep(stack) == asleep) return false;
        if (asleep) stack.set(ModComponents.TRICHAUDRON_ASLEEP, true);
        else stack.remove(ModComponents.TRICHAUDRON_ASLEEP);
        return true;
    }

    @Override
    protected void appendState(ItemStack stack, Tooltips tips) {
        Entity viewer = stack.getHolder();
        List<GlobalPos> containers = CartridgeContainers.of(stack, viewer == null ? World.OVERWORLD : viewer.getWorld().getRegistryKey());
        if (containers.isEmpty()) tips.warn(Text.translatable("tooltip.steveparty.no_container"));
        else tips.state("tooltip.steveparty.linked_containers",
                Tooltips.value(Text.translatable("tooltip.steveparty.count_of", containers.size(), CartridgeContainers.MAX)));
        int set = filters(stack).size();
        tips.state("tooltip.steveparty.trichaudron_cartridge.prizes", Tooltips.value(set == 0
                ? Text.translatable("tooltip.steveparty.trichaudron_cartridge.any") : Text.literal(Integer.toString(set))));    }

    @Override
    protected void appendMore(ItemStack stack, Tooltips.More more) {
        Entity viewer = stack.getHolder();
        List<GlobalPos> containers = CartridgeContainers.of(stack, viewer == null ? World.OVERWORLD : viewer.getWorld().getRegistryKey());
        for (int i = 0; i < containers.size(); i++) {
            BlockPos pos = containers.get(i).pos();
            more.detail(Text.translatable("tooltip.steveparty.container_entry_indexed", i + 1, pos.getX(), pos.getY(), pos.getZ())
                    .formatted(Tooltips.DIM));
        }
        more.detail("tooltip.steveparty.trichaudron_cartridge.restock", Tooltips.setting(Text.translatable(K
                + (samePrizes(stack) ? "same" : "consumed"))));
        more.detail("tooltip.steveparty.trichaudron_cartridge.pick", Tooltips.setting(Text.translatable(K
                + (trueChoice(stack) ? "true_choice" : "blind"))));
        more.use(Tooltips.Keys.use(), "tooltip.steveparty.controls.container_click");
        more.note("tooltip.steveparty.cartridge.trichaudron_cartridge.rules");
    }
}
