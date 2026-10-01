package fr.lordfinn.steveparty.client.minigame;

import fr.lordfinn.steveparty.Steveparty;
import fr.lordfinn.steveparty.client.gui.party.MiniGameCardHud;
import fr.lordfinn.steveparty.client.screens.MiniGamePageEditorScreen;
import fr.lordfinn.steveparty.minigame.MiniGamePageData;
import fr.lordfinn.steveparty.minigame.MiniGamePageImage;
import fr.lordfinn.steveparty.minigame.MiniGamePageImages;
import fr.lordfinn.steveparty.payloads.custom.MiniGamePagePayloads;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.render.RenderLayer;
import net.minecraft.client.texture.NativeImage;
import net.minecraft.client.texture.NativeImageBackedTexture;
import net.minecraft.util.Identifier;
import org.jetbrains.annotations.Nullable;

import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * What this client knows of the mini-game pages of the server it is on: their content and their pictures, asked for
 * when first needed (a tooltip, the editor, the card of a party) and kept until the connection ends. The server tells
 * every client when a page changes, so what is kept stays right; a picture is known by the hash of its bytes, so a
 * changed picture is another picture.
 * <p>
 * Pictures are drawn sharp: for each size they are shown at, a texture is made at the exact number of screen pixels
 * (resized here with clean filtering, rather than stretched by the graphics card). Render thread only.
 */
public final class MiniGamePageClient {
    /** A picture ready to be drawn: its texture, and its size on screen (in GUI pixels). */
    public record Picture(Identifier texture, int width, int height) {
        /** Draws it centred in the box it was fitted in. */
        public void draw(DrawContext context, int boxX, int boxY, int boxWidth, int boxHeight, int color) {
            context.drawTexture(RenderLayer::getGuiTextured, texture, boxX + (boxWidth - width) / 2, boxY + (boxHeight - height) / 2,
                    0, 0, width, height, width, height, color);
        }
    }

    private record TextureKey(String hash, int width, int height) {
    }

    private static final int MAX_IMAGES = 24, MAX_TEXTURES = 64;

    private static final Map<UUID, MiniGamePageData> PAGES = new HashMap<>();
    private static final Set<UUID> ASKED_PAGES = new HashSet<>();
    private static final Set<String> ASKED_IMAGES = new HashSet<>();
    private static final Map<String, ByteArrayOutputStream> INCOMING = new HashMap<>();
    /** The decoded pictures, last used last. */
    private static final Map<String, BufferedImage> IMAGES = new LinkedHashMap<>(16, 0.75F, true) {
        @Override
        protected boolean removeEldestEntry(Map.Entry<String, BufferedImage> eldest) {
            if (size() <= MAX_IMAGES) return false;
            ASKED_IMAGES.remove(eldest.getKey());
            return true;
        }
    };
    private static final Map<TextureKey, Identifier> TEXTURES = new LinkedHashMap<>(16, 0.75F, true) {
        @Override
        protected boolean removeEldestEntry(Map.Entry<TextureKey, Identifier> eldest) {
            if (size() <= MAX_TEXTURES) return false;
            MinecraftClient.getInstance().getTextureManager().destroyTexture(eldest.getValue());
            return true;
        }
    };
    private static int textureSerial;

    private MiniGamePageClient() {
    }

    public static void initialize() {
        ClientPlayNetworking.registerGlobalReceiver(MiniGamePagePayloads.Data.ID, (payload, context) ->
                context.client().execute(() -> put(payload.data())));
        ClientPlayNetworking.registerGlobalReceiver(MiniGamePagePayloads.ImageChunk.ID, (payload, context) ->
                context.client().execute(() -> onImageChunk(payload)));
        ClientPlayNetworking.registerGlobalReceiver(MiniGamePagePayloads.Open.ID, (payload, context) -> context.client().execute(() -> {
            MinecraftClient client = context.client();
            if (client.player == null) return;
            if (!payload.data().id().equals(MiniGamePageData.NO_ID)) put(payload.data());
            // Opened again on the same hand (after a copy, an unlinking): the editor follows the item
            if (client.currentScreen == null || client.currentScreen instanceof MiniGamePageEditorScreen) {
                String status = client.currentScreen instanceof MiniGamePageEditorScreen editor ? editor.handOver() : null;
                client.setScreen(new MiniGamePageEditorScreen(payload.hand(), payload.data(), payload.canEdit(), payload.linked(), status));
            }
        }));
        ClientPlayNetworking.registerGlobalReceiver(MiniGamePagePayloads.Status.ID, (payload, context) -> context.client().execute(() -> {
            if (context.client().currentScreen instanceof MiniGamePageEditorScreen editor) editor.onStatus(payload.page(), payload.code());
        }));
        ClientPlayNetworking.registerGlobalReceiver(MiniGamePagePayloads.Preview.ID, (payload, context) -> context.client().execute(() -> {
            if (payload.show()) {
                if (!payload.data().id().equals(MiniGamePageData.NO_ID)) put(payload.data());
                MiniGameCardHud.show(payload.data(), payload.mode(), payload.countdown());
            } else {
                MiniGameCardHud.hide();
            }
        }));
    }

