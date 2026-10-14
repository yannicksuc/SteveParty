package fr.lordfinn.steveparty.items.custom.parachute;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.item.ItemStack;
import net.minecraft.network.RegistryByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.codec.PacketCodecs;

/**
 * The parachute an entity carries (see {@link Parachutes#TYPE}): its item (a mob's own, dropped when it lands; a
 * player's is a copy of the one in his hand, for its colour), since when it is in this state (game time: the canopy
 * unfolds from there), and whether it is open (a mob's waits packed until it falls).
 */
public record ParachuteState(ItemStack stack, long since, boolean open) {
    public static final Codec<ParachuteState> CODEC = RecordCodecBuilder.create(instance -> instance.group(
            ItemStack.CODEC.fieldOf("stack").forGetter(ParachuteState::stack),
            Codec.LONG.fieldOf("since").forGetter(ParachuteState::since),
            Codec.BOOL.fieldOf("open").forGetter(ParachuteState::open)
    ).apply(instance, ParachuteState::new));

    public static final PacketCodec<RegistryByteBuf, ParachuteState> PACKET_CODEC = PacketCodec.tuple(
            ItemStack.PACKET_CODEC, ParachuteState::stack,
            PacketCodecs.VAR_LONG, ParachuteState::since,
            PacketCodecs.BOOL, ParachuteState::open,
            ParachuteState::new);

    /** Its stripes' colour (ARGB). */
    public int color() {
        return ParachuteItem.color(stack);
    }
}
