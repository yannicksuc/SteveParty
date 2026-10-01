package fr.lordfinn.steveparty.minigame;

import net.minecraft.server.network.ServerPlayerEntity;
import org.jetbrains.annotations.Nullable;

import java.util.Collection;

/**
 * The introduction of a mini-game: the shots of its page ({@link MiniGamePageData#intro()}), shown one after the
 * other to the players between the countdown and their departure for the pipes.
 * <p>
 * This is where the sequence runs. Pages have no shots yet (nothing writes them), so there is nothing to show and the
 * players leave at once. To build it: for each {@link MiniGameIntroShot} in order, put the viewers in spectator mode
 * at {@code shot.pos()} looking along its yaw and pitch (their game mode and place are given back by the party step
 * at the end, like their return position), send {@code MiniGameText.parse(shot.text())} to their chat when it is not
 * empty, wait {@code shot.ticks()} with {@code Steveparty.SCHEDULER} (a task id kept by the step so that ending the
 * step cancels it), then run {@code then}.
 */
public final class MiniGameIntro {
    private MiniGameIntro() {
    }

    /** @return true if the page has an introduction to show. */
    public static boolean has(@Nullable MiniGamePageData page) {
        return page != null && !page.intro().isEmpty();
    }

    /**
     * Shows the introduction of {@code page} to {@code viewers}, then runs {@code then} (the departure).
     *
     * @param page null when the mini-game has no page content
     */
    public static void play(@Nullable MiniGamePageData page, Collection<ServerPlayerEntity> viewers, Runnable then) {
        // The shots are not played yet: see the class
        then.run();
    }
}
