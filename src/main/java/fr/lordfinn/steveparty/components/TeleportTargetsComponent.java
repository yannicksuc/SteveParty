package fr.lordfinn.steveparty.components;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.util.math.BlockPos;

import java.util.List;

/**
 * What a Teleport Cartridge holds: the board spaces a token landing on its tile is sent to (absolute positions, like the
 * path links of {@link DestinationsComponent}, but in a list of their own: they are not a way to walk), and its options.
 * Linked with the Wrench's Teleport mode.
 *
 * @param targets      the arrival spaces
 * @param cycle        several targets: in turn (true) or at random (false, the default)
 * @param landOnTarget the token's move ends on the target as a landing there (its role plays: bonus, item...); off by
 *                     default: it just ends there. Never a teleport again (no chains).
 */
public record TeleportTargetsComponent(List<BlockPos> targets, boolean cycle, boolean landOnTarget) {
    public static final TeleportTargetsComponent DEFAULT = new TeleportTargetsComponent(List.of(), false, false);

    public static final Codec<TeleportTargetsComponent> CODEC = RecordCodecBuilder.create(builder -> builder.group(
            BlockPos.CODEC.listOf().optionalFieldOf("targets", List.of()).forGetter(TeleportTargetsComponent::targets),
            Codec.BOOL.optionalFieldOf("cycle", false).forGetter(TeleportTargetsComponent::cycle),
            Codec.BOOL.optionalFieldOf("land_on_target", false).forGetter(TeleportTargetsComponent::landOnTarget)
    ).apply(builder, TeleportTargetsComponent::new));

    public TeleportTargetsComponent withTargets(List<BlockPos> targets) {
        return new TeleportTargetsComponent(List.copyOf(targets), cycle, landOnTarget);
    }

    public TeleportTargetsComponent withCycle(boolean cycle) {
        return new TeleportTargetsComponent(targets, cycle, landOnTarget);
    }

    public TeleportTargetsComponent withLandOnTarget(boolean landOnTarget) {
        return new TeleportTargetsComponent(targets, cycle, landOnTarget);
    }
}
