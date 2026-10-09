package fr.lordfinn.steveparty.items.custom.jumpshoes;

import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.util.math.Direction;
import org.jetbrains.annotations.Nullable;

/**
 * What the server keeps of a player's airborne shoe moves, from a reported move until he is back on the ground:
 * whether he used his double jump, slides down a wall, and the height his fall counts from (no fall damage for the
 * height a boosted jump, a double jump or a kick gave, nor while sliding).
 */
public final class JumpShoesState {
    /** Ticks a reported move is trusted although the server still sees the player on the ground (packet order). */
    private static final int FRESH_TICKS = 4;

    /** Fastest fall (blocks since the last check) a wall kick is accepted from. */
    private static final double MAX_KICK_DESCENT = 0.6;

    private double launchY = Double.NaN;
    private double lastY = Double.NaN;
    private boolean doubleUsed;
    private @Nullable Direction slideWall;
    private int fresh;

    /** Checks a move the client reports; true if accepted (then shown to the others). */
    public boolean accept(PlayerEntity player, JumpShoes.Action action, @Nullable Direction side) {
        if (!JumpShoes.wears(player) || player.isSpectator() || player.getAbilities().flying) return false;
        switch (action) {
            case JUMP_2, JUMP_3 -> {
                // a jump off the ground: the ground is still right under the feet
                if (!player.isOnGround() && player.getWorld().isSpaceEmpty(player, player.getBoundingBox().stretch(0, -1.5, 0))) return false;
            }
            case DOUBLE_JUMP -> {
                if (doubleUsed || !JumpShoes.hasDoubleJump(player)) return false;
                doubleUsed = true;
            }
            case SLIDE_START -> {
                if (side == null || !side.getAxis().isHorizontal()
                        || !JumpShoes.touchesWall(player, side, JumpShoes.SERVER_REACH)) return false;
                slideWall = side;
                doubleUsed = false;
            }
            case SLIDE_STOP -> {
                if (slideWall == null) return false;
                slideWall = null;
            }
            case WALL_KICK -> {
                if (side == null || !side.getAxis().isHorizontal()
                        || !JumpShoes.touchesWall(player, side, JumpShoes.SERVER_REACH + 0.4)
                        || fallingFast(player)) return false;
                slideWall = null;
                doubleUsed = false;
            }
        }
        launchY = player.getY();
        lastY = player.getY();
        fresh = FRESH_TICKS;
        return true;
    }

    /** Once a tick: spares the fall damage; false once the player is back on the ground (the state can go). */
    public boolean tick(PlayerEntity player) {
        double y = player.getY();
        double descent = Double.isNaN(lastY) ? 0 : lastY - y;
        lastY = y;
        if (fresh > 0) {
            fresh--;
        } else if (player.isOnGround() || player.isTouchingWater() || player.getAbilities().flying || !JumpShoes.wears(player)) {
            return false;
        }
        // sliding for real (touching the wall, falling slowly): the fall starts again from here
        if (slideWall != null && descent <= JumpShoes.SLIDE_SPEED + 0.1
                && JumpShoes.touchesWall(player, slideWall, JumpShoes.SERVER_REACH)) {
            launchY = y;
        }
        if (!Double.isNaN(launchY)) player.fallDistance = JumpShoes.clampFall(player.fallDistance, launchY, y);
        return true;
    }

    /** A kick off a wall comes from a slide, not from a free fall along it. */
    private boolean fallingFast(PlayerEntity player) {
        return !Double.isNaN(lastY) && lastY - player.getY() > MAX_KICK_DESCENT;
    }

    public boolean doubleUsed() {
        return doubleUsed;
    }

    public boolean sliding() {
        return slideWall != null;
    }
}
