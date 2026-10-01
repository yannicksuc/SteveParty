package fr.lordfinn.steveparty.items.custom;

import fr.lordfinn.steveparty.entities.ModEntities;
import net.minecraft.item.SpawnEggItem;

/** Spawns a Hiding Trader in a random box (see HidingTraderBoxes). Its icon is drawn: the two colours are not used. */
public class HidingTraderSpawnEggItem extends SpawnEggItem {
    public HidingTraderSpawnEggItem(Settings settings) {
        super(ModEntities.HIDING_TRADER_ENTITY, 0xB8945F, 0x3AAFA9, settings);
    }
}
