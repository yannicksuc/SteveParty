package fr.lordfinn.steveparty.criteria;


import fr.lordfinn.steveparty.Steveparty;
import net.minecraft.scoreboard.ScoreboardCriterion;

public class ModScoreboardCriteria {

    public static ScoreboardCriterion LANDED_ON_POLE;
    public static String LANDED_ON_POLE_ID = Steveparty.MOD_ID + ":landed_on_pole";
    public static void initialize() {
        LANDED_ON_POLE = ScoreboardCriterion.create(LANDED_ON_POLE_ID, false, ScoreboardCriterion.RenderType.INTEGER);
    }
}
