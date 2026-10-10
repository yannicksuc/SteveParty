package fr.lordfinn.steveparty.items.custom;

import fr.lordfinn.steveparty.items.tooltip.Tooltips;
import fr.lordfinn.steveparty.board.TileLinkerBrush;
import fr.lordfinn.steveparty.items.custom.cartridges.CartridgeItem;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.Item;
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
import net.minecraft.util.TypedActionResult;
import net.minecraft.util.UseAction;
import net.minecraft.world.World;

import java.util.List;

/**
 * The Tile Linker Brush (« Pinceau lieur de tuiles »): links board spaces by painting them, the button held while
 * sweeping over them; going over a link again erases it. Its wheel (left click) picks the level (the redstone power
 * whose cartridge gets the link on a 16-slot board space), the kind of Cartridge put in new tiles, undo and redo. See
 * {@link TileLinkerBrush}.
 */
public class TileLinkerBrushItem extends Item {

    public TileLinkerBrushItem(Settings settings) {
        super(settings);
    }

    /**
     * Right click on a block: nothing done here, so the use goes on to {@link #use} and the stroke is held (a board
     * space opens nothing with it: see CartridgeContainer).
     */
    @Override
    public ActionResult useOnBlock(ItemUsageContext context) {
        return ActionResult.PASS;
    }

    /**
     * Right click, on a tile or in the air: the brush is held in use while the button is (no swing of the arm, played
     * once per click, nor any repeated one), and paints each tick (see {@link #usageTick}).
     */
    @Override
    public TypedActionResult<ItemStack> use(World world, PlayerEntity player, Hand hand) {
        ItemStack stack = player.getStackInHand(hand);
        if (hand != Hand.MAIN_HAND) return TypedActionResult.pass(stack);
        player.setCurrentHand(hand);
        if (!world.isClient) TileLinkerBrush.use((ServerPlayerEntity) player, stack, (ServerWorld) world);
        return TypedActionResult.consume(stack);
    }

    /** Held as long as the button is. */
    @Override
    public int getMaxUseTime(ItemStack stack, LivingEntity user) {
        return 72000;
    }

    /** No pose of its own: the arm holds the brush still while it paints. */
    @Override
    public UseAction getUseAction(ItemStack stack) {
        return UseAction.NONE;
    }

    /** While held: the stroke goes on, following the look. */
    @Override
    public void usageTick(World world, LivingEntity user, ItemStack stack, int remainingUseTicks) {
        if (world instanceof ServerWorld serverWorld && user instanceof ServerPlayerEntity player)
            TileLinkerBrush.use(player, stack, serverWorld);
    }

    /** The button released: the stroke ends. */
    @Override
    public void onStoppedUsing(ItemStack stack, World world, LivingEntity user, int remainingUseTicks) {
        if (!world.isClient && user instanceof ServerPlayerEntity player) TileLinkerBrush.endStroke(player);
    }

    /** The level, cartridge and anchor change as it paints: no re-equip animation of the hand. */
    @Override
    public boolean allowComponentsUpdateAnimation(PlayerEntity player, Hand hand, ItemStack oldStack, ItemStack newStack) {
        return false;
    }

    /** "Tile Linker Brush (level 5)". */
    @Override
    public Text getName(ItemStack stack) {
        int level = TileLinkerBrush.level(stack);
        if (level == TileLinkerBrush.POWERED) return super.getName(stack);
        return super.getName(stack).copy().append(Text.literal(" (").formatted(Formatting.GRAY))
                .append(Text.translatable("item.steveparty.tile_linker_brush.level", level).formatted(Formatting.RED))
                .append(Text.literal(")").formatted(Formatting.GRAY));
    }

    @Override
    public void appendTooltip(ItemStack stack, TooltipContext context, List<Text> tooltip, TooltipType type) {
        Item cartridge = TileLinkerBrush.cartridge(stack);
        Tooltips tips = Tooltips.of(tooltip);
        // The painted tiles keep their cartridge unless one is chosen in the wheel
        if (cartridge != null) tips.state("tooltip.steveparty.tile_linker_brush.cartridge", Tooltips.value(new ItemStack(cartridge).getName()));
        tips.summary("tooltip.steveparty.tile_linker_brush")
                .more(more -> more
                        .use(Tooltips.Keys.use(), "tooltip.steveparty.tile_linker_brush.controls.chest")
                        .use(Tooltips.Keys.of("tooltip.steveparty.key.offhand"), "tooltip.steveparty.tile_linker_brush.controls.offhand"));
    }
}
