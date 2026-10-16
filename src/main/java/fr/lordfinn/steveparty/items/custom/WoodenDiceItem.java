package fr.lordfinn.steveparty.items.custom;

/**
 * The Wooden Die: the first die, carved from planks. It throws like the Simple Die (and anywhere a die is thrown) but
 * only rolls 1 to 6, and takes neither forged faces nor modules: those stay with the Simple Die and the Dice Forge.
 */
public class WoodenDiceItem extends DefaultDiceItem {
    public static final int MAX = 6;

    public WoodenDiceItem(Settings settings) {
        super(settings);
    }

    @Override
    public int plainMax() {
        return MAX;
    }

    @Override
    public boolean takesModules() {
        return false;
    }

    @Override
    public String plainFaceTexture() {
        return "wooden_dice";
    }
}
