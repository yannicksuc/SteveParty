package fr.lordfinn.steveparty.items.custom;

import fr.lordfinn.steveparty.blocks.custom.boardspaces.ABoardSpaceBlock;
import fr.lordfinn.steveparty.items.tooltip.Tooltips;
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

    @Override
    public boolean hasGlint(ItemStack stack) {
        return powerUp.hasGlint() || super.hasGlint(stack);
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
     * Tagged [Power-up] [Consumed]; then this stack's state ({@link #appendState}), what it does; behind Shift when
     * and how to use it, the rules, its default price.
     */
    @Override
    public final void appendTooltip(ItemStack stack, TooltipContext context, List<Text> tooltip, TooltipType type) {
        Tooltips tips = Tooltips.of(tooltip).tags(Tooltips.Tag.POWER_UP, Tooltips.Tag.CONSUMED);
        appendState(stack, tips);
        for (Text line : powerUp.effectLines()) tips.summary(line);
        tips.more(more -> {
            howToUse(more, powerUp.target());
            appendMore(stack, more);
            more.note(KEY + "one_per_turn");
            more.note(KEY + "price", Tooltips.coins(Text.translatable("tooltip.steveparty.coins", powerUp.defaultPrice())));
        });
    }

    /** This stack's own state (a signed trap...), under the tags. */
    protected void appendState(ItemStack stack, Tooltips tips) {
    }

    /** Its own controls and rules, behind Shift. */
    protected void appendMore(ItemStack stack, Tooltips.More more) {
    }

    /** When to use a power-up (a die carrying the Power-up module too), and on what. */
    public static void howToUse(Tooltips.More more, PowerUp.Target target) {
        more.use(Tooltips.Keys.use(), KEY + (target == PowerUp.Target.TILE ? "use.tile" : "use"));
        if (target == PowerUp.Target.PLAYER) more.use(KEY + "target.player");
    }
}
