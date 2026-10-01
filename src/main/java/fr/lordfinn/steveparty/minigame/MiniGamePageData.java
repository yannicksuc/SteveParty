package fr.lordfinn.steveparty.minigame;

import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtElement;
import net.minecraft.nbt.NbtList;
import net.minecraft.network.PacketByteBuf;
import net.minecraft.network.codec.PacketCodec;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/**
 * What a mini-game page says about its mini-game. It lives on the server ({@link MiniGamePages}), under the page's
 * id: the page items only carry that id, so every copy of a page shows the same content.
 * <p>
 * Immutable: change it with the {@code with...} methods, then {@link MiniGamePages#update}.
 *
 * @param id          the page's id, shared by its linked copies
 * @param description free text, lines split by {@code \n}
 * @param image       its picture, null for none
 * @param modes       the team layouts the mini-game accepts (never empty)
 * @param pipeLinks   the pipe mouths of the mini-game, with their roles
 */
public record MiniGamePageData(UUID id, String title, String description, @Nullable MiniGamePageImage image,
                               Set<MiniGameMode> modes, int minPlayers, int maxPlayers, List<MiniGamePipeLink> pipeLinks) {
    public static final int MAX_TITLE_LENGTH = 40;
    public static final int MAX_DESCRIPTION_LENGTH = 400;
    public static final int MAX_DESCRIPTION_LINES = 8;
    public static final int MIN_PLAYERS = 1;
    public static final int MAX_PLAYERS = 16;
    public static final int MAX_PIPE_LINKS = 64;
    /** The id sent in place of a page's when the item has none yet (a page never written on, read only). */
    public static final UUID NO_ID = new UUID(0, 0);

    public static final PacketCodec<PacketByteBuf, MiniGamePageData> PACKET_CODEC = PacketCodec.of(MiniGamePageData::write, MiniGamePageData::read);

    /** Cleans whatever it is given: lengths, players range, at least one mode. */
    public MiniGamePageData {
        title = cleanTitle(title);
        description = cleanDescription(description);
        EnumSet<MiniGameMode> accepted = EnumSet.noneOf(MiniGameMode.class);
        if (modes != null) accepted.addAll(modes);
        if (accepted.isEmpty()) accepted = EnumSet.allOf(MiniGameMode.class);
        modes = Collections.unmodifiableSet(accepted);
        minPlayers = Math.max(MIN_PLAYERS, Math.min(MAX_PLAYERS, minPlayers));
        maxPlayers = Math.max(minPlayers, Math.min(MAX_PLAYERS, maxPlayers));
        pipeLinks = pipeLinks == null ? List.of()
                : List.copyOf(pipeLinks.size() > MAX_PIPE_LINKS ? pipeLinks.subList(0, MAX_PIPE_LINKS) : pipeLinks);
    }

    /** A page nobody wrote yet: no title, every mode, any number of players. */
    public static MiniGamePageData empty(UUID id) {
        return new MiniGamePageData(id, "", "", null, EnumSet.allOf(MiniGameMode.class), MIN_PLAYERS, MAX_PLAYERS, List.of());
    }

    /** @return true if nothing was written on the page yet. */
    public boolean isBlank() {
        return equals(empty(id));
    }

    /**
     * @param playerCount the number of players of the mini-game
     * @param layout      how they are split
     * @return true if the mini-game can be played by that many players split that way
     */
    public boolean accepts(int playerCount, MiniGameMode layout) {
        return layout != null && modes.contains(layout) && playerCount >= minPlayers && playerCount <= maxPlayers;
    }

    /** The pipes of a role, in the order they were linked. */
    public List<MiniGamePipeLink> pipes(MiniGamePipeRole role) {
        List<MiniGamePipeLink> found = new ArrayList<>();
        for (MiniGamePipeLink link : pipeLinks) if (link.role() == role) found.add(link);
        return found;
    }

    /** The roles the mini-game has no pipe of, among those it needs to be played in {@code mode}. */
    public List<MiniGamePipeRole> missing(MiniGameMode mode) {
        List<MiniGamePipeRole> missing = new ArrayList<>();
        for (MiniGamePipeRole role : MiniGamePipeRole.needed(mode)) {
            if (pipes(role).isEmpty()) missing.add(role);
        }
        return missing;
    }

    /** @return true if the players of a mini-game played in {@code mode} all have a pipe to come out of. */
    public boolean hasPipesFor(MiniGameMode mode) {
        return mode != null && missing(mode).isEmpty();
    }

    /** @return true if the party controller can draw this mini-game for that many players split that way. */
    public boolean isPlayable(int playerCount, MiniGameMode layout) {
        return accepts(playerCount, layout) && hasPipesFor(layout);
    }

    /** @return true if the mini-game can be played in at least one of the modes it ticks. */
    public boolean isPlayable() {
        for (MiniGameMode mode : modes) if (hasPipesFor(mode)) return true;
        return false;
    }

    /** The index of the link to the pipe at {@code mouth}, -1 if it is not linked. */
    public int linkIndex(net.minecraft.util.math.GlobalPos mouth) {
        for (int i = 0; i < pipeLinks.size(); i++) if (pipeLinks.get(i).isAt(mouth)) return i;
        return -1;
    }

    public boolean hasTitle() {
        return !title.isEmpty();
    }

    public MiniGamePageData withId(UUID newId) {
        return new MiniGamePageData(newId, title, description, image, modes, minPlayers, maxPlayers, pipeLinks);
    }

    public MiniGamePageData withTexts(String newTitle, String newDescription) {
        return new MiniGamePageData(id, newTitle, newDescription, image, modes, minPlayers, maxPlayers, pipeLinks);
    }

    public MiniGamePageData withImage(@Nullable MiniGamePageImage newImage) {
        return new MiniGamePageData(id, title, description, newImage, modes, minPlayers, maxPlayers, pipeLinks);
    }

    public MiniGamePageData withModes(Set<MiniGameMode> newModes) {
        return new MiniGamePageData(id, title, description, image, newModes, minPlayers, maxPlayers, pipeLinks);
    }

    public MiniGamePageData withPlayers(int min, int max) {
        return new MiniGamePageData(id, title, description, image, modes, min, max, pipeLinks);
    }

    public MiniGamePageData withPipeLinks(List<MiniGamePipeLink> links) {
        return new MiniGamePageData(id, title, description, image, modes, minPlayers, maxPlayers, links);
    }

    // ------------------------------------------------------------------ cleaning

    public static String cleanTitle(@Nullable String title) {
        if (title == null) return "";
        StringBuilder clean = new StringBuilder();
        title.codePoints().filter(c -> !Character.isISOControl(c) && c != '§').forEach(clean::appendCodePoint);
        String text = clean.toString().strip();
        return text.length() > MAX_TITLE_LENGTH ? text.substring(0, MAX_TITLE_LENGTH).strip() : text;
    }

    public static String cleanDescription(@Nullable String description) {
        if (description == null) return "";
        StringBuilder clean = new StringBuilder();
        int lines = 1;
        for (int i = 0; i < description.length() && clean.length() < MAX_DESCRIPTION_LENGTH; i++) {
            char c = description.charAt(i);
            if (c == '\n') {
                if (lines >= MAX_DESCRIPTION_LINES) break;
                lines++;
                clean.append(c);
            } else if (!Character.isISOControl(c) && c != '§') {
                clean.append(c);
            }
        }
        return clean.toString().stripTrailing();
    }

    // ------------------------------------------------------------------ saving

    public NbtCompound toNbt() {
        NbtCompound nbt = new NbtCompound();
        nbt.putUuid("Id", id);
        nbt.putString("Title", title);
        nbt.putString("Description", description);
        if (image != null) nbt.put("Image", image.toNbt());
        nbt.putInt("Modes", MiniGameMode.toMask(modes));
        nbt.putInt("MinPlayers", minPlayers);
        nbt.putInt("MaxPlayers", maxPlayers);
        if (!pipeLinks.isEmpty()) {
            NbtList links = new NbtList();
            pipeLinks.forEach(link -> links.add(link.toNbt()));
            nbt.put("PipeLinks", links);
        }
        return nbt;
    }

    public static @Nullable MiniGamePageData fromNbt(NbtCompound nbt) {
        if (!nbt.containsUuid("Id")) return null;
        List<MiniGamePipeLink> links = new ArrayList<>();
        NbtList linksNbt = nbt.getList("PipeLinks", NbtElement.COMPOUND_TYPE);
        for (int i = 0; i < linksNbt.size(); i++) {
            MiniGamePipeLink link = MiniGamePipeLink.fromNbt(linksNbt.getCompound(i));
            if (link != null) links.add(link);
        }
        return new MiniGamePageData(nbt.getUuid("Id"), nbt.getString("Title"), nbt.getString("Description"),
                nbt.contains("Image", NbtElement.COMPOUND_TYPE) ? MiniGamePageImage.fromNbt(nbt.getCompound("Image")) : null,
                MiniGameMode.fromMask(nbt.getInt("Modes")), nbt.getInt("MinPlayers"), nbt.getInt("MaxPlayers"), links);
    }

    private void write(PacketByteBuf buf) {
        buf.writeUuid(id);
        buf.writeString(title, MAX_TITLE_LENGTH);
        buf.writeString(description, MAX_DESCRIPTION_LENGTH);
        buf.writeBoolean(image != null);
        if (image != null) image.write(buf);
        buf.writeByte(MiniGameMode.toMask(modes));
        buf.writeByte(minPlayers);
        buf.writeByte(maxPlayers);
        buf.writeVarInt(pipeLinks.size());
        pipeLinks.forEach(link -> link.write(buf));
    }

    private static MiniGamePageData read(PacketByteBuf buf) {
        UUID id = buf.readUuid();
        String title = buf.readString(MAX_TITLE_LENGTH);
        String description = buf.readString(MAX_DESCRIPTION_LENGTH);
        MiniGamePageImage image = buf.readBoolean() ? MiniGamePageImage.read(buf) : null;
        int modes = buf.readByte();
        int min = buf.readByte(), max = buf.readByte();
        int count = Math.min(buf.readVarInt(), MAX_PIPE_LINKS);
        List<MiniGamePipeLink> links = new ArrayList<>(count);
        for (int i = 0; i < count; i++) links.add(MiniGamePipeLink.read(buf));
        return new MiniGamePageData(id, title, description, image, MiniGameMode.fromMask(modes), min, max, links);
    }
}
