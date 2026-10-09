package fr.lordfinn.steveparty.gametest;

import fr.lordfinn.steveparty.Steveparty;
import fr.lordfinn.steveparty.items.ModItemGroups;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.item.Item;
import net.minecraft.item.ItemGroup;
import net.minecraft.item.ItemStack;
import net.minecraft.registry.Registries;
import net.minecraft.registry.RegistryKey;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.test.GameTest;
import net.minecraft.test.TestContext;
import net.minecraft.util.Identifier;

import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;

/**
 * The creative tabs ({@link ModItemGroups}): every item of the mod is listed in exactly one tab, except the ones kept
 * only for the worlds that hold them (never listed).
 */
public class CreativeTabsGameTests implements FabricGameTest {
    /**
     * Items left out of the creative tabs on purpose: the 10 fixed-wood easel signs (kept for the worlds that have
     * them; the material easel sign covers every planks) and the Frousseux candle holder (a tamed Frousseux asleep).
     */
    private static final Set<String> NOT_LISTED = Set.of(
            "oak_easel_sign", "spruce_easel_sign", "birch_easel_sign", "jungle_easel_sign", "acacia_easel_sign",
            "dark_oak_easel_sign", "mangrove_easel_sign", "cherry_easel_sign", "crimson_easel_sign",
            "warped_easel_sign", "frousseux_candle_holder");

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
}
