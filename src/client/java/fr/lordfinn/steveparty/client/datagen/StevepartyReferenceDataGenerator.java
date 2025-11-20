package fr.lordfinn.steveparty.client.datagen;

import net.fabricmc.fabric.api.datagen.v1.DataGeneratorEntrypoint;
import net.fabricmc.fabric.api.datagen.v1.FabricDataGenerator;

public class StevepartyReferenceDataGenerator implements DataGeneratorEntrypoint {
    @Override
    public void onInitializeDataGenerator(FabricDataGenerator fabricDataGenerator) {
        FabricDataGenerator.Pack pack = fabricDataGenerator.createPack();
        pack.addProvider(ModBlockLootTableProvider::new);
        pack.addProvider(StevepartyReferenceBlockTagProvider::new);
        pack.addProvider(StevepartyReferenceItemTagProvider::new);
        pack.addProvider(StevepartyRecipeProvider::new);
        pack.addProvider(StevepartyBlockProvider::new);
    }
}
