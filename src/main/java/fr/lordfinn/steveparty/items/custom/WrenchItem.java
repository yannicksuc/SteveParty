package fr.lordfinn.steveparty.items.custom;

import fr.lordfinn.steveparty.blocks.switchable.Switchables;
import fr.lordfinn.steveparty.board.BoardText;
import fr.lordfinn.steveparty.board.WrenchActions;
import fr.lordfinn.steveparty.board.WrenchMode;
import fr.lordfinn.steveparty.board.WrenchState;
import fr.lordfinn.steveparty.components.ModComponents;
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
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;

import java.util.List;

/**
 * The Wrench (« Clé »): links board spaces, with modes (see {@link WrenchMode}) and a chain (see {@link WrenchActions}).
 * Everything it remembers (origin, mode, chain) is on the item: each player has their own.
 */
public class WrenchItem extends AbstractDestinationsSelectorItem implements CartridgeContainerOpener {

    // Instant break needs speed / hardness / 30 >= 1, even when the /5 airborne or underwater penalty applies
    private static final float PLASTIC_MINING_SPEED = 1000f;
    private static final int CONTROLS_COLOR = 0xfcb017;
    /** Translation key of the key binding that switches the mode (registered by the client). */
    public static final String MODE_KEY = "key.steveparty.wrench_mode";

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
     * Right click on a block. The client predicts a success (the hand swings, and the off hand item is not used
     * instead); the server decides.
     */
    @Override
    public ActionResult useOnBlock(ItemUsageContext context) {
        if (context.getHand() != Hand.MAIN_HAND || context.getPlayer() == null) return ActionResult.PASS;
        if (context.getWorld().isClient) return ActionResult.SUCCESS;
        return WrenchActions.useOnBlock((ServerPlayerEntity) context.getPlayer(), context.getStack(),
                (ServerWorld) context.getWorld(), context.getBlockPos());
    }

    /** Right click in the air: a board space aimed at from afar, or (sneaking) the end of the chain. */
    @Override
    public ActionResult use(World world, PlayerEntity player, Hand hand) {
        if (hand != Hand.MAIN_HAND) return ActionResult.PASS;
        ItemStack stack = player.getStackInHand(hand);
        if (world.isClient) {
            boolean acts = player.isSneaking() ? WrenchActions.origin(stack, world) != null : WrenchActions.aimedBoardSpace(player, world) != null;
            return acts ? ActionResult.SUCCESS : ActionResult.PASS;
        }
        return WrenchActions.use((ServerPlayerEntity) player, stack, (ServerWorld) world);
    }

    /** The mode, origin and chain change at every click: no re-equip animation of the hand. */
    @Override
    public boolean allowComponentsUpdateAnimation(PlayerEntity player, Hand hand, ItemStack oldStack, ItemStack newStack) {
        return false;
    }

    @Override
    public void inventoryTick(ItemStack stack, World world, Entity entity, int slot, boolean selected) {
        super.inventoryTick(stack, world, entity, slot, selected);
        // Wrenches of older versions mirrored the links of their tile on themselves: the board view shows them now
        if (!world.isClient && stack.contains(ModComponents.DESTINATIONS_COMPONENT)) stack.remove(ModComponents.DESTINATIONS_COMPONENT);
    }

    /** "Wrench (Trace)". */
    @Override
    public Text getName(ItemStack stack) {
        return super.getName(stack).copy().append(Text.literal(" (").formatted(Formatting.GRAY))
                .append(WrenchState.of(stack).mode().displayName()).append(Text.literal(")").formatted(Formatting.GRAY));
    }

    @Environment(EnvType.CLIENT)
    @Override
    public void appendTooltip(ItemStack stack, TooltipContext context, List<Text> tooltip, TooltipType type) {
        WrenchState state = WrenchState.of(stack);
        tooltip.add(Text.translatable("tooltip.steveparty.wrench.mode", state.mode().displayName()).formatted(Formatting.GRAY));
        tooltip.add(Text.translatable("tooltip.steveparty.wrench.mode." + state.mode().asString()).formatted(Formatting.DARK_GRAY));
        Entity holder = stack.getHolder();
        BlockPos origin = holder == null ? null : WrenchActions.origin(stack, holder.getWorld());
        if (origin != null) {
            tooltip.add(state.mode() == WrenchMode.TRACE && state.chainLength() > 0
                    ? Text.translatable("tooltip.steveparty.wrench.chain", BoardText.pos(origin), state.chainLength()).formatted(Formatting.WHITE)
                    : Text.translatable("tooltip.steveparty.wrench.origin", BoardText.pos(origin)).formatted(Formatting.WHITE));
        }
        tooltip.add(Text.translatable("tooltip.steveparty.controls").setStyle(Style.EMPTY.withBold(true).withColor(CONTROLS_COLOR)));
        tooltip.add(Text.translatable("tooltip.steveparty.wrench.controls.click." + state.mode().asString()).formatted(Formatting.GRAY));
        if (state.mode() == WrenchMode.TRACE) {
            tooltip.add(Text.translatable("tooltip.steveparty.wrench.auto_link",
                    Text.translatable(state.autoLink() ? "hud.steveparty.wrench.auto_link.on" : "hud.steveparty.wrench.auto_link.off")).formatted(Formatting.GRAY));
        }
        for (String control : List.of("sweep", "far", "sneak_click", "sneak_air", "undo", "place", "offhand", "chest", "shop", "controller")) {
            tooltip.add(Text.translatable("tooltip.steveparty.wrench.controls." + control).formatted(Formatting.GRAY));
        }
        tooltip.add(Text.translatable("tooltip.steveparty.wrench.controls.mode", Text.keybind(MODE_KEY)).formatted(Formatting.GRAY));
    }
}
