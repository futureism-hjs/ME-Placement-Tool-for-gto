package com.moakiee.meplacementtool;

import appeng.api.config.Actionable;
import appeng.api.features.GridLinkables;
import appeng.api.implementations.items.IAEItemPowerStorage;
import com.mojang.logging.LogUtils;
import com.moakiee.meplacementtool.client.CablePreviewRenderer;
import com.moakiee.meplacementtool.client.CableToolScreen;
import com.moakiee.meplacementtool.client.MEPartPreviewRenderer;
import com.moakiee.meplacementtool.client.ModKeyBindings;
import com.moakiee.meplacementtool.client.MultiblockPreviewRenderer;
import com.moakiee.meplacementtool.client.RadialMenuKeyHandler;
import com.moakiee.meplacementtool.client.UndoKeyHandler;
import com.moakiee.meplacementtool.client.ToolInfoHudRenderer;
import com.moakiee.meplacementtool.network.ClearCableToolPointsPacket;
import com.moakiee.meplacementtool.network.ModNetwork;
import com.moakiee.meplacementtool.recipe.PlacementToolRecipeRegistration;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.MenuScreens;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.CreativeModeTabs;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RegisterKeyMappingsEvent;
import net.minecraftforge.client.event.RenderGuiOverlayEvent;
import net.minecraftforge.client.gui.overlay.VanillaGuiOverlay;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.BuildCreativeModeTabContentsEvent;
import net.minecraftforge.event.server.ServerStartedEvent;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.ModLoadingContext;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.config.ModConfig;
import net.minecraftforge.fml.event.lifecycle.FMLClientSetupEvent;
import net.minecraftforge.fml.event.lifecycle.FMLCommonSetupEvent;
import net.minecraftforge.fml.event.lifecycle.FMLLoadCompleteEvent;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;
import org.slf4j.Logger;

/** GTO-compatible standalone port of ME Placement Tool. */
@Mod(MEPlacementToolMod.MODID)
public final class MEPlacementToolMod {
    public static final String MODID = "meplacementtool";
    public static final Logger LOGGER = LogUtils.getLogger();

    public static final DeferredRegister<Item> ITEMS =
            DeferredRegister.create(ForgeRegistries.ITEMS, MODID);

    public static final RegistryObject<Item> ME_PLACEMENT_TOOL = ITEMS.register(
            "me_placement_tool",
            () -> new ItemMEPlacementTool(new Item.Properties().stacksTo(1)));
    public static final RegistryObject<Item> MULTIBLOCK_PLACEMENT_TOOL = ITEMS.register(
            "multiblock_placement_tool",
            () -> new ItemMultiblockPlacementTool(new Item.Properties().stacksTo(1)));
    public static final RegistryObject<Item> KEY_OF_SPECTRUM = ITEMS.register(
            "key_of_spectrum",
            () -> new ItemKeyOfSpectrum(new Item.Properties().stacksTo(64)));
    public static final RegistryObject<Item> PRISM_CORE = ITEMS.register(
            "prism_core",
            () -> new ItemPrismCore(new Item.Properties().stacksTo(64)));
    public static final RegistryObject<Item> ME_CABLE_PLACEMENT_TOOL = ITEMS.register(
            "me_cable_placement_tool",
            () -> new ItemMECablePlacementTool(new Item.Properties().stacksTo(1)));

    public static MEPlacementToolMod instance;

    public final UndoHistory undoHistory;

    public MEPlacementToolMod() {
        if (instance != null) {
            throw new IllegalStateException("ME Placement Tool for GTO was initialized twice");
        }
        instance = this;
        undoHistory = new UndoHistory();
        IEventBus modBus = FMLJavaModLoadingContext.get().getModEventBus();

        ModMenus.register(modBus);
        ITEMS.register(modBus);
        ModNetwork.register();
        modBus.addListener(this::commonSetup);
        modBus.addListener(this::addCreativeTabContents);
        modBus.addListener(this::loadComplete);

        MinecraftForge.EVENT_BUS.register(undoHistory);
        MinecraftForge.EVENT_BUS.addListener(this::serverStarted);
        ModLoadingContext.get().registerConfig(
                ModConfig.Type.COMMON, Config.SPEC, "meplacementtool-common.toml");
        ModLoadingContext.get().registerConfig(
                ModConfig.Type.CLIENT, ClientConfig.SPEC, "meplacementtool-client.toml");
    }

    public static ResourceLocation id(String path) {
        return new ResourceLocation(MODID, path);
    }

    public static ResourceLocation contentId(String path) {
        return id(path);
    }

