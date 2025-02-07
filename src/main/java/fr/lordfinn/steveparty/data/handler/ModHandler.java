package fr.lordfinn.steveparty.data.handler;

import net.minecraft.entity.data.TrackedDataHandlerRegistry;

public class ModHandler {

    public static void init() {
        TrackedDataHandlerRegistry.register(ListUuidTrackedDataHandler.INSTANCE);
    }
}
