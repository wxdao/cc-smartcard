package dev.wxdao.ccsmartcard;

import net.neoforged.neoforge.common.ModConfigSpec;

public final class ModConfig {
    public static final ModConfigSpec SPEC;
    private static final ModConfigSpec.IntValue SMART_CARD_SPACE_LIMIT;

    static {
        ModConfigSpec.Builder builder = new ModConfigSpec.Builder();
        SMART_CARD_SPACE_LIMIT = builder
                .comment("Maximum total bytes available in a smart card file space.")
                .defineInRange("smart_card_space_limit", 125_000, 1, Integer.MAX_VALUE);
        SPEC = builder.build();
    }

    private ModConfig() {
    }

    public static int smartCardSpaceLimit() {
        return SMART_CARD_SPACE_LIMIT.get();
    }
}
