package fr.lordfinn.steveparty.payloads;

import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.codec.PacketCodecs;
import net.minecraft.util.math.Vec3d;

/** Packet codecs missing from vanilla 1.21.1. */
public final class ModPacketCodecs {
    /** A {@link Vec3d} as 3 doubles (Vec3d.PACKET_CODEC only exists from 1.21.2). */
    public static final PacketCodec<ByteBuf, Vec3d> VEC3D = PacketCodec.tuple(
            PacketCodecs.DOUBLE, Vec3d::getX,
            PacketCodecs.DOUBLE, Vec3d::getY,
            PacketCodecs.DOUBLE, Vec3d::getZ,
            Vec3d::new);

    private ModPacketCodecs() {
    }
}
