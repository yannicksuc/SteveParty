package fr.lordfinn.steveparty.minigame;

import org.jetbrains.annotations.Nullable;

import javax.imageio.IIOImage;
import javax.imageio.ImageIO;
import javax.imageio.ImageReader;
import javax.imageio.ImageWriteParam;
import javax.imageio.ImageWriter;
import javax.imageio.stream.MemoryCacheImageInputStream;
import javax.imageio.stream.MemoryCacheImageOutputStream;
import java.awt.AlphaComposite;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Iterator;

/**
 * The pictures of the mini-game pages, as bytes: a PNG or a JPEG of at most {@link #MAX_WIDTH} × {@link #MAX_HEIGHT}
 * pixels and {@link #MAX_BYTES} bytes. The client shrinks and compresses what the player picked ({@link #prepare}),
 * the server only checks what it receives ({@link #inspect}): it never decodes the pixels.
 */
public final class MiniGamePageImages {
    /** 16:9, sharp on a big card: pictures are shrunk to fit in it, keeping their proportions. */
    public static final int MAX_WIDTH = 640, MAX_HEIGHT = 360;
    public static final int MAX_BYTES = 512 * 1024;
    private static final float[] JPEG_QUALITIES = {0.9F, 0.8F, 0.65F, 0.5F};

    public enum Format {
        PNG("png"), JPEG("jpg");

        private final String extension;

        Format(String extension) {
            this.extension = extension;
        }

        public String extension() {
            return extension;
        }
    }

    /** What the header of a picture says. */
    public record Info(Format format, int width, int height) {
    }

    private MiniGamePageImages() {
    }

