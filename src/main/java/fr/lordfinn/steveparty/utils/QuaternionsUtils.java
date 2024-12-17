package fr.lordfinn.steveparty.utils;

public class QuaternionsUtils {

    public static float dirToYAngle(double x, double z) {
        double angle = Math.PI - Math.atan2(z, x) - 90 * Math.PI / 180;
        if (angle < 0) {
            angle += 2 * Math.PI;
        }
        return (float) angle;
    }

    public static float dirToXAngle(double y) {
        double angle = 90 * y - 90;
        return (float) Math.toRadians(angle);
    }
}
