package fr.lordfinn.steveparty.particles;

import fr.lordfinn.steveparty.Steveparty;
import net.minecraft.util.math.Vec3d;
import org.joml.Vector3d;
import org.joml.Vector3f;

import java.awt.*;

import static java.lang.Math.min;

public class ParticleUtils {

    /**
     * Encodes color (r, g, b) and distance (x, y, z) into a single Vector3f.
     * The distance values are clamped to two decimals, and the color is added
     * after the second decimal.
     *
     * @param color  The color to encode.
     * @param distX  X component of the distance vector.
     * @param distY  Y component of the distance vector.
     * @param distZ  Z component of the distance vector.
     * @return The encoded Vector3f.
     */
    public static Vec3d encodeVelocity(Color color, float distX, float distY, float distZ) {
        // Clamp distance to two decimals
        int clampedX = (int) Math.floor(distX * 100);
        int clampedY = (int) Math.floor(distY * 100);
        int clampedZ = (int) Math.floor(distZ * 100);

        // Add color information after two decimals
        float encodedX = clampedX + ((float) color.getRed() / 1000);
        float encodedY = clampedY + ((float) color.getGreen() / 1000);
        float encodedZ = clampedZ + ((float) color.getBlue() / 1000);

        return new Vec3d(encodedX, encodedY, encodedZ);
    }

    /**
     * Decodes the encoded Vector3f into color (r, g, b) and distance (x, y, z).
     *
     * @param encoded The encoded Vector3d.
     * @return A two-dimensional array: [0] = {r, g, b}, [1] = {x, y, z}.
     */
    public static float[][] decodeVelocity(Vector3d encoded) {
        // Extract the clamped distance values
        float distX = (float) Math.floor(encoded.x());
        float distY = (float) Math.floor(encoded.y());
        float distZ = (float) Math.floor(encoded.z());

        // Extract color information from the remaining fractional part
        float colorR = min(1f,(((float)encoded.x() - distX) * 1000.0f) / 255f);
        float colorG = min(1f,(((float)encoded.y() - distY) * 1000.0f) / 255f);
        float colorB = min(1f,(((float)encoded.z() - distZ) * 1000.0f) / 255f);

        return new float[][]{
                {colorR, colorG, colorB},
                {distX / 100, distY / 100, distZ / 100}
        };
    }

    public static float getFadingAlpha(int age, int maxAge, float startFadingPercentage, float endFadingPercentage) {
        float ageFraction = (float) age / (float) maxAge;

        if (ageFraction < startFadingPercentage) {
            // Fade-in effect during the first 20%
            return ageFraction / startFadingPercentage;
        } else if (ageFraction > (1-endFadingPercentage)) {
            // Fade-out effect during the last 20%
            return 1 - ((ageFraction - (1-endFadingPercentage)) / endFadingPercentage);
        }
        // No change in alpha during the middle 60%
        return 1.0f;
    }


}
