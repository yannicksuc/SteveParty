package fr.lordfinn.steveparty.payloads;

import com.mojang.serialization.Codec;

import java.util.UUID;

public class UUIDCodec {
    public static final Codec<UUID> CODEC = Codec.STRING.xmap(UUID::fromString, UUID::toString);
}