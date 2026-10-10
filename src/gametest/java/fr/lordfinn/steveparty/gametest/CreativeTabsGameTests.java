package fr.lordfinn.steveparty.gametest;

import fr.lordfinn.steveparty.blocks.ModBlocks;
import fr.lordfinn.steveparty.blocks.custom.frousseux.FrousseuxCandleHolderBlock;
import fr.lordfinn.steveparty.blocks.custom.frousseux.FrousseuxCandleHolderBlockEntity;
import fr.lordfinn.steveparty.entities.custom.frousseux.FrousseuxColor;
import fr.lordfinn.steveparty.entities.custom.frousseux.FrousseuxEntity;
import fr.lordfinn.steveparty.gametest.kit.SteveGameTest;
import fr.lordfinn.steveparty.Steveparty;
import fr.lordfinn.steveparty.items.ModItemGroups;
import fr.lordfinn.steveparty.items.ModItems;
import fr.lordfinn.steveparty.stencil.StencilPatterns;
import net.minecraft.item.BlockItem;
import net.minecraft.item.Item;
import net.minecraft.item.ItemGroup;
import net.minecraft.item.ItemStack;
import net.minecraft.registry.Registries;
import net.minecraft.registry.RegistryKey;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.test.GameTest;
import net.minecraft.test.TestContext;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;

/**
 * The creative tabs ({@link ModItemGroups}): every item of the mod is listed in exactly one tab, except the ones kept
 * only for the worlds that hold them (never listed).
 */
public class CreativeTabsGameTests implements SteveGameTest {
    /**
     * Items left out of the creative tabs on purpose: the 10 fixed-wood easel signs (kept for the worlds that have
     * them; the material easel sign covers every planks).
     */
    private static final Set<String> NOT_LISTED = Set.of(
            "oak_easel_sign", "spruce_easel_sign", "birch_easel_sign", "jungle_easel_sign", "acacia_easel_sign",
            "dark_oak_easel_sign", "mangrove_easel_sign", "cherry_easel_sign", "crimson_easel_sign",
            "warped_easel_sign");

    @GameTest(templateName = EMPTY_STRUCTURE)
    public void everyItemIsInExactlyOneTab(TestContext context) {
        ServerWorld world = context.getWorld();
        ItemGroup.DisplayContext display = new ItemGroup.DisplayContext(world.getEnabledFeatures(), false,
                world.getRegistryManager());
        Map<Item, RegistryKey<ItemGroup>> tabOf = new HashMap<>();
        Set<String> twice = new TreeSet<>();
        for (RegistryKey<ItemGroup> key : ModItemGroups.ALL) {
            ItemGroup group = Registries.ITEM_GROUP.getOrThrow(key);
            group.updateEntries(display);
            context.assertFalse(group.getDisplayStacks().isEmpty(), "tab " + key.getValue() + " is empty");
            for (ItemStack stack : group.getDisplayStacks()) {
                RegistryKey<ItemGroup> other = tabOf.putIfAbsent(stack.getItem(), key);
                if (other != null && other != key) {
                    twice.add(Registries.ITEM.getId(stack.getItem()).getPath());
                }
            }
        }
        Set<String> missing = new TreeSet<>();
        Set<String> listedAnyway = new TreeSet<>();
        for (Item item : Registries.ITEM) {
            Identifier id = Registries.ITEM.getId(item);
            if (!id.getNamespace().equals(Steveparty.MOD_ID)) continue;
            boolean listed = tabOf.containsKey(item);
            if (NOT_LISTED.contains(id.getPath())) {
                if (listed) listedAnyway.add(id.getPath());
            } else if (!listed) {
                missing.add(id.getPath());
            }
        }
        context.assertTrue(missing.isEmpty(), "in no creative tab: " + missing);
        context.assertTrue(twice.isEmpty(), "in several creative tabs: " + twice);
        context.assertTrue(listedAnyway.isEmpty(), "listed, yet meant to stay out: " + listedAnyway);
        context.complete();
    }

