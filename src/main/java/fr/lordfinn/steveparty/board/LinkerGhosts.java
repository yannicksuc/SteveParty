package fr.lordfinn.steveparty.board;

import fr.lordfinn.steveparty.components.DestinationsComponent;
import fr.lordfinn.steveparty.components.ModComponents;
import fr.lordfinn.steveparty.items.LinkTags;
import fr.lordfinn.steveparty.items.custom.AbstractDestinationsSelectorItem;
import fr.lordfinn.steveparty.items.custom.ShopkeeperKeyItem;
import fr.lordfinn.steveparty.items.custom.WrenchItem;
import fr.lordfinn.steveparty.items.custom.cartridges.CartridgeContainers;
import fr.lordfinn.steveparty.items.custom.cartridges.CartridgeSpawnMarker;
import fr.lordfinn.steveparty.persistent_state.VendorLinkPersistentState;
import fr.lordfinn.steveparty.sounds.ModSounds;
import net.fabricmc.fabric.api.event.player.UseBlockCallback;
import net.fabricmc.fabric.api.event.player.UseItemCallback;
import net.minecraft.util.ActionResult;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.text.Text;
import net.minecraft.util.Hand;
import net.minecraft.util.TypedActionResult;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.GlobalPos;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.RaycastContext;
import net.minecraft.world.World;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * The ghosts of the « [Linker] » items (see {@link LinkTags}) other than the Tile Linker Brush (which has its own, see
 * {@link BrushAim}): the blocks a held item links that are gone (nothing to click where they stood: air, water...),
 * still highlighted. A right click aimed at that cell, up to {@link #REACH} blocks away and before any block, forgets
 * that link, as a click on the block would have: a cartridge's destination, container or Spawn Bubble, a block of a
 * Shopkeeper Key's shop, a Mini-game Remote's controller. (A Mini-game Page forgets its links to gone blocks by itself.)
 * Decided the same way on both sides (the held stack and the world say it all), so the client neither opens the item's
 * menu nor mispredicts.
 */
public final class LinkerGhosts {
    /** Ghosts are aimed at this far, as the brush's. */
    public static final double REACH = WrenchActions.LONG_REACH;

    private LinkerGhosts() {
    }

    public static void initialize() {
        UseItemCallback.EVENT.register((player, world, hand) -> {
            ItemStack stack = player.getStackInHand(hand);
            return use(player, world, hand) ? TypedActionResult.success(stack, world.isClient) : TypedActionResult.pass(stack);
        });
        // The look crossing a ghost on its way to a block (the ground under a gone tile): the ghost, not the block
        UseBlockCallback.EVENT.register((player, world, hand, hit) -> use(player, world, hand) ? ActionResult.SUCCESS : ActionResult.PASS);
    }

    /**
     * A right click of {@code player} with the item in {@code hand}: if it aims at a ghost of that item, the link is
     * forgotten (server side) and the click taken (both sides).
     */
    public static boolean use(PlayerEntity player, World world, Hand hand) {
        if (hand != Hand.MAIN_HAND || player.isSpectator()) return false;
        ItemStack stack = player.getStackInHand(hand);
        BlockPos aimed = aimed(player, world, ghosts(world, stack));
        if (aimed == null) return false;
        if (player instanceof ServerPlayerEntity serverPlayer) forget(serverPlayer, (ServerWorld) world, stack, aimed);
        return true;
    }

    /** The links of the linker {@code stack} (not the brush) to blocks gone in {@code world}, loaded chunks only. */
    public static List<BlockPos> ghosts(World world, ItemStack stack) {
        if (!LinkTags.isLinker(stack) || TileLinkerBrush.isBrush(stack)) return List.of();
        List<BlockPos> ghosts = new ArrayList<>();
        if (stack.getItem() instanceof AbstractDestinationsSelectorItem && !(stack.getItem() instanceof WrenchItem)) {
            DestinationsComponent destinations = stack.get(ModComponents.DESTINATIONS_COMPONENT);
            if (destinations != null && (destinations.world().isEmpty() || destinations.world().equals(BoardLinks.worldName(world)))) {
                for (BlockPos pos : destinations.destinations()) if (gone(world, pos)) ghosts.add(pos);
            }
        }
        if (CartridgeContainers.linksContainers(stack)) {
            for (BlockPos pos : CartridgeContainers.in(stack, world)) if (gone(world, pos) && !ghosts.contains(pos)) ghosts.add(pos);
        }
        if (CartridgeSpawnMarker.spawnsMobs(stack)) {
            BlockPos marker = CartridgeSpawnMarker.linked(stack, world);
            if (marker != null && gone(world, marker) && !ghosts.contains(marker)) ghosts.add(marker);
        }
        GlobalPos remote = stack.get(ModComponents.MINI_GAME_REMOTE_LINK);
        if (remote != null && remote.dimension().equals(world.getRegistryKey()) && gone(world, remote.pos())) ghosts.add(remote.pos());
        return ghosts;
    }

    /** Nothing to click at {@code pos} any more (its chunk loaded: an unloaded block may still be there). */
    @SuppressWarnings("deprecation") // isChunkLoaded(BlockPos): never loads it
    static boolean gone(World world, BlockPos pos) {
        return world.isChunkLoaded(pos) && world.getBlockState(pos).getOutlineShape(world, pos).isEmpty();
    }

    /** The ghost of {@code ghosts} the look of {@code player} meets first, before any block, or null. */
    public static @Nullable BlockPos aimed(PlayerEntity player, World world, List<BlockPos> ghosts) {
        if (ghosts.isEmpty()) return null;
        Vec3d eye = player.getEyePos(), end = eye.add(player.getRotationVector().multiply(REACH));
        HitResult hit = world.raycast(new RaycastContext(eye, end, RaycastContext.ShapeType.OUTLINE, RaycastContext.FluidHandling.NONE, player));
        double limit = hit.getType() == HitResult.Type.MISS ? REACH * REACH : hit.getPos().squaredDistanceTo(eye) + 1.0E-4;
        BlockPos best = null;
        double bestDistance = limit;
        for (BlockPos pos : ghosts) {
            Optional<Vec3d> at = new Box(pos).raycast(eye, end);
            if (at.isEmpty()) continue;
            double distance = at.get().squaredDistanceTo(eye);
            if (distance <= bestDistance) {
                best = pos;
                bestDistance = distance;
            }
        }
        return best;
    }

    /** Forgets the link of {@code stack} to the ghost at {@code pos}, as a click on its block would have. */
    static void forget(ServerPlayerEntity player, ServerWorld world, ItemStack stack, BlockPos pos) {
        boolean forgotten = false;
        DestinationsComponent destinations = stack.get(ModComponents.DESTINATIONS_COMPONENT);
        if (stack.getItem() instanceof ShopkeeperKeyItem) {
            UUID vendor = stack.get(ModComponents.SHOPKEEPER_UUID);
            VendorLinkPersistentState links = VendorLinkPersistentState.get(world.getServer());
            GlobalPos at = GlobalPos.create(world.getRegistryKey(), pos);
            if (vendor != null && links != null && links.isBlockLinkedToVendor(vendor, at) && links.claimOrCheckOwner(vendor, player.getUuid())) {
                links.toggleLink(vendor, at);
                ShopkeeperKeyItem.refreshDestinations(stack, world);
                forgotten = true;
            }
        } else if (stack.getItem() instanceof AbstractDestinationsSelectorItem item && destinations != null
                && destinations.destinations().contains(pos)) {
            // Says « removed » and plays its sound
            item.addOrRemoveDestination(destinations, pos, player, stack, world);
            return;
        }
        if (!forgotten && CartridgeContainers.linksContainers(stack) && CartridgeContainers.in(stack, world).contains(pos)) {
            CartridgeContainers.toggle(stack, world, pos);
            forgotten = true;
        }
        if (!forgotten && CartridgeSpawnMarker.spawnsMobs(stack) && pos.equals(CartridgeSpawnMarker.linked(stack, world))) {
            CartridgeSpawnMarker.set(stack, world, null);
            forgotten = true;
        }
        GlobalPos remote = stack.get(ModComponents.MINI_GAME_REMOTE_LINK);
        if (!forgotten && remote != null && remote.pos().equals(pos) && remote.dimension().equals(world.getRegistryKey())) {
            stack.remove(ModComponents.MINI_GAME_REMOTE_LINK);
            forgotten = true;
        }
        if (!forgotten) return;
        player.sendMessage(Text.translatable("message.steveparty.removed_position", pos.getX(), pos.getY(), pos.getZ()), true);
        ModSounds.playCancel(world, pos);
    }
}
