package fr.lordfinn.steveparty.items.custom;

import fr.lordfinn.steveparty.entities.ModEntities;
import net.minecraft.item.SpawnEggItem;

/** Spawns a Boxed Trader in a random box (see BoxedTraderBoxes). Its icon is drawn: the two colours are not used. */
public class BoxedTraderSpawnEggItem extends SpawnEggItem {
    public BoxedTraderSpawnEggItem(Settings settings) {
        super(ModEntities.BOXED_TRADER_ENTITY, 0xB8945F, 0x3AAFA9, settings);
    }
}
