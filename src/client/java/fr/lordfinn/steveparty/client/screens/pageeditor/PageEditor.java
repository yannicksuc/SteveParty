package fr.lordfinn.steveparty.client.screens.pageeditor;

import fr.lordfinn.steveparty.minigame.MiniGameFormat;
import fr.lordfinn.steveparty.minigame.MiniGamePageData;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.Drawable;
import net.minecraft.client.gui.Element;
import net.minecraft.client.gui.Selectable;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.text.Text;
import net.minecraft.util.Hand;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.UUID;

/** What the page editor's tabs and popups see of its screen: the page edited, where it is, its widgets and its actions. */
public interface PageEditor {
    TextRenderer font();

    @Nullable MinecraftClient client();

    /** The page's corner on the screen. */
    int left();

    int top();

    /** The screen's size (a popup dims all of it). */
    int screenWidth();

    int screenHeight();

    /** The hand holding the page, and the page's id: what the payloads name. */
    Hand hand();

    UUID pageId();

    /** Whether the player may change the page (else he only reads it). */
    boolean canEdit();

    /** The page as the server has it now, with the formats edited here. */
    MiniGamePageData current();

    /** The formats as edited (sent when the editor closes). */
    List<MiniGameFormat> formats();

    <T extends Element & Drawable & Selectable> T add(T widget);

    void focus(@Nullable Element element);

    void send(CustomPayload payload);

    /** Sends the texts and formats if they changed. */
    void save();

    void close();

    void playClick();

    void setStatus(@Nullable Text text, boolean error);

    /** The « Test » button follows the formats. */
    void refreshTestButton();

    /** The popup open over the page, null for none. */
    @Nullable PagePopup popup();

    void openPopup(@Nullable PagePopup popup);

    void showTab(BinderTab tab);
}
