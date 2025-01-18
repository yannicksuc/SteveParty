package fr.lordfinn.steveparty.blocks.custom.PartyController.steps;

import com.mojang.serialization.Codec;
import net.minecraft.text.Text;

public enum PartyStepType {
    DEFAULT("party_step_type.default"),
    TOKEN_TURN("party_step_type.token_turn"),
    MINI_GAME("party_step_type.mini_game"),
    START_ROLLS("party_step_type.start_rolls"),
    BASIC_GAME_GENERATOR("party_step_type.basic_game_generator"),
    END("party_step_type.end");

    private final String translationKey;

    public static final Codec<PartyStepType> CODEC = Codec.STRING.xmap(PartyStepType::valueOf, PartyStepType::name);

    PartyStepType(String translationKey) {
        this.translationKey = translationKey;
    }

    public String getTranslationKey() {
        return translationKey;
    }

    public Text getTranslatedText() {
        return Text.translatable(translationKey);
    }
}
