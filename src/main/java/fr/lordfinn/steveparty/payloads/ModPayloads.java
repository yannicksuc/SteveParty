package fr.lordfinn.steveparty.payloads;

import fr.lordfinn.steveparty.payloads.custom.*;

import static fr.lordfinn.steveparty.payloads.Payloads.c2s;
import static fr.lordfinn.steveparty.payloads.Payloads.s2c;

/** The mod's play payloads (those of the mini-game pages and the Telescope are registered by their own services). */
public class ModPayloads {
    public static void initialize() {
        // Server → client
        s2c(ArrowParticlesPayload.ID, ArrowParticlesPayload.CODEC);
        s2c(EnchantedCircularParticlePayload.ID, EnchantedCircularParticlePayload.CODEC);
        s2c(UpdateColoredTilePayload.ID, UpdateColoredTilePayload.CODEC);
        s2c(PartyDataPayload.ID, PartyDataPayload.CODEC);
        s2c(PartyLivePayload.ID, PartyLivePayload.CODEC);
        s2c(PartyDashboardPayload.ID, PartyDashboardPayload.CODEC);
        s2c(FloatingTextPayload.ID, FloatingTextPayload.CODEC);
        s2c(SwitchableBlocksPayload.ID, SwitchableBlocksPayload.CODEC);
        s2c(StarSpacesPayload.ID, StarSpacesPayload.CODEC);
        s2c(SquishAnimationPayload.ID, SquishAnimationPayload.CODEC);
        s2c(StencilHammerStrikePayload.ID, StencilHammerStrikePayload.CODEC);
        s2c(OpenTokenSpellPayload.ID, OpenTokenSpellPayload.CODEC);
        s2c(TrapSetupPayloads.Open.ID, TrapSetupPayloads.Open.CODEC);
        c2s(TrapSetupPayloads.Sign.ID, TrapSetupPayloads.Sign.CODEC);
        s2c(DicePromptPayload.ID, DicePromptPayload.CODEC);
        s2c(DiceRevealPayload.ID, DiceRevealPayload.CODEC);
        s2c(SixSevenPayload.ID, SixSevenPayload.CODEC);

        // Client → server
        c2s(SaveStencilPayload.ID, SaveStencilPayload.CODEC);
        c2s(StencilMakerActionPayload.ID, StencilMakerActionPayload.CODEC);
        c2s(ToolWheelPayload.ID, ToolWheelPayload.CODEC);
        c2s(PipettePayload.ID, PipettePayload.CODEC);
        c2s(DestinationSwapPayload.ID, DestinationSwapPayload.CODEC);
        c2s(PageZonePayload.ID, PageZonePayload.CODEC);
        c2s(HeldItemScrollPayload.ID, HeldItemScrollPayload.CODEC);
        c2s(GoalPoleBasePayload.ID, GoalPoleBasePayload.CODEC);
        c2s(GoalPolePayload.ID, GoalPolePayload.CODEC);
        c2s(CartridgeSlotScrollPayload.ID, CartridgeSlotScrollPayload.CODEC);
        c2s(CartridgeSettingPayload.ID, CartridgeSettingPayload.CODEC);
        c2s(TokenSpellPayload.ID, TokenSpellPayload.CODEC);
        c2s(VillagerBlockPunchPayload.ID, VillagerBlockPunchPayload.CODEC);
        c2s(DicePromptAnswerPayload.ID, DicePromptAnswerPayload.CODEC);
    }
}
