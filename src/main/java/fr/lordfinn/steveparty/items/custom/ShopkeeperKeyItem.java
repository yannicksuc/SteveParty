package fr.lordfinn.steveparty.items.custom;

import fr.lordfinn.steveparty.blocks.custom.TradingStallBlockEntity;
import fr.lordfinn.steveparty.components.DestinationsComponent;
import fr.lordfinn.steveparty.components.ModComponents;
import fr.lordfinn.steveparty.entities.custom.HidingTraderEntity;
import fr.lordfinn.steveparty.persistent_state.TraderStallRegistry;
import fr.lordfinn.steveparty.persistent_state.VendorLinkPersistentState;
import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.ItemUsageContext;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.text.Text;
import net.minecraft.util.ActionResult;
import net.minecraft.util.Hand;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.GlobalPos;
import net.minecraft.world.World;

import java.util.*;

public class ShopkeeperKeyItem extends AbstractDestinationsSelectorItem {
    /** Ticks between two refreshes of the displayed destinations from the persistent link state. */
    private static final int DISPLAY_REFRESH_INTERVAL = 10;

    public ShopkeeperKeyItem(Settings settings) {
        super(settings);
    }

    @Override
    public ActionResult useOnEntity(ItemStack stack, net.minecraft.entity.player.PlayerEntity user, LivingEntity entity, Hand hand) {
        if (user.getWorld().isClient) return ActionResult.SUCCESS;
        ItemStack realStack = user.getStackInHand(hand);
        return useKey(realStack, user, entity, hand);
    }

    private ActionResult useKey(ItemStack stack, net.minecraft.entity.player.PlayerEntity user, LivingEntity entity, Hand hand) {
        UUID uuid = entity.getUuid();
        if (stack.contains(ModComponents.SHOPKEEPER_UUID)) {
            UUID storedUuid = stack.get(ModComponents.SHOPKEEPER_UUID);
            if (uuid.equals(storedUuid)) {
                stack.remove(ModComponents.SHOPKEEPER_UUID);
                user.sendMessage(Text.translatable("message.steveparty.shopkeeper_key.unlinked"), false);
                stack.set(ModComponents.DESTINATIONS_COMPONENT, DestinationsComponent.DEFAULT);
                return ActionResult.SUCCESS;
            }
        }

        if (entity instanceof HidingTraderEntity) {
            stack.set(ModComponents.SHOPKEEPER_UUID, uuid);
            // The destinations shown by the key are derived from the persistent link state
            refreshDestinations(stack, (ServerWorld) entity.getWorld());
            user.sendMessage(Text.translatable("message.steveparty.shopkeeper_key.linked", entity.getDisplayName()), false);
            return ActionResult.SUCCESS;
        }
        return ActionResult.PASS;
    }

    @Override
    public boolean hasGlint(ItemStack stack) {
        return stack.contains(ModComponents.SHOPKEEPER_UUID) || super.hasGlint(stack);
    }

    @Override
    public ActionResult useOnBlock(ItemUsageContext context) {
        World world = context.getWorld();
        ItemStack stack = context.getStack();
        if (world.isClient) {
            return ActionResult.SUCCESS;
        }

        PlayerEntity player = context.getPlayer();
        if (player == null) return ActionResult.PASS;

        if (!stack.contains(ModComponents.SHOPKEEPER_UUID)) {
            player.sendMessage(Text.translatable("message.steveparty.shopkeeper_key.not_linked"), false);
            return ActionResult.PASS;
        }

        BlockPos pos = context.getBlockPos();
        UUID vendorId = stack.get(ModComponents.SHOPKEEPER_UUID);

        MinecraftServer server = world.getServer();
        if (server == null) return ActionResult.PASS;

        VendorLinkPersistentState vendorLinks = VendorLinkPersistentState.get(server);
        if (vendorLinks == null) return ActionResult.PASS;

        // The persistent state is the single source of truth: toggle it, then derive the key display from it
        // (two keys linked to the same trader can no longer disagree).
        boolean linked = vendorLinks.toggleLink(vendorId, GlobalPos.create(world.getRegistryKey(), pos));
        if (linked) {
            player.sendMessage(Text.translatable("message.steveparty.shopkeeper_key.linked_block", pos.toShortString(), vendorId.toString()), true);
            playSelectSound(pos, player);
        } else {
            player.sendMessage(Text.translatable("message.steveparty.shopkeeper_key.unlinked_block", pos.toShortString(), vendorId.toString()), true);
            playCancelSound(pos, player);
        }
        refreshDestinations(stack, (ServerWorld) world);

        return ActionResult.SUCCESS;
    }

