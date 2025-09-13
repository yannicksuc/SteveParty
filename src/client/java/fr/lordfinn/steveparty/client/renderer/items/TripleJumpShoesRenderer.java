package fr.lordfinn.steveparty.client.renderer.items;

import fr.lordfinn.steveparty.Steveparty;
import fr.lordfinn.steveparty.items.custom.TripleJumpShoesItem;
import software.bernie.geckolib.model.DefaultedItemGeoModel;
import software.bernie.geckolib.renderer.GeoArmorRenderer;

public final class TripleJumpShoesRenderer extends GeoArmorRenderer<TripleJumpShoesItem> {
    public TripleJumpShoesRenderer() {
        super(new DefaultedItemGeoModel<>(Steveparty.id("armor/triple_jump_shoes")));
    }
}