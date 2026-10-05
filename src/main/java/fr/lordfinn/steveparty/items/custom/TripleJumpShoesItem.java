package fr.lordfinn.steveparty.items.custom;

import fr.lordfinn.steveparty.items.ModArmorMaterials;
import net.minecraft.item.ArmorItem;
import org.apache.commons.lang3.mutable.MutableObject;
import org.jetbrains.annotations.Nullable;
import software.bernie.geckolib.animatable.GeoItem;
import software.bernie.geckolib.animatable.client.GeoRenderProvider;
import software.bernie.geckolib.animatable.instance.AnimatableInstanceCache;
import software.bernie.geckolib.animation.AnimatableManager;
import software.bernie.geckolib.animation.AnimationController;
import software.bernie.geckolib.animation.PlayState;
import software.bernie.geckolib.renderer.GeoArmorRenderer;
import software.bernie.geckolib.util.GeckoLibUtil;

import java.util.function.Consumer;

public final class TripleJumpShoesItem extends ArmorItem implements GeoItem {
    private final AnimatableInstanceCache cache = GeckoLibUtil.createInstanceCache(this);
    public final MutableObject<GeoRenderProvider> renderProviderHolder = new MutableObject<>();

    /** Durability multiplier, as for vanilla armour: 13 x 15 = 195 uses for boots. */
    private static final int DURABILITY = 15;

    public TripleJumpShoesItem(Settings settings) {
        super(ModArmorMaterials.TRIPLE_JUMP_SHOES, Type.BOOTS, settings.maxDamage(Type.BOOTS.getMaxDamage(DURABILITY)));
    }

    @Override
    public void createGeoRenderer(Consumer<GeoRenderProvider> consumer) {
        consumer.accept(this.renderProviderHolder.getValue());
    }

    @Override
    public void registerControllers(AnimatableManager.ControllerRegistrar controllers) {
        controllers.add(new AnimationController<>(this, "controller", 20, state -> {
            return PlayState.CONTINUE; // Always animate, or gate with conditions
        }));
    }

    @Override
    public AnimatableInstanceCache getAnimatableInstanceCache() {
        return this.cache;
    }
}
