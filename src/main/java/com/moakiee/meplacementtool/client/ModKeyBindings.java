package com.moakiee.meplacementtool.client;

import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.client.KeyMapping;
import net.minecraftforge.client.settings.KeyConflictContext;
import org.lwjgl.glfw.GLFW;

/**
 * Key bindings for the ME Placement Tool mod.
 */
public class ModKeyBindings {
    public static final String CATEGORY = "key.categories.meplacementtool";

    public static final KeyMapping OPEN_RADIAL_MENU = new KeyMapping(
            "key.meplacementtool.open_radial_menu",
            KeyConflictContext.IN_GAME,
            InputConstants.Type.KEYSYM,
            GLFW.GLFW_KEY_G,
            CATEGORY
    );

    public static final KeyMapping OPEN_CABLE_TOOL_GUI = new KeyMapping(
            "key.meplacementtool.open_cable_tool_gui",
            KeyConflictContext.IN_GAME,
            InputConstants.Type.KEYSYM,
            GLFW.GLFW_KEY_G,
            CATEGORY
    );

    public static final KeyMapping UNDO_MODIFIER = new KeyMapping(
            "key.meplacementtool.undo_modifier",
            KeyConflictContext.IN_GAME,
            InputConstants.Type.KEYSYM,
            GLFW.GLFW_KEY_LEFT_CONTROL,
            CATEGORY
    );

    public static final KeyMapping MARK_COLOR_SHORTCUT = new KeyMapping(
            "key.meplacementtool.mark_color",
            KeyConflictContext.GUI,
            InputConstants.Type.KEYSYM,
            GLFW.GLFW_KEY_A,
            CATEGORY
    );
}
