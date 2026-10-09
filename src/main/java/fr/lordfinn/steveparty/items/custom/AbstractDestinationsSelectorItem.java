package fr.lordfinn.steveparty.items.custom;

import fr.lordfinn.steveparty.blocks.custom.boardspaces.BoardSpaceDestination;
import fr.lordfinn.steveparty.blocks.custom.boardspaces.BoardSpaces;
import fr.lordfinn.steveparty.components.ModComponents;
import fr.lordfinn.steveparty.components.DestinationsComponent;
import fr.lordfinn.steveparty.items.custom.cartridges.CartridgeItem;
import fr.lordfinn.steveparty.sounds.ModSounds;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.entity.Entity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.ItemUsageContext;
import net.minecraft.item.tooltip.TooltipType;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvent;
import net.minecraft.text.MutableText;
import net.minecraft.text.Style;
import net.minecraft.text.Text;
import net.minecraft.util.ActionResult;
import net.minecraft.util.Formatting;
import net.minecraft.util.Hand;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;

import java.util.ArrayList;
import java.util.List;

import org.jetbrains.annotations.Nullable;

import static fr.lordfinn.steveparty.blocks.custom.boardspaces.BoardSpaceBlockEntity.getDestinationsStatus;
import static fr.lordfinn.steveparty.components.DestinationsComponent.DEFAULT;

public abstract class AbstractDestinationsSelectorItem extends Item {
    public AbstractDestinationsSelectorItem(Settings settings) {
        super(settings);
    }

    @Override
    public ActionResult useOnBlock(ItemUsageContext context) {
        if (context.getHand() == Hand.OFF_HAND) return ActionResult.PASS;
        return toggleDestination(context);
    }

    /** Adds the clicked block to the destinations, or removes it if it is one (whatever the hand). */
    protected ActionResult toggleDestination(ItemUsageContext context) {
        World world = context.getWorld();
        if (isClientWorld(world)) return ActionResult.PASS;

        PlayerEntity player = context.getPlayer();
        if (player == null) return ActionResult.PASS;

        // A part of a large tile stands for the tile
        BlockPos clickedPos = BoardSpaces.resolve(world, context.getBlockPos());

        ItemStack stack = context.getStack();
        ServerWorld serverWorld = (ServerWorld) world;

        DestinationsComponent component = getBoardSpaceBehaviorComponent(stack);

        if (isInvalidWorld(component, serverWorld, player)) return ActionResult.PASS;

        return addOrRemoveDestination(component, clickedPos, player, stack, serverWorld) == null ? ActionResult.PASS : ActionResult.SUCCESS;
    }

    public DestinationsComponent addOrRemoveDestination(DestinationsComponent component, BlockPos clickedPos, PlayerEntity player, ItemStack stack, ServerWorld serverWorld) {
        List<BlockPos> destinations = new ArrayList<>(component.destinations());
        updateDestinations(destinations, clickedPos, player);

        DestinationsComponent updatedComponent = new DestinationsComponent(destinations, getWorldName(serverWorld));
        // No destination left: no component at all, so the stack is the same as a new one again (it stacks with them)
        if (destinations.isEmpty()) stack.remove(ModComponents.DESTINATIONS_COMPONENT);
        else stack.set(ModComponents.DESTINATIONS_COMPONENT, updatedComponent);

        return updatedComponent;
    }

    private boolean isClientWorld(World world) {
        return world.isClient;
    }

    protected static String getWorldName(World world) {
        return world.getRegistryKey().getValue().toString();
    }

    /**
     * Whether its destinations are board spaces, a destination with none there any more being shown as such (with how
     * to fix it) in the tooltip.
     */
    protected boolean showsMissingDestinations() {
        return false;
    }

    private DestinationsComponent getBoardSpaceBehaviorComponent(ItemStack stack) {
        return stack.getOrDefault(ModComponents.DESTINATIONS_COMPONENT, DEFAULT);
    }

    private boolean isInvalidWorld(DestinationsComponent component, ServerWorld serverWorld, PlayerEntity player) {
        if (!component.world().isEmpty() && !getWorldName(serverWorld).equals(component.world())) {
            player.sendMessage(Text.translatable("message.steveparty.invalid_world"), true);
            return true;
        }
        return false;
    }

