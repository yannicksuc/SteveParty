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
 * @param formats     the ways the mini-game can be played: teams, free for all, all together, with their players
 *                    ({@link MiniGameFormat}; never empty)
 * @param description free text with the markup of {@link MiniGameText}, lines split by {@code \n}
 * @param pipeLinks   the pipe mouths of the mini-game, with their roles
 * @param randomRoles the roles whose players are sent to a pipe picked at random (the others: each pipe in turn)
 * @param intro       the shots of the introduction shown before the players leave, empty for none
 * @param podiumLinks the podiums (the places of the mini-game) and the goal pole bases (its counters) linked to the page
 */
public record MiniGamePageData(UUID id, String title, String description, @Nullable MiniGamePageImage image,
                               List<MiniGameFormat> formats, List<MiniGamePipeLink> pipeLinks,
                               Set<MiniGamePipeRole> randomRoles, List<MiniGameIntroShot> intro, List<MiniGamePodiumLink> podiumLinks) {
    /**
     * The version of the saved form: 2 added the random roles, the introduction and the text markup; 3 the podiums; 4
     * the formats (in place of the ways to play and the players range, see {@link MiniGameFormat#migrate}).
     */
    public static final int FORMAT = 4;
    public static final int MAX_FORMATS = 8;
    public static final int MAX_PODIUM_LINKS = 32;
    public static final int MAX_INTRO_SHOTS = 32;
    public static final int MAX_TITLE_LENGTH = 40;
    /** The description's characters shown (its formatting codes not counted). */
    public static final int MAX_DESCRIPTION_LENGTH = 400;
    /** The description as stored: its characters and its formatting codes. */
    public static final int MAX_DESCRIPTION_STORED = 2000;
    public static final int MAX_DESCRIPTION_LINES = 8;
    /** The most players the old players range had: it meant « any number ». */
    public static final int MAX_PLAYERS_OLD = 16;
    public static final int MAX_PIPE_LINKS = 64;
    /** The id sent in place of a page's when the item has none yet (a page never written on, read only). */
    public static final UUID NO_ID = new UUID(0, 0);

    public static final PacketCodec<PacketByteBuf, MiniGamePageData> PACKET_CODEC = PacketCodec.of(MiniGamePageData::write, MiniGamePageData::read);

    /** Cleans whatever it is given: lengths, at least one format (free for all, any number, by default). */
    public MiniGamePageData {
        title = cleanTitle(title);
        description = cleanDescription(description);
        List<MiniGameFormat> accepted = new ArrayList<>();
        if (formats != null) for (MiniGameFormat format : formats) if (format != null && accepted.size() < MAX_FORMATS) accepted.add(format);
        if (accepted.isEmpty()) accepted.add(MiniGameFormat.freeForAll(1, MiniGameFormat.Side.INFINITE));
        formats = List.copyOf(accepted);
        pipeLinks = pipeLinks == null ? List.of()
                : List.copyOf(pipeLinks.size() > MAX_PIPE_LINKS ? pipeLinks.subList(0, MAX_PIPE_LINKS) : pipeLinks);
        EnumSet<MiniGamePipeRole> random = EnumSet.noneOf(MiniGamePipeRole.class);
        if (randomRoles != null) random.addAll(randomRoles);
        randomRoles = Collections.unmodifiableSet(random);
        intro = intro == null ? List.of() : List.copyOf(intro.size() > MAX_INTRO_SHOTS ? intro.subList(0, MAX_INTRO_SHOTS) : intro);
        podiumLinks = podiumLinks == null ? List.of()
                : List.copyOf(podiumLinks.size() > MAX_PODIUM_LINKS ? podiumLinks.subList(0, MAX_PODIUM_LINKS) : podiumLinks);
    }

    public MiniGamePageData(UUID id, String title, String description, @Nullable MiniGamePageImage image,
                            List<MiniGameFormat> formats, List<MiniGamePipeLink> pipeLinks,
                            Set<MiniGamePipeRole> randomRoles, List<MiniGameIntroShot> intro) {
        this(id, title, description, image, formats, pipeLinks, randomRoles, intro, List.of());
    }

    public MiniGamePageData(UUID id, String title, String description, @Nullable MiniGamePageImage image,
                            List<MiniGameFormat> formats, List<MiniGamePipeLink> pipeLinks) {
        this(id, title, description, image, formats, pipeLinks, Set.of(), List.of());
    }

    /** A page nobody wrote yet: no title, free for all with any number of players (who writes it adds the others). */
    public static MiniGamePageData empty(UUID id) {
        return new MiniGamePageData(id, "", "", null, List.of(), List.of());
    }

    /** @return true if nothing was written on the page yet. */
    public boolean isBlank() {
        return equals(empty(id));
    }


    /** The pipes of a role, in the order they were linked. */
    public List<MiniGamePipeLink> pipes(MiniGamePipeRole role) {
        List<MiniGamePipeLink> found = new ArrayList<>();
        for (MiniGamePipeLink link : pipeLinks) if (link.role() == role) found.add(link);
        return found;
    }

    /** The roles the mini-game has no pipe of, among those it needs to be played in {@code format}. */
    public List<MiniGamePipeRole> missing(MiniGameFormat format) {
        List<MiniGamePipeRole> missing = new ArrayList<>();
        for (MiniGamePipeRole role : format.neededRoles()) {
            if (pipes(role).isEmpty()) missing.add(role);
        }
        return missing;
    }

    /** @return true if the players of a mini-game played in {@code format} all have a pipe to come out of. */
    public boolean hasPipesFor(MiniGameFormat format) {
        return format != null && missing(format).isEmpty();
    }

    /**
     * The format the mini-game is played in by a composition: among its formats that fit (and have their pipes), the
     * most specific, the first one when equal; -1 for none.
     *
     * @param counts the players of each team (a single count: everyone on one side)
     */
    public int formatFor(List<Integer> counts) {
        int best = -1;
        for (int i = 0; i < formats.size(); i++) {
            MiniGameFormat format = formats.get(i);
            if (!format.matches(counts) || !hasPipesFor(format)) continue;
            if (best < 0 || format.specificity() < formats.get(best).specificity()) best = i;
        }
        return best;
    }

    /** @return true if the party controller can draw this mini-game for these teams. */
    public boolean isPlayable(List<Integer> counts) {
        return formatFor(counts) >= 0;
    }

    /** @return true if the mini-game can be played in at least one of its formats (it has their pipes). */
    public boolean isPlayable() {
        for (MiniGameFormat format : formats) if (hasPipesFor(format)) return true;
        return false;
    }

    /** The format at {@code index}, null when there is none there. */
    public @Nullable MiniGameFormat format(int index) {
        return index >= 0 && index < formats.size() ? formats.get(index) : null;
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
        return new MiniGamePageData(newId, title, description, image, formats, pipeLinks, randomRoles, intro, podiumLinks);
    }

    public MiniGamePageData withTexts(String newTitle, String newDescription) {
        return new MiniGamePageData(id, newTitle, newDescription, image, formats, pipeLinks, randomRoles, intro, podiumLinks);
    }

    public MiniGamePageData withImage(@Nullable MiniGamePageImage newImage) {
        return new MiniGamePageData(id, title, description, newImage, formats, pipeLinks, randomRoles, intro, podiumLinks);
    }

    public MiniGamePageData withFormats(List<MiniGameFormat> newFormats) {
        return new MiniGamePageData(id, title, description, image, newFormats, pipeLinks, randomRoles, intro, podiumLinks);
    }

    public MiniGamePageData withPipeLinks(List<MiniGamePipeLink> links) {
        return new MiniGamePageData(id, title, description, image, formats, links, randomRoles, intro, podiumLinks);
    }

    /** The players of {@code role} are sent to a pipe picked at random ({@code random}), or to each pipe in turn. */
    public MiniGamePageData withRandom(MiniGamePipeRole role, boolean random) {
        EnumSet<MiniGamePipeRole> roles = EnumSet.noneOf(MiniGamePipeRole.class);
        roles.addAll(randomRoles);
        if (random) roles.add(role);
        else roles.remove(role);
        return new MiniGamePageData(id, title, description, image, formats, pipeLinks, roles, intro, podiumLinks);
    }

    public MiniGamePageData withIntro(List<MiniGameIntroShot> shots) {
        return new MiniGamePageData(id, title, description, image, formats, pipeLinks, randomRoles, shots, podiumLinks);
    }

    public MiniGamePageData withPodiumLinks(List<MiniGamePodiumLink> links) {
        return new MiniGamePageData(id, title, description, image, formats, pipeLinks, randomRoles, intro, links);
    }

    /** The index of the link to the block at {@code pos}, -1 if it is not linked. */
    public int podiumLinkIndex(net.minecraft.util.math.GlobalPos pos) {
        for (int i = 0; i < podiumLinks.size(); i++) if (podiumLinks.get(i).pos().equals(pos)) return i;
        return -1;
    }

    /** @return true if a podium is linked to the page: its mini-game says who won. */
    public boolean hasPodium() {
        for (MiniGamePodiumLink link : podiumLinks) if (link.kind() == MiniGamePodiumLink.Kind.PODIUM) return true;
        return false;
    }

    /** @return true if the players of {@code role} are sent to a pipe picked at random. */
    public boolean isRandom(MiniGamePipeRole role) {
        return randomRoles.contains(role);
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
        for (int i = 0; i < description.length() && clean.length() < MAX_DESCRIPTION_STORED; i++) {
            char c = description.charAt(i);
            if (c == '\n') {
                if (lines >= MAX_DESCRIPTION_LINES) break;
                lines++;
                clean.append(c);
            } else if (!Character.isISOControl(c) && c != '§') {
                clean.append(c);
            }
        }
        String text = clean.toString().stripTrailing();
        // At most the characters shown that the editor lets type
        if (MiniGameText.strip(text).length() > MAX_DESCRIPTION_LENGTH) {
            RichText rich = RichText.fromCodes(text);
            rich.truncate(MAX_DESCRIPTION_LENGTH);
            text = rich.toCodes().stripTrailing();
        }
        return text;
    }

    // ------------------------------------------------------------------ saving

    public NbtCompound toNbt() {
        NbtCompound nbt = new NbtCompound();
        nbt.putInt("Format", FORMAT);
        nbt.putUuid("Id", id);
        nbt.putString("Title", title);
        nbt.putString("Description", description);
        if (image != null) nbt.put("Image", image.toNbt());
        NbtList formatsNbt = new NbtList();
        formats.forEach(format -> formatsNbt.add(format.toNbt()));
        nbt.put("Formats", formatsNbt);
        if (!pipeLinks.isEmpty()) {
            NbtList links = new NbtList();
            pipeLinks.forEach(link -> links.add(link.toNbt()));
            nbt.put("PipeLinks", links);
        }
        if (!randomRoles.isEmpty()) {
            int mask = 0;
            for (MiniGamePipeRole role : randomRoles) mask |= 1 << role.ordinal();
            nbt.putInt("RandomRoles", mask);
        }
        if (!intro.isEmpty()) {
            NbtList shots = new NbtList();
            intro.forEach(shot -> shots.add(shot.toNbt()));
            nbt.put("Intro", shots);
        }
        if (!podiumLinks.isEmpty()) {
            NbtList podiums = new NbtList();
            podiumLinks.forEach(link -> podiums.add(link.toNbt()));
            nbt.put("Podiums", podiums);
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
        List<MiniGameIntroShot> shots = new ArrayList<>();
        NbtList shotsNbt = nbt.getList("Intro", NbtElement.COMPOUND_TYPE);
        for (int i = 0; i < shotsNbt.size(); i++) {
            MiniGameIntroShot shot = MiniGameIntroShot.fromNbt(shotsNbt.getCompound(i));
            if (shot != null) shots.add(shot);
        }
        List<MiniGamePodiumLink> podiums = new ArrayList<>();
        NbtList podiumsNbt = nbt.getList("Podiums", NbtElement.COMPOUND_TYPE);
        for (int i = 0; i < podiumsNbt.size(); i++) {
            MiniGamePodiumLink link = MiniGamePodiumLink.fromNbt(podiumsNbt.getCompound(i));
            if (link != null) podiums.add(link);
        }
        List<MiniGameFormat> formats = new ArrayList<>();
        if (nbt.contains("Formats", NbtElement.LIST_TYPE)) {
            NbtList formatsNbt = nbt.getList("Formats", NbtElement.COMPOUND_TYPE);
            for (int i = 0; i < formatsNbt.size(); i++) formats.add(MiniGameFormat.fromNbt(formatsNbt.getCompound(i)));
        } else {
            // Saved before the formats: from its ways to play and its players range
            formats.addAll(MiniGameFormat.migrate(nbt.getInt("Modes"), nbt.contains("MinPlayers") ? nbt.getInt("MinPlayers") : 1,
                    nbt.contains("MaxPlayers") ? nbt.getInt("MaxPlayers") : MAX_PLAYERS_OLD));
        }
        return new MiniGamePageData(nbt.getUuid("Id"), nbt.getString("Title"), nbt.getString("Description"),
                nbt.contains("Image", NbtElement.COMPOUND_TYPE) ? MiniGamePageImage.fromNbt(nbt.getCompound("Image")) : null,
                formats, links, rolesFromMask(nbt.getInt("RandomRoles")), shots, podiums);
    }

    private static Set<MiniGamePipeRole> rolesFromMask(int mask) {
        EnumSet<MiniGamePipeRole> roles = EnumSet.noneOf(MiniGamePipeRole.class);
        for (MiniGamePipeRole role : MiniGamePipeRole.values()) if ((mask & (1 << role.ordinal())) != 0) roles.add(role);
        return roles;
    }

    private void write(PacketByteBuf buf) {
        buf.writeUuid(id);
        buf.writeString(title, MAX_TITLE_LENGTH);
        buf.writeString(description, MAX_DESCRIPTION_STORED);
        buf.writeBoolean(image != null);
        if (image != null) image.write(buf);
        MiniGameFormat.writeList(buf, formats);
        buf.writeVarInt(pipeLinks.size());
        pipeLinks.forEach(link -> link.write(buf));
        int mask = 0;
        for (MiniGamePipeRole role : randomRoles) mask |= 1 << role.ordinal();
        buf.writeVarInt(mask);
        buf.writeVarInt(intro.size());
        intro.forEach(shot -> shot.write(buf));
        buf.writeVarInt(podiumLinks.size());
        podiumLinks.forEach(link -> link.write(buf));
    }

    private static MiniGamePageData read(PacketByteBuf buf) {
        UUID id = buf.readUuid();
        String title = buf.readString(MAX_TITLE_LENGTH);
        String description = buf.readString(MAX_DESCRIPTION_STORED);
        MiniGamePageImage image = buf.readBoolean() ? MiniGamePageImage.read(buf) : null;
        List<MiniGameFormat> formats = MiniGameFormat.readList(buf);
        int count = Math.min(buf.readVarInt(), MAX_PIPE_LINKS);
        List<MiniGamePipeLink> links = new ArrayList<>(count);
        for (int i = 0; i < count; i++) links.add(MiniGamePipeLink.read(buf));
        Set<MiniGamePipeRole> random = rolesFromMask(buf.readVarInt());
        int shotCount = Math.min(buf.readVarInt(), MAX_INTRO_SHOTS);
        List<MiniGameIntroShot> shots = new ArrayList<>(shotCount);
        for (int i = 0; i < shotCount; i++) shots.add(MiniGameIntroShot.read(buf));
        int podiumCount = Math.min(buf.readVarInt(), MAX_PODIUM_LINKS);
        List<MiniGamePodiumLink> podiums = new ArrayList<>(podiumCount);
        for (int i = 0; i < podiumCount; i++) podiums.add(MiniGamePodiumLink.read(buf));
        return new MiniGamePageData(id, title, description, image, formats, links, random, shots, podiums);
    }
}
