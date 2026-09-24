package fr.lordfinn.steveparty.mixin;

import fr.lordfinn.steveparty.registry.RegistryAliases;
import net.minecraft.registry.SimpleRegistry;
import net.minecraft.util.Identifier;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

/** Resolves renamed ids ({@link RegistryAliases}) on every lookup by id, including the codecs used by saves. */
@Mixin(SimpleRegistry.class)
public abstract class SimpleRegistryAliasMixin {
    @ModifyVariable(
            method = {
                    "getEntry(Lnet/minecraft/util/Identifier;)Ljava/util/Optional;",
                    "get(Lnet/minecraft/util/Identifier;)Ljava/lang/Object;",
                    "containsId(Lnet/minecraft/util/Identifier;)Z"
            },
            at = @At("HEAD"),
            argsOnly = true
    )
    private Identifier steveparty$resolveAlias(Identifier id) {
        return RegistryAliases.resolve(id);
    }
}
