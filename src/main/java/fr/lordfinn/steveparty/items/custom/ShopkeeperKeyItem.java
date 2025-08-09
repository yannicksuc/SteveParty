package fr.lordfinn.steveparty.items.custom;

import fr.lordfinn.steveparty.Steveparty;
import fr.lordfinn.steveparty.components.DestinationsComponent;
import fr.lordfinn.steveparty.components.ModComponents;
import fr.lordfinn.steveparty.entities.custom.HidingTraderEntity;
import fr.lordfinn.steveparty.persistent_state.VendorLinkPersistentState;
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
import net.minecraft.world.World;

import java.util.Collection;
import java.util.UUID;

public class ShopkeeperKeyItem extends AbstractDestinationsSelectorItem {

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
            VendorLinkPersistentState vendorLinkPersistentState = VendorLinkPersistentState.get(user.getServer());
            if (vendorLinkPersistentState == null) return ActionResult.PASS;
            Collection<BlockPos> vendorLinks = vendorLinkPersistentState.getVendorLinks(uuid);
            DestinationsComponent component = DestinationsComponent.DEFAULT;
            for (BlockPos pos : vendorLinks) {
                component = addOrRemoveDestination(component, pos, user, stack, (ServerWorld) entity.getWorld());
            }
            stack.set(ModComponents.DESTINATIONS_COMPONENT, component);
            stack.set(ModComponents.SHOPKEEPER_UUID, uuid);
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

        boolean wasLinked = vendorLinks.isBlockLinkedToVendor(vendorId, pos);
        if (wasLinked) {
            vendorLinks.unlinkBlock(vendorId, pos);
            player.sendMessage(Text.translatable("message.steveparty.shopkeeper_key.unlinked_block", pos.toShortString(), vendorId.toString()), true);
        } else {
            vendorLinks.linkBlock(vendorId, pos);
            player.sendMessage(Text.translatable("message.steveparty.shopkeeper_key.linked_block", pos.toShortString(), vendorId.toString()), true);
        }
        stack.set(ModComponents.DESTINATIONS_COMPONENT,
                addOrRemoveDestination(
                        stack.getOrDefault(ModComponents.DESTINATIONS_COMPONENT, DestinationsComponent.DEFAULT),
                        pos, player, stack, (ServerWorld) world)
        );

        return ActionResult.SUCCESS;
    }


}
