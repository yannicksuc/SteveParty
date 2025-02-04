package fr.lordfinn.steveparty.items.custom.teleportation_books;

import fr.lordfinn.steveparty.items.custom.AbstractDestinationsSelectorItem;
import fr.lordfinn.steveparty.payloads.HereWeGoBookPayload;
import fr.lordfinn.steveparty.screen_handlers.TeleportationBookScreenHandler;
import fr.lordfinn.steveparty.utils.RaycastUtils;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.ItemUsageContext;
import net.minecraft.item.tooltip.TooltipType;
import net.minecraft.screen.NamedScreenHandlerFactory;
import net.minecraft.screen.ScreenHandler;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;
import net.minecraft.util.ActionResult;
import net.minecraft.util.Hand;
import net.minecraft.world.World;

import java.util.List;

import static fr.lordfinn.steveparty.components.ModComponents.STATE;

public abstract class AbstractTeleportationBookItem extends AbstractDestinationsSelectorItem {
    public AbstractTeleportationBookItem(Settings settings) {
        super(settings);
    }

    @Override
    public void appendTooltip(ItemStack stack, TooltipContext context, List<Text> tooltip, TooltipType type) {
        int state = stack.getOrDefault(STATE, 0);
        if (state == 2) {
            super.appendTooltip(stack, context, tooltip, type);
        }
    }

    @Override
    public ActionResult useOnBlock(ItemUsageContext context) {
        int state = context.getStack().getOrDefault(STATE, 0);
        if (state == 2) {
            return super.useOnBlock(context);
        }
        return ActionResult.PASS;
    }

    @Override
    public ActionResult use(World world, PlayerEntity user, Hand hand) {
        if (!world.isClient && hand == Hand.MAIN_HAND) {
            if (RaycastUtils.isTargetingBlock(user)) {
                return ActionResult.PASS;
            }
            Text displayName = this.getName();
            user.openHandledScreen(new NamedScreenHandlerFactory() {
                @Override
                public Text getDisplayName() {
                    return displayName;
                }

                @Override
                public ScreenHandler createMenu(int syncId, PlayerInventory inv, PlayerEntity player) {
                    return new TeleportationBookScreenHandler(syncId, inv);
                }
            });
        }
        return ActionResult.SUCCESS;
    }

    public static void handleHereWeGoBookPayload(ServerPlayerEntity player, int newState) {
        ItemStack bookStack = player.getMainHandStack();

        // Vérifie que le joueur tient bien le bon livre
        if (bookStack.getItem() instanceof AbstractTeleportationBookItem) {
            bookStack.set(STATE, newState);
            player.getInventory().markDirty(); // Mettre à jour l'inventaire
        }
    }
}