    /** @return the id of these bytes: 32 hexadecimal characters (the start of their SHA-256). */
    public static String hash(byte[] bytes) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(bytes);
            StringBuilder hex = new StringBuilder(32);
            for (int i = 0; i < 16; i++) hex.append(Character.forDigit((digest[i] >> 4) & 0xF, 16)).append(Character.forDigit(digest[i] & 0xF, 16));
            return hex.toString();
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }

    /** @return the format of these bytes from their first bytes, null if they are neither a PNG nor a JPEG. */
    public static @Nullable Format formatOf(byte[] bytes) {
        if (bytes == null) return null;
        if (bytes.length > 8 && (bytes[0] & 0xFF) == 0x89 && bytes[1] == 'P' && bytes[2] == 'N' && bytes[3] == 'G') return Format.PNG;
        if (bytes.length > 3 && (bytes[0] & 0xFF) == 0xFF && (bytes[1] & 0xFF) == 0xD8 && (bytes[2] & 0xFF) == 0xFF) return Format.JPEG;
        return null;
    }

    /**
     * Reads the header of a picture, without decoding its pixels.
     *
     * @return its format and size, null if the bytes are not a readable PNG or JPEG
     */
    public static @Nullable Info inspect(byte[] bytes) {
        Format format = formatOf(bytes);
        if (format == null) return null;
        Iterator<ImageReader> readers = ImageIO.getImageReadersByFormatName(format == Format.PNG ? "png" : "jpeg");
        if (!readers.hasNext()) return null;
        ImageReader reader = readers.next();
        try (MemoryCacheImageInputStream in = new MemoryCacheImageInputStream(new ByteArrayInputStream(bytes))) {
            reader.setInput(in, true, true);
            int width = reader.getWidth(0), height = reader.getHeight(0);
            return width > 0 && height > 0 ? new Info(format, width, height) : null;
        } catch (Exception e) {
            return null;
        } finally {
            reader.dispose();
        }
    }

    /** @return true if these bytes can be a page's picture: a PNG or JPEG within the size limits. */
    public static boolean isAcceptable(byte[] bytes) {
        if (bytes == null || bytes.length == 0 || bytes.length > MAX_BYTES) return false;
        Info info = inspect(bytes);
        return info != null && info.width() <= MAX_WIDTH && info.height() <= MAX_HEIGHT;
    }

    /** Decodes a PNG or a JPEG, null if it can't be read. */
    public static @Nullable BufferedImage decode(byte[] bytes) {
        if (formatOf(bytes) == null) return null;
        try {
            return ImageIO.read(new MemoryCacheImageInputStream(new ByteArrayInputStream(bytes)));
        } catch (Exception e) {
            return null;
        }
    }

    /**
     * Shrinks a picture to fit in {@link #MAX_WIDTH} × {@link #MAX_HEIGHT}, keeping its proportions (never enlarged).
     * Shrunk by halves then bicubic, so that a big photo stays clean instead of turning into noise.
     */
    public static BufferedImage fit(BufferedImage source) {
        int width = source.getWidth(), height = source.getHeight();
        double scale = Math.min(1.0, Math.min(MAX_WIDTH / (double) width, MAX_HEIGHT / (double) height));
        return resize(source, Math.max(1, (int) Math.round(width * scale)), Math.max(1, (int) Math.round(height * scale)));
    }

    /** Resizes a picture to exactly {@code targetWidth} × {@code targetHeight} with clean filtering. */
    public static BufferedImage resize(BufferedImage source, int targetWidth, int targetHeight) {
        BufferedImage current = toArgb(source);
        while (current.getWidth() / 2 >= targetWidth && current.getHeight() / 2 >= targetHeight
                && (current.getWidth() / 2 > targetWidth || current.getHeight() / 2 > targetHeight)) {
            current = scaled(current, current.getWidth() / 2, current.getHeight() / 2, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
        }
        if (current.getWidth() != targetWidth || current.getHeight() != targetHeight) {
            current = scaled(current, targetWidth, targetHeight, RenderingHints.VALUE_INTERPOLATION_BICUBIC);
        }
        return current;
    }

    private static BufferedImage toArgb(BufferedImage source) {
        if (source.getType() == BufferedImage.TYPE_INT_ARGB) return source;
        return scaled(source, source.getWidth(), source.getHeight(), RenderingHints.VALUE_INTERPOLATION_NEAREST_NEIGHBOR);
    }

    private static BufferedImage scaled(BufferedImage source, int width, int height, Object interpolation) {
        BufferedImage target = new BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB);
        Graphics2D graphics = target.createGraphics();
        try {
            graphics.setComposite(AlphaComposite.Src);
            graphics.setRenderingHint(RenderingHints.KEY_INTERPOLATION, interpolation);
            graphics.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
            graphics.drawImage(source, 0, 0, width, height, null);
        } finally {
            graphics.dispose();
        }
        return target;
    }

    /**
     * Compresses a picture that already fits: a PNG when it has transparency (and stays small enough), otherwise a
     * JPEG, its quality lowered until it is under {@link #MAX_BYTES}.
     *
     * @return the bytes, null if the picture can't be written
     */
    public static byte @Nullable [] encode(BufferedImage image) {
        try {
            if (hasTransparency(image)) {
                ByteArrayOutputStream out = new ByteArrayOutputStream();
                boolean written;
                try (MemoryCacheImageOutputStream stream = new MemoryCacheImageOutputStream(out)) {
                    written = ImageIO.write(image, "png", stream);
                }
                if (written && out.size() > 0 && out.size() <= MAX_BYTES) return out.toByteArray();
            }
            BufferedImage opaque = new BufferedImage(image.getWidth(), image.getHeight(), BufferedImage.TYPE_INT_RGB);
            Graphics2D graphics = opaque.createGraphics();
            try {
                graphics.drawImage(image, 0, 0, null);
            } finally {
                graphics.dispose();
            }
            for (float quality : JPEG_QUALITIES) {
                byte[] bytes = jpeg(opaque, quality);
                if (bytes != null && bytes.length <= MAX_BYTES) return bytes;
            }
        } catch (Exception e) {
            return null;
        }
        return null;
    }

    /** {@link #fit} then {@link #encode}: what the client sends for a picture the player picked. */
    public static byte @Nullable [] prepare(BufferedImage source) {
        return encode(fit(source));
    }

    private static byte @Nullable [] jpeg(BufferedImage opaque, float quality) throws java.io.IOException {
        Iterator<ImageWriter> writers = ImageIO.getImageWritersByFormatName("jpeg");
        if (!writers.hasNext()) return null;
        ImageWriter writer = writers.next();
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        try (MemoryCacheImageOutputStream stream = new MemoryCacheImageOutputStream(out)) {
            writer.setOutput(stream);
            ImageWriteParam param = writer.getDefaultWriteParam();
            param.setCompressionMode(ImageWriteParam.MODE_EXPLICIT);
            param.setCompressionQuality(quality);
            writer.write(null, new IIOImage(opaque, null, null), param);
        } finally {
            writer.dispose();
        }
        return out.toByteArray();
    }

    private static boolean hasTransparency(BufferedImage image) {
        if (!image.getColorModel().hasAlpha()) return false;
        for (int y = 0; y < image.getHeight(); y++) {
            for (int x = 0; x < image.getWidth(); x++) {
                if ((image.getRGB(x, y) >>> 24) != 0xFF) return true;
            }
        }
        return false;
    }
}
