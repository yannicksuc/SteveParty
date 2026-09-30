package fr.lordfinn.steveparty.client.screens;

import fr.lordfinn.steveparty.blocks.custom.boardspaces.BoardSpaceBlockEntity;
import fr.lordfinn.steveparty.blocks.custom.boardspaces.TileTeleport;
import fr.lordfinn.steveparty.board.BoardGraph;
import fr.lordfinn.steveparty.client.gui.PartyButton;
import fr.lordfinn.steveparty.client.gui.PartyGui;
import fr.lordfinn.steveparty.components.TeleportNetwork;
import fr.lordfinn.steveparty.components.TeleportSettingsComponent;
import fr.lordfinn.steveparty.items.custom.cartridges.TeleportCartridgeItem;
import fr.lordfinn.steveparty.payloads.custom.TeleportSettingsPayload;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.tooltip.Tooltip;
import net.minecraft.item.ItemStack;
import net.minecraft.text.Text;
import net.minecraft.util.math.BlockPos;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static fr.lordfinn.steveparty.sounds.ModSounds.CLOSE_TILE_GUI_SOUND_EVENT;
import static fr.lordfinn.steveparty.sounds.ModSounds.OPEN_TILE_GUI_SOUND_EVENT;

/**
 * The menu of a Teleport Cartridge (its tile's, or the one in hand), in the mod's pixel style:
 * <ul>
 *     <li><b>Réseau</b>: four big colour buttons, one click switches (the current one pushed in, gold);</li>
 *     <li><b>À l'arrivée</b>: stay on the Teleport tile, or move on one space;</li>
 *     <li><b>La case suivante déclenche son effet</b>: yes / no, only when moving on;</li>
 *     <li><b>Plusieurs cases du réseau</b>: at random / in turn.</li>
 * </ul>
 * Each click is sent to the server at once (it checks the player may edit the cartridge, then saves and syncs it); on
 * a tile, the number of other tiles of the network on its board is shown (a warning when it is alone).
 */
public class TeleportSettingsScreen extends Screen {
    private static final String KEY = "gui.steveparty.teleport_settings.";
    private static final int WIDTH = 224, PAD = 12;
    private static final int NETWORK_W = 44, NETWORK_H = 30, NETWORK_GAP = 6;
    private static final int ROW_H = 18, LABEL_GAP = 11, SECTION_GAP = 7;

    /** The tile whose cartridge is edited, or null: the one in hand. */
    private final @Nullable BlockPos tile;
    private TeleportSettingsComponent settings;
    private final List<PartyButton> networkButtons = new ArrayList<>();
    private PartyButton stay, push, triggersYes, triggersNo, random, cycle;
    private int x, y, height0;
    private int networkY, arrivalY, triggersY, pickY, infoY, closeY;
    /** On a tile: how many other tiles of its network are on its board (-1: not known, the cartridge in hand). */
    private int partners = -1;
    private boolean openSoundPlayed;

    public TeleportSettingsScreen(@Nullable BlockPos tile) {
        super(Text.translatable("item.steveparty.teleport_cartridge"));
        this.tile = tile == null ? null : tile.toImmutable();
        ItemStack cartridge = cartridge();
        this.settings = cartridge == null ? TeleportSettingsComponent.DEFAULT : TeleportCartridgeItem.settings(cartridge);
    }

    /** The cartridge edited, as the client knows it; null if gone (the screen closes). */
    private @Nullable ItemStack cartridge() {
        if (client == null) client = net.minecraft.client.MinecraftClient.getInstance();
        if (client.player == null || client.world == null) return null;
        if (tile == null) return TeleportCartridgeItem.inHand(client.player);
        if (!(client.world.getBlockEntity(tile) instanceof BoardSpaceBlockEntity space)) return null;
        ItemStack stack = space.getActiveCartridgeItemStack();
        return stack.getItem() instanceof TeleportCartridgeItem ? stack : null;
    }

