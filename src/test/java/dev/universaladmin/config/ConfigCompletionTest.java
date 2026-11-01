package dev.universaladmin.config;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import org.bukkit.configuration.InvalidConfigurationException;
import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.Test;

/** New options reach an existing config.yml - with their explanation - and nothing the owner set changes. */
class ConfigCompletionTest {

    private static final String BUNDLED = """
            config-version: 3
            gui:
              # Ask for confirmation before destructive GUI actions.
              confirmations: true
              # Show staff what they have switched on in the action bar.
              status-line: true
            # Telemetry, see docs/user/telemetry.md.
            telemetry:
              # Send anonymous usage statistics.
              enabled: true
            """;

    private static YamlConfiguration yaml(String text) throws InvalidConfigurationException {
        YamlConfiguration config = new YamlConfiguration();
        config.loadFromString(text);
        return config;
    }

    @Test
    void addsWhatIsMissingWithItsCommentAndKeepsTheOwnersValues() throws InvalidConfigurationException {
        YamlConfiguration server = yaml("""
                config-version: 3
                gui:
                  # My own note.
                  confirmations: false
                """);

        List<String> added = ConfigCompletion.complete(server, yaml(BUNDLED));

        assertEquals(List.of("gui.status-line", "telemetry.enabled"), added);
        assertFalse(server.getBoolean("gui.confirmations"));
        assertTrue(server.getBoolean("gui.status-line"));
        String saved = server.saveToString();
        assertTrue(saved.contains("# My own note."), saved);
        assertTrue(saved.contains("# Show staff what they have switched on in the action bar."), saved);
        assertTrue(saved.contains("# Telemetry, see docs/user/telemetry.md."), saved);
    }

    @Test
    void aCompleteFileStaysAsItIs() throws InvalidConfigurationException {
        YamlConfiguration server = yaml(BUNDLED);

        assertEquals(List.of(), ConfigCompletion.complete(server, yaml(BUNDLED)));
    }
}
