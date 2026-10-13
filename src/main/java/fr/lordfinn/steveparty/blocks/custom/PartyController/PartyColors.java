package fr.lordfinn.steveparty.blocks.custom.PartyController;

import fr.lordfinn.steveparty.entities.TokenizedEntityInterface;
import net.minecraft.entity.Entity;
import net.minecraft.entity.mob.MobEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.text.Text;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.UUID;

/**
 * Each pawn of a party has its own colour (HUD, beams, bases): at the start, a pawn without a colour, or whose colour
 * is the same as (or too close to) one of a pawn before it, is given the first free colour of {@link #PALETTE}.
 */
public final class PartyColors {
    /** The colours given, most distinct first (0xRRGGBB). */
    static final int[] PALETTE = {
            0xE53935, 0x1E88E5, 0x43A047, 0xFDD835, 0x8E24AA, 0xFB8C00, 0x00ACC1, 0xD81B60,
            0x7CB342, 0x3949AB, 0x6D4C41, 0xF06292, 0x26A69A, 0xFFFFFF, 0x9E9E9E, 0x212121
    };
    /** Two colours closer than this (distance in RGB) look the same on the HUD. */
    static final double TOO_CLOSE = 60;

    private PartyColors() {
    }

    /** Gives the pawns {@code tokens} (in their order: the first ones keep theirs) a colour each, all different. */
    public static void makeUnique(ServerWorld world, Collection<UUID> tokens) {
        List<Integer> taken = new ArrayList<>();
        List<MobEntity> recolour = new ArrayList<>();
        for (UUID uuid : tokens) {
            if (!(world.getEntity(uuid) instanceof MobEntity mob) || !(mob instanceof TokenizedEntityInterface)) continue;
            int color = PartyLiveData.colorOf(mob);
            if (color < 0 || isTaken(color, taken)) recolour.add(mob);
            else taken.add(color);
        }
        for (MobEntity mob : recolour) {
            int color = free(taken);
            taken.add(color);
            setColor(mob, color);
        }
    }

    static boolean isTaken(int color, List<Integer> taken) {
        for (int other : taken) if (distance(color, other) < TOO_CLOSE) return true;
        return false;
    }

    /** The first colour of the palette far from all of {@code taken} (the least close one if none is). */
    static int free(List<Integer> taken) {
        int best = PALETTE[0];
        double bestDistance = -1;
        for (int color : PALETTE) {
            double nearest = Double.MAX_VALUE;
            for (int other : taken) nearest = Math.min(nearest, distance(color, other));
            if (nearest >= TOO_CLOSE) return color;
            if (nearest > bestDistance) {
                bestDistance = nearest;
                best = color;
            }
        }
        return best;
    }

    private static double distance(int a, int b) {
        int dr = (a >> 16 & 0xFF) - (b >> 16 & 0xFF), dg = (a >> 8 & 0xFF) - (b >> 8 & 0xFF), db = (a & 0xFF) - (b & 0xFF);
        return Math.sqrt(dr * dr + dg * dg + db * db);
    }

    /** Stores the colour on the pawn and, if it has a name, styles it with it (start tiles read its colour there). */
    private static void setColor(MobEntity mob, int color) {
        ((TokenizedEntityInterface) mob).steveparty$setTokenColor(color);
        Text name = ((Entity) mob).getCustomName();
        if (name != null) mob.setCustomName(Text.literal(name.getString()).withColor(color));
    }
}
