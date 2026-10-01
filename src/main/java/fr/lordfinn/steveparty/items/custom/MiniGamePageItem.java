package fr.lordfinn.steveparty.items.custom;

import fr.lordfinn.steveparty.components.MiniGamePageRef;
import fr.lordfinn.steveparty.components.ModComponents;
import fr.lordfinn.steveparty.minigame.MiniGamePageNetworking;
import fr.lordfinn.steveparty.minigame.MiniGamePages;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.entity.Entity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.item.tooltip.TooltipData;
import net.minecraft.item.tooltip.TooltipType;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;
import net.minecraft.util.ActionResult;
import net.minecraft.util.Formatting;
import net.minecraft.util.Hand;
import net.minecraft.world.World;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static fr.lordfinn.steveparty.utils.RaycastUtils.isTargetingBlock;

/**
 * A page of the mini-games catalogue. The item only carries the id of the page ({@link MiniGamePageRef}): what the
 * page says (title, description, picture, accepted team layouts, players) is kept by the server
 * ({@link MiniGamePages}), so every copy of a page shows the same thing. Right-clicked in hand, it opens its editor.
 */
public class MiniGamePageItem extends AbstractDestinationsSelectorItem {
    /** What the tooltip of a page shows under its name: the page's picture and summary (drawn by the client). */
    public record PageTooltip(UUID page) implements TooltipData {
    }

    public MiniGamePageItem(Settings settings) {
        super(settings);
    }

    @Override
    public ActionResult use(World world, PlayerEntity player, Hand hand) {
        if (world.isClient) {
            return ActionResult.PASS;
        }

        // Prevent opening the editor if the player is targeting a block or an entity
        if (isTargetingBlock(player)) {
            return ActionResult.PASS;
        }

        if (player instanceof ServerPlayerEntity serverPlayer) {
            MiniGamePageNetworking.open(serverPlayer, hand);
        }

        return ActionResult.SUCCESS;
    }

    /** The page's title once it has one. */
    @Override
    public Text getName(ItemStack stack) {
        MiniGamePageRef ref = stack.get(ModComponents.MINI_GAME_PAGE);
        return ref != null && !ref.title().isEmpty() ? Text.literal(ref.title()) : super.getName(stack);
    }

    /** Keeps the title shown by the item up to date with the page (changed through a linked copy, for instance). */
    @Override
    public void inventoryTick(ItemStack stack, World world, Entity entity, int slot, boolean selected) {
        if (world.isClient || world.getServer() == null || (world.getTime() + slot) % 20 != 0) return;
        MiniGamePages.refresh(world.getServer(), stack);
    }

    @Override
    public Optional<TooltipData> getTooltipData(ItemStack stack) {
        UUID id = MiniGamePages.idOf(stack);
        return id == null ? Optional.empty() : Optional.of(new PageTooltip(id));
    }

    @Environment(EnvType.CLIENT)
    @Override
    public void appendTooltip(ItemStack stack, TooltipContext context, List<Text> tooltip, TooltipType type) {
        MiniGamePageRef ref = stack.get(ModComponents.MINI_GAME_PAGE);
        if (ref != null && ref.linked()) {
            tooltip.add(Text.translatable("tooltip.steveparty.mini_game_page.linked").formatted(Formatting.AQUA));
        }
        tooltip.add(Text.translatable(ref == null ? "tooltip.steveparty.mini_game_page.blank" : "tooltip.steveparty.mini_game_page.open")
                .formatted(Formatting.GRAY));
        super.appendTooltip(stack, context, tooltip, type);
        if (ref != null && type.isAdvanced()) {
            tooltip.add(Text.literal(ref.id().toString()).formatted(Formatting.DARK_GRAY));
        }
    }
}
