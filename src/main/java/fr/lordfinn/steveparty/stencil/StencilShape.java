package fr.lordfinn.steveparty.stencil;

import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

/**
 * A stencil shape: a 16x16 grid of pixels stored column by column ({@code shape[x * 16 + y]}, x to the right,
 * y downwards), 1 for a cut-out pixel (where the paint goes), 0 elsewhere.
 * <p>
 * Every method returns a new array: shapes are shared (item components, block entities, texture caches) and must
 * never be modified in place once handed out.
 */
public final class StencilShape {
    public static final int SIDE = 16;
    public static final int SIZE = SIDE * SIDE;

    private StencilShape() {
    }

    public static int index(int x, int y) {
        return x * SIDE + y;
    }

    public static boolean isValid(@Nullable byte[] shape) {
        return shape != null && shape.length == SIZE;
    }

    /** @return true if no pixel is set (or the shape is invalid). */
    public static boolean isBlank(@Nullable byte[] shape) {
        if (!isValid(shape)) return true;
        for (byte b : shape) {
            if (b != 0) return false;
        }
        return true;
    }

    public static boolean get(byte[] shape, int x, int y) {
        return x >= 0 && x < SIDE && y >= 0 && y < SIDE && shape[index(x, y)] != 0;
    }

    public static byte[] blank() {
        return new byte[SIZE];
    }

    public static byte[] full() {
        return filled(true);
    }

    public static byte[] filled(boolean on) {
        byte[] shape = new byte[SIZE];
        if (on) java.util.Arrays.fill(shape, (byte) 1);
        return shape;
    }

    /** @return a valid copy: values normalised to 0/1, null or wrong sized input gives a blank shape. */
    public static byte[] sanitize(@Nullable byte[] shape) {
        byte[] result = new byte[SIZE];
        if (!isValid(shape)) return result;
        for (int i = 0; i < SIZE; i++) result[i] = (byte) (shape[i] != 0 ? 1 : 0);
        return result;
    }

    /** Builds a shape from 16 rows of 16 characters, top row first: {@code #} sets a pixel. */
    public static byte[] fromRows(String... rows) {
        if (rows.length != SIDE) throw new IllegalArgumentException("A stencil has " + SIDE + " rows, got " + rows.length);
        byte[] shape = new byte[SIZE];
        for (int y = 0; y < SIDE; y++) {
            String row = rows[y];
            if (row.length() != SIDE) throw new IllegalArgumentException("Row " + y + " must have " + SIDE + " characters: " + row);
            for (int x = 0; x < SIDE; x++) {
                if (row.charAt(x) == '#') shape[index(x, y)] = 1;
            }
        }
        return shape;
    }

    public static byte[] invert(byte[] shape) {
        byte[] result = new byte[SIZE];
        for (int i = 0; i < SIZE; i++) result[i] = (byte) (shape[i] != 0 ? 0 : 1);
        return result;
    }

    /** Left-right mirror. */
    public static byte[] mirrorHorizontal(byte[] shape) {
        byte[] result = new byte[SIZE];
        for (int x = 0; x < SIDE; x++)
            for (int y = 0; y < SIDE; y++) result[index(SIDE - 1 - x, y)] = shape[index(x, y)];
        return result;
    }

    /** Top-bottom mirror. */
    public static byte[] mirrorVertical(byte[] shape) {
        byte[] result = new byte[SIZE];
        for (int x = 0; x < SIDE; x++)
            for (int y = 0; y < SIDE; y++) result[index(x, SIDE - 1 - y)] = shape[index(x, y)];
        return result;
    }

    /** Quarter turn clockwise (as seen on screen, y downwards). */
    public static byte[] rotateClockwise(byte[] shape) {
        byte[] result = new byte[SIZE];
        for (int x = 0; x < SIDE; x++)
            for (int y = 0; y < SIDE; y++) result[index(SIDE - 1 - y, x)] = shape[index(x, y)];
        return result;
    }

    /** Moves every pixel by (dx, dy); pixels pushed out are lost, the uncovered side is left empty. */
    public static byte[] shift(byte[] shape, int dx, int dy) {
        byte[] result = new byte[SIZE];
        for (int x = 0; x < SIDE; x++) {
            for (int y = 0; y < SIDE; y++) {
                int nx = x + dx, ny = y + dy;
                if (nx >= 0 && nx < SIDE && ny >= 0 && ny < SIDE) result[index(nx, ny)] = shape[index(x, y)];
            }
        }
        return result;
    }

    public static List<Byte> toList(byte[] shape) {
        List<Byte> list = new ArrayList<>(shape.length);
        for (byte b : shape) list.add(b);
        return List.copyOf(list);
    }

    public static @Nullable byte[] fromList(@Nullable List<Byte> list) {
        if (list == null) return null;
        byte[] shape = new byte[list.size()];
        for (int i = 0; i < shape.length; i++) shape[i] = list.get(i);
        return shape;
    }
}