    /** Everything kept is about one server: forgotten when the connection ends. */
    public static void clear() {
        PAGES.clear();
        ASKED_PAGES.clear();
        ASKED_IMAGES.clear();
        INCOMING.clear();
        IMAGES.clear();
        TEXTURES.values().forEach(texture -> MinecraftClient.getInstance().getTextureManager().destroyTexture(texture));
        TEXTURES.clear();
        MiniGameCardHud.clear();
    }

    // ------------------------------------------------------------------ content

    public static void put(MiniGamePageData data) {
        PAGES.put(data.id(), data);
    }

    /** @return the content of a page, null while it is not known (it is asked to the server, once). */
    public static @Nullable MiniGamePageData page(UUID id) {
        MiniGamePageData data = PAGES.get(id);
        if (data == null && ASKED_PAGES.add(id) && ClientPlayNetworking.canSend(MiniGamePagePayloads.Request.ID)) {
            ClientPlayNetworking.send(new MiniGamePagePayloads.Request(id));
        }
        return data;
    }

    // ------------------------------------------------------------------ pictures

    /**
     * The picture of a page, fitted in a box (proportions kept, never cropped).
     *
     * @return null while the picture is not there (it is asked to the server, once), or when the page has none
     */
    public static @Nullable Picture picture(@Nullable MiniGamePageImage image, int boxWidth, int boxHeight) {
        if (image == null || boxWidth <= 0 || boxHeight <= 0) return null;
        BufferedImage source = IMAGES.get(image.hash());
        if (source == null) {
            if (ASKED_IMAGES.add(image.hash()) && ClientPlayNetworking.canSend(MiniGamePagePayloads.ImageRequest.ID)) {
                ClientPlayNetworking.send(new MiniGamePagePayloads.ImageRequest(image.hash()));
            }
            return null;
        }
        double ratio = Math.min(boxWidth / (double) source.getWidth(), boxHeight / (double) source.getHeight());
        int width = Math.max(1, Math.min(boxWidth, (int) Math.round(source.getWidth() * ratio)));
        int height = Math.max(1, Math.min(boxHeight, (int) Math.round(source.getHeight() * ratio)));
        double scale = MinecraftClient.getInstance().getWindow().getScaleFactor();
        TextureKey key = new TextureKey(image.hash(), Math.max(1, (int) Math.round(width * scale)), Math.max(1, (int) Math.round(height * scale)));
        Identifier texture = TEXTURES.get(key);
        if (texture == null) {
            texture = createTexture(source, key);
            TEXTURES.put(key, texture);
        }
        return new Picture(texture, width, height);
    }

    /** A picture this client just made (picked in the editor): shown right away, without asking the server for it. */
    public static void putImage(String hash, BufferedImage image) {
        IMAGES.put(hash, image);
        ASKED_IMAGES.add(hash);
    }

    private static Identifier createTexture(BufferedImage source, TextureKey key) {
        BufferedImage sized = MiniGamePageImages.resize(source, key.width(), key.height());
        NativeImage pixels = new NativeImage(key.width(), key.height(), false);
        for (int y = 0; y < key.height(); y++) {
            for (int x = 0; x < key.width(); x++) pixels.setColorArgb(x, y, sized.getRGB(x, y));
        }
        Identifier id = Steveparty.id("minigame_page/" + key.hash() + "_" + key.width() + "x" + key.height() + "_" + textureSerial++);
        MinecraftClient.getInstance().getTextureManager().registerTexture(id, new NativeImageBackedTexture(pixels));
        return id;
    }

    private static void onImageChunk(MiniGamePagePayloads.ImageChunk chunk) {
        String hash = chunk.hash();
        if (!MiniGamePageImage.isHash(hash) || !ASKED_IMAGES.contains(hash) || IMAGES.containsKey(hash)) return;
        // total 0: the server does not have it. It stays "asked", so that it is not asked again and again
        if (chunk.total() <= 0 || chunk.total() > MiniGamePagePayloads.MAX_CHUNKS || chunk.index() < 0 || chunk.index() >= chunk.total()) {
            INCOMING.remove(hash);
            return;
        }
        ByteArrayOutputStream bytes = chunk.index() == 0 ? new ByteArrayOutputStream() : INCOMING.get(hash);
        if (bytes == null || bytes.size() != chunk.index() * MiniGamePagePayloads.CHUNK_SIZE
                || bytes.size() + chunk.bytes().length > MiniGamePageImages.MAX_BYTES) {
            INCOMING.remove(hash);
            return;
        }
        bytes.write(chunk.bytes(), 0, chunk.bytes().length);
        if (chunk.index() < chunk.total() - 1) {
            INCOMING.put(hash, bytes);
            return;
        }
        INCOMING.remove(hash);
        byte[] all = bytes.toByteArray();
        if (!MiniGamePageImages.isAcceptable(all) || !hash.equals(MiniGamePageImages.hash(all))) return;
        BufferedImage image = MiniGamePageImages.decode(all);
        if (image != null) IMAGES.put(hash, image);
    }
}
