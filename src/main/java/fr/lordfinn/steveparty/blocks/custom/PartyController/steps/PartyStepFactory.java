package fr.lordfinn.steveparty.blocks.custom.PartyController.steps;

import fr.lordfinn.steveparty.Steveparty;
import net.minecraft.nbt.NbtCompound;

public class PartyStepFactory {

    /**
     * Rebuilds a step from NBT, with the constructor its {@link PartyStepType} holds. Never returns null: an unknown
     * or broken step is replaced by a generic placeholder so that the indexes of the following steps (and the saved
     * step index) stay aligned.
     */
    public static PartyStep get(NbtCompound nbt){
        String type = nbt.getString("Type");
        PartyStepType stepType;
        try {
            stepType = PartyStepType.valueOf(type.toUpperCase());
        } catch (IllegalArgumentException e) {
            Steveparty.LOGGER.warn("Unknown party step type '{}', replaced by a placeholder step", type);
            return new PartyStep(nbt);
        }
        try {
            return stepType.fromNbt(nbt);
        } catch (RuntimeException e) {
            Steveparty.LOGGER.error("Could not rebuild party step of type {}, replaced by a placeholder step", type, e);
        }
        return new PartyStep(nbt);
    }
}
