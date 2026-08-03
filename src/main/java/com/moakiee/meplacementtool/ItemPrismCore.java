package com.moakiee.meplacementtool;

import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;
import org.jetbrains.annotations.Nullable;

import java.util.List;

/**
 * Prism Core - An intermediate crafting item that carries the possibility of colors
 */
public class ItemPrismCore extends Item {

    public ItemPrismCore(Properties properties) {
        super(properties);
    }

    @Override
    public void appendHoverText(ItemStack stack,
                                @Nullable Level world,
                                List<Component> tooltip,
                                TooltipFlag flag) {
        // Display tooltip with colorful "Key of Spectrum" (colors defined in lang file)
        tooltip.add(Component.translatable("tooltip.meplacementtool.prism_core.prefix"));
        super.appendHoverText(stack, world, tooltip, flag);
    }
}
