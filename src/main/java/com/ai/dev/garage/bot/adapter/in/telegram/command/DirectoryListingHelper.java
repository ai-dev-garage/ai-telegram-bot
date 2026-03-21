package com.ai.dev.garage.bot.adapter.in.telegram.command;

import com.ai.dev.garage.bot.application.support.AllowedPathValidator;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.stream.Stream;

/**
 * Shared helper for building Telegram inline-keyboard directory listings used by /ls, /cd, /nav callbacks.
 */
public final class DirectoryListingHelper {

    public static final String CD_CALLBACK_PREFIX = "cd:";
    public static final String CD_UP = "cd:..";

    private static final Set<String> SKIP_DIR_NAMES = Set.of(
        ".git", "node_modules", "target", "build", "dist", ".gradle", ".idea");

    private DirectoryListingHelper() {
    }

    public static List<String> listChildDirectories(Path dir) {
        if (!Files.isDirectory(dir)) {
            return List.of();
        }
        try (Stream<Path> children = Files.list(dir)) {
            return children
                .filter(Files::isDirectory)
                .map(p -> p.getFileName().toString())
                .filter(name -> !shouldSkip(name))
                .sorted(String.CASE_INSENSITIVE_ORDER)
                .toList();
        } catch (IOException e) {
            return List.of();
        }
    }

    /**
     * Builds an inline keyboard with one button per child directory, plus an optional "Up" button.
     *
     * @param canonicalPath current working directory (absolute canonical)
     * @param validator     allowlist validator -- used to decide whether "Up" is available
     * @return keyboard rows ready for {@code TelegramBotClient.sendWithInlineKeyboard}
     */
    public static List<List<Map<String, String>>> buildDirectoryKeyboard(String canonicalPath, AllowedPathValidator validator) {
        List<String> children = listChildDirectories(Path.of(canonicalPath));
        List<List<Map<String, String>>> keyboard = new ArrayList<>();
        for (String name : children) {
            String callbackData = CD_CALLBACK_PREFIX + name;
            if (callbackData.length() > 64) {
                continue;
            }
            keyboard.add(List.of(button(name, callbackData)));
        }
        Path parent = Path.of(canonicalPath).getParent();
        if (parent != null && validator.isAllowedCwd(parent.toString())) {
            keyboard.add(List.of(button(".. (up)", CD_UP)));
        }
        return keyboard;
    }

    public static String formatCwdMessage(String canonicalPath) {
        String folderName = Path.of(canonicalPath).getFileName().toString();
        return "Current: " + folderName + "\n\nSubfolders:";
    }

    public static String folderName(String canonicalPath) {
        Path name = Path.of(canonicalPath).getFileName();
        return name != null ? name.toString() : canonicalPath;
    }

    private static boolean shouldSkip(String name) {
        if (name.isEmpty() || name.startsWith(".")) {
            return true;
        }
        return SKIP_DIR_NAMES.contains(name.toLowerCase(Locale.ROOT));
    }

    private static Map<String, String> button(String text, String callbackData) {
        Map<String, String> btn = new LinkedHashMap<>();
        btn.put("text", text);
        btn.put("callback_data", callbackData);
        return btn;
    }
}
