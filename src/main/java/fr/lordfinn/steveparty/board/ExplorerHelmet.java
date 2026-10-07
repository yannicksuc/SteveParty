package fr.lordfinn.steveparty.board;

import com.mojang.serialization.Codec;
import fr.lordfinn.steveparty.Steveparty;
import fr.lordfinn.steveparty.components.ModComponents;
import fr.lordfinn.steveparty.items.ModItems;
import fr.lordfinn.steveparty.items.custom.TileLinkerBrushItem;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.component.ComponentType;
import net.minecraft.entity.EquipmentSlot;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.network.PacketByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.text.Text;

/**
 * The Explorer's Helmet and who sees the board view. Worn with its headlamp lit, it shows the board view at all times
 * (the Tile Linker Brush only while held), with the details of each space. The lamp is the switch: its
 * state lives on the stack ({@link #LAMP}), so everyone sees it lit or not, and the server decides (the client's key
 * sends {@link ToggleLamp}).
 */
public final class ExplorerHelmet {
    /** The headlamp of a helmet: lit (the default) or not. */
    public static final ComponentType<Boolean> LAMP = ModComponents.registerComponent("explorer_helmet_lamp", Codec.BOOL);

    /** What a player sees of the board. */
    public enum View {
        /** Nothing. */
        NONE,
        /** The board view: links, step plates, warnings (a board tool in hand, or a lit helmet). */
        TOOL,
        /** The board view and the details of the spaces around and aimed at (a lit helmet). */
        HELMET;

        public boolean shown() {
            return this != NONE;
        }

        public boolean details() {
            return this == HELMET;
        }
    }

    private ExplorerHelmet() {
    }

    public static void initialize() {
        PayloadTypeRegistry.playC2S().register(ToggleLamp.ID, ToggleLamp.CODEC);
        ServerPlayNetworking.registerGlobalReceiver(ToggleLamp.ID, (payload, context) -> toggle(context.player()));
    }

    public static boolean isHelmet(ItemStack stack) {
        return stack.isOf(ModItems.EXPLORER_HELMET);
    }

    /** The helmet's lamp is lit (any other stack: false). */
    public static boolean lit(ItemStack stack) {
        return isHelmet(stack) && stack.getOrDefault(LAMP, true);
    }

    public static ItemStack helmet(LivingEntity entity) {
        return entity.getEquippedStack(EquipmentSlot.HEAD);
    }

    public static boolean wears(LivingEntity entity) {
        return isHelmet(helmet(entity));
    }

    /** The Tile Linker Brush in either hand. */
    public static boolean holdsBoardTool(PlayerEntity player) {
        return isBoardTool(player.getMainHandStack().getItem()) || isBoardTool(player.getOffHandStack().getItem());
    }

    private static boolean isBoardTool(Item item) {
        return item instanceof TileLinkerBrushItem;
    }

    /** What {@code player} sees: the details with a lit helmet on, the board view alone with a board tool in hand. */
    public static View view(PlayerEntity player) {
        if (lit(helmet(player))) return View.HELMET;
        return holdsBoardTool(player) ? View.TOOL : View.NONE;
    }

    /**
     * Switches the lamp of the helmet {@code player} wears (a click, and a word above the hotbar).
     *
     * @return false when no helmet is worn
     */
    public static boolean toggle(ServerPlayerEntity player) {
        ItemStack helmet = helmet(player);
        if (!isHelmet(helmet)) {
            player.sendMessage(Text.translatable("message.steveparty.explorer_helmet.not_worn"), true);
            return false;
        }
        boolean lit = switchLamp(helmet);
        player.getWorld().playSound(null, player.getX(), player.getEyeY(), player.getZ(), SoundEvents.BLOCK_LEVER_CLICK,
                SoundCategory.PLAYERS, 0.4f, lit ? 1.3f : 0.9f);
        player.sendMessage(Text.translatable(lit ? "message.steveparty.explorer_helmet.on" : "message.steveparty.explorer_helmet.off"), true);
        return true;
    }

    /** Switches the lamp of a helmet stack: lit, it goes off, and the other way round. @return lit now */
    public static boolean switchLamp(ItemStack helmet) {
        boolean lit = !lit(helmet);
        helmet.set(LAMP, lit);
        return lit;
    }

    /** Client → server: switch the lamp of the helmet worn. */
    public record ToggleLamp() implements CustomPayload {
        public static final Id<ToggleLamp> ID = new Id<>(Steveparty.id("explorer_helmet_toggle"));
        public static final PacketCodec<PacketByteBuf, ToggleLamp> CODEC = PacketCodec.unit(new ToggleLamp());

        @Override
        public Id<? extends CustomPayload> getId() {
            return ID;
        }
    }
}
