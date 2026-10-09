package fr.lordfinn.steveparty.components;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.codec.PacketCodecs;

/**
 * The settings of a Teleport Cartridge, edited in its menu (see
 * {@code TeleportCartridgeItem}).
 *
 * @param network      its network: a token landing on the tile is sent to another Teleport tile of this colour
 * @param cycle        several other tiles in the network: in turn (true) or at random (false, the default)
 * @param push         on arrival, the token is pushed one space further along the arrival tile's path (true), or
 *                     stays on the arrival Teleport tile (false, the default)
 * @param pushTriggers pushed: the space it is pushed onto triggers its effect (bonus, item...) like an ordinary
 *                     landing (true, the default), or it just stops there (false). Never a teleport again (no chains).
 */
public record TeleportSettingsComponent(TeleportNetwork network, boolean cycle, boolean push, boolean pushTriggers) {
    public static final TeleportSettingsComponent DEFAULT = new TeleportSettingsComponent(TeleportNetwork.VIOLET, false, false, true);

    public static final Codec<TeleportSettingsComponent> CODEC = RecordCodecBuilder.create(builder -> builder.group(
            TeleportNetwork.CODEC.lenientOptionalFieldOf("network", TeleportNetwork.VIOLET).forGetter(TeleportSettingsComponent::network),
            Codec.BOOL.optionalFieldOf("cycle", false).forGetter(TeleportSettingsComponent::cycle),
            Codec.BOOL.optionalFieldOf("push", false).forGetter(TeleportSettingsComponent::push),
            Codec.BOOL.optionalFieldOf("push_triggers", true).forGetter(TeleportSettingsComponent::pushTriggers)
    ).apply(builder, TeleportSettingsComponent::new));

    public static final PacketCodec<ByteBuf, TeleportSettingsComponent> PACKET_CODEC = PacketCodec.tuple(
            PacketCodecs.VAR_INT.xmap(TeleportNetwork::byIndex, TeleportNetwork::ordinal), TeleportSettingsComponent::network,
            PacketCodecs.BOOL, TeleportSettingsComponent::cycle,
            PacketCodecs.BOOL, TeleportSettingsComponent::push,
            PacketCodecs.BOOL, TeleportSettingsComponent::pushTriggers,
            TeleportSettingsComponent::new);

    public TeleportSettingsComponent withNetwork(TeleportNetwork network) {
        return new TeleportSettingsComponent(network, cycle, push, pushTriggers);
    }

    public TeleportSettingsComponent withCycle(boolean cycle) {
        return new TeleportSettingsComponent(network, cycle, push, pushTriggers);
    }

    public TeleportSettingsComponent withPush(boolean push) {
        return new TeleportSettingsComponent(network, cycle, push, pushTriggers);
    }

    public TeleportSettingsComponent withPushTriggers(boolean pushTriggers) {
        return new TeleportSettingsComponent(network, cycle, push, pushTriggers);
    }
}
