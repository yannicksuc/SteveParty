package fr.lordfinn.steveparty.items.custom.parachute;

import net.minecraft.component.type.DyedColorComponent;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.registry.tag.ItemTags;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.util.ActionResult;
import net.minecraft.util.Hand;
import net.minecraft.util.TypedActionResult;
import net.minecraft.world.World;

/**
 * The Parachute: held while falling it opens over its holder, a use in the air opens or folds it; a use on a mob puts
 * it on its back, opening when it falls ({@link Parachutes}). Wears out like a shield, repaired with wool or string;
 * dyed like leather (its stripes take the colour, the white stays white).
 */
public class ParachuteItem extends Item {
    /** The stripes of an undyed one (ARGB): sky blue. */
    public static final int DEFAULT_COLOR = 0xFF3FA9E0;

    public ParachuteItem(Settings settings) {
        super(settings.maxDamage(Parachutes.MAX_DAMAGE));
    }

    /** The colour of {@code stack}'s stripes (ARGB). */
    public static int color(ItemStack stack) {
        return DyedColorComponent.getColor(stack, DEFAULT_COLOR);
    }

    @Override
    public TypedActionResult<ItemStack> use(World world, PlayerEntity user, Hand hand) {
        ItemStack stack = user.getStackInHand(hand);
        if (!Parachutes.canFloat(user)) return TypedActionResult.pass(stack);
        if (user instanceof ServerPlayerEntity player && !Parachutes.toggle(player, hand)) return TypedActionResult.fail(stack);
        return TypedActionResult.success(stack, world.isClient);
    }

    @Override
    public ActionResult useOnEntity(ItemStack stack, PlayerEntity user, LivingEntity entity, Hand hand) {
        if (!Parachutes.canCarry(entity)) return ActionResult.PASS;
        if (!user.getWorld().isClient) Parachutes.attach(user, entity, stack);
        return ActionResult.success(user.getWorld().isClient);
    }

    @Override
    public boolean canRepair(ItemStack stack, ItemStack ingredient) {
        return ingredient.isIn(ItemTags.WOOL) || ingredient.isOf(Items.STRING);
    }
}
