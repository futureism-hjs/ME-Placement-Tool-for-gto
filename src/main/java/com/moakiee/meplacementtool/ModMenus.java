package com.moakiee.meplacementtool;

import net.minecraft.core.registries.Registries;
import net.minecraft.world.inventory.MenuType;
import net.minecraftforge.common.extensions.IForgeMenuType;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.RegistryObject;

public class ModMenus {
    public static final DeferredRegister<MenuType<?>> MENUS = DeferredRegister.create(Registries.MENU, MEPlacementToolMod.MODID);

    public static final RegistryObject<MenuType<WandMenu>> WAND_MENU = MENUS.register("wand_menu",
            () -> IForgeMenuType.create((id, inv, buf) -> new WandMenu(id, inv, buf)));

    public static final RegistryObject<MenuType<CableToolMenu>> CABLE_TOOL_MENU = MENUS.register("cable_tool_menu",
            () -> IForgeMenuType.create((id, inv, buf) -> new CableToolMenu(id, inv, buf)));

    public static void register(IEventBus bus) {
        MENUS.register(bus);
    }
}