    @Override
    public void inventoryTick(ItemStack stack, World world, Entity entity, int slot, boolean selected) {
        super.inventoryTick(stack, world, entity, slot, selected);
        // Keep every key in sync with the persistent links (another key may have changed them)
        if (world instanceof ServerWorld serverWorld && world.getTime() % DISPLAY_REFRESH_INTERVAL == 0) {
            refreshDestinations(stack, serverWorld);
        }
    }

    /**
     * Rebuilds the destinations displayed by the key from {@link VendorLinkPersistentState}: the blocks linked to
     * the key's trader in the given world. The component is only rewritten when it actually changes.
     */
    public static void refreshDestinations(ItemStack stack, ServerWorld world) {
        DestinationsComponent current = stack.get(ModComponents.DESTINATIONS_COMPONENT);
        UUID vendorId = stack.get(ModComponents.SHOPKEEPER_UUID);
        DestinationsComponent updated;
        if (vendorId == null) {
            updated = DestinationsComponent.DEFAULT;
        } else {
            VendorLinkPersistentState state = VendorLinkPersistentState.get(world.getServer());
            if (state == null) return;
            List<BlockPos> positions = new ArrayList<>(state.getLinkedPositionsIn(vendorId, world.getRegistryKey()));
            positions.sort(Comparator.naturalOrder());
            updated = new DestinationsComponent(positions, getWorldName(world));
        }
        if (current == null ? !updated.equals(DestinationsComponent.DEFAULT) : !current.equals(updated)) {
            stack.set(ModComponents.DESTINATIONS_COMPONENT, updated);
        }
    }

    /** Trader UUIDs of the linked Shopkeeper Keys held in either hand. */
    private static Set<UUID> getHeldKeyVendors(PlayerEntity player) {
        Set<UUID> vendors = new HashSet<>();
        for (Hand hand : Hand.values()) {
            ItemStack held = player.getStackInHand(hand);
            if (held.getItem() instanceof ShopkeeperKeyItem && held.contains(ModComponents.SHOPKEEPER_UUID)) {
                vendors.add(held.get(ModComponents.SHOPKEEPER_UUID));
            }
        }
        return vendors;
    }

    private static boolean holdsKey(PlayerEntity player) {
        for (Hand hand : Hand.values()) {
            if (player.getStackInHand(hand).getItem() instanceof ShopkeeperKeyItem) return true;
        }
        return false;
    }

    /**
     * Server-side access rule of the shop GUIs (Trading Stall, Cash Register).
     * <ul>
     *     <li>creative players and operators (permission level 2+) always have access;</li>
     *     <li>a block linked to one or more traders only opens for a player holding (either hand) a Shopkeeper
     *     Key linked to one of these traders;</li>
     *     <li>a block linked to no trader yet opens for a player holding any Shopkeeper Key, so a shop can be set up.</li>
     * </ul>
     * Sends an action-bar message to the player when the access is denied.
     */
    public static boolean canOpenShopBlock(PlayerEntity player, World world, BlockPos pos) {
        if (player.isCreative() || player.hasPermissionLevel(2)) return true;

        Set<UUID> owners = new HashSet<>();
        VendorLinkPersistentState state = VendorLinkPersistentState.get(world.getServer());
        if (state != null) {
            owners.addAll(state.getVendorsLinkedTo(GlobalPos.create(world.getRegistryKey(), pos)));
        }
        if (world.getBlockEntity(pos) instanceof TradingStallBlockEntity) {
            // Runtime links made by the stall's key block (slot 27)
            owners.addAll(TraderStallRegistry.getLinkedTraders(pos));
        }

        if (owners.isEmpty()) {
            if (holdsKey(player)) return true;
            player.sendMessage(Text.translatableWithFallback("message.steveparty.shop.no_owner_access_denied",
                    "This shop isn't linked to a trader yet: hold a Shopkeeper Key to set it up."), true);
            return false;
        }

        for (UUID vendorId : getHeldKeyVendors(player)) {
            if (owners.contains(vendorId)) return true;
        }
        player.sendMessage(Text.translatableWithFallback("message.steveparty.shop.access_denied",
                "Only a Shopkeeper Key linked to this shop's trader can open it."), true);
        return false;
    }
}
