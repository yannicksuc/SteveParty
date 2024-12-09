package fr.lordfinn.steveparty.events;

import net.fabricmc.fabric.api.event.player.UseEntityCallback;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.mob.MobEntity;
import net.minecraft.item.DyeItem;
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

        System.out.println("Mod initialized: Dye changes mob name color!");
    }

    /**
     * Handles the interaction with a mob using a dye.
     *
     * @param isCreative    Whether the player is in creative mode.
     * @param dyeItem       The dye item being used.
     * @param livingEntity  The mob being interacted with.
     * @param heldItemStack The player's held item stack.
     */
    private static void handleDyeInteraction(boolean isCreative, DyeItem dyeItem, LivingEntity livingEntity, net.minecraft.item.ItemStack heldItemStack) {
        // Get the RGB color of the dye
        int colorRgb = dyeItem.getColor().getSignColor();

        // Create a TextColor using the RGB value
        Text newName = Text.literal(livingEntity.getName().getString())
                .styled(style -> style.withColor(ColorHelper.getArgb(255,
                        (colorRgb >> 16) & 0xFF,  // Red
                        (colorRgb >> 8) & 0xFF,   // Green
                        colorRgb & 0xFF)));       // Blue

        // Set the entity's custom name
        livingEntity.setCustomName(newName);

        // Consume one dye item if not in creative mode
        if (!isCreative) {
            heldItemStack.decrement(1);
        }
    }
}
