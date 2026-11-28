package com.vitalik.aicompanion;

import net.minecraftforge.common.ForgeConfigSpec;

public final class ChattyAIConfig {
    private static final ForgeConfigSpec.Builder BUILDER = new ForgeConfigSpec.Builder();

    public static final ForgeConfigSpec.BooleanValue ENABLED;
    public static final ForgeConfigSpec.ConfigValue<String> API_URL;
    public static final ForgeConfigSpec.ConfigValue<String> API_KEY;
    public static final ForgeConfigSpec.ConfigValue<String> MODEL;
    public static final ForgeConfigSpec.IntValue AUTONOMY_INTERVAL_SECONDS;

    public static final ForgeConfigSpec SPEC;

    static {
        BUILDER.push("chatty_ai");

        ENABLED = BUILDER
                .comment("Enable Chatty's optional remote AI brain. A valid API key is also required.")
                .define("enabled", true);

        API_URL = BUILDER
                .comment("OpenAI-compatible Chat Completions URL.")
                .define("apiUrl", "https://api.openai.com/v1/chat/completions");

        API_KEY = BUILDER
                .comment("Your private API key. Never share it or commit this config file.")
                .define("apiKey", "");

        MODEL = BUILDER
                .comment("Model name supported by your API provider.")
                .define("model", "gpt-5-mini");

        AUTONOMY_INTERVAL_SECONDS = BUILDER
                .comment("How often Chatty may ask the AI to independently assess the situation.")
                .defineInRange("autonomyIntervalSeconds", 300, 120, 1800);

        BUILDER.pop();
        SPEC = BUILDER.build();
    }

    private ChattyAIConfig() {}
}
