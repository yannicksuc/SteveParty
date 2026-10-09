package fr.lordfinn.steveparty.items.custom;

import fr.lordfinn.steveparty.items.tooltip.Tooltips;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.tooltip.TooltipType;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;

import java.util.List;

/**
 * A card of the party program: put in the program slots of a party controller (wrench + right-click), read from
 * left to right, top to bottom, it tells what the party is made of. The size of the stack is the card's number
 * (how many times a "repeat" card repeats, the channel of an "event" card).
 */
public class PartyCardItem extends Item {
    public enum CardType {
        /** Every token plays one turn, in the play order. */
        TURNS("turns"),
        /** A mini-game drawn from the catalogue. */
        MINIGAME("minigame"),
        /** Rings the "event" party bells (value = stack size, up to 15) and waits for the waiting ones. */
        EVENT("event"),
        /**
         * Plays N times in all (N = stack size) the cards since the nearest "sequence start" or "repeat" card on its
         * left; since the beginning of the program when there is none.
         */
        REPEAT("repeat"),
        /**
         * A marker: where the next "repeat" card starts repeating from. Without a "repeat" card after it, it does
         * nothing; a stack of several is the same as one.
         */
        SEQUENCE_START("sequence_start");

        private final String name;

        CardType(String name) {
            this.name = name;
        }

        public String getName() {
            return name;
        }
    }

    private final CardType cardType;

    public PartyCardItem(CardType cardType, Settings settings) {
        super(settings);
        this.cardType = cardType;
    }

    public CardType getCardType() {
        return cardType;
    }

    @Override
    public void appendTooltip(ItemStack stack, TooltipContext context, List<Text> tooltip, TooltipType type) {
        super.appendTooltip(stack, context, tooltip, type);
        Tooltips.of(tooltip)
                .summary("item.steveparty.party_card_" + cardType.getName() + ".tooltip", Tooltips.value(stack.getCount()))
                .more(more -> more.use("tooltip.steveparty.party_card.use"));
    }
}
