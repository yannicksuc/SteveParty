package fr.lordfinn.steveparty.items.custom;

import fr.lordfinn.steveparty.items.tooltip.Tooltips;
import fr.lordfinn.steveparty.components.MiniGamePageRef;
import fr.lordfinn.steveparty.components.ModComponents;
import fr.lordfinn.steveparty.minigame.MiniGamePageNetworking;
import fr.lordfinn.steveparty.minigame.MiniGameText;
import fr.lordfinn.steveparty.minigame.MiniGamePages;
import fr.lordfinn.steveparty.minigame.PageZoneTool;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.entity.Entity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.tooltip.TooltipData;
import net.minecraft.item.tooltip.TooltipType;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.Hand;
import net.minecraft.util.TypedActionResult;
import net.minecraft.world.World;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static fr.lordfinn.steveparty.utils.RaycastUtils.isTargetingBlock;

/**
 * A page of the mini-games catalogue. The item only carries the id of the page ({@link MiniGamePageRef}): what the
 * page says (title, description, picture, accepted team layouts, players) is kept by the server
 * ({@link MiniGamePages}), so every copy of a page shows the same thing. Right-clicked in hand, it opens its editor;
 * a click on a pipe mouth links that pipe to the page, or unlinks it (see {@code PipeBlock}, {@code MiniGamePipes#click}).
 * In zone mode, its clicks draw the zone of the page instead ({@link PageZoneTool}); sneak + right-click in the air
 * ends the mode, and so does putting it away (out of the hands).
 */
public class MiniGamePageItem extends Item {
    /** What the tooltip of a page shows under its name: the page's picture and summary (drawn by the client). */
    public record PageTooltip(UUID page) implements TooltipData {
    }

    /** Characters per line of its tooltip. */
    public static final int TOOLTIP_WIDTH = 40;

    public MiniGamePageItem(Settings settings) {
        super(settings);
    }

    @Override
    public TypedActionResult<ItemStack> use(World world, PlayerEntity player, Hand hand) {
        if (PageZoneTool.isInMode(player.getStackInHand(hand)) && player.isSneaking()) {
            if (!world.isClient) PageZoneTool.end(player, player.getStackInHand(hand));
            return TypedActionResult.success(player.getStackInHand(hand), world.isClient());
        }
        if (world.isClient) {
            return TypedActionResult.pass(player.getStackInHand(hand));
        }

        // Prevent opening the editor if the player is targeting a block or an entity
        if (isTargetingBlock(player)) {
            return TypedActionResult.pass(player.getStackInHand(hand));
        }

        if (player instanceof ServerPlayerEntity serverPlayer) {
            MiniGamePageNetworking.open(serverPlayer, hand);
        }

        return TypedActionResult.success(player.getStackInHand(hand), world.isClient());
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
        if (world.isClient || world.getServer() == null) return;
        // Zone mode lasts while the page is in a hand
        if (entity instanceof PlayerEntity player && player.getMainHandStack() != stack && player.getOffHandStack() != stack
                && PageZoneTool.end(null, stack)) return;
        if ((world.getTime() + slot) % 20 != 0) return;
        // Pages no longer keep positions of their own: the pipes are on the page's content
        if (stack.contains(ModComponents.DESTINATIONS_COMPONENT)) stack.remove(ModComponents.DESTINATIONS_COMPONENT);
        MiniGamePages.refresh(world.getServer(), stack);
    }

    @Override
    public Optional<TooltipData> getTooltipData(ItemStack stack) {
        UUID id = MiniGamePages.idOf(stack);
        return id == null ? Optional.empty() : Optional.of(new PageTooltip(id));
    }

    @Override
    public void appendTooltip(ItemStack stack, TooltipContext context, List<Text> tooltip, TooltipType type) {
        MiniGamePageRef ref = stack.get(ModComponents.MINI_GAME_PAGE);
        Tooltips tips = Tooltips.of(tooltip);
        if (ref != null && ref.linked()) tips.tags(Tooltips.Tag.LINKED_COPY);
        tips.more(more -> more
                .use(Tooltips.Keys.use(), ref == null ? "tooltip.steveparty.mini_game_page.blank" : "tooltip.steveparty.mini_game_page.open")
                .use(Tooltips.Keys.use(), "tooltip.steveparty.mini_game_page.link"));
        if (ref != null && type.isAdvanced()) {
            tooltip.add(Text.literal(ref.id().toString()).formatted(Tooltips.DIM));
        }
    }
}
