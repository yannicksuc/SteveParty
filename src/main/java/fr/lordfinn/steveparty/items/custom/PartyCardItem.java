package fr.lordfinn.steveparty.items.custom;

import fr.lordfinn.steveparty.Steveparty;
import net.minecraft.util.Identifier;
import fr.lordfinn.steveparty.items.tooltip.Tooltips;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.tooltip.TooltipType;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import fr.lordfinn.steveparty.blocks.custom.PartyController.EventTimeOut;
import fr.lordfinn.steveparty.components.ModComponents;
import fr.lordfinn.steveparty.sounds.ModSounds;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.util.Hand;
import net.minecraft.util.TypedActionResult;
import net.minecraft.world.World;

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
        /**
         * A time out (see fr.lordfinn.steveparty.blocks.custom.PartyController.EventTimeOut): rings the "event" party
         * bells (value = stack size, up to 15), sends the players out of its pipes (a right click on each) and waits for
         * the waiting bells or its hourglass (a right click in the air).
         */
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
        SEQUENCE_START("sequence_start"),
        /**
         * How the party ends: plays no step of its own (the end always comes last); linked to a pipe (a right click on
         * it), the players come out of that pipe once the party is over (see PartyEnding).
         */
        END("end"),
        /** A card of an addon: what it plays is the {@link fr.lordfinn.steveparty.api.party.PartyCard} of its id ({@link #getCardId}). */
        CUSTOM("custom");

        private final String name;

        CardType(String name) {
            this.name = name;
        }

        public String getName() {
            return name;
        }
    }

    private final CardType cardType;
    private final Identifier cardId;

    public PartyCardItem(CardType cardType, Settings settings) {
        super(settings);
        this.cardType = cardType;
        this.cardId = Steveparty.id(cardType.getName());
    }

    /** A card of an addon, playing the {@link fr.lordfinn.steveparty.api.party.PartyCard} registered under {@code cardId}. */
    public PartyCardItem(Identifier cardId, Settings settings) {
        super(settings);
        this.cardType = CardType.CUSTOM;
        this.cardId = cardId;
    }

    public CardType getCardType() {
        return cardType;
    }

    /** What it plays: an id of {@link fr.lordfinn.steveparty.api.StevePartyRegistries#PARTY_CARDS} ({@code steveparty:turns}...). */
    public Identifier getCardId() {
        return cardId;
    }

    @Override
    public void appendTooltip(ItemStack stack, TooltipContext context, List<Text> tooltip, TooltipType type) {
        super.appendTooltip(stack, context, tooltip, type);
        if (cardType == CardType.END) {
            fr.lordfinn.steveparty.blocks.custom.PartyController.EndPipe pipe = stack.get(fr.lordfinn.steveparty.components.ModComponents.END_PIPE);
            Tooltips tips = Tooltips.of(tooltip);
            if (pipe != null) tips.state("tooltip.steveparty.party_card_end.pipe",
                    Tooltips.value(pipe.mouth().pos().getX() + " " + pipe.mouth().pos().getY() + " " + pipe.mouth().pos().getZ()));
            tips.summary("item.steveparty.party_card_end.tooltip")
                    .more(more -> more.use("tooltip.steveparty.party_card_end.program").use(Tooltips.Keys.use(), "tooltip.steveparty.party_card_end.use"));
            return;
        }
        if (cardType == CardType.EVENT) {
            appendEventTooltip(stack, tooltip);
            return;
        }
        Tooltips.of(tooltip)
                .summary(cardType == CardType.CUSTOM ? getTranslationKey() + ".tooltip"
                        : "item.steveparty.party_card_" + cardType.getName() + ".tooltip", Tooltips.value(stack.getCount()))
                .more(more -> more.use("tooltip.steveparty.party_card.use"));
    }

    /** The Event card: its hourglass and pipes, what it does; with Shift, how to set it and what ends it. */
    private static void appendEventTooltip(ItemStack stack, List<Text> tooltip) {
        int seconds = stack.getOrDefault(ModComponents.EVENT_SECONDS, 0);
        int pipes = EventTimeOut.pipes(stack).size();
        Tooltips.of(tooltip)
                .state("tooltip.steveparty.event.hourglass", Tooltips.setting(EventTimeOut.durationText(seconds)))
                .state("tooltip.steveparty.event.pipes", Tooltips.value(pipes))
                .summary("item.steveparty.party_card_event.tooltip", Tooltips.value(stack.getCount()))
                .more(more -> more.use("tooltip.steveparty.party_card.use")
                        .use(Tooltips.Keys.use(), "tooltip.steveparty.party_card_event.use_pipe")
                        .use(Tooltips.Keys.use(), "tooltip.steveparty.party_card_event.use_air")
                        .note("tooltip.steveparty.event.ends")
                        .note("tooltip.steveparty.party_card_event.channel"));
    }

    /** An Event card used in the air: its hourglass turns to the next duration. */
    @Override
    public TypedActionResult<ItemStack> use(World world, PlayerEntity user, Hand hand) {
        ItemStack stack = user.getStackInHand(hand);
        if (cardType != CardType.EVENT) return super.use(world, user, hand);
        if (!world.isClient) {
            int seconds = EventTimeOut.nextDuration(stack.getOrDefault(ModComponents.EVENT_SECONDS, 0));
            if (seconds > 0) stack.set(ModComponents.EVENT_SECONDS, seconds);
            else stack.remove(ModComponents.EVENT_SECONDS);
            user.sendMessage(Text.translatable("message.steveparty.event.hourglass_set", EventTimeOut.durationText(seconds)), true);
            ModSounds.playSelect(world, user.getBlockPos());
        }
        return TypedActionResult.success(stack, world.isClient);
    }
}