    /** The building tab shows five stencils by the stencil tools; the rest of the library closes the tab. */
    @GameTest(templateName = EMPTY_STRUCTURE)
    public void fewStencilsFirstTheLibraryLast(TestContext context) {
        ServerWorld world = context.getWorld();
        ItemGroup group = Registries.ITEM_GROUP.getOrThrow(ModItemGroups.BUILDING);
        group.updateEntries(new ItemGroup.DisplayContext(world.getEnabledFeatures(), false, world.getRegistryManager()));
        List<ItemStack> stacks = List.copyOf(group.getDisplayStacks());
        int lastOther = -1, stencils = 0;
        for (int i = 0; i < stacks.size(); i++) {
            if (stacks.get(i).isOf(ModItems.STENCIL)) stencils++;
            else lastOther = i;
        }
        int before = 0;
        for (int i = 0; i < lastOther; i++) {
            if (stacks.get(i).isOf(ModItems.STENCIL)) before++;
        }
        context.assertTrue(before == 5, "stencils before the end of the tab: " + before);
        context.assertTrue(stencils == StencilPatterns.all().size() + 1, "every pattern and the blank stencil listed: " + stencils);
        context.complete();
    }

    /**
     * The creatures tab: the Frousseux egg, the candle saucer, then a Frousseux candle holder per candle (plain, then
     * the 16 colours in dye order), each placed as its colour.
     */
    @GameTest(templateName = EMPTY_STRUCTURE)
    public void everyFrousseuxCandleHolderAfterTheSaucer(TestContext context) {
        ServerWorld world = context.getWorld();
        ItemGroup group = Registries.ITEM_GROUP.getOrThrow(ModItemGroups.CREATURES);
        group.updateEntries(new ItemGroup.DisplayContext(world.getEnabledFeatures(), false, world.getRegistryManager()));
        List<ItemStack> stacks = List.copyOf(group.getDisplayStacks());
        int saucer = -1;
        for (int i = 0; i < stacks.size(); i++) if (stacks.get(i).isOf(ModItems.CANDLE_SAUCER)) saucer = i;
        context.assertTrue(saucer > 0 && stacks.get(saucer - 1).isOf(ModItems.FROUSSEUX_SPAWN_EGG),
                "the candle saucer right after the Frousseux egg");
        FrousseuxColor[] colors = FrousseuxColor.values();
        context.assertTrue(colors.length == 17, "plain and the 16 dye colours: " + colors.length);
        for (int i = 0; i < colors.length; i++) {
            int at = saucer + 1 + i;
            context.assertTrue(at < stacks.size() && stacks.get(at).isOf(ModBlocks.FROUSSEUX_CANDLE_HOLDER.asItem()),
                    "a candle holder for " + colors[i].asString() + " after the saucer");
            FrousseuxColor placed = FrousseuxCandleHolderBlockEntity.colorOf(FrousseuxCandleHolderBlock.keptIn(stacks.get(at)));
            context.assertTrue(placed == colors[i], "the " + colors[i].asString() + " one placed as " + placed.asString());
        }
        context.assertFalse(stacks.get(saucer + 1 + colors.length).isOf(ModBlocks.FROUSSEUX_CANDLE_HOLDER.asItem()),
                "no other candle holder");
        context.complete();
    }

    /** A creative tab's candle holder, placed and woken: a wild Frousseux of its colour. */
    @GameTest(templateName = EMPTY_STRUCTURE)
    public void aCreativeCandleHolderWakesItsColour(TestContext context) {
        BlockPos at = context.getAbsolutePos(new BlockPos(1, 2, 1));
        ServerWorld world = context.getWorld();
        ItemStack stack = FrousseuxCandleHolderBlock.asleep(FrousseuxColor.CYAN);
        world.setBlockState(at, ModBlocks.FROUSSEUX_CANDLE_HOLDER.getDefaultState()
                .with(FrousseuxCandleHolderBlock.COLOR, FrousseuxColor.CYAN));
        BlockItem.writeNbtToBlockEntity(world, null, at, stack);
        FrousseuxEntity awake = FrousseuxCandleHolderBlock.wakeUp(world, at);
        context.assertTrue(awake != null && awake.getColor() == FrousseuxColor.CYAN, "a cyan Frousseux wakes up");
        context.assertTrue(awake.getOwner() == null, "a wild one");
        awake.discard();
        context.complete();
    }
}
