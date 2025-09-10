package fr.lordfinn.steveparty.data.handler;

import net.minecraft.entity.data.TrackedDataHandlerRegistry;

public class ModHandler {

    public static void initialize() {
        TrackedDataHandlerRegistry.register(ListUuidTrackedDataHandler.INSTANCE);
    }
}
