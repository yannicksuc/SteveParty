package fr.lordfinn.steveparty.client.datagen;

import fr.lordfinn.steveparty.Steveparty;
import fr.lordfinn.steveparty.items.ModItems;
import net.fabricmc.fabric.api.datagen.v1.FabricDataOutput;
import net.fabricmc.fabric.api.datagen.v1.provider.FabricTagProvider;
import net.minecraft.item.Item;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.registry.RegistryWrapper;
import net.minecraft.registry.tag.TagKey;

import java.util.concurrent.CompletableFuture;

public class StevepartyReferenceItemTagProvider  extends FabricTagProvider<Item> {

    public static final TagKey<Item> DICE_FACES_TAG =
            TagKey.of(RegistryKeys.ITEM, Steveparty.id("dice_faces"));

    public StevepartyReferenceItemTagProvider(FabricDataOutput output, CompletableFuture<RegistryWrapper.WrapperLookup> registriesFuture) {
        super(output, RegistryKeys.ITEM, registriesFuture);
    }

    @Override
    protected void configure(RegistryWrapper.WrapperLookup wrapperLookup) {
        for (Item diceFace : ModItems.DICE_FACES)
            getOrCreateTagBuilder(DICE_FACES_TAG).add(diceFace);
    }
}
