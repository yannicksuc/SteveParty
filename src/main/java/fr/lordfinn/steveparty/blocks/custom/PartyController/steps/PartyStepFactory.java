package fr.lordfinn.steveparty.blocks.custom.PartyController.steps;

import fr.lordfinn.steveparty.Steveparty;
import net.minecraft.nbt.NbtCompound;

import java.lang.reflect.InvocationTargetException;
import java.util.HashMap;
import java.util.Map;

public class PartyStepFactory {

    private static final Map<PartyStepType, Class<? extends PartyStep>> STEPS_TYPES = new HashMap<>();

    static {
        STEPS_TYPES.put(PartyStepType.DEFAULT, PartyStep.class);
        STEPS_TYPES.put(PartyStepType.TOKEN_TURN, TokenTurnPartyStep.class);
        STEPS_TYPES.put(PartyStepType.MINI_GAME, MiniGamePartyStep.class);
        STEPS_TYPES.put(PartyStepType.END, EndPartyStep.class);
        STEPS_TYPES.put(PartyStepType.START_ROLLS, StartRollsStep.class);
        STEPS_TYPES.put(PartyStepType.BASIC_GAME_GENERATOR, BasicGameGeneratorStep.class);
        STEPS_TYPES.put(PartyStepType.EVENT, EventPartyStep.class);
    }

    /**
     * Rebuilds a step from NBT. Never returns null: an unknown or broken step is replaced by a generic
     * placeholder so that the indexes of the following steps (and the saved step index) stay aligned.
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
        Class<? extends PartyStep> stepClass = STEPS_TYPES.getOrDefault(stepType, PartyStep.class);
        try {
            return stepClass.getConstructor(NbtCompound.class).newInstance(nbt);
        } catch (NoSuchMethodException | InvocationTargetException | InstantiationException |
                 IllegalAccessException e) {
            Steveparty.LOGGER.error("Could not rebuild party step of type {}, replaced by a placeholder step", type, e);
        }
        return new PartyStep(nbt);
    }
}
