package fr.lordfinn.steveparty.client.screens;

import fr.lordfinn.steveparty.Steveparty;
import fr.lordfinn.steveparty.blocks.ModBlocks;
import fr.lordfinn.steveparty.blocks.custom.PartyController.PartyMoment;
import fr.lordfinn.steveparty.items.ModItems;
import fr.lordfinn.steveparty.utils.Argb;
import net.minecraft.client.MinecraftClient;
import net.minecraft.component.DataComponentTypes;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.render.DiffuseLighting;
import net.minecraft.client.render.LightmapTextureManager;
import net.minecraft.client.render.OverlayTexture;
import net.minecraft.client.render.RenderLayer;
import net.minecraft.client.render.TexturedRenderLayers;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.render.model.BakedModel;
import net.minecraft.client.render.model.json.ModelTransformationMode;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.item.ItemConvertible;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.util.Identifier;

import java.util.EnumMap;
import java.util.Map;

/**
 * What shows a Party Antenna's moments: an item of the game for each (the item says what, the button's colour says the
 * family), with a small badge where the item alone does not say it (a gain, a loss, a stop, done). The badges and the
 * screen's controls come from {@code antenna_icons.png} (16 x 16 cells: badges 0-3, controls from 32).
 */
public final class AntennaIcons {
    public static final Identifier SHEET = Steveparty.id("textures/gui/antenna_icons.png");
    public static final int BADGE_PLUS = 0, BADGE_MINUS = 1, BADGE_CHECK = 3;
    /** The hourglass, and the same off (grey sand): the screen's hold toggle. */
    public static final int WAIT_OFF = 32, WAIT = 33, MINUS = 38, PLUS = 39, TEST = 40;

    /**
     * Its item, its badge (-1: none), a size for an item drawn small in its own icon (the die), and whether it is lit
     * flat (from the front: the Party Star's pale gold reads washed out under the side light of 3D models).
     */
    private record Icon(ItemConvertible item, int badge, float scale, boolean flat) {
    }

    private static final Map<PartyMoment, Icon> ICONS = new EnumMap<>(PartyMoment.class);

    static {
        put(PartyMoment.PARTY_START, ModBlocks.PARTY_CONTROLLER, -1);
        put(PartyMoment.FINAL_ROUNDS, Items.CLOCK, -1);
        put(PartyMoment.PARTY_END, ModItems.END_CARTRIDGE, -1);
        put(PartyMoment.REWIND, ModBlocks.STEP_CONTROLLER, -1);
        put(PartyMoment.ROUND_START, ModItems.PARTY_CARD_TURNS, -1);
        put(PartyMoment.TURN_START, ModItems.FLAG, -1);
        ICONS.put(PartyMoment.DICE_ROLLED, new Icon(ModItems.DEFAULT_DICE, -1, 1.5f, false));
        put(PartyMoment.CROSSROADS, ModBlocks.BOARD_SPACE_REDSTONE_ROUTER, -1);
        put(PartyMoment.PATH_CHOSEN, Items.ARROW, -1);
        put(PartyMoment.TOKEN_LANDS, ModBlocks.TILE, -1);
        put(PartyMoment.TURN_END, ModItems.FLAG, BADGE_CHECK);
        put(PartyMoment.COINS_WON, ModItems.COIN, BADGE_PLUS);
        put(PartyMoment.COINS_LOST, ModItems.COIN, BADGE_MINUS);
        ICONS.put(PartyMoment.STAR_WON, new Icon(ModItems.PARTY_STAR, BADGE_PLUS, 1, true));
        put(PartyMoment.STAR_MOVES, ModItems.STAR_CARTRIDGE, -1);
        put(PartyMoment.NEW_LEADER, Items.GOLDEN_HELMET, -1);
        put(PartyMoment.EVENT, ModItems.EVENT_CARTRIDGE, -1);
        put(PartyMoment.EVENT_OVER, ModItems.EVENT_CARTRIDGE, BADGE_CHECK);
        put(PartyMoment.MINIGAME_CHOSEN, ModItems.MINI_GAME_PAGE, -1);
        put(PartyMoment.PRACTICE_ROUND, Items.TARGET, -1);
        put(PartyMoment.MINIGAME_START, ModItems.MINIGAME_CARTRIDGE, -1);
        put(PartyMoment.MINIGAME_END, ModBlocks.GOLD_PODIUM, -1);
    }

