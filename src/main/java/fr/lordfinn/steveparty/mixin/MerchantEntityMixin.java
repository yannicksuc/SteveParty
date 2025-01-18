package fr.lordfinn.steveparty.mixin;

import fr.lordfinn.steveparty.Steveparty;
import fr.lordfinn.steveparty.utils.CashRegisterPersistentState;
import net.minecraft.entity.passive.MerchantEntity;
import net.minecraft.util.math.BlockPos;
import net.minecraft.village.TradeOffer;
import net.minecraft.world.World;
import fr.lordfinn.steveparty.blocks.custom.CashRegister;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;


@Mixin(MerchantEntity.class)
public class MerchantEntityMixin {

    @Inject(method = "trade", at = @At("TAIL"))
    private void onTrade(TradeOffer offer, CallbackInfo ci) {
        MerchantEntity merchant = (MerchantEntity) (Object) this;
        World world = merchant.getWorld();

        if (!world.isClient) {
            BlockPos merchantPos = merchant.getBlockPos();
            CashRegisterPersistentState cashRegisterState = CashRegisterPersistentState.get(world.getServer());

            Steveparty.LOGGER.info("Merchant pos: {}", merchantPos);
            Steveparty.LOGGER.info("CashRegisterState: {}", cashRegisterState);

            if (cashRegisterState != null) {
                cashRegisterState.getPositions().stream()
                        .filter(pos -> pos.isWithinDistance(merchantPos, 8))
                        .filter(pos -> world.getBlockState(pos).getBlock() instanceof CashRegister)
                        .forEach(pos -> {
                            Steveparty.LOGGER.info("CashRegister pos: {}", pos);
                            CashRegister block = (CashRegister) world.getBlockState(pos).getBlock();
                            block.setPowered(world, pos, true);
                        });
            }
        }
    }
}
