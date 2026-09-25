package fr.lordfinn.steveparty.client.mixin;

import net.minecraft.client.model.ModelPart;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

import java.util.List;
import java.util.Map;

/** Read access to a model part's cuboids and children (for the token foot anchor, see TokenFootAnchor). */
@Mixin(ModelPart.class)
public interface ModelPartAccessor {
    @Accessor("cuboids")
    List<ModelPart.Cuboid> steveparty$getCuboids();

    @Accessor("children")
    Map<String, ModelPart> steveparty$getChildren();
}
