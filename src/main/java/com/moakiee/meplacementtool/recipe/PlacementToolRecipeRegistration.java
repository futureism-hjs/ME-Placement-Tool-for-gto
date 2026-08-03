package com.moakiee.meplacementtool.recipe;

import com.gregtechceu.gtceu.api.data.chemical.material.stack.MaterialEntry;
import com.gregtechceu.gtceu.api.data.tag.TagPrefix;
import com.gregtechceu.gtceu.common.data.GTMaterials;
import com.gregtechceu.gtceu.data.recipe.VanillaRecipeHelper;
import com.gregtechceu.gtceu.data.recipe.builder.ShapedRecipeBuilder;
import com.moakiee.meplacementtool.MEPlacementToolMod;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.tags.ItemTags;
import net.minecraft.tags.TagKey;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.Recipe;
import net.minecraft.world.item.crafting.RecipeType;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Registers the five crafting recipes in GTO's authoritative recipe-loading window. */
public final class PlacementToolRecipeRegistration {
    public enum State {
        NOT_STARTED,
        REGISTERING,
        REGISTERED,
        FAILED
    }

    public static final ResourceLocation ME_PLACEMENT_TOOL_RECIPE_ID =
            MEPlacementToolMod.contentId("me_placement_tool");
    public static final ResourceLocation MULTIBLOCK_PLACEMENT_TOOL_RECIPE_ID =
            MEPlacementToolMod.contentId("multiblock_placement_tool");
    public static final ResourceLocation ME_CABLE_PLACEMENT_TOOL_RECIPE_ID =
            MEPlacementToolMod.contentId("me_cable_placement_tool");
    public static final ResourceLocation PRISM_CORE_RECIPE_ID =
            MEPlacementToolMod.contentId("prism_core");
    public static final ResourceLocation KEY_OF_SPECTRUM_RECIPE_ID =
            MEPlacementToolMod.contentId("key_of_spectrum");

    private static final TagKey<Item> AE2_SMART_DENSE_CABLES =
            ItemTags.create(new ResourceLocation("ae2", "smart_dense_cable"));
    private static final List<ResourceLocation> RAW_RECIPE_IDS = List.of(
            ME_PLACEMENT_TOOL_RECIPE_ID,
            MULTIBLOCK_PLACEMENT_TOOL_RECIPE_ID,
            ME_CABLE_PLACEMENT_TOOL_RECIPE_ID,
            PRISM_CORE_RECIPE_ID,
            KEY_OF_SPECTRUM_RECIPE_ID);

    private static volatile State state = State.NOT_STARTED;
    private static volatile Item mePlacementToolOutput;
    private static volatile Item multiblockPlacementToolOutput;
    private static volatile Item meCablePlacementToolOutput;
    private static volatile Item prismCoreOutput;
    private static volatile Item keyOfSpectrumOutput;

    private PlacementToolRecipeRegistration() {
    }

