package fr.lordfinn.steveparty.items.custom;

import fr.lordfinn.steveparty.blocks.custom.PartyController.PartyBank;
import fr.lordfinn.steveparty.components.ModComponents;
import net.fabricmc.fabric.api.event.player.UseBlockCallback;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.tooltip.TooltipType;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.text.Text;
import net.minecraft.util.ActionResult;
import net.minecraft.util.Formatting;
import net.minecraft.util.Hand;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.GlobalPos;
import net.minecraft.world.World;
import org.jetbrains.annotations.Nullable;

import java.util.List;

/**
 * The Chest Cartridge: points to a chest (its dimension and position). In the bank slot of a Party Controller (Gains
 * page), that chest is the party's bank: the gains of its mini-games are taken from it (see {@link PartyBank}).
 * <ul>
 *     <li>Right-click a storage container (chest, trapped chest, barrel, shulker box; a double chest counts whole):
 *     the cartridge points to it (the container does not open).</li>
 *     <li>Sneak + right-click in the air: it points to nothing any more.</li>
 * </ul>
 */
public class ChestCartridgeItem extends Item {
    public ChestCartridgeItem(Settings settings) {
        super(settings);
    }

    /** The chest a cartridge points to, null for none. */
    public static @Nullable GlobalPos target(ItemStack stack) {
        return stack.getItem() instanceof ChestCartridgeItem ? stack.get(ModComponents.CHEST_TARGET) : null;
    }

    /**
     * A click on a container with the cartridge in the main hand points it there, before the container would open
     * (a click on anything else is left to the block).
     */
    public static void initialize() {
        UseBlockCallback.EVENT.register((player, world, hand, hit) -> {
            ItemStack stack = player.getStackInHand(hand);
            if (!(stack.getItem() instanceof ChestCartridgeItem) || player.isSpectator()) return ActionResult.PASS;
            BlockPos pos = hit.getBlockPos().toImmutable();
            if (!PartyBank.isBank(world.getBlockEntity(pos))) return ActionResult.PASS;
            if (world.isClient) return ActionResult.SUCCESS;
            point(stack, world, pos, player);
            return ActionResult.SUCCESS;
        });
    }

    /** Points the cartridge to the container at {@code pos}. */
    public static void point(ItemStack stack, World world, BlockPos pos, @Nullable PlayerEntity player) {
        stack.set(ModComponents.CHEST_TARGET, GlobalPos.create(world.getRegistryKey(), pos));
        if (player == null) return;
        player.sendMessage(Text.translatable("message.steveparty.chest_cartridge.set", world.getBlockState(pos).getBlock().getName(), pos(pos)), true);
        world.playSound(null, pos, SoundEvents.BLOCK_CHEST_LOCKED, SoundCategory.PLAYERS, 0.5F, 1.4F);
    }

    @Override
    public ActionResult use(World world, PlayerEntity user, Hand hand) {
        ItemStack stack = user.getStackInHand(hand);
        if (!user.isSneaking() || target(stack) == null) return ActionResult.PASS;
        if (world.isClient) return ActionResult.SUCCESS;
        stack.remove(ModComponents.CHEST_TARGET);
        user.sendMessage(Text.translatable("message.steveparty.chest_cartridge.cleared"), true);
        world.playSound(null, user.getBlockPos(), SoundEvents.UI_BUTTON_CLICK.value(), SoundCategory.PLAYERS, 0.4F, 0.7F);
        return ActionResult.SUCCESS;
    }

    public static Text pos(BlockPos pos) {
        return Text.literal(pos.getX() + " " + pos.getY() + " " + pos.getZ());
    }

    @Override
    public void appendTooltip(ItemStack stack, TooltipContext context, List<Text> tooltip, TooltipType type) {
        super.appendTooltip(stack, context, tooltip, type);
        GlobalPos target = target(stack);
        tooltip.add(target == null ? Text.translatable("tooltip.steveparty.chest_cartridge.none").formatted(Formatting.GRAY)
                : Text.translatable("tooltip.steveparty.chest_cartridge.chest", pos(target.pos()), target.dimension().getValue().toString()).formatted(Formatting.GOLD));
        tooltip.add(Text.translatable("tooltip.steveparty.chest_cartridge.how").formatted(Formatting.DARK_GRAY));
    }
}
