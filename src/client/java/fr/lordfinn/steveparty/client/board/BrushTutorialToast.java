package fr.lordfinn.steveparty.client.board;

import fr.lordfinn.steveparty.board.BrushTutorialSteps;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.toast.Toast;
import net.minecraft.client.toast.ToastManager;
import net.minecraft.item.ItemStack;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.MathHelper;

/**
 * One step of the Tile Linker Brush's tutorial, as a vanilla tutorial toast (the movement keys' one): its frame, the
 * brush for icon, a purple title, a black line, and a progress bar for a step counted in several actions. It comes in
 * and goes out with the vanilla toast sounds ({@link Toast.Visibility}); done, its bar fills, then it leaves.
 */
final class BrushTutorialToast implements Toast {
    private static final Identifier TEXTURE = Identifier.ofVanilla("toast/tutorial");
    /** The vanilla tutorial toast's colours: title, line, progress (filling, emptying). */
    private static final int TITLE = 0xFF500050, LINE = 0xFF000000, BAR_BACK = 0xFFFFFFFF, BAR_UP = 0xFF005500, BAR_DOWN = 0xFF550000;
    /** How long a done step stays shown, its bar full (ms). */
    private static final long DONE_SHOWN = 1200;

    final BrushTutorialSteps.Step step;
    private final ItemStack icon;
    private final Text title, line;
    private final boolean progressBar;
    private float progress, shownProgress;
    private long lastTime = -1, doneAt = -1;
    private Toast.Visibility visibility = Toast.Visibility.SHOW;

    BrushTutorialToast(BrushTutorialSteps.Step step, ItemStack icon, Text title, Text line, boolean progressBar, float progress) {
        this.step = step;
        this.icon = icon;
        this.title = title;
        this.line = line;
        this.progressBar = progressBar;
        this.progress = this.shownProgress = progress;
    }

    @Override
    public Toast.Visibility draw(DrawContext context, ToastManager manager, long startTime) {
        context.drawGuiTexture(TEXTURE, 0, 0, getWidth(), getHeight());
        context.drawItem(icon, 8, 8);
        TextRenderer font = manager.getClient().textRenderer;
        context.drawText(font, title, 30, 7, TITLE, false);
        context.drawText(font, line, 30, 18, LINE, false);
        if (progressBar) {
            context.fill(3, 28, 157, 29, BAR_BACK);
            if (lastTime < 0) lastTime = startTime;
            float shown = MathHelper.clampedLerp(shownProgress, progress, (startTime - lastTime) / 100f);
            context.fill(3, 28, (int) (3 + 154 * shown), 29, progress >= shownProgress ? BAR_UP : BAR_DOWN);
            shownProgress = shown;
            lastTime = startTime;
        }
        if (doneAt >= 0 && startTime - doneAt > DONE_SHOWN) visibility = Toast.Visibility.HIDE;
        if (doneAt == -2) doneAt = startTime;
        return visibility;
    }

    /** The step counted this far (0 to 1). */
    void progress(float progress) {
        this.progress = MathHelper.clamp(progress, 0, 1);
    }

    /** The step done: its bar full, it leaves a moment later. */
    void done() {
        progress(1);
        if (doneAt == -1) doneAt = -2;
    }

    /** Put away at once (the brush left the hand). */
    void hide() {
        visibility = Toast.Visibility.HIDE;
    }

    boolean hidden() {
        return visibility == Toast.Visibility.HIDE;
    }
}
