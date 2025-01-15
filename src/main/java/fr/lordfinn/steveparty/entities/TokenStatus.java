package fr.lordfinn.steveparty.entities;

import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.PacketCodec;

public final class TokenStatus {
    // Define the bitmask values
    public static final int IN_GAME = 1;   // 0b0001
    public static final int CAN_MOVE = 1 << 1;   // 0b0100

    private TokenStatus() {
        // Private constructor to prevent instantiation
    }

    // Set a specific status (add a flag)
    public static int setStatus(int currentStatus, int flag) {
        return currentStatus | flag; // Use bitwise OR to add a flag
    }

    // Set multiple statuses at once
    public static int setStatuses(int currentStatus, int... flags) {
        for (int flag : flags) {
            currentStatus |= flag;
        }
        return currentStatus;
    }

    // Clear a specific status (remove a flag)
    public static int clearStatus(int currentStatus, int flag) {
        return currentStatus & ~flag; // Use bitwise AND and NOT to remove a flag
    }

    // Clear multiple statuses at once
    public static int clearStatuses(int currentStatus, int... flags) {
        for (int flag : flags) {
            currentStatus &= ~flag;
        }
        return currentStatus;
    }

    // Toggle a specific status (flip the flag)
    public static int toggleStatus(int currentStatus, int flag) {
        return currentStatus ^ flag; // Use bitwise XOR to toggle a flag
    }

    // Toggle multiple statuses at once
    public static int toggleStatuses(int currentStatus, int... flags) {
        for (int flag : flags) {
            currentStatus ^= flag;
        }
        return currentStatus;
    }

    // Check if a specific status is set
    public static boolean hasStatus(int currentStatus, int flag) {
        return (currentStatus & flag) != 0; // Use bitwise AND to check a flag
    }

    // Check if all specified statuses are set
    public static boolean hasAllStatuses(int currentStatus, int... flags) {
        for (int flag : flags) {
            if (!hasStatus(currentStatus, flag)) {
                return false;
            }
        }
        return true;
    }

    // Check if any of the specified statuses are set
    public static boolean hasAnyStatus(int currentStatus, int... flags) {
        for (int flag : flags) {
            if (hasStatus(currentStatus, flag)) {
                return true;
            }
        }
        return false;
    }

    // Reset all statuses (clear all flags)
    public static int reset() {
        return 0; // Return the default empty status
    }

    // Check if the token is in the game
    public static boolean isInGame(int status) {
        return hasStatus(status, IN_GAME);
    }

    // Check if the token is in the game and can move
    public static boolean canMoveInGame(int status) {
        return hasAllStatuses(status, CAN_MOVE, IN_GAME);
    }
}
