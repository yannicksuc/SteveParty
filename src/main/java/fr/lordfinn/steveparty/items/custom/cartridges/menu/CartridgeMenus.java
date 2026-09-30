package fr.lordfinn.steveparty.items.custom.cartridges.menu;

import fr.lordfinn.steveparty.items.custom.cartridges.CartridgeItem;
import fr.lordfinn.steveparty.screen_handlers.custom.CartridgeScreenHandler;
import fr.lordfinn.steveparty.sounds.ModSounds;
import net.fabricmc.fabric.api.screenhandler.v1.ExtendedScreenHandlerFactory;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.item.ItemStack;
import net.minecraft.screen.ScreenHandler;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.sound.SoundCategory;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.Hand;
import net.minecraft.util.math.BlockPos;
import org.jetbrains.annotations.Nullable;

import java.util.List;

/**
 * Opening a cartridge's menu and applying its changes (server side).
 * <ul>
 *     <li>{@link #openInHand}: right click in the air with a cartridge;</li>
 *     <li>{@link #openForContainer}: the cartridge in slot {@code slot} of any block holding cartridges (the hook for
 *     blocks other than the tiles, whose own interface shows the menu of the slot chosen);</li>
 *     <li>{@link #apply}: a change from a menu, checked (who, where, which module, which value) then written.</li>
 * </ul>
 */
public final class CartridgeMenus {
    private CartridgeMenus() {}

    public static @Nullable CartridgeItem cartridge(ItemStack stack) {
        return stack.getItem() instanceof CartridgeItem cartridge ? cartridge : null;
    }

    public static List<CartridgeModule> modules(ItemStack stack) {
        CartridgeItem cartridge = cartridge(stack);
        return cartridge == null ? List.of() : cartridge.modules();
    }

    /** The module {@code id} of {@code stack}'s cartridge, or null. */
    public static @Nullable CartridgeModule module(ItemStack stack, String id) {
        for (CartridgeModule module : modules(stack)) {
            if (module.id().equals(id)) return module;
        }
        return null;
    }

    public static void openInHand(ServerPlayerEntity player, Hand hand) {
        open(player, CartridgeRef.hand(hand));
    }

    /** Opens the menu of the cartridge in {@code slot} of the block at {@code pos}, if {@code player} is in reach. */
    public static void openForContainer(ServerPlayerEntity player, BlockPos pos, int slot) {
        open(player, CartridgeRef.slot(pos, slot));
    }

    public static boolean open(ServerPlayerEntity player, CartridgeRef ref) {
        ItemStack stack = ref.resolve(player);
        if (cartridge(stack) == null || !ref.inReach(player)) return false;
        if (!ref.inHand() && !ref.mayEdit(player)) {
            player.sendMessage(Text.translatable("message.steveparty.cartridge_menu.not_allowed").formatted(Formatting.RED), true);
            return false;
        }
        Text title = stack.getName();
        player.openHandledScreen(new ExtendedScreenHandlerFactory<CartridgeRef>() {
            @Override
            public CartridgeRef getScreenOpeningData(ServerPlayerEntity serverPlayer) {
                return ref;
            }

            @Override
            public Text getDisplayName() {
                return title;
            }

            @Override
            public ScreenHandler createMenu(int syncId, PlayerInventory playerInventory, PlayerEntity playerEntity) {
                return new CartridgeScreenHandler(syncId, playerInventory, ref);
            }
        });
        player.getWorld().playSound(null, player.getBlockPos(), ModSounds.OPEN_TILE_GUI_SOUND_EVENT, SoundCategory.BLOCKS, 1.0F, 1.0F);
        return true;
    }

    /**
     * A change from a menu: module {@code moduleId} of the cartridge at {@code ref} takes {@code value}, if
     * {@code player} may edit it there, the module exists, can be changed now and accepts the value.
     *
     * @return true if written (and the block saved and sent)
     */
    public static boolean apply(ServerPlayerEntity player, CartridgeRef ref, String moduleId, int value) {
        if (!ref.mayEdit(player)) return false;
        ItemStack stack = ref.resolve(player);
        if (stack.isEmpty() || cartridge(stack) == null) return false;
        CartridgeModule module = module(stack, moduleId);
        if (module == null || !module.editable() || !module.enabled(stack) || !module.accepts(stack, value)) return false;
        module.set(new CartridgeEdit(stack, ref.holder(player.getWorld()), ref.index()), value);
        ref.commit(player.getWorld());
        return true;
    }
}
