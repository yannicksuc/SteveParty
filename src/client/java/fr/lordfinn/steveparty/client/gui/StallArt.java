package fr.lordfinn.steveparty.client.gui;

import fr.lordfinn.steveparty.Steveparty;
import fr.lordfinn.steveparty.items.custom.cartridges.ShopCartridgeItem;
import fr.lordfinn.steveparty.items.custom.cartridges.ShopStock;
import fr.lordfinn.steveparty.items.custom.cartridges.menu.StallOffersModule;
import fr.lordfinn.steveparty.screen_handlers.custom.ShopStallScreenHandler;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.item.ItemStack;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.Identifier;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

/**
 * The Trading Stall's look (its screen's texture, {@code trading_stall.png}), shared by the stall block's screen, the
 * Shop Cartridge's stall ({@link fr.lordfinn.steveparty.client.screens.ShopStallScreen}) and the small stall of its
 * menu: the sold row over the yellow and white awning, an arrow per column (the way the item goes, to the customer),
 * the two price rows under it with their coin marks. Coordinates are the texture's pixels, from its top-left corner.
 */
public final class StallArt {
    public static final Identifier TEXTURE = Steveparty.id("textures/gui/trading_stall.png");
    /** The stall's body edges (the texture's), to close its bottom on the left, where the texture holds the inventory's tab. */
    private static final int EDGE = 0xFF3D1C04, BEVEL = 0xFF68340C, LIGHT = 0xFFB97333;
    /** The texture rows drawn as they are (under them, its left part holds the inventory's tab). */
    private static final int TOP_ROWS = 88;
    /** The stall's bottom rows: the texture's from this x on, its left corner redrawn. */
    private static final int BOTTOM_FROM = 70;
    /** Where a column's stock is written: on its arrow's shaft. */
    public static final int STOCK_Y = 41;
    private static final int STOCK = 0xFFFFFFFF, STOCK_OUT = 0xFFFF5555, STOCK_INFINITE = 0xFFFFD34A;
    private static final String SLOT_KEY = "gui.steveparty.trading_stall.slot.";
    private static final String SHOP_KEY = "gui.steveparty.shop_stall.";

    private StallArt() {
    }

    /**
     * The stall's top, {@link StallOffersModule#STALL_W} × {@link StallOffersModule#STALL_H}: its slots, awning, arrows
     * and price rows, its bottom edge closed under them.
     */
    public static void drawTop(DrawContext context, int x, int y) {
        context.drawTexture(TEXTURE, x, y, 0, 0, StallOffersModule.STALL_W, TOP_ROWS, 256, 256);
        // Its bottom as on the stall's screen: three more rows of wood, its light edge, its bevel and edge, rounded
        for (int row = TOP_ROWS; row < TOP_ROWS + 3; row++)
            context.drawTexture(TEXTURE, x, y + row, 0, TOP_ROWS - 1, 180, 1, 256, 256);
        int[] right = {180, 179, 178, 177};
        for (int i = 0; i < 4; i++) {
            int row = TOP_ROWS + 3 + i;
            context.drawTexture(TEXTURE, x + BOTTOM_FROM, y + row, BOTTOM_FROM, row, right[i] - BOTTOM_FROM, 1, 256, 256);
        }
        int b = y + TOP_ROWS + 3, end = x + BOTTOM_FROM;
        context.fill(x + 4, b, x + 5, b + 1, EDGE);
        context.fill(x + 5, b, x + 8, b + 1, BEVEL);
        context.fill(x + 8, b, end, b + 1, LIGHT);
        context.fill(x + 5, b + 1, x + 6, b + 2, EDGE);
        context.fill(x + 6, b + 1, end, b + 2, BEVEL);
        context.fill(x + 6, b + 2, x + 7, b + 3, EDGE);
        context.fill(x + 7, b + 2, end, b + 3, BEVEL);
        context.fill(x + 7, b + 3, end, b + 4, EDGE);
    }

    /** Where slot {@code slot} (a stall's order: prices, second prices, items sold) draws its item, from the stall's corner. */
    public static int slotX(int slot) {
        return ShopStallScreenHandler.SLOTS_X + (slot % ShopCartridgeItem.OFFERS) * ShopStallScreenHandler.SLOT;
    }

    public static int slotY(int slot) {
        int row = slot / ShopCartridgeItem.OFFERS;
        return row == 2 ? ShopStallScreenHandler.SOLD_Y : ShopStallScreenHandler.PRICE_Y + row * ShopStallScreenHandler.SLOT;
    }

    /** The centre of column {@code column}'s arrow. */
    public static int arrowX(int column) {
        return ShopStallScreenHandler.SLOTS_X + column * ShopStallScreenHandler.SLOT + 8;
    }

    /** The offers' items (copies, a stall's order), each with its count. */
    public static void drawItems(DrawContext context, TextRenderer font, List<ItemStack> slots, int x, int y) {
        for (int i = 0; i < slots.size(); i++) {
            ItemStack stack = slots.get(i);
            if (stack.isEmpty()) continue;
            context.drawItem(stack, x + slotX(i), y + slotY(i));
            context.drawItemInSlot(font, stack, x + slotX(i), y + slotY(i));
        }
    }

    /** Column {@code column}'s stock on its arrow: white, red when sold out, gold « ∞ »; nothing without an offer or a storage. */
    public static void drawStock(DrawContext context, TextRenderer font, int x, int y, int column, int stock) {
        String label = ShopStock.label(stock);
        if (label == null) return;
        int color = stock == ShopStock.INFINITE ? STOCK_INFINITE : stock == 0 ? STOCK_OUT : STOCK;
        int width = Math.min(font.getWidth(label), ShopStallScreenHandler.SLOT);
        // Over the item layer of the slots above and below
        context.getMatrices().push();
        context.getMatrices().translate(0, 0, 200);
        UiText.centered(context, font, label, x + arrowX(column) - ShopStallScreenHandler.SLOT / 2, y + STOCK_Y,
                ShopStallScreenHandler.SLOT, color, true);
        context.getMatrices().pop();
    }

    /** What column {@code column}'s stock means, for its tooltip (null: no offer there). */
    public static @Nullable List<Text> stockHelp(int stock) {
        if (stock == ShopStock.NO_OFFER) return null;
        List<Text> lines = new ArrayList<>();
        if (stock == ShopStock.NO_STORAGE) {
            lines.add(Text.translatable(SHOP_KEY + "stock.none").formatted(Formatting.RED));
        } else if (stock == ShopStock.INFINITE) {
            lines.add(Text.translatable(SHOP_KEY + "stock.infinite").formatted(Formatting.GOLD));
        } else {
            lines.add(Text.translatable(SHOP_KEY + "stock", stock).formatted(stock == 0 ? Formatting.RED : Formatting.GOLD));
        }
        return lines;
    }

    /**
     * What a slot of the Shop Cartridge's stall is (a stall's order: 0-8 the price, 9-17 the second price, 18-26 the item
     * sold), the stall block's texts, and that it is a model.
     */
    public static List<Text> slotHelp(int slot) {
        String role = switch (slot / ShopCartridgeItem.OFFERS) {
            case 0 -> "price";
            case 1 -> "second_price";
            default -> "sold";
        };
        return List.of(Text.translatable(SLOT_KEY + role).formatted(Formatting.GOLD),
                Text.translatable(SLOT_KEY + role + ".help").formatted(Formatting.GRAY),
                Text.translatable(SHOP_KEY + "models").formatted(Formatting.DARK_GRAY, Formatting.ITALIC));
    }
}
