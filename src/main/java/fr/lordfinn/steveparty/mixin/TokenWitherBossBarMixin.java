package fr.lordfinn.steveparty.mixin;

import fr.lordfinn.steveparty.entities.TokenBase;
import net.minecraft.entity.boss.ServerBossBar;
import net.minecraft.entity.boss.WitherEntity;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * A Wither turned into a pawn is no longer a boss fight: its boss bar is hidden while it is a token, and comes back
 * when it is turned back into a mob. Hiding the bar keeps the players tracking it on its list, so it shows again for
 * them without anything else to do. Checked every tick (the bar only sends packets when its visibility changes).
 */
@Mixin(WitherEntity.class)
public abstract class TokenWitherBossBarMixin {
    @Shadow
    @Final
    private ServerBossBar bossBar;

    @Inject(method = "tickMovement", at = @At("HEAD"))
    private void steveparty$hideBossBarOfTokens(CallbackInfo ci) {
        boolean visible = !TokenBase.isToken((WitherEntity) (Object) this);
        if (this.bossBar.isVisible() != visible) this.bossBar.setVisible(visible);
    }
}
