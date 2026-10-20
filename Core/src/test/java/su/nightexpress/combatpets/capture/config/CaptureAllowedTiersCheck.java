package su.nightexpress.combatpets.capture.config;

import su.nightexpress.nightcore.config.FileConfig;
import su.nightexpress.nightcore.configuration.codec.CodecRegistry;
import su.nightexpress.nightcore.configuration.codec.ConfigCodecs;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Set;

public final class CaptureAllowedTiersCheck {

    private CaptureAllowedTiersCheck() {

    }

    public static void main(String[] args) throws Exception {
        ConfigCodecs.init(new CodecRegistry());

        Path oldConfig = Files.createTempFile("combatpets-old-capture-tiers-", ".yml");
        try {
            Files.writeString(oldConfig, "Unrelated: keep\n");

            FileConfig config = FileConfig.load(oldConfig);
            Set<String> allowed = CaptureTierPolicy.configValue().read(config);
            config.saveChanges();

            require(allowed.equals(Set.of("common", "rare")), "only common and rare should be enabled by default");
            require(CaptureTierPolicy.isAllowed(allowed, "COMMON"), "tier matching should ignore case");
            require(!CaptureTierPolicy.isAllowed(allowed, "unique"), "unique must be disabled by default");
            require(!CaptureTierPolicy.isAllowed(allowed, "mythic"), "mythic must be disabled by default");

            FileConfig saved = FileConfig.load(oldConfig);
            require("keep".equals(saved.getString("Unrelated")), "unrelated config value was changed");
            require(saved.getStringList("Settings.Allowed_Tiers").containsAll(Set.of("common", "rare")), "allowed tiers were not added");
            require(!saved.getComments("Settings.Allowed_Tiers").isEmpty(), "allowed tier comments were not added");
        }
        finally {
            Files.deleteIfExists(oldConfig);
        }

        System.out.println("CaptureAllowedTiersCheck PASS");
    }

    private static void require(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }
}
