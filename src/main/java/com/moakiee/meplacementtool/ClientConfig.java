package com.moakiee.meplacementtool;

import net.minecraftforge.common.ForgeConfigSpec;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.event.config.ModConfigEvent;

@Mod.EventBusSubscriber(modid = MEPlacementToolMod.MODID, bus = Mod.EventBusSubscriber.Bus.MOD)
public class ClientConfig {
    private static final ForgeConfigSpec.Builder BUILDER = new ForgeConfigSpec.Builder();
    public static final ForgeConfigSpec.LongValue HUD_DISPLAY_DURATION = BUILDER.comment("HUD display duration (ms) after switching to an item from this mod. 0 = disabled, -1 = permanent.").defineInRange("hudDisplayDuration", 2000L, -1, Long.MAX_VALUE);
    static final ForgeConfigSpec SPEC = BUILDER.build();

    public static long hudDisplayDuration;

    @SubscribeEvent
    static void onLoad(final ModConfigEvent event) {
        if (event.getConfig().getSpec() != ClientConfig.SPEC) return;
        hudDisplayDuration = HUD_DISPLAY_DURATION.get();
    }
}
