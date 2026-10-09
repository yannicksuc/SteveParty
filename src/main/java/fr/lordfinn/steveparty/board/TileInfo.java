package fr.lordfinn.steveparty.board;

import net.minecraft.item.ItemStack;
import net.minecraft.network.RegistryByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.text.MutableText;
import net.minecraft.text.Text;
import net.minecraft.text.TextCodecs;
import net.minecraft.text.TextColor;

import java.util.ArrayList;
import java.util.List;

/**
 * What a board space tells a player in a party (its « game » info, see TileInfos): its role's name, a few short lines
 * (an icon each, optional) and the items it lists (given, taken, won...), drawn by the client as a panel over the space
 * and a ring of items turning above it. Computed by the server from the space's state (its role's
 * {@code ABoardSpaceBehavior#describe}), never per tick. The lines are written for the mod's light plates: dark text,
 * values in the colours below.
 */
public record TileInfo(Text title, int accent, List<Line> lines, List<ItemStack> ring) {
    /** At most this many lines, and this many items in the ring. */
    public static final int MAX_LINES = 5, MAX_RING = 12;
    /** Values readable on the light plates: gain, loss, coins, a setting, something unimportant. */
    public static final int GOOD = 0x1D7A1D, BAD = 0xB3261E, COINS = 0xA05F00, VALUE = 0x1C4FA8, DIM = 0x707070;

    public static final TileInfo EMPTY = new TileInfo(Text.empty(), 0xFFFFFF, List.of(), List.of());

    /** A line: {@code icon} (empty: none) left of {@code text}. */
    public record Line(ItemStack icon, Text text) {
    }

    public boolean isEmpty() {
        return lines.isEmpty() && ring.isEmpty();
    }

    /** Same content (ItemStack has no value equality, so the records can't just be compared). */
    public boolean sameAs(TileInfo other) {
        if (other == this) return true;
        if (other == null || accent != other.accent || !title.equals(other.title) || lines.size() != other.lines.size()
                || ring.size() != other.ring.size()) return false;
        for (int i = 0; i < lines.size(); i++) {
            Line a = lines.get(i), b = other.lines.get(i);
            if (!a.text().equals(b.text()) || !ItemStack.areEqual(a.icon(), b.icon())) return false;
        }
        for (int i = 0; i < ring.size(); i++) {
            if (!ItemStack.areEqual(ring.get(i), other.ring.get(i))) return false;
        }
        return true;
    }

    public static final PacketCodec<RegistryByteBuf, TileInfo> PACKET_CODEC = new PacketCodec<>() {
        @Override
        public TileInfo decode(RegistryByteBuf buf) {
            Text title = TextCodecs.REGISTRY_PACKET_CODEC.decode(buf);
            int accent = buf.readInt();
            int lineCount = Math.min(buf.readVarInt(), MAX_LINES);
            List<Line> lines = new ArrayList<>(lineCount);
            for (int i = 0; i < lineCount; i++) {
                ItemStack icon = ItemStack.OPTIONAL_PACKET_CODEC.decode(buf);
                lines.add(new Line(icon, TextCodecs.REGISTRY_PACKET_CODEC.decode(buf)));
            }
            int ringCount = Math.min(buf.readVarInt(), MAX_RING);
            List<ItemStack> ring = new ArrayList<>(ringCount);
            for (int i = 0; i < ringCount; i++) ring.add(ItemStack.PACKET_CODEC.decode(buf));
            return new TileInfo(title, accent, List.copyOf(lines), List.copyOf(ring));
        }

        @Override
        public void encode(RegistryByteBuf buf, TileInfo info) {
            TextCodecs.REGISTRY_PACKET_CODEC.encode(buf, info.title);
            buf.writeInt(info.accent);
            buf.writeVarInt(info.lines.size());
            for (Line line : info.lines) {
                ItemStack.OPTIONAL_PACKET_CODEC.encode(buf, line.icon());
                TextCodecs.REGISTRY_PACKET_CODEC.encode(buf, line.text());
            }
            buf.writeVarInt(info.ring.size());
            for (ItemStack stack : info.ring) ItemStack.PACKET_CODEC.encode(buf, stack);
        }
    };

    // ---------------------------------------------------------------- values

    public static MutableText good(Object value) {
        return rgb(value, GOOD);
    }

    public static MutableText bad(Object value) {
        return rgb(value, BAD);
    }

    public static MutableText coins(Object value) {
        return rgb(value, COINS);
    }

    public static MutableText value(Object value) {
        return rgb(value, VALUE);
    }

    public static MutableText dim(Object value) {
        return rgb(value, DIM);
    }

    public static MutableText rgb(Object value, int rgb) {
        MutableText text = value instanceof Text t ? t.copy() : Text.literal(String.valueOf(value));
        return text.styled(style -> style.withColor(TextColor.fromRgb(rgb)));
    }

    /** {@code hud.steveparty.tile_info.<key>} with its arguments. */
    public static MutableText line(String key, Object... args) {
        return Text.translatable("hud.steveparty.tile_info." + key, args);
    }

    // ---------------------------------------------------------------- building

    public static Builder builder() {
        return new Builder();
    }

    /** Filled by the space's role ({@code ABoardSpaceBehavior#describe}), then by what lies on the space (a Trap). */
    public static final class Builder {
        private Text title = Text.empty();
        private int accent = 0xFFFFFF;
        private final List<Line> lines = new ArrayList<>();
        private final List<ItemStack> ring = new ArrayList<>();

        private Builder() {
        }

        public Builder title(Text title, int accent) {
            this.title = title;
            this.accent = accent & 0xFFFFFF;
            return this;
        }

        public boolean hasTitle() {
            return !title.getString().isEmpty();
        }

        public Builder line(Text text) {
            return line(ItemStack.EMPTY, text);
        }

        public Builder line(ItemStack icon, Text text) {
            if (lines.size() < MAX_LINES) lines.add(new Line(icon.isEmpty() ? ItemStack.EMPTY : icon.copyWithCount(1), text));
            return this;
        }

        /** An item of the ring (its count is shown; a stack flagged as taken, as a loss). */
        public Builder item(ItemStack stack) {
            if (!stack.isEmpty() && ring.size() < MAX_RING) ring.add(stack.copy());
            return this;
        }

        public boolean isEmpty() {
            return lines.isEmpty() && ring.isEmpty();
        }

        public TileInfo build() {
            if (isEmpty()) return EMPTY;
            return new TileInfo(title, accent, List.copyOf(lines), List.copyOf(ring));
        }
    }
}
