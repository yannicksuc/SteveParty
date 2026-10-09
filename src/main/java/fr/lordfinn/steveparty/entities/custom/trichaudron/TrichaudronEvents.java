package fr.lordfinn.steveparty.entities.custom.trichaudron;

import fr.lordfinn.steveparty.Steveparty;
import fr.lordfinn.steveparty.payloads.Payloads;
import net.fabricmc.fabric.api.event.player.UseItemCallback;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.network.PacketByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.util.Hand;
import net.minecraft.util.TypedActionResult;
import net.minecraft.world.World;

/**
 * What reaches a Trichaudron from outside its hitbox: a rider's clicks (his attack and use keys fire his head, or grab a
 * wall in the air: {@link RiderClick}, sent by the client in place of the vanilla attack and use), and a magma cream
 * held out to one of its heads, far in front of its shell (the cream used in the air, the head under the crosshair).
 */
public final class TrichaudronEvents {
    /** How far around a player a Trichaudron's head may be fed from. */
    private static final double FEED_RANGE = 14;

    private TrichaudronEvents() {
    }

    public static void initialize() {
        Payloads.c2s(RiderClick.ID, RiderClick.CODEC, (player, payload) -> {
            if (player.getVehicle() instanceof TrichaudronEntity trichaudron) trichaudron.riderClick(player);
        });
        UseItemCallback.EVENT.register(TrichaudronEvents::useItem);
    }

    private static TypedActionResult<ItemStack> useItem(PlayerEntity player, World world, Hand hand) {
        ItemStack stack = player.getStackInHand(hand);
        if (!stack.isOf(Items.MAGMA_CREAM) || player.isSpectator()) return TypedActionResult.pass(stack);
        for (TrichaudronEntity trichaudron : world.getEntitiesByClass(TrichaudronEntity.class, player.getBoundingBox().expand(FEED_RANGE), e -> e.isAlive())) {
            int head = trichaudron.headLookedAt(player);
            if (head < 0) continue;
            if (!world.isClient) trichaudron.feedHead(player, head, stack);
            return TypedActionResult.success(stack, world.isClient);
        }
        return TypedActionResult.pass(stack);
    }

    /** A rider clicked (attack or use): fire his head, or grab a wall in the air. */
    public record RiderClick() implements CustomPayload {
        public static final Id<RiderClick> ID = new Id<>(Steveparty.id("trichaudron_rider_click"));
        public static final PacketCodec<PacketByteBuf, RiderClick> CODEC = PacketCodec.unit(new RiderClick());

        @Override
        public Id<? extends CustomPayload> getId() {
            return ID;
        }
    }
}
