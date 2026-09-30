package fr.lordfinn.steveparty.items.custom.cartridges.menu;

import net.minecraft.item.ItemStack;
import net.minecraft.text.Text;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.function.Function;

/**
 * Read-only lines (a description, a link, a hint), wrapped to the module's width and cut at {@link #lines()} lines,
 * with an optional item icon on their left. Computed by the client when drawn.
 */
public final class InfoModule extends CartridgeModule {
    public static final int LINE_H = 10;
    public static final int ICON = 16;

    /** What the lines are about: the cartridge, the world, and its block (null: in a hand). */
    public record Context(ItemStack stack, World world, @Nullable BlockPos pos) {}

    /** How a line is drawn: plain, soft (a hint), good news, bad news. */
    public enum Tone { NORMAL, SOFT, GOOD, BAD }

    /** A line and its tone. */
    public record Line(Text text, Tone tone) {
        public static Line of(Text text) {
            return new Line(text, Tone.NORMAL);
        }
    }

    private final int lines;
    private final Function<Context, List<Line>> content;
    private final @Nullable Function<ItemStack, ItemStack> icon;

    public InfoModule(String id, @Nullable String labelKey, int lines, Function<Context, List<Line>> content,
                      @Nullable Function<ItemStack, ItemStack> icon) {
        super(id, labelKey);
        if (lines < 1) throw new IllegalArgumentException("an info without lines: " + id);
        this.lines = lines;
        this.content = content;
        this.icon = icon;
    }

    public InfoModule(String id, @Nullable String labelKey, int lines, Function<Context, List<Line>> content) {
        this(id, labelKey, lines, content, null);
    }

    public int lines() {
        return lines;
    }

    public List<Line> content(Context context) {
        return content.apply(context);
    }

    /** The icon for {@code stack}, or null / empty: none. */
    public @Nullable ItemStack icon(ItemStack stack) {
        return icon == null ? null : icon.apply(stack);
    }

    public boolean hasIcon() {
        return icon != null;
    }

    @Override
    public int height() {
        return labelHeight() + Math.max(lines * LINE_H, hasIcon() ? ICON + 2 : 0);
    }
}
