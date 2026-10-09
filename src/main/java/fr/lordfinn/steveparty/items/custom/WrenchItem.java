package fr.lordfinn.steveparty.items.custom;

import fr.lordfinn.steveparty.items.tooltip.Tooltips;
import fr.lordfinn.steveparty.blocks.switchable.Switchables;
import fr.lordfinn.steveparty.board.WrenchActions;
import fr.lordfinn.steveparty.components.ModComponents;
import fr.lordfinn.steveparty.items.custom.cartridges.CartridgeItem;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.block.BlockState;
import net.minecraft.entity.Entity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.item.ItemUsageContext;
import net.minecraft.item.tooltip.TooltipType;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.text.Style;
import net.minecraft.text.Text;
import net.minecraft.util.ActionResult;
import net.minecraft.util.Formatting;
import net.minecraft.util.Hand;
import net.minecraft.world.World;

import java.util.List;

/**
 * The Wrench (« Clé »): opens and works what is otherwise locked (board spaces and routers, the blocks that answer
 * only to it), checks the board on the Party Controller, and takes plastic apart in one hit. Links are painted with
 * the Tile Linker Brush. See {@link WrenchActions}.
 */
public class WrenchItem extends AbstractDestinationsSelectorItem implements CartridgeContainerOpener {

    // Instant break needs speed / hardness / 30 >= 1, even when the /5 airborne or underwater penalty applies
    private static final float PLASTIC_MINING_SPEED = 1000f;

    public WrenchItem(Settings settings) {
        super(settings);
    }

    // The wrench takes anything made of plastic (the steveparty:plastic tag) apart in one hit
    @Override
    public float getMiningSpeed(ItemStack stack, BlockState state) {
        if (state.isIn(Switchables.PLASTIC)) return PLASTIC_MINING_SPEED;
        return super.getMiningSpeed(stack, state);
    }

    /**
     * Right click on a block. The client predicts a success on the blocks the Wrench works (the hand swings, and the
     * off hand item is not used instead); the server decides.
     */
    @Override
    public ActionResult useOnBlock(ItemUsageContext context) {
        if (context.getHand() != Hand.MAIN_HAND || context.getPlayer() == null) return ActionResult.PASS;
        if (context.getWorld().isClient) return ActionResult.SUCCESS;
        return WrenchActions.useOnBlock((ServerPlayerEntity) context.getPlayer(), context.getStack(),
                (ServerWorld) context.getWorld(), context.getBlockPos());
    }

    @Override
    public boolean allowComponentsUpdateAnimation(PlayerEntity player, Hand hand, ItemStack oldStack, ItemStack newStack) {
        return false;
    }

    @Override
    public void inventoryTick(ItemStack stack, World world, Entity entity, int slot, boolean selected) {
        super.inventoryTick(stack, world, entity, slot, selected);
        if (world.isClient) return;
        // Wrenches of older versions mirrored the links of their tile, or remembered an origin and a mode for linking
        // (the Tile Linker Brush links now): none of it is used any more
        if (stack.contains(ModComponents.DESTINATIONS_COMPONENT)) stack.remove(ModComponents.DESTINATIONS_COMPONENT);
        if (stack.contains(ModComponents.WRENCH_STATE)) stack.remove(ModComponents.WRENCH_STATE);
        if (stack.contains(ModComponents.BLOCK_ORIGIN_COMPONENT)) stack.remove(ModComponents.BLOCK_ORIGIN_COMPONENT);
    }

    @Override
    public void appendTooltip(ItemStack stack, TooltipContext context, List<Text> tooltip, TooltipType type) {
        Tooltips.of(tooltip).tags(Tooltips.Tag.TOOL)
                .summary("tooltip.steveparty.wrench")
                .more(more -> more
                        .use(Tooltips.Keys.use(), "tooltip.steveparty.wrench.controls.open")
                        .use(Tooltips.Keys.use(), "tooltip.steveparty.wrench.controls.offhand")
                        .use(Tooltips.Keys.use(), "tooltip.steveparty.wrench.controls.controller")
                        .use(Tooltips.Keys.use(), "tooltip.steveparty.wrench.controls.podium")
                        .use(Tooltips.Keys.sneakUse(), "tooltip.steveparty.wrench.controls.podium.reset")
                        .use(Tooltips.Keys.attack(), "tooltip.steveparty.wrench.controls.plastic")
                        .note("tooltip.steveparty.wrench.links"));
    }
}
