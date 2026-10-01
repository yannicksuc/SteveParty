package fr.lordfinn.steveparty.items.custom;

import fr.lordfinn.steveparty.components.ModComponents;
import fr.lordfinn.steveparty.entities.custom.HidingTraderEntity;
import fr.lordfinn.steveparty.items.ModItems;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.component.DataComponentTypes;
import net.minecraft.component.type.EquippableComponent;
import net.minecraft.entity.EquipmentSlot;
import net.minecraft.entity.mob.MobEntity;
import net.minecraft.entity.mob.Monster;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.tooltip.TooltipType;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.sound.SoundEvents;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import org.jetbrains.annotations.Nullable;

import java.util.List;

/**
 * The look of the Hiding Trader's box, taken with shears from a merchant who already lost his bandana (he keeps a box
 * with the missing texture, until a costume is given back to him). It keeps the block look his box had
 * ({@link ModComponents#BOX_BLOCK}) and is worn in the chest slot (vanilla equippable, right click to put it
 * on): around the waist while walking, and the wearer hides inside it, closed like a block on the ground, while
 * sneaking (see {@link #isHiddenInBox}). Monsters hardly notice a player hidden in it.
 */
public class BoxCostumeItem extends Item {
    /** Monsters notice a player hidden in the box from this fraction of their usual range (2 blocks at least). */
    public static final double HIDDEN_DETECTION_FACTOR = 0.1;

    /** The look of a costume that wasn't taken from a merchant (creative tab, /give) or whose block can't be a box: a beehive. */
    public static final BlockState DEFAULT_BLOCK = Blocks.BEEHIVE.getDefaultState();

    public BoxCostumeItem(Settings settings) {
        super(settings.component(ModComponents.BOX_BLOCK, DEFAULT_BLOCK)
                .component(DataComponentTypes.EQUIPPABLE, EquippableComponent.builder(EquipmentSlot.CHEST)
                        .equipSound(SoundEvents.ITEM_ARMOR_EQUIP_LEATHER)
                        .build()));
    }

    public static ItemStack create(BlockState block) {
        ItemStack stack = new ItemStack(ModItems.BOX_COSTUME);
        if (HidingTraderEntity.isValidBoxBlock(block)) stack.set(ModComponents.BOX_BLOCK, block);
        return stack;
    }

    /** The block the box looks like ({@link #DEFAULT_BLOCK} if the stored one can't be a box). */
    public static BlockState getBlock(ItemStack stack) {
        BlockState block = stack.get(ModComponents.BOX_BLOCK);
        return HidingTraderEntity.isValidBoxBlock(block) ? block : DEFAULT_BLOCK;
    }

    public static boolean isBoxCostume(ItemStack stack) {
        return stack.isOf(ModItems.BOX_COSTUME);
    }

    /** @return the costume the player wears, or an empty stack. */
    public static ItemStack getWorn(@Nullable PlayerEntity player) {
        if (player == null) return ItemStack.EMPTY;
        ItemStack chest = player.getEquippedStack(EquipmentSlot.CHEST);
        return isBoxCostume(chest) ? chest : ItemStack.EMPTY;
    }

    /**
     * Hidden in the box: wearing it and sneaking on foot (not flying, gliding, swimming, riding, nor a spectator).
     * Same rule on both sides: the sneaking flag and the chest slot are synced to every player around.
     */
    public static boolean isHiddenInBox(@Nullable PlayerEntity player) {
        return player != null && !getWorn(player).isEmpty() && player.isSneaking() && !player.isSpectator()
                && !player.getAbilities().flying && !player.isGliding() && !player.isSwimming() && !player.hasVehicle();
    }

    /** Monsters chasing a player farther than this lose him when he hides in the box. */
    private static final double LOSE_TARGET_DISTANCE = 2.5;

    /** Server: twice a second, monsters chasing a player hidden in a box (not right next to him) lose interest. */
    public static void initialize() {
        ServerTickEvents.END_WORLD_TICK.register(world -> {
            if (world.getTime() % 10 != 0) return;
            for (ServerPlayerEntity player : world.getPlayers()) {
                if (!isHiddenInBox(player)) continue;
                for (MobEntity mob : world.getEntitiesByClass(MobEntity.class, player.getBoundingBox().expand(48),
                        mob -> mob instanceof Monster && mob.getTarget() == player
                                && mob.squaredDistanceTo(player) > LOSE_TARGET_DISTANCE * LOSE_TARGET_DISTANCE)) {
                    mob.setTarget(null);
                }
            }
        });
    }

    @Override
    public Text getName(ItemStack stack) {
        return Text.translatable("item.steveparty.box_costume.of", getBlock(stack).getBlock().getName());
    }

    @Override
    public void appendTooltip(ItemStack stack, TooltipContext context, List<Text> tooltip, TooltipType type) {
        tooltip.add(Text.translatable("item.steveparty.box_costume.tooltip.wear").formatted(Formatting.GRAY));
        tooltip.add(Text.translatable("item.steveparty.box_costume.tooltip.hide").formatted(Formatting.GRAY));
        tooltip.add(Text.translatable("item.steveparty.box_costume.tooltip.origin").formatted(Formatting.DARK_GRAY, Formatting.ITALIC));
        super.appendTooltip(stack, context, tooltip, type);
    }
}