    private static void put(PartyMoment moment, ItemConvertible item, int badge) {
        ICONS.put(moment, new Icon(item, badge, 1, false));
    }

    private AntennaIcons() {
    }

    /** How much bigger than its item its icon is drawn (1: as it is). */
    public static float scale(PartyMoment moment) {
        return ICONS.get(moment).scale();
    }

    /** The moment's item, without its glint (drawn solid, a glint would be drawn into the same buffer). */
    public static ItemStack item(PartyMoment moment) {
        ItemStack stack = new ItemStack(ICONS.get(moment).item());
        stack.set(DataComponentTypes.ENCHANTMENT_GLINT_OVERRIDE, false);
        return stack;
    }

    /** Its badge's cell in the sheet, -1 for none. */
    public static int badge(PartyMoment moment) {
        return ICONS.get(moment).badge();
    }

    /** The moment's item at ({@code x}, {@code y}), and its badge above it. */
    public static void drawMoment(DrawContext context, PartyMoment moment, int x, int y) {
        Icon icon = ICONS.get(moment);
        drawSolid(context, item(moment), x, y, icon.scale(), icon.flat());
        int badge = ICONS.get(moment).badge();
        if (badge < 0) return;
        context.getMatrices().push();
        context.getMatrices().translate(0, 0, 200);
        cell(context, badge, x, y);
        context.getMatrices().pop();
    }

    /**
     * An item as {@link DrawContext#drawItem} draws it, {@code scale} times its size, but solid (its translucent layer
     * drawn cutout: {@link #solid}), lit flat if {@code flat}.
     */
    private static void drawSolid(DrawContext context, ItemStack stack, int x, int y, float scale, boolean flat) {
        MinecraftClient client = MinecraftClient.getInstance();
        BakedModel model = client.getItemRenderer().getModel(stack, client.world, client.player, 0);
        MatrixStack matrices = context.getMatrices();
        matrices.push();
        matrices.translate(x + 8, y + 8, 150);
        matrices.scale(16 * scale, -16 * scale, 16 * scale);
        context.draw();
        boolean front = flat || !model.isSideLit();
        if (front) DiffuseLighting.disableGuiDepthLighting();
        VertexConsumerProvider consumers = context.getVertexConsumers();
        client.getItemRenderer().renderItem(stack, ModelTransformationMode.GUI, false, matrices,
                layer -> consumers.getBuffer(solid(layer)), LightmapTextureManager.MAX_LIGHT_COORDINATE,
                OverlayTexture.DEFAULT_UV, model);
        context.draw();
        if (front) DiffuseLighting.enableGuiDepthLighting();
        matrices.pop();
    }

    /** The item atlas' translucent layers as cutout ones; any other layer (an item's own texture) as it is. */
    public static RenderLayer solid(RenderLayer layer) {
        return layer == TexturedRenderLayers.getItemEntityTranslucentCull() || layer == TexturedRenderLayers.getEntityTranslucentCull()
                ? TexturedRenderLayers.getEntityCutout() : layer;
    }

    /** Whether the moment's item is lit flat (see {@link Icon}). */
    public static boolean flat(PartyMoment moment) {
        return ICONS.get(moment).flat();
    }

    /** A 16 x 16 cell of the sheet at ({@code x}, {@code y}). */
    public static void cell(DrawContext context, int cell, int x, int y) {
        context.drawTexture(SHEET, x, y, (cell % 16) * 16, (cell / 16) * 16, 16, 16, 256, 64);
    }

    /** The family's colour, ARGB. */
    public static int colour(PartyMoment.Family family) {
        return 0xFF000000 | family.colour();
    }

    /** The family's colour as text on a dark tooltip: the dark green lightened a little, the others as they are. */
    public static int textColour(PartyMoment.Family family) {
        int colour = colour(family);
        return Argb.luminance(colour) < 110 ? Argb.lighten(colour, 0.35f) : colour;
    }
}
