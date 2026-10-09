package fr.lordfinn.steveparty.client.mixin;

import net.minecraft.client.texture.AbstractTexture;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/** A texture's GL name: {@link TextureManagerDestroyMixin} forgets a freed one without deleting it a second time. */
@Mixin(AbstractTexture.class)
public interface AbstractTextureAccessor {
    @Accessor("glId")
    void steveparty$setGlId(int glId);
}
