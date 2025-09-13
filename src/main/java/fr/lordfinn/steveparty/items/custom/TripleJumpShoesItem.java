package fr.lordfinn.steveparty.items.custom;

import fr.lordfinn.steveparty.Steveparty;
import net.minecraft.entity.EquipmentSlot;
import net.minecraft.entity.LivingEntity;
import net.minecraft.item.ArmorItem;
import net.minecraft.item.ItemStack;
import net.minecraft.item.equipment.ArmorMaterial;
import net.minecraft.item.equipment.EquipmentType;
import net.minecraft.registry.entry.RegistryEntry;
import net.minecraft.registry.tag.ItemTags;
import net.minecraft.sound.SoundEvents;
import net.minecraft.state.property.Properties;
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

import java.util.Map;
import java.util.function.Consumer;

public final class TripleJumpShoesItem extends ArmorItem implements GeoItem {
    private final AnimatableInstanceCache cache = GeckoLibUtil.createInstanceCache(this);
    public final MutableObject<GeoRenderProvider> renderProviderHolder = new MutableObject<>();

    public TripleJumpShoesItem(Settings settings) {
        super(new ArmorMaterial(
                15,
                Map.of(
                        EquipmentType.BOOTS, 3
                ),
                25, // durability
                RegistryEntry.of(SoundEvents.ITEM_ARMOR_EQUIP_LEATHER.value()),
                1.0f, // toughness
                0.0f, // knockback
                ItemTags.REPAIRS_LEATHER_ARMOR, // repair ingredient
                Steveparty.id("triple_jump_shoes")
        ), EquipmentType.BOOTS, settings);
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
