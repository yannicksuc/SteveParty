package fr.lordfinn.steveparty.items.custom;

import fr.lordfinn.steveparty.blocks.custom.boardspaces.ABoardSpaceBlock;
import fr.lordfinn.steveparty.powerups.PowerUp;
import fr.lordfinn.steveparty.powerups.PowerUpService;
import fr.lordfinn.steveparty.powerups.PowerUps;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.ItemUsageContext;
import net.minecraft.item.tooltip.TooltipType;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.MutableText;
import net.minecraft.text.Text;
import net.minecraft.util.ActionResult;
import net.minecraft.util.Formatting;
import net.minecraft.util.Hand;
import net.minecraft.util.TypedActionResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;

import java.util.List;

/**
 * The item of a power-up ({@link PowerUp}, one per power-up of {@link PowerUps}): used with a right-click during its
 * player's turn, before the roll ({@link PowerUpService#use}); a power-up aiming at a board space is used on it.
 */
public class PowerUpItem extends Item {
    private static final String KEY = "tooltip.steveparty.powerup.";
    private final PowerUp powerUp;

    public PowerUpItem(PowerUp powerUp, Settings settings) {
        super(settings);
        this.powerUp = powerUp;
    }

    public PowerUp powerUp() {
        return powerUp;
    }

    @Override
    public TypedActionResult<ItemStack> use(World world, PlayerEntity player, Hand hand) {
        ItemStack stack = player.getStackInHand(hand);
        if (player instanceof ServerPlayerEntity serverPlayer) {
            return PowerUpService.use(serverPlayer, hand, powerUp, null)
                    ? TypedActionResult.success(stack) : TypedActionResult.fail(stack);
        }
        return TypedActionResult.success(stack, true);
    }

    /** On a board space: the space is its target (a power-up aiming at nothing is used as in the air). */
    @Override
    public ActionResult useOnBlock(ItemUsageContext context) {
        BlockPos pos = context.getBlockPos();
        if (powerUp.target() != PowerUp.Target.TILE || ABoardSpaceBlock.getBoardSpaceEntity(context.getWorld(), pos) == null)
            return ActionResult.PASS;
        if (context.getPlayer() instanceof ServerPlayerEntity player)
            return PowerUpService.use(player, context.getHand(), powerUp, pos) ? ActionResult.SUCCESS : ActionResult.FAIL;
        return ActionResult.SUCCESS;
    }

    /**
     * Like the Dice Forge's guides: its tags ([Power-up] [Consumed]), then short points, the key words in colour: what
     * it does, when to use it, what it aims at; its default price last.
     */
    @Override
    public void appendTooltip(ItemStack stack, TooltipContext context, List<Text> tooltip, TooltipType type) {
        tooltip.add(tag("tag.power_up", Formatting.LIGHT_PURPLE).append(" ").append(tag("tag.consumed", Formatting.RED)));
        for (Text line : powerUp.effectLines()) tooltip.add(point(line));
        tooltip.add(point(Text.translatable(KEY + "when",
                Text.translatable(KEY + "when.turn").formatted(Formatting.AQUA),
                Text.translatable(KEY + "when.before_roll").formatted(Formatting.AQUA)).formatted(Formatting.GRAY)));
        switch (powerUp.target()) {
            case PLAYER -> tooltip.add(point(Text.translatable(KEY + "target.player",
                    Text.translatable(KEY + "target.player.word").formatted(Formatting.AQUA)).formatted(Formatting.GRAY)));
            case TILE -> tooltip.add(point(Text.translatable(KEY + "target.tile",
                    Text.translatable(KEY + "target.tile.word").formatted(Formatting.AQUA)).formatted(Formatting.GRAY)));
            default -> {
            }
        }
        tooltip.add(point(Text.translatable(KEY + "one_per_turn").formatted(Formatting.GRAY)));
        tooltip.add(Text.translatable(KEY + "price",
                Text.translatable(KEY + "coins", powerUp.defaultPrice()).formatted(Formatting.GOLD)).formatted(Formatting.DARK_GRAY));
        super.appendTooltip(stack, context, tooltip, type);
    }

    private static MutableText tag(String key, Formatting colour) {
        return Text.literal("[").append(Text.translatable(KEY + key)).append("]").formatted(colour);
    }

    private MutableText point(Text line) {
        return Text.literal("• ").formatted(powerUp.color()).append(line);
    }
}
