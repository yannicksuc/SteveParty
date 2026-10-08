package fr.lordfinn.steveparty.entities.custom.boomcart;

import net.minecraft.nbt.NbtCompound;
import org.jetbrains.annotations.Nullable;

import java.util.UUID;

/**
 * The Boomcart's fuse, the hot potato's rules (docs/petaroule.md):
 * <ul>
 *     <li>Lit with flint and steel: {@link #FUSE_TICKS} before it blows.</li>
 *     <li>Passed on (flint and steel again, by someone else): the fuse gets longer, less each time: +3 s, then +2 s,
 *     then +1 s, then nothing more ({@link #EXTENSIONS}). It always blows in the end.</li>
 *     <li>Never twice in a row: whoever lit it or passed it last can't pass it again until someone else has.</li>
 * </ul>
 */
public final class BoomcartFuse {
    /** Lit, it blows after this long (ticks). */
    public static final int FUSE_TICKS = 8 * 20;
    /** Each pass's extension, in order (ticks); none after the last. */
    public static final int[] EXTENSIONS = {3 * 20, 2 * 20, 20};

    public enum Result {
        /** Lit: the fuse starts burning. */
        LIT,
        /** Passed on to someone else: the fuse got {@link #extension()} longer (maybe 0). */
        PASSED,
        /** Refused: the same player again, before anyone else. */
        SAME_PLAYER
    }

    private int ticks = -1;
    private int total;
    private int passes;
    private int lastExtension;
    private @Nullable UUID last;

    /** Ticks left, -1 while unlit. */
    public int ticks() {
        return ticks;
    }

    /** Ticks it had in all: lit plus every extension. */
    public int total() {
        return total;
    }

    public boolean isLit() {
        return ticks >= 0;
    }

    /** Who lit it or passed it last, null while unlit. */
    public @Nullable UUID last() {
        return last;
    }

    /** The last pass's extension (ticks). */
    public int extension() {
        return lastExtension;
    }

    /** Flint and steel by {@code who}: lights it, or passes it on. */
    public Result strike(UUID who) {
        if (!isLit()) {
            ticks = total = FUSE_TICKS;
            passes = 0;
            lastExtension = 0;
            last = who;
            return Result.LIT;
        }
        if (who.equals(last)) return Result.SAME_PLAYER;
        lastExtension = passes < EXTENSIONS.length ? EXTENSIONS[passes] : 0;
        passes++;
        ticks += lastExtension;
        total += lastExtension;
        last = who;
        return Result.PASSED;
    }

    /** A tick of burning: true when it blows. */
    public boolean tick() {
        if (ticks < 0) return false;
        if (ticks > 0) ticks--;
        return ticks == 0;
    }

    public void write(NbtCompound nbt) {
        if (!isLit()) return;
        NbtCompound fuse = new NbtCompound();
        fuse.putInt("Ticks", ticks);
        fuse.putInt("Total", total);
        fuse.putInt("Passes", passes);
        if (last != null) fuse.putUuid("Last", last);
        nbt.put("Fuse", fuse);
    }

    public void read(NbtCompound nbt) {
        if (!nbt.contains("Fuse")) {
            ticks = -1;
            total = passes = 0;
            last = null;
            return;
        }
        NbtCompound fuse = nbt.getCompound("Fuse");
        ticks = Math.max(1, fuse.getInt("Ticks"));
        total = Math.max(ticks, fuse.getInt("Total"));
        passes = fuse.getInt("Passes");
        last = fuse.containsUuid("Last") ? fuse.getUuid("Last") : null;
    }
}