    private void commonSetup(FMLCommonSetupEvent event) {
        event.enqueueWork(() -> {
            GridLinkables.register(ME_PLACEMENT_TOOL.get(), BasePlacementToolItem.LINKABLE_HANDLER);
            GridLinkables.register(MULTIBLOCK_PLACEMENT_TOOL.get(), BasePlacementToolItem.LINKABLE_HANDLER);
            GridLinkables.register(ME_CABLE_PLACEMENT_TOOL.get(), BasePlacementToolItem.LINKABLE_HANDLER);
            appeng.api.upgrades.Upgrades.add(KEY_OF_SPECTRUM.get(), ME_CABLE_PLACEMENT_TOOL.get(), 1);
            LOGGER.info("Registered GTO-compatible ME placement-tool link handlers");
        });
    }

    private void loadComplete(FMLLoadCompleteEvent event) {
        validateLoaded();
        PlacementToolRecipeRegistration.validateLoaded();
        LOGGER.info("ME Placement Tool for GTO registration validated");
    }

    private void serverStarted(ServerStartedEvent event) {
        PlacementToolRecipeRegistration.validateServerRecipes(event.getServer());
    }

    private void addCreativeTabContents(BuildCreativeModeTabContentsEvent event) {
        if (!CreativeModeTabs.TOOLS_AND_UTILITIES.equals(event.getTabKey())) {
            return;
        }
        event.accept(fullyChargedStack(ME_PLACEMENT_TOOL.get()));
        event.accept(fullyChargedStack(MULTIBLOCK_PLACEMENT_TOOL.get()));
        event.accept(fullyChargedStack(ME_CABLE_PLACEMENT_TOOL.get()));
        event.accept(KEY_OF_SPECTRUM.get());
        event.accept(PRISM_CORE.get());
    }

    private static ItemStack fullyChargedStack(Item item) {
        ItemStack stack = new ItemStack(item);
        if (item instanceof IAEItemPowerStorage storage) {
            storage.injectAEPower(stack, storage.getAEMaxPower(stack), Actionable.MODULATE);
        }
        return stack;
    }

    public static void validateLoaded() {
        if (!ME_PLACEMENT_TOOL.isPresent() || !MULTIBLOCK_PLACEMENT_TOOL.isPresent()
                || !ME_CABLE_PLACEMENT_TOOL.isPresent() || !KEY_OF_SPECTRUM.isPresent()
                || !PRISM_CORE.isPresent() || !ModMenus.WAND_MENU.isPresent()
                || !ModMenus.CABLE_TOOL_MENU.isPresent()) {
            throw new IllegalStateException("ME placement-tool items or menus were not registered");
        }
        if (GridLinkables.get(ME_PLACEMENT_TOOL.get()) != BasePlacementToolItem.LINKABLE_HANDLER
                || GridLinkables.get(MULTIBLOCK_PLACEMENT_TOOL.get()) != BasePlacementToolItem.LINKABLE_HANDLER
                || GridLinkables.get(ME_CABLE_PLACEMENT_TOOL.get()) != BasePlacementToolItem.LINKABLE_HANDLER) {
            throw new IllegalStateException("ME placement-tool GridLinkables were not registered");
        }
    }