    @Override
    protected void init() {
        networkY = 30;
        arrivalY = networkY + NETWORK_H + SECTION_GAP + LABEL_GAP;
        triggersY = arrivalY + 2 * ROW_H + 3 + SECTION_GAP + LABEL_GAP;
        pickY = triggersY + ROW_H + SECTION_GAP + LABEL_GAP;
        infoY = pickY + ROW_H + SECTION_GAP;
        closeY = infoY + (tile != null ? 14 : 0);
        height0 = closeY + 20 + 10;
        x = (width - WIDTH) / 2;
        y = Math.max(16, (height - height0) / 2);

        networkButtons.clear();
        int rowWidth = 4 * NETWORK_W + 3 * NETWORK_GAP;
        int nx = x + (WIDTH - rowWidth) / 2;
        for (TeleportNetwork network : TeleportNetwork.values()) {
            PartyButton button = new PartyButton(nx, y + networkY, NETWORK_W, NETWORK_H, network.displayName(),
                    b -> change(settings.withNetwork(network)))
                    .content((context, textRenderer, centerX, centerY, color) -> {
                        // A swatch of the network's colour, its name under it
                        int sw = NETWORK_W - 12;
                        PartyGui.button(context, centerX - sw / 2, centerY - 11, sw, 11,
                                new PartyGui.Theme(0xFF000000 | shade(network.color(), 0.45f), 0xFF000000 | tint(network.color(), 0.45f),
                                        0xFF000000 | network.color(), 0xFF000000 | shade(network.color(), 0.7f)), false);
                        Text name = network.displayName();
                        context.drawText(textRenderer, Text.literal(name.getString()), centerX - textRenderer.getWidth(name) / 2,
                                centerY + 3, color, false);
                    });
            networkButtons.add(addDrawableChild(button));
            nx += NETWORK_W + NETWORK_GAP;
        }
        int half = (WIDTH - 2 * PAD - 6) / 2;
        int left = x + PAD, right = x + PAD + half + 6;
        stay = addDrawableChild(new PartyButton(left, y + arrivalY, WIDTH - 2 * PAD, ROW_H, Text.translatable(KEY + "stay"),
                b -> change(settings.withPush(false))));
        push = addDrawableChild(new PartyButton(left, y + arrivalY + ROW_H + 3, WIDTH - 2 * PAD, ROW_H, Text.translatable(KEY + "push"),
                b -> change(settings.withPush(true))));
        triggersYes = addDrawableChild(new PartyButton(left, y + triggersY, half, ROW_H, Text.translatable(KEY + "yes"),
                b -> change(settings.withPushTriggers(true))));
        triggersNo = addDrawableChild(new PartyButton(right, y + triggersY, half, ROW_H, Text.translatable(KEY + "no"),
                b -> change(settings.withPushTriggers(false))));
        random = addDrawableChild(new PartyButton(left, y + pickY, half, ROW_H, Text.translatable(KEY + "random"),
                b -> change(settings.withCycle(false))));
        cycle = addDrawableChild(new PartyButton(right, y + pickY, half, ROW_H, Text.translatable(KEY + "cycle"),
                b -> change(settings.withCycle(true))));
        stay.setTooltip(Tooltip.of(Text.translatable(KEY + "stay.tooltip")));
        push.setTooltip(Tooltip.of(Text.translatable(KEY + "push.tooltip")));
        addDrawableChild(new PartyButton(x + (WIDTH - 90) / 2, y + closeY, 90, 20, Text.translatable(KEY + "close"),
                b -> close()).style(PartyButton.Style.PRIMARY));
        refresh();
        countPartners();

        if (!openSoundPlayed && client != null && client.player != null) {
            client.player.playSound(OPEN_TILE_GUI_SOUND_EVENT, 1.0F, 1.0F);
        }
        openSoundPlayed = true;
    }

    // ------------------------------------------------------------------ state

    private void change(TeleportSettingsComponent next) {
        if (next.equals(settings)) return;
        boolean networkChanged = next.network() != settings.network();
        settings = next;
        ClientPlayNetworking.send(new TeleportSettingsPayload(Optional.ofNullable(tile), settings));
        refresh();
        if (networkChanged) countPartners();
    }

    private void refresh() {
        for (int i = 0; i < networkButtons.size(); i++) networkButtons.get(i).setSelected(TeleportNetwork.values()[i] == settings.network());
        stay.setSelected(!settings.push());
        push.setSelected(settings.push());
        // Only when moving on: the space it is pushed onto
        triggersYes.active = triggersNo.active = settings.push();
        triggersYes.setSelected(settings.push() && settings.pushTriggers());
        triggersNo.setSelected(settings.push() && !settings.pushTriggers());
        random.setSelected(!settings.cycle());
        cycle.setSelected(settings.cycle());
    }

