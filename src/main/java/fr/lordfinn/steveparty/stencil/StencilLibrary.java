package fr.lordfinn.steveparty.stencil;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import fr.lordfinn.steveparty.Steveparty;
import fr.lordfinn.steveparty.items.custom.StencilItem;
import net.fabricmc.fabric.api.attachment.v1.AttachmentRegistry;
import net.fabricmc.fabric.api.attachment.v1.AttachmentSyncPredicate;
import net.fabricmc.fabric.api.attachment.v1.AttachmentType;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.network.RegistryByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.codec.PacketCodecs;
import net.minecraft.server.network.ServerPlayerEntity;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * A player's stencil library, shown in the stencil maker: every stencil pattern the player came across (stencils
 * found or carried are learnt as they go through their inventory), plus the ones they saved from the editor.
 * Favourites are listed first. Kept on the player (and after death), synced to their own client only.
 * In creative mode the editor also lists the whole built-in library ({@link StencilPatterns}).
 */
public record StencilLibrary(List<Entry> entries) {
    public static final int MAX_ENTRIES = 128;
    public static final StencilLibrary EMPTY = new StencilLibrary(List.of());

    public record Entry(List<Byte> shape, boolean favorite) {
        public static final Codec<Entry> CODEC = RecordCodecBuilder.create(instance -> instance.group(
                Codec.BYTE.listOf().fieldOf("shape").forGetter(Entry::shape),
                Codec.BOOL.optionalFieldOf("favorite", false).forGetter(Entry::favorite)
        ).apply(instance, Entry::new));
        public static final PacketCodec<RegistryByteBuf, Entry> PACKET_CODEC = PacketCodec.tuple(
                PacketCodecs.BYTE_ARRAY.xmap(StencilShape::toList, StencilShape::fromList), Entry::shape,
                PacketCodecs.BOOL, Entry::favorite,
                Entry::new);

        public Entry {
            shape = List.copyOf(shape);
        }

        public byte[] shapeArray() {
            return StencilShape.sanitize(StencilShape.fromList(shape));
        }

        boolean is(byte[] other) {
            return Arrays.equals(StencilShape.fromList(shape), other);
        }
    }

    public static final Codec<StencilLibrary> CODEC = Entry.CODEC.listOf().xmap(StencilLibrary::new, StencilLibrary::entries);
    public static final PacketCodec<RegistryByteBuf, StencilLibrary> PACKET_CODEC =
            Entry.PACKET_CODEC.collect(PacketCodecs.toList()).xmap(StencilLibrary::new, StencilLibrary::entries);

    public static final AttachmentType<StencilLibrary> TYPE = AttachmentRegistry.<StencilLibrary>builder()
            .persistent(CODEC)
            .copyOnDeath()
            .initializer(() -> EMPTY)
            .syncWith(PACKET_CODEC, AttachmentSyncPredicate.targetOnly())
            .buildAndRegister(Steveparty.id("stencil_library"));

    /** Ticks between two looks at the players' inventories for stencils to learn. */
    private static final int LEARN_INTERVAL = 40;

    public StencilLibrary {
        entries = List.copyOf(entries);
    }

    public static void initialize() {
        ServerTickEvents.END_SERVER_TICK.register(server -> {
            if (server.getTicks() % LEARN_INTERVAL != 0) return;
            for (ServerPlayerEntity player : server.getPlayerManager().getPlayerList()) learnFromInventory(player);
        });
    }

    public static StencilLibrary of(PlayerEntity player) {
        StencilLibrary library = player.getAttached(TYPE);
        return library == null ? EMPTY : library;
    }

    public static void set(PlayerEntity player, StencilLibrary library) {
        player.setAttached(TYPE, library);
    }

    /** Adds the patterns of the stencils the player carries. */
    public static void learnFromInventory(ServerPlayerEntity player) {
        StencilLibrary library = of(player);
        StencilLibrary learnt = library;
        for (int i = 0; i < player.getInventory().size(); i++) {
            ItemStack stack = player.getInventory().getStack(i);
            if (!(stack.getItem() instanceof StencilItem)) continue;
            byte[] shape = StencilItem.getShape(stack);
            if (!StencilShape.isBlank(shape)) learnt = learnt.with(shape);
        }
        if (learnt != library) set(player, learnt);
    }

    public boolean contains(byte[] shape) {
        return entries.stream().anyMatch(entry -> entry.is(shape));
    }

    public boolean isFavorite(byte[] shape) {
        return entries.stream().anyMatch(entry -> entry.is(shape) && entry.favorite());
    }

    /** @return this library with {@code shape} added at the end (unchanged if already there or full). */
    public StencilLibrary with(byte[] shape) {
        if (!StencilShape.isValid(shape) || StencilShape.isBlank(shape) || contains(shape) || entries.size() >= MAX_ENTRIES) return this;
        List<Entry> list = new ArrayList<>(entries);
        list.add(new Entry(StencilShape.toList(StencilShape.sanitize(shape)), false));
        return new StencilLibrary(list);
    }

    public StencilLibrary without(byte[] shape) {
        List<Entry> list = new ArrayList<>(entries);
        if (!list.removeIf(entry -> entry.is(shape))) return this;
        return new StencilLibrary(list);
    }

    /** @return this library with {@code shape} (added if needed) marked or unmarked as a favourite. */
    public StencilLibrary toggleFavorite(byte[] shape) {
        StencilLibrary library = with(shape);
        List<Entry> list = new ArrayList<>(library.entries);
        for (int i = 0; i < list.size(); i++) {
            Entry entry = list.get(i);
            if (entry.is(shape)) list.set(i, new Entry(entry.shape(), !entry.favorite()));
        }
        return new StencilLibrary(list);
    }
}
