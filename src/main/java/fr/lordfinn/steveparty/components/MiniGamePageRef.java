package fr.lordfinn.steveparty.components;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.util.Uuids;

import java.util.UUID;

/**
 * What a mini-game page item carries: the id of its content, kept by the server
 * ({@link fr.lordfinn.steveparty.minigame.MiniGamePages}). Items with the same id are the same page.
 *
 * @param id     the page's id
 * @param title  the page's title when the item was last seen by the server (shown where the content is not at hand)
 * @param linked true on the copies made of a page (and on the page they were made from)
 */
public record MiniGamePageRef(UUID id, String title, boolean linked) {
    public static final Codec<MiniGamePageRef> CODEC = RecordCodecBuilder.create(instance -> instance.group(
            Uuids.STRING_CODEC.fieldOf("id").forGetter(MiniGamePageRef::id),
            Codec.STRING.optionalFieldOf("title", "").forGetter(MiniGamePageRef::title),
            Codec.BOOL.optionalFieldOf("linked", false).forGetter(MiniGamePageRef::linked)
    ).apply(instance, MiniGamePageRef::new));

    public MiniGamePageRef withTitle(String newTitle) {
        return new MiniGamePageRef(id, newTitle, linked);
    }

    public MiniGamePageRef withLinked(boolean nowLinked) {
        return new MiniGamePageRef(id, title, nowLinked);
    }
}
