package fr.lordfinn.steveparty.components;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.codec.PacketCodecs;
import net.minecraft.util.math.BlockPos;

import java.util.List;

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
 * @param targets      its TP links, painted with the Tile Linker Brush (« TP » picked): the board spaces, of any kind,
 *                     a token landing on the tile is sent to, instead of the other tiles of its network; empty: its
 *                     network's
 */
public record TeleportSettingsComponent(TeleportNetwork network, boolean cycle, boolean push, boolean pushTriggers,
                                        List<BlockPos> targets) {
    public static final TeleportSettingsComponent DEFAULT = new TeleportSettingsComponent(TeleportNetwork.VIOLET, false, false, true);
    /** The most TP links a tile keeps. */
    public static final int MAX_TARGETS = 16;

    public static final Codec<TeleportSettingsComponent> CODEC = RecordCodecBuilder.create(builder -> builder.group(
            TeleportNetwork.CODEC.lenientOptionalFieldOf("network", TeleportNetwork.VIOLET).forGetter(TeleportSettingsComponent::network),
            Codec.BOOL.optionalFieldOf("cycle", false).forGetter(TeleportSettingsComponent::cycle),
            Codec.BOOL.optionalFieldOf("push", false).forGetter(TeleportSettingsComponent::push),
            Codec.BOOL.optionalFieldOf("push_triggers", true).forGetter(TeleportSettingsComponent::pushTriggers),
            BlockPos.CODEC.listOf().optionalFieldOf("tp_links", List.of()).forGetter(TeleportSettingsComponent::targets)
    ).apply(builder, TeleportSettingsComponent::new));

    public static final PacketCodec<ByteBuf, TeleportSettingsComponent> PACKET_CODEC = PacketCodec.tuple(
            PacketCodecs.VAR_INT.xmap(TeleportNetwork::byIndex, TeleportNetwork::ordinal), TeleportSettingsComponent::network,
            PacketCodecs.BOOL, TeleportSettingsComponent::cycle,
            PacketCodecs.BOOL, TeleportSettingsComponent::push,
            PacketCodecs.BOOL, TeleportSettingsComponent::pushTriggers,
            BlockPos.PACKET_CODEC.collect(PacketCodecs.toList(MAX_TARGETS)), TeleportSettingsComponent::targets,
            TeleportSettingsComponent::new);

    public TeleportSettingsComponent {
        targets = List.copyOf(targets);
    }

    /** Settings without TP links (its network's tiles). */
    public TeleportSettingsComponent(TeleportNetwork network, boolean cycle, boolean push, boolean pushTriggers) {
        this(network, cycle, push, pushTriggers, List.of());
    }

    public TeleportSettingsComponent withNetwork(TeleportNetwork network) {
        return new TeleportSettingsComponent(network, cycle, push, pushTriggers, targets);
    }

    public TeleportSettingsComponent withCycle(boolean cycle) {
        return new TeleportSettingsComponent(network, cycle, push, pushTriggers, targets);
    }

    public TeleportSettingsComponent withPush(boolean push) {
        return new TeleportSettingsComponent(network, cycle, push, pushTriggers, targets);
    }

    public TeleportSettingsComponent withPushTriggers(boolean pushTriggers) {
        return new TeleportSettingsComponent(network, cycle, push, pushTriggers, targets);
    }

    public TeleportSettingsComponent withTargets(List<BlockPos> targets) {
        return new TeleportSettingsComponent(network, cycle, push, pushTriggers, targets);
    }
}
