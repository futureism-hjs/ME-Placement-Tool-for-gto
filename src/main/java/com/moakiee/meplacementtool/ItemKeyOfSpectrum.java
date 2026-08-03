package com.moakiee.meplacementtool;

import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;
import org.jetbrains.annotations.Nullable;
import appeng.items.materials.UpgradeCardItem;

import java.util.List;

/**
 * Key of Spectrum - An upgrade card that allows cables to be dyed in any color
 */
public class ItemKeyOfSpectrum extends UpgradeCardItem {

    public ItemKeyOfSpectrum(Properties properties) {
        super(properties);
    }

    @Override
    public void appendHoverText(ItemStack stack,
                                @Nullable Level world,
                                List<Component> tooltip,
                                TooltipFlag flag) {
        tooltip.add(Component.translatable("tooltip.meplacementtool.key_of_spectrum"));
        super.appendHoverText(stack, world, tooltip, flag);
    }
}