    private void updateDestinations(List<BlockPos> destinations, BlockPos clickedPos, PlayerEntity player) {
        BlockPos blockAbove = clickedPos.add(0,1,0);
        if (destinations.contains(clickedPos) || destinations.contains(blockAbove)) {
            destinations.remove(clickedPos);
            destinations.remove(blockAbove);
            player.sendMessage(Text.translatable("message.steveparty.removed_position", clickedPos.getX(), clickedPos.getY(), clickedPos.getZ()), true);
            playCancelSound(clickedPos, player);
        } else {
            destinations.add(clickedPos);
            player.sendMessage(Text.translatable("message.steveparty.added_position", clickedPos.getX(), clickedPos.getY(), clickedPos.getZ()), true);
            playSelectSound(clickedPos, player);
        }
    }

    protected static void playSelectSound(BlockPos clickedPos, PlayerEntity player) {
        ModSounds.playSelect(player.getWorld(), clickedPos);
    }

    protected static void playCancelSound(BlockPos clickedPos, PlayerEntity player) {
        ModSounds.playCancel(player.getWorld(), clickedPos);
    }

    protected static void playSound(BlockPos clickedPos, PlayerEntity player, SoundEvent category) {
        player.getWorld().playSound(null, clickedPos, category, SoundCategory.BLOCKS, 1.0F, 1.0F);
    }

    @Environment(EnvType.CLIENT)
    @Override
    public void appendTooltip(ItemStack stack, TooltipContext context, List<Text> tooltip, TooltipType type) {
        DestinationsComponent component = getBoardSpaceBehaviorComponent(stack);
        Entity holder = stack.getHolder();
        List<BoardSpaceDestination> tileDestinations =
                getDestinationsStatus(component.destinations(), holder == null ? null : holder.getWorld());

        if (!tileDestinations.isEmpty()) {
            addTooltipHeading(tooltip, component);
            addDestinationsToTooltip(tooltip, tileDestinations, component, holder == null ? null : holder.getWorld());
        } else {
            addNoDestinationsMessage(tooltip);
        }
    }

    protected void addTooltipHeading(List<Text> tooltip, DestinationsComponent component) {
        tooltip.add(Text.translatable("tooltip.steveparty.bound_to")
                .setStyle(Style.EMPTY.withColor(0xb91e8c).withBold(true))
                .append(Text.literal(component.world())
                        .setStyle(Style.EMPTY.withColor(Formatting.WHITE))));
        tooltip.add(Text.translatable("tooltip.steveparty.destinations")
                .setStyle(Style.EMPTY.withColor(0xEA528E).withBold(true)));
    }

    /**
     * The destinations, one per line; one where no board space is any more (for {@link #showsMissingDestinations()}) in
     * red, followed by how to fix it.
     */
    protected void addDestinationsToTooltip(List<Text> tooltip, List<BoardSpaceDestination> tileDestinations,
                                            DestinationsComponent component, @Nullable World world) {
        boolean missing = false;
        for (BoardSpaceDestination destination : tileDestinations) {
            BlockPos pos = destination.position();
            // Only where the client knows the world (same dimension, chunk loaded): never a false alarm
            boolean gone = showsMissingDestinations() && !destination.isTile() && world != null
                    && getWorldName(world).equals(component.world()) && world.isChunkLoaded(pos);
            MutableText entry = Text.translatable("tooltip.steveparty.destination_entry", pos.getX(), pos.getY(), pos.getZ())
                    .setStyle(Style.EMPTY.withColor(gone ? Formatting.RED : Formatting.WHITE));
            if (gone) entry.append(Text.translatable("tooltip.steveparty.destination_missing").formatted(Formatting.RED));
            tooltip.add(entry);
            missing |= gone;
        }
        if (missing) {
            CartridgeItem.addWrapped(tooltip,
                    Text.translatable("tooltip.steveparty.destination_missing.hint"), Formatting.GOLD);
        }
    }

    protected void addNoDestinationsMessage(List<Text> tooltip) {
        tooltip.add(Text.translatable("tooltip.steveparty.no_destinations")
                .setStyle(Style.EMPTY.withColor(Formatting.RED).withItalic(true)));
    }
}
