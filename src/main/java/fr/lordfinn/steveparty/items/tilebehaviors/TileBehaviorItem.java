package fr.lordfinn.steveparty.items.tilebehaviors;

import fr.lordfinn.steveparty.blocks.tiles.TileDestination;
import fr.lordfinn.steveparty.blocks.tiles.TileService;
import fr.lordfinn.steveparty.components.ModComponents;
import fr.lordfinn.steveparty.components.TileBehaviorComponent;
import fr.lordfinn.steveparty.items.TileOpener;
import fr.lordfinn.steveparty.sounds.ModSounds;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.ItemUsageContext;
import net.minecraft.item.tooltip.TooltipType;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.text.Style;
import net.minecraft.text.Text;
import net.minecraft.util.ActionResult;
import net.minecraft.util.Formatting;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;

import java.util.ArrayList;
import java.util.List;

import static fr.lordfinn.steveparty.blocks.tiles.TileEntity.getDestinationsStatus;
import static fr.lordfinn.steveparty.components.TileBehaviorComponent.DEFAULT_TILE_BEHAVIOR;
import static fr.lordfinn.steveparty.particles.ModParticles.HERE_PARTICLE;

public class TileBehaviorItem extends Item implements TileOpener {
    private long lastTimeItemHoldParticleUpdate = 0;
    public TileBehaviorItem(Settings settings) {
        super(settings);
    }

    @Override
    public ActionResult useOnBlock(ItemUsageContext context) {
        World world = context.getWorld();
        if (world.isClient) return ActionResult.SUCCESS;

        PlayerEntity player = context.getPlayer();
        if (player == null) return ActionResult.PASS;

        BlockPos clickedPos = context.getBlockPos();
        BlockPos blockAbove = clickedPos.up();

        ItemStack stack = context.getStack();
        if (!(world instanceof ServerWorld serverWorld)) return ActionResult.PASS;

        // Get or create the TileBehaviorComponent
        TileBehaviorComponent component = stack.getOrDefault(ModComponents.TILE_BEHAVIOR_COMPONENT, DEFAULT_TILE_BEHAVIOR);
        String worldName = serverWorld.getRegistryKey().getValue().toString(); // Get current world as a string
        List<BlockPos> destinations = new ArrayList<>(component.destinations());

        if (!destinations.isEmpty() && !component.world().isEmpty() && !worldName.equals(component.world())) {
            player.sendMessage(Text.translatable("message.steveparty.invalid_world"), true);
            return ActionResult.PASS;
        }

        if (destinations.contains(clickedPos) || destinations.contains(blockAbove)) {
            // Remove the block position if already exists
            destinations.remove(clickedPos);
            destinations.remove(blockAbove);
            player.sendMessage(Text.translatable("message.steveparty.removed_position", clickedPos.getX(), clickedPos.getY(), clickedPos.getZ()), true);
            player.getWorld().playSound(null, clickedPos, ModSounds.CANCEL_SOUND_EVENT, SoundCategory.BLOCKS, 1.0F, 1.0F);
        } else {
            // Add the block position
            destinations.add(clickedPos);
            player.sendMessage(Text.translatable("message.steveparty.added_position", clickedPos.getX(), clickedPos.getY(), clickedPos.getZ()), true);
            player.getWorld().playSound(null, clickedPos, ModSounds.SELECT_SOUND_EVENT, SoundCategory.BLOCKS, 1.0F, 1.0F);
        }

        TileBehaviorComponent updatedComponent = new TileBehaviorComponent(destinations, component.tileType(), worldName);
        stack.set(ModComponents.TILE_BEHAVIOR_COMPONENT, updatedComponent);
        return ActionResult.SUCCESS;
    }

    @Environment(EnvType.CLIENT)
    @Override
    public void appendTooltip(ItemStack stack, TooltipContext context, List<Text> tooltip, TooltipType type) {
        TileBehaviorComponent component = stack.getOrDefault(ModComponents.TILE_BEHAVIOR_COMPONENT, DEFAULT_TILE_BEHAVIOR);
        Entity holder = stack.getHolder();
        List<TileDestination> tileDestinations = getDestinationsStatus(component.destinations(), holder == null ? null : holder.getWorld());

        if (!tileDestinations.isEmpty()) {
            // Add a heading for the destinations with a different color
            tooltip.add(Text.literal("Bound to: ").setStyle(Style.EMPTY.withColor(Formatting.AQUA).withBold(true))
                    .append(Text.literal(component.world()).setStyle(Style.EMPTY.withColor(Formatting.WHITE).withBold(false))));
            tooltip.add(Text.literal("Destinations:")
                    .setStyle(Style.EMPTY.withColor(Formatting.AQUA).withBold(true)));

            for (TileDestination destination : tileDestinations) {
                // Format each BlockPos with its coordinates in a distinct color
                BlockPos pos = destination.position();
                tooltip.add(Text.literal(String.format("  - (%d, %d, %d)", pos.getX(), pos.getY(), pos.getZ()))
                        .setStyle(Style.EMPTY.withColor(Formatting.WHITE)));
            }
        } else {
            // Display a message when there are no destinations
            tooltip.add(Text.literal("No destinations set.")
                    .setStyle(Style.EMPTY.withColor(Formatting.RED).withItalic(true)));
        }
    }

    @Override
    public void inventoryTick(ItemStack stack, World world, Entity entity, int slot, boolean selected) {
        if (!selected)
            return;
        if (!(entity instanceof LivingEntity))
            return;
        if (lastTimeItemHoldParticleUpdate + 150 > System.currentTimeMillis())
            return;
        lastTimeItemHoldParticleUpdate = System.currentTimeMillis();
        TileBehaviorComponent component = stack.getOrDefault(ModComponents.TILE_BEHAVIOR_COMPONENT, DEFAULT_TILE_BEHAVIOR);
        List<BlockPos> destinations = component.destinations();
        if (!destinations.isEmpty()) {
            for (BlockPos pos : destinations) {
                world.addParticle(HERE_PARTICLE,
                        pos.getX() + 0.5, pos.getY() + 2, pos.getZ() + 0.5,
                        0.0, 0.0, 0.0);
            }
        }
    }
}