    /** Called by the minimal coremod immediately after GTO's RecipeFilter initialization. */
    public static synchronized void register() {
        if (state == State.REGISTERED || state == State.REGISTERING) {
            MEPlacementToolMod.LOGGER.info("Skipping duplicate placement-tool recipe registration; state={}", state);
            return;
        }
        state = State.REGISTERING;
        try {
            Item mePlacementTool = requiredItem("meplacementtool:me_placement_tool");
            Item multiblockPlacementTool = requiredItem("meplacementtool:multiblock_placement_tool");
            Item meCablePlacementTool = requiredItem("meplacementtool:me_cable_placement_tool");
            Item prismCore = requiredItem("meplacementtool:prism_core");
            Item keyOfSpectrum = requiredItem("meplacementtool:key_of_spectrum");
            Item formationPlane = requiredItem("ae2:formation_plane");
            Item wirelessTerminal = requiredItem("ae2:wireless_terminal");
            Item denseEnergyCell = requiredItem("ae2:dense_energy_cell");
            Item colorApplicator = requiredItem("ae2:color_applicator");
            Item advancedCard = requiredItem("ae2:advanced_card");
            Item whitePaintBall = requiredItem("ae2:white_paint_ball");
            Item orangePaintBall = requiredItem("ae2:orange_paint_ball");
            Item magentaPaintBall = requiredItem("ae2:magenta_paint_ball");
            Item lightBluePaintBall = requiredItem("ae2:light_blue_paint_ball");
            Item grayPaintBall = requiredItem("ae2:gray_paint_ball");
            Item yellowPaintBall = requiredItem("ae2:yellow_paint_ball");
            Item limePaintBall = requiredItem("ae2:lime_paint_ball");
            Item pinkPaintBall = requiredItem("ae2:pink_paint_ball");
            Item lightGrayPaintBall = requiredItem("ae2:light_gray_paint_ball");
            Item cyanPaintBall = requiredItem("ae2:cyan_paint_ball");
            Item purplePaintBall = requiredItem("ae2:purple_paint_ball");
            Item bluePaintBall = requiredItem("ae2:blue_paint_ball");
            Item blackPaintBall = requiredItem("ae2:black_paint_ball");
            Item brownPaintBall = requiredItem("ae2:brown_paint_ball");
            Item redPaintBall = requiredItem("ae2:red_paint_ball");
            Item greenPaintBall = requiredItem("ae2:green_paint_ball");

            VanillaRecipeHelper.addShapedRecipe(
                    ME_PLACEMENT_TOOL_RECIPE_ID,
                    mePlacementTool,
                    "  A",
                    " B ",
                    "C  ",
                    'A', formationPlane,
                    'B', wirelessTerminal,
                    'C', new MaterialEntry(TagPrefix.rod, GTMaterials.Wood));

            VanillaRecipeHelper.addShapedRecipe(
                    MULTIBLOCK_PLACEMENT_TOOL_RECIPE_ID,
                    multiblockPlacementTool,
                    "  A",
                    " B ",
                    "C  ",
                    'A', denseEnergyCell,
                    'B', mePlacementTool,
                    'C', new MaterialEntry(TagPrefix.gem, GTMaterials.NetherStar));

            VanillaRecipeHelper.addShapedRecipe(
                    ME_CABLE_PLACEMENT_TOOL_RECIPE_ID,
                    meCablePlacementTool,
                    " AB",
                    " CD",
                    "E  ",
                    'A', AE2_SMART_DENSE_CABLES,
                    'B', colorApplicator,
                    'C', wirelessTerminal,
                    'D', denseEnergyCell,
                    'E', new MaterialEntry(TagPrefix.rod, GTMaterials.Wood));

            VanillaRecipeHelper.addShapedRecipe(
                    PRISM_CORE_RECIPE_ID,
                    prismCore,
                    "ABC",
                    "DEF",
                    "GHI",
                    'A', whitePaintBall,
                    'B', orangePaintBall,
                    'C', magentaPaintBall,
                    'D', lightBluePaintBall,
                    'E', advancedCard,
                    'F', grayPaintBall,
                    'G', yellowPaintBall,
                    'H', limePaintBall,
                    'I', pinkPaintBall);

            VanillaRecipeHelper.addShapedRecipe(
                    KEY_OF_SPECTRUM_RECIPE_ID,
                    keyOfSpectrum,
                    "ABC",
                    "DEF",
                    "GHI",
                    'A', lightGrayPaintBall,
                    'B', cyanPaintBall,
                    'C', purplePaintBall,
                    'D', bluePaintBall,
                    'E', prismCore,
                    'F', blackPaintBall,
                    'G', brownPaintBall,
                    'H', redPaintBall,
                    'I', greenPaintBall);

            mePlacementToolOutput = mePlacementTool;
            multiblockPlacementToolOutput = multiblockPlacementTool;
            meCablePlacementToolOutput = meCablePlacementTool;
            prismCoreOutput = prismCore;
            keyOfSpectrumOutput = keyOfSpectrum;
            state = State.REGISTERED;
            MEPlacementToolMod.LOGGER.info("Registered five GTO-native placement-tool crafting recipes: {}",
                    resolvedRecipeIds());
        } catch (Throwable error) {
            state = State.FAILED;
            clearOutputs();
            MEPlacementToolMod.LOGGER.error("Placement-tool crafting recipe registration failed", error);
        }
    }

