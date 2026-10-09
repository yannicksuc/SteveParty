package fr.lordfinn.steveparty.components;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.util.Uuids;

import java.util.UUID;

/**
 * The trap set on a Trap cartridge's space ({@link ModComponents#TRAP_SET}): who set it (their player, their token),
 * and the colour its plate shows on the board.
 */
public record TrapSetComponent(UUID owner, UUID token, int color) {
    public static final Codec<TrapSetComponent> CODEC = RecordCodecBuilder.create(instance -> instance.group(
            Uuids.CODEC.fieldOf("owner").forGetter(TrapSetComponent::owner),
            Uuids.CODEC.fieldOf("token").forGetter(TrapSetComponent::token),
            Codec.INT.optionalFieldOf("color", 0xB02E26).forGetter(TrapSetComponent::color)
    ).apply(instance, TrapSetComponent::new));

    /** Set by this player, or by this token (a player's own trap never springs on them). */
    public boolean isOwnedBy(@org.jetbrains.annotations.Nullable UUID player, UUID token) {
        return owner.equals(player) || this.token.equals(token);
    }
}
