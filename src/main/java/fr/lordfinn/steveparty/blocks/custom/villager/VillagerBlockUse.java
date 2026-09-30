package fr.lordfinn.steveparty.blocks.custom.villager;

import net.minecraft.component.DataComponentTypes;
import net.minecraft.item.BlockItem;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.registry.tag.ItemTags;
import org.jetbrains.annotations.Nullable;

/**
 * What the villager block does when right-clicked with an item, and which items it cares about when shown.
 * <p>
 * It never takes anything (it's a block: it pretends to eat, it admires the emerald). Blocks in hand are placed as
 * usual (building around it has its own reactions), and items it doesn't care about keep their own use (a water
 * bucket, flint and steel, the wrench...): it only shakes its head on top.
 */
public final class VillagerBlockUse {
    private VillagerBlockUse() {
    }

    /** The reaction to a right-click with {@code stack} (empty: a poke), or null for none (a block to place). */
    @Nullable
    public static VillagerReaction reactionTo(ItemStack stack) {
        if (stack.isEmpty()) return VillagerReaction.POKED;
        if (stack.isOf(Items.EMERALD_BLOCK)) return VillagerReaction.JACKPOT;
        if (stack.isOf(Items.EMERALD)) return VillagerReaction.TRADE_HAPPY;
        if (isTreasure(stack)) return VillagerReaction.GREEDY;
        if (isWeapon(stack)) return VillagerReaction.WEAPON_SCARED;
        if (stack.isIn(ItemTags.FLOWERS)) return VillagerReaction.SNIFF_FLOWER;
        if (stack.contains(DataComponentTypes.FOOD)) {
            if (isYucky(stack)) return VillagerReaction.YUCK;
            if (isVillagerFood(stack)) return VillagerReaction.FED_LOVE;
            return VillagerReaction.FED;
        }
        if (stack.getItem() instanceof BlockItem) return null;
        return VillagerReaction.REFUSE;
    }

    /**
     * Whether a right-click with {@code stack} is taken by the villager block (the item's own use doesn't happen):
     * an empty hand and the items it reacts to specially. Blocks are placed and the other items keep their use.
     */
    public static boolean takesUse(ItemStack stack) {
        VillagerReaction reaction = reactionTo(stack);
        return reaction != null && reaction != VillagerReaction.REFUSE;
    }

    public static boolean isWeapon(ItemStack stack) {
        return stack.isIn(ItemTags.SWORDS) || stack.isIn(ItemTags.AXES) || stack.isOf(Items.TRIDENT)
                || stack.isOf(Items.MACE) || stack.isOf(Items.BOW) || stack.isOf(Items.CROSSBOW);
    }

    /** Emeralds, diamonds and gold: what a villager's eyes shine for. */
    public static boolean isTreasure(ItemStack stack) {
        return stack.isOf(Items.EMERALD) || stack.isOf(Items.EMERALD_BLOCK) || stack.isOf(Items.DIAMOND)
                || stack.isOf(Items.DIAMOND_BLOCK) || stack.isOf(Items.GOLD_INGOT) || stack.isOf(Items.GOLD_BLOCK)
                || stack.isOf(Items.RAW_GOLD);
    }

    /** What villagers eat and share: bread, carrots, potatoes, beetroots (and the golden treats). */
    public static boolean isVillagerFood(ItemStack stack) {
        return stack.isOf(Items.BREAD) || stack.isOf(Items.CARROT) || stack.isOf(Items.POTATO)
                || stack.isOf(Items.BAKED_POTATO) || stack.isOf(Items.BEETROOT) || stack.isOf(Items.GOLDEN_CARROT)
                || stack.isOf(Items.GOLDEN_APPLE) || stack.isOf(Items.ENCHANTED_GOLDEN_APPLE);
    }

    public static boolean isYucky(ItemStack stack) {
        return stack.isOf(Items.ROTTEN_FLESH) || stack.isOf(Items.SPIDER_EYE) || stack.isOf(Items.POISONOUS_POTATO)
                || stack.isOf(Items.PUFFERFISH) || stack.isOf(Items.SUSPICIOUS_STEW);
    }
}
