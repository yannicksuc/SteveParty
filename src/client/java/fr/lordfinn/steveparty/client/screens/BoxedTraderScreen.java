package fr.lordfinn.steveparty.client.screens;

import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.screen.MerchantScreenHandler;
import net.minecraft.text.Text;

/** A Boxed Trader's shop, face to face: the cardboard trade screen. */
public class BoxedTraderScreen extends CardboardTradeScreen {
    public BoxedTraderScreen(MerchantScreenHandler handler, PlayerInventory inventory, Text title) {
        super(handler, inventory, title);
    }
}
