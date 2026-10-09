package fr.lordfinn.steveparty.payloads.custom;

import fr.lordfinn.steveparty.Steveparty;
import fr.lordfinn.steveparty.components.ModComponents;
import fr.lordfinn.steveparty.components.TrapSetupComponent;
import fr.lordfinn.steveparty.items.custom.TrapPowerUpItem;
import fr.lordfinn.steveparty.payloads.ServerboundPayload;
import fr.lordfinn.steveparty.powerups.effects.TrapKind;
import net.minecraft.item.ItemStack;
import net.minecraft.network.RegistryByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.codec.PacketCodecs;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.Hand;

/** The payloads of the Trap's setup screen: opened by the server on an unsigned Trap, signed by the client. */
public final class TrapSetupPayloads {
    private TrapSetupPayloads() {
    }

    private static final PacketCodec<RegistryByteBuf, Hand> HAND = PacketCodecs.BOOL.xmap(
            main -> main ? Hand.MAIN_HAND : Hand.OFF_HAND, hand -> hand == Hand.MAIN_HAND).cast();

    /** S2C: opens the setup screen of the unsigned Trap held in {@code hand}. */
    public record Open(Hand hand) implements CustomPayload {
        public static final Id<Open> ID = new Id<>(Steveparty.id("trap_setup_open"));
        public static final PacketCodec<RegistryByteBuf, Open> CODEC = PacketCodec.tuple(HAND, Open::hand, Open::new);

        @Override
        public Id<? extends CustomPayload> getId() {
            return ID;
        }
    }

    /**
     * C2S: signs the unsigned Trap(s) held in {@code hand}: they do {@code kind} ({@code amount} coins or spaces) for
     * good, signed by the player. Checked here: an unsigned Trap in that hand; the amount brought within its bounds.
     */
    public record Sign(Hand hand, int kind, int amount) implements ServerboundPayload {
        public static final Id<Sign> ID = new Id<>(Steveparty.id("trap_setup_sign"));
        public static final PacketCodec<RegistryByteBuf, Sign> CODEC = PacketCodec.tuple(
                HAND, Sign::hand, PacketCodecs.VAR_INT, Sign::kind, PacketCodecs.VAR_INT, Sign::amount, Sign::new);

        @Override
        public Id<? extends CustomPayload> getId() {
            return ID;
        }

        @Override
        public void handle(ServerPlayerEntity player) {
            ItemStack stack = player.getStackInHand(hand);
            if (!(stack.getItem() instanceof TrapPowerUpItem) || TrapSetupComponent.isSigned(stack)) return;
            if (kind < 0 || kind >= TrapKind.values().length) return;
            TrapKind trapKind = TrapKind.values()[kind];
            TrapSetupComponent setup = new TrapSetupComponent(trapKind, trapKind.clamp(amount),
                    player.getGameProfile().getName(), player.getUuid());
            stack.set(ModComponents.TRAP_SETUP, setup);
            player.playSoundToPlayer(SoundEvents.ITEM_BOOK_PAGE_TURN, SoundCategory.PLAYERS, 1F, 0.9F);
            player.sendMessage(Text.translatable("message.steveparty.powerup.trap.signed", setup.effect().describe())
                    .formatted(Formatting.DARK_RED), true);
        }
    }
}
