package io.izzel.arclight.common.mod.compat;

import com.mojang.brigadier.tree.CommandNode;
import io.izzel.arclight.api.Unsafe;
import io.izzel.arclight.common.bridge.core.command.CommandSourceStackBridge;

import java.lang.reflect.Field;
import java.util.Map;

public class CommandNodeHooks {

    private static final long CHILDREN, LITERALS, ARGUMENTS, CURRENT;
    private static final Object CURRENT_BASE;
    private static boolean CURRENT_COMMAND_MISSING = false;

    static {
        long children = 0;
        long literals = 0;
        long arguments = 0;
        long current = 0;
        Object currentBase = null;

        try {
            children = Unsafe.objectFieldOffset(CommandNode.class.getDeclaredField("children"));
            literals = Unsafe.objectFieldOffset(CommandNode.class.getDeclaredField("literals"));
            arguments = Unsafe.objectFieldOffset(CommandNode.class.getDeclaredField("arguments"));
        } catch (Throwable t) {
            throw new RuntimeException("Failed to access CommandNode fields", t);
        }

        try {
            Field currentCommandField = findCurrentCommandField(CommandNode.class);
            if (currentCommandField != null) {
                currentBase = Unsafe.staticFieldBase(currentCommandField);
                current = Unsafe.staticFieldOffset(currentCommandField);
            } else {
                CURRENT_COMMAND_MISSING = true;
            }
        } catch (Throwable t) {
            CURRENT_COMMAND_MISSING = true;
            System.err.println("[Arclight] WARNING: Failed to find CURRENT_COMMAND field. Some features may be limited.");
        }

        CHILDREN = children;
        LITERALS = literals;
        ARGUMENTS = arguments;
        CURRENT = current;
        CURRENT_BASE = currentBase;
    }

    private static Field findCurrentCommandField(Class<?> clazz) {
        String[] possibleNames = {"CURRENT_COMMAND", "currentCommand", "current", "COMMAND_CONTEXT"};

        for (String name : possibleNames) {
            try {
                Field field = clazz.getDeclaredField(name);
                field.setAccessible(true);
                return field;
            } catch (NoSuchFieldException ignored) {
            }
        }
        return null;
    }

    @SuppressWarnings("unchecked")
    public static void removeCommand(CommandNode<?> node, String command) {
        ((Map<String, ?>) Unsafe.getObject(node, CHILDREN)).remove(command);
        ((Map<String, ?>) Unsafe.getObject(node, LITERALS)).remove(command);
        ((Map<String, ?>) Unsafe.getObject(node, ARGUMENTS)).remove(command);
    }

    public static CommandNode<?> getCurrent() {
        if (CURRENT_COMMAND_MISSING) {
            return null;
        }
        return (CommandNode<?>) Unsafe.getObjectVolatile(CURRENT_BASE, CURRENT);
    }

    public static <S> boolean canUse(CommandNode<S> node, S source) {
        if (source instanceof CommandSourceStackBridge s) {
            try {
                if (!CURRENT_COMMAND_MISSING) {
                    s.bridge$setCurrentCommand(node);
                }
                return node.canUse(source);
            } finally {
                if (!CURRENT_COMMAND_MISSING) {
                    s.bridge$setCurrentCommand(null);
                }
            }
        } else {
            return node.canUse(source);
        }
    }
}