    @Mod.EventBusSubscriber(modid = MODID, bus = Mod.EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
    public static final class ClientModEvents {
        private ClientModEvents() {
        }

        @SubscribeEvent
        public static void onClientSetup(FMLClientSetupEvent event) {
            event.enqueueWork(() -> {
                MenuScreens.register(ModMenus.WAND_MENU.get(), WandScreen::new);
                MenuScreens.register(ModMenus.CABLE_TOOL_MENU.get(), CableToolScreen::new);
                MinecraftForge.EVENT_BUS.register(new MultiblockPreviewRenderer());
                MinecraftForge.EVENT_BUS.register(new UndoKeyHandler());
                MinecraftForge.EVENT_BUS.register(new RadialMenuKeyHandler());
                MEPartPreviewRenderer.install();
                CablePreviewRenderer.install();
            });
        }

        @SubscribeEvent
        public static void onRegisterKeyMappings(RegisterKeyMappingsEvent event) {
            event.register(ModKeyBindings.OPEN_RADIAL_MENU);
            event.register(ModKeyBindings.OPEN_CABLE_TOOL_GUI);
            event.register(ModKeyBindings.UNDO_MODIFIER);
            event.register(ModKeyBindings.MARK_COLOR_SHORTCUT);
        }
    }

    @Mod.EventBusSubscriber(modid = MODID, bus = Mod.EventBusSubscriber.Bus.FORGE, value = Dist.CLIENT)
    public static final class ClientForgeEvents {
        private static final ToolInfoHudRenderer TOOL_INFO_HUD = new ToolInfoHudRenderer();
        public static String lastSelectedText;
        public static long lastSelectedTime;
        public static String lastCountText;
        public static long lastCountTime;

        static {
            MinecraftForge.EVENT_BUS.register(TOOL_INFO_HUD);
        }

        private ClientForgeEvents() {
        }

        @SubscribeEvent
        public static void onRenderCrosshair(RenderGuiOverlayEvent.Pre event) {
            if (event.getOverlay() == VanillaGuiOverlay.CROSSHAIR.type()) {
                var screen = Minecraft.getInstance().screen;
                if (screen instanceof com.moakiee.meplacementtool.client.RadialMenuScreen
                        || screen instanceof com.moakiee.meplacementtool.client.DualLayerRadialMenuScreen) {
                    event.setCanceled(true);
                }
            }
        }

        public static void showCountOverlay(String text) {
            lastCountText = text;
            lastCountTime = System.currentTimeMillis();
        }

        public static void showSelectedOverlay(String text) {
            lastSelectedText = text;
            lastSelectedTime = System.currentTimeMillis();
        }

        @SubscribeEvent
        public static void onRenderOverlay(RenderGuiOverlayEvent.Post event) {
            if (event.getOverlay() != VanillaGuiOverlay.HOTBAR.type()) {
                return;
            }
            var minecraft = Minecraft.getInstance();
            int width = minecraft.getWindow().getGuiScaledWidth();
            int height = minecraft.getWindow().getGuiScaledHeight();
            var graphics = event.getGuiGraphics();
            var font = minecraft.font;
            long now = System.currentTimeMillis();
            if (lastSelectedText != null && now - lastSelectedTime < 2_000L) {
                int textWidth = font.width(lastSelectedText);
                graphics.drawString(font, lastSelectedText, width / 2 - textWidth / 2, height - 50, 0xFFFFFF, false);
            }
            if (lastCountText != null && now - lastCountTime < 2_000L) {
                int textWidth = font.width(lastCountText);
                graphics.drawString(font, lastCountText, width / 2 - textWidth / 2, height - 70, 0xFFFF00, false);
            }
        }
    }

    @Mod.EventBusSubscriber(modid = MODID, bus = Mod.EventBusSubscriber.Bus.FORGE)
    public static final class CommonForgeEvents {
        private CommonForgeEvents() {
        }

        @SubscribeEvent
        public static void onLeftClickBlock(net.minecraftforge.event.entity.player.PlayerInteractEvent.LeftClickBlock event) {
            if (clearCablePoints(event.getEntity(), event.getLevel().isClientSide)) {
                event.setCanceled(true);
            }
        }

        @SubscribeEvent
        public static void onLeftClickEmpty(net.minecraftforge.event.entity.player.PlayerInteractEvent.LeftClickEmpty event) {
            var player = event.getEntity();
            var stack = player.getMainHandItem();
            if (stack.getItem() != ME_CABLE_PLACEMENT_TOOL.get()) {
                return;
            }
            if (ItemMECablePlacementTool.getPoint1(stack) != null
                    || ItemMECablePlacementTool.getPoint2(stack) != null
                    || ItemMECablePlacementTool.getPoint3(stack) != null) {
                ModNetwork.CHANNEL.sendToServer(new ClearCableToolPointsPacket(player.getInventory().selected));
            }
        }

        private static boolean clearCablePoints(net.minecraft.world.entity.player.Player player, boolean clientSide) {
            var stack = player.getMainHandItem();
            if (stack.getItem() != ME_CABLE_PLACEMENT_TOOL.get()) {
                return false;
            }
            if (ItemMECablePlacementTool.getPoint1(stack) == null
                    && ItemMECablePlacementTool.getPoint2(stack) == null
                    && ItemMECablePlacementTool.getPoint3(stack) == null) {
                return false;
            }
            ItemMECablePlacementTool.setPoint1(stack, null);
            ItemMECablePlacementTool.setPoint2(stack, null);
            ItemMECablePlacementTool.setPoint3(stack, null);
            if (!clientSide) {
                player.displayClientMessage(Component.translatable("message.meplacementtool.points_cleared"), true);
            }
            return true;
        }

        @SubscribeEvent
        public static void onEquipmentChange(net.minecraftforge.event.entity.living.LivingEquipmentChangeEvent event) {
            if (!(event.getEntity() instanceof net.minecraft.world.entity.player.Player)
                    || event.getSlot() != net.minecraft.world.entity.EquipmentSlot.MAINHAND
                    || event.getFrom().getItem() != ME_CABLE_PLACEMENT_TOOL.get()
                    || event.getTo().getItem() == ME_CABLE_PLACEMENT_TOOL.get()) {
                return;
            }
            ItemMECablePlacementTool.setPoint1(event.getFrom(), null);
            ItemMECablePlacementTool.setPoint2(event.getFrom(), null);
            ItemMECablePlacementTool.setPoint3(event.getFrom(), null);
        }
    }
}