    public static synchronized void validateLoaded() {
        if (state != State.REGISTERED ||
                mePlacementToolOutput == null || mePlacementToolOutput == Items.AIR ||
                multiblockPlacementToolOutput == null || multiblockPlacementToolOutput == Items.AIR ||
                meCablePlacementToolOutput == null || meCablePlacementToolOutput == Items.AIR ||
                prismCoreOutput == null || prismCoreOutput == Items.AIR ||
                keyOfSpectrumOutput == null || keyOfSpectrumOutput == Items.AIR) {
            throw new IllegalStateException("Placement-tool crafting recipes were not registered; state=" + state);
        }
    }

    public static void validateServerRecipes(MinecraftServer server) {
        Map<ResourceLocation, Item> expectedOutputs = expectedOutputs();
        List<ResourceLocation> missing = new ArrayList<>();
        List<String> invalid = new ArrayList<>();
        expectedOutputs.forEach((id, expectedOutput) -> {
            Recipe<?> recipe = server.getRecipeManager().byKey(id).orElse(null);
            if (recipe == null) {
                missing.add(id);
                return;
            }
            Item actualOutput = recipe.getResultItem(server.registryAccess()).getItem();
            if (recipe.getType() != RecipeType.CRAFTING || actualOutput != expectedOutput) {
                invalid.add(id + "{type=" + recipe.getType() + ", output=" +
                        BuiltInRegistries.ITEM.getKey(actualOutput) + "}");
            }
        });
        if (!missing.isEmpty() || !invalid.isEmpty()) {
            throw new IllegalStateException(
                    "Invalid final placement-tool recipes: missing=" + missing + ", invalid=" + invalid);
        }
        MEPlacementToolMod.LOGGER.info("Validated five placement-tool recipes in the final RecipeManager");
    }

    private static Item requiredItem(String rawId) {
        ResourceLocation expectedId = ResourceLocation.tryParse(rawId);
        if (expectedId == null) {
            throw new IllegalArgumentException("Invalid crafting item ID: " + rawId);
        }
        Item item = BuiltInRegistries.ITEM.get(expectedId);
        ResourceLocation actualId = BuiltInRegistries.ITEM.getKey(item);
        if (item == Items.AIR || !expectedId.equals(actualId)) {
            throw new IllegalStateException(
                    "Missing or mismatched crafting item: expected=" + expectedId + ", actual=" + actualId);
        }
        return item;
    }

    private static ResourceLocation resolveShapedRecipeId(ResourceLocation rawId) {
        return new ShapedRecipeBuilder(rawId).getId();
    }

    private static List<ResourceLocation> resolvedRecipeIds() {
        return RAW_RECIPE_IDS.stream().map(PlacementToolRecipeRegistration::resolveShapedRecipeId).toList();
    }

    private static synchronized Map<ResourceLocation, Item> expectedOutputs() {
        if (state != State.REGISTERED) {
            throw new IllegalStateException("Placement-tool crafting outputs are unavailable; state=" + state);
        }
        Map<ResourceLocation, Item> outputs = new LinkedHashMap<>();
        outputs.put(resolveShapedRecipeId(ME_PLACEMENT_TOOL_RECIPE_ID), mePlacementToolOutput);
        outputs.put(resolveShapedRecipeId(MULTIBLOCK_PLACEMENT_TOOL_RECIPE_ID), multiblockPlacementToolOutput);
        outputs.put(resolveShapedRecipeId(ME_CABLE_PLACEMENT_TOOL_RECIPE_ID), meCablePlacementToolOutput);
        outputs.put(resolveShapedRecipeId(PRISM_CORE_RECIPE_ID), prismCoreOutput);
        outputs.put(resolveShapedRecipeId(KEY_OF_SPECTRUM_RECIPE_ID), keyOfSpectrumOutput);
        return outputs;
    }

    private static void clearOutputs() {
        mePlacementToolOutput = null;
        multiblockPlacementToolOutput = null;
        meCablePlacementToolOutput = null;
        prismCoreOutput = null;
        keyOfSpectrumOutput = null;
    }

    public static State state() {
        return state;
    }
}
