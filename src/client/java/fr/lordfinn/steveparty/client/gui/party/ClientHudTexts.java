package fr.lordfinn.steveparty.client.gui.party;

import fr.lordfinn.steveparty.hud.HudTexts;
import fr.lordfinn.steveparty.hud.TurnStripLayout;
import net.minecraft.text.Text;

/** The HUD layouts' texts on the client: the game's font, the translations. */
final class ClientHudTexts implements HudTexts {
    static final ClientHudTexts INSTANCE = new ClientHudTexts();

    private ClientHudTexts() {
    }

    @Override
    public int width(String text) {
        return text.isEmpty() ? 0 : HudDraw.font().getWidth(text) - 1;
    }

    @Override
    public String step(TurnStripLayout.Kind kind) {
        return Text.translatable(switch (kind) {
            case START -> "hud.steveparty.party.round.start";
            case MINI_GAME -> "hud.steveparty.party.strip.mini_game";
            case EVENT -> "hud.steveparty.party.strip.event";
            case PREPARING -> "hud.steveparty.party.strip.preparing";
            case END -> "hud.steveparty.party.strip.end";
            default -> "hud.steveparty.party.strip.other";
        }).getString();
    }

    @Override
    public String pinned(int steps) {
        return Text.translatable("hud.steveparty.party.strip.pinned", steps).getString();
    }

    @Override
    public String toi() {
        return Text.translatable("hud.steveparty.party.toi").getString();
    }

    @Override
    public String toiNow() {
        return Text.translatable("hud.steveparty.party.toi.now").getString();
    }

    @Override
    public String toiIn(int steps) {
        return Text.translatable("hud.steveparty.party.toi.in", steps).getString();
    }
}
