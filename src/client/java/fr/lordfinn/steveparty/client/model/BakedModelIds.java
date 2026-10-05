package fr.lordfinn.steveparty.client.model;

import net.fabricmc.fabric.api.client.model.loading.v1.ModelModifier;
import net.minecraft.client.util.ModelIdentifier;
import net.minecraft.util.Identifier;
import org.jetbrains.annotations.Nullable;

/** The JSON model id of a model being baked, as the model plugins match it. */
public final class BakedModelIds {
    private BakedModelIds() {}

    /**
     * The model's resource id ({@code ns:block/x}, {@code ns:item/x}); an item's top-level model ({@code ns:x#inventory},
     * baked without a resource id) as {@code ns:item/x}. Null for the other top-level models (block states).
     */
    @Nullable
    public static Identifier of(ModelModifier.AfterBake.Context ctx) {
        if (ctx.resourceId() != null) return ctx.resourceId();
        ModelIdentifier top = ctx.topLevelId();
        if (top != null && ModelIdentifier.INVENTORY_VARIANT.equals(top.variant())) return top.id().withPrefixedPath("item/");
        return null;
    }
}
