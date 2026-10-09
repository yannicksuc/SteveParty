package fr.lordfinn.steveparty.client.gui;

/** A rectangle of a screen the mouse can be over: its left and top edges are in it, its right and bottom ones are not. */
public record HitArea(int x, int y, int width, int height) {
    /** The mouse is over this area. */
    public boolean contains(double mouseX, double mouseY) {
        return contains(mouseX, mouseY, x, y, width, height);
    }

    /** The mouse is over the {@code width} x {@code height} area at ({@code x}, {@code y}). */
    public static boolean contains(double mouseX, double mouseY, int x, int y, int width, int height) {
        return mouseX >= x && mouseX < x + width && mouseY >= y && mouseY < y + height;
    }
}
