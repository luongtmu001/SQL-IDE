package app.ui;

import org.junit.jupiter.api.Test;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class Phase3ColorTokensTest {

    private static final List<String> REQUIRED_TOKENS = List.of(
            "-ide-graph-bg",
            "-ide-graph-edge",
            "-ide-graph-edge-active",
            "-ide-graph-stage-line",
            "-ide-node-table-fg",
            "-ide-node-table-bg",
            "-ide-node-table-border",
            "-ide-node-view-fg",
            "-ide-node-view-bg",
            "-ide-node-view-border",
            "-ide-node-proc-fg",
            "-ide-node-proc-bg",
            "-ide-node-proc-border",
            "-ide-node-trigger-fg",
            "-ide-node-trigger-bg",
            "-ide-node-trigger-border",
            "-ide-op-source",
            "-ide-op-join",
            "-ide-op-filter",
            "-ide-op-aggregate",
            "-ide-op-sort",
            "-ide-op-output",
            "-ide-diff-added-fg",
            "-ide-diff-added-bg",
            "-ide-diff-removed-fg",
            "-ide-diff-removed-bg",
            "-ide-diff-changed-fg",
            "-ide-diff-changed-bg",
            "-ide-code-inline-fg",
            "-ide-shadow"
    );

    private static Set<String> parseDefinedTokens(String cssResource) throws Exception {
        try (InputStream in = Phase3ColorTokensTest.class.getResourceAsStream(cssResource)) {
            assertNotNull(in, "Resource not found: " + cssResource);
            String content = new String(in.readAllBytes(), StandardCharsets.UTF_8).replaceAll("(?s)/\\*.*?\\*/", "");
            Set<String> tokens = new TreeSet<>();
            Matcher matcher = Pattern.compile("(?m)^\\s*(-ide-[a-z0-9-]+)\\s*:").matcher(content);
            while (matcher.find()) {
                tokens.add(matcher.group(1));
            }
            return tokens;
        }
    }

    @Test
    void testPhase3TokensPresentInBothDarkAndLightThemes() throws Exception {
        Set<String> darkTokens = parseDefinedTokens("/css/ide-vars.css");
        Set<String> lightTokens = parseDefinedTokens("/css/light-theme.css");

        for (String token : REQUIRED_TOKENS) {
            assertTrue(darkTokens.contains(token), "Missing token in ide-vars.css: " + token);
            assertTrue(lightTokens.contains(token), "Missing token in light-theme.css: " + token);
        }
    }
}