    /**
     * On a tile: the other tiles of the network it would have on its board (read on the client, with the network being
     * chosen: the server may not have answered yet).
     */
    private void countPartners() {
        if (tile == null || client == null || client.world == null) return;
        BoardGraph graph = BoardGraph.collect(client.world, tile, TileTeleport.NETWORK_RANGE);
        TeleportNetwork shown = settings.network();
        BoardGraph.Node node = graph.node(tile);
        if (node == null) {
            partners = 0;
            return;
        }
        // The same board, the chosen network (the graph still has the tile's former one until the server answers)
        partners = (int) graph.nodes().stream().filter(other -> !other.pos().equals(tile) && other.teleportNetwork() == shown
                && graph.sameBoard(tile, other.pos())).count();
    }

    @Override
    public void tick() {
        super.tick();
        // The cartridge gone (taken out of the tile, out of the hand) or the tile out of reach: nothing left to edit
        ItemStack cartridge = cartridge();
        if (cartridge == null || client == null || client.player == null
                || (tile != null && !client.player.canInteractWithBlockAt(tile, 1.0))) {
            close();
        }
    }

    // ------------------------------------------------------------------ drawing

    @Override
    public void renderBackground(DrawContext context, int mouseX, int mouseY, float delta) {
        super.renderBackground(context, mouseX, mouseY, delta);
        int color = settings.network().color();
        PartyGui.panel(context, x, y, WIDTH, height0, PartyGui.PANEL);
        PartyGui.Theme plate = new PartyGui.Theme(0xFF000000 | shade(color, 0.75f), 0xFF000000 | tint(color, 0.4f),
                0xFF000000 | shade(color, 0.12f), 0xFF000000 | shade(color, 0.45f));
        PartyGui.titlePlate(context, textRenderer, x + WIDTH / 2, y - 11, 18, title, plate);
        ItemStack icon = cartridge();
        if (icon != null) {
            context.drawItem(icon, PartyGui.titlePlateIconX(textRenderer, x + WIDTH / 2, 18, title), y - 8);
        }
    }

    @Override
    public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        super.render(context, mouseX, mouseY, delta);
        int dark = PartyGui.TEXT_DARK;
        context.drawText(textRenderer, Text.translatable(KEY + "network"), x + PAD, y + networkY - LABEL_GAP + 1, dark, false);
        context.drawText(textRenderer, Text.translatable(KEY + "arrival"), x + PAD, y + arrivalY - LABEL_GAP + 1, dark, false);
        context.drawText(textRenderer, Text.translatable(KEY + "triggers"), x + PAD, y + triggersY - LABEL_GAP + 1,
                settings.push() ? dark : PartyGui.TEXT_SOFT, false);
        context.drawText(textRenderer, Text.translatable(KEY + "pick"), x + PAD, y + pickY - LABEL_GAP + 1, dark, false);
        if (tile != null && partners >= 0) {
            Text info = partners == 0 ? Text.translatable(KEY + "alone")
                    : Text.translatable(partners == 1 ? KEY + "partners.one" : KEY + "partners", partners);
            int w = textRenderer.getWidth(info);
            context.drawText(textRenderer, info, x + (WIDTH - w) / 2, y + infoY, partners == 0 ? PartyGui.TEXT_ERROR : PartyGui.TEXT_OK, false);
        }
    }

    @Override
    public boolean shouldPause() {
        return false;
    }

    @Override
    public void close() {
        if (client != null && client.player != null) client.player.playSound(CLOSE_TILE_GUI_SOUND_EVENT, 1.0F, 1.0F);
        super.close();
    }

    // ------------------------------------------------------------------ colours

    /** {@code rgb} darker by {@code amount} (0..1). */
    private static int shade(int rgb, float amount) {
        int r = (int) (((rgb >> 16) & 0xFF) * (1 - amount)), g = (int) (((rgb >> 8) & 0xFF) * (1 - amount)), b = (int) ((rgb & 0xFF) * (1 - amount));
        return (r << 16) | (g << 8) | b;
    }

    /** {@code rgb} lighter by {@code amount} (0..1), toward white. */
    private static int tint(int rgb, float amount) {
        int r = (rgb >> 16) & 0xFF, g = (rgb >> 8) & 0xFF, b = rgb & 0xFF;
        r += (int) ((255 - r) * amount);
        g += (int) ((255 - g) * amount);
        b += (int) ((255 - b) * amount);
        return (r << 16) | (g << 8) | b;
    }
}
