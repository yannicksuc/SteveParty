package fr.lordfinn.steveparty.events;

import fr.lordfinn.steveparty.blocks.custom.PartyController.PartyControllerEntity;
import fr.lordfinn.steveparty.blocks.custom.boardspaces.BoardSpaceEntity;
import fr.lordfinn.steveparty.blocks.custom.boardspaces.behaviors.StartTileBehavior;
import fr.lordfinn.steveparty.items.custom.cartridges.InventoryCartridgeItem;
import net.fabricmc.fabric.api.event.player.UseEntityCallback;
import net.fabricmc.fabric.api.event.player.UseItemCallback;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.mob.MobEntity;
import net.minecraft.item.DyeItem;
import net.minecraft.item.ItemStack;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.text.Text;
import net.minecraft.util.ActionResult;
import net.minecraft.util.math.ColorHelper;

public class ModEvents {

    public static void initialize() {
        UseEntityCallback.EVENT.register((player, world, hand, entity, hitResult) -> {
            if (!world.isClient && entity instanceof MobEntity livingEntity) {
                // Check if the player is holding a dye
                if (player.getStackInHand(hand).getItem() instanceof DyeItem dyeItem) {
                    // Call the method to handle the dye interaction
                    handleDyeInteraction(player.getAbilities().creativeMode, dyeItem, livingEntity, player.getStackInHand(hand));
                    return ActionResult.SUCCESS;
                }
            }
            return ActionResult.PASS;
        });

        DiceRollEvent.EVENT.register(PartyControllerEntity::handleDiceRoll);
        TileReachedEvent.EVENT.register(PartyControllerEntity::handleTileReached);
        //Event to detect player connection
        ServerPlayConnectionEvents.JOIN.register(PartyControllerEntity::handlePlayerJoin);

    }

    /**
     * Handles the interaction with a mob using a dye.
     *
     * @param isCreative    Whether the player is in creative mode.
     * @param dyeItem       The dye item being used.
     * @param livingEntity  The mob being interacted with.
     * @param itemstack The player's held item stack.
     */
    public static void handleDyeInteraction(boolean isCreative, DyeItem dyeItem, LivingEntity livingEntity, ItemStack itemstack) {
        // Get the RGB color of the dye
        if (!livingEntity.isCustomNameVisible() || !livingEntity.hasCustomName()) return;
        int colorRgb = dyeItem.getColor().getSignColor();

        // Create a TextColor using the RGB value
        Text newName = Text.literal(livingEntity.getName().getString())
                .styled(style -> style.withColor(ColorHelper.getArgb(255,
                        (colorRgb >> 16) & 0xFF,  // Red
                        (colorRgb >> 8) & 0xFF,   // Green
                        colorRgb & 0xFF)));       // Blue

        // Set the entity's custom name
        livingEntity.setCustomName(newName);

        BlockEntity blockEntity = livingEntity.getWorld().getBlockEntity(livingEntity.getBlockPos());
        if (blockEntity instanceof BoardSpaceEntity tileEntity && tileEntity.getTileBehavior() instanceof StartTileBehavior startTileBehavior) {
            startTileBehavior.setColor(tileEntity, colorRgb);
        }

        livingEntity.getWorld().playSound(
                null,
                livingEntity.getBlockPos(),
                SoundEvents.ITEM_DYE_USE,
                SoundCategory.BLOCKS,
                1F,
                1F
        );

        // Consume one dye item if not in creative mode
        if (!isCreative) {
            itemstack.decrement(1);
        }
    }
}
