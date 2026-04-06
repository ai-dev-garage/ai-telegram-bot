package com.ai.dev.garage.bot.adapter.out.cursor;

import com.ai.dev.garage.bot.config.CursorCliProperties;

import org.apache.commons.lang3.StringUtils;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;


/**
 * Builds common Cursor CLI command prefixes shared by all Cursor adapters.
 */
final class CursorCommandBuilder {

    private CursorCommandBuilder() {
    }

    /**
     * Returns a mutable list containing the executable followed by any
     * configured prefix args (nulls and blanks filtered out, values trimmed).
     */
    static List<String> executableWithPrefix(CursorCliProperties props) {
        List<String> cmd = new ArrayList<>();
        cmd.add(props.getExecutable());
        Optional.ofNullable(props.getPlanPrefixArgs())
            .ifPresent(args -> args.stream()
                .filter(StringUtils::isNotBlank)
                .map(String::trim)
                .forEach(cmd::add));
        return cmd;
    }

    /**
     * Returns a mutable list starting with the executable and prefix args,
     * followed by {@code --print}, the given mode flag, and {@code --trust}.
     *
     * @param props Cursor CLI properties supplying executable and prefix args
     * @param modeFlag the mode-specific flag, e.g. {@code "--force"} or {@code "--plan"}
     */
    static List<String> baseCommand(CursorCliProperties props, String modeFlag) {
        List<String> cmd = executableWithPrefix(props);
        cmd.add("--print");
        cmd.add(modeFlag);
        cmd.add("--trust");
        return cmd;
    }
}
