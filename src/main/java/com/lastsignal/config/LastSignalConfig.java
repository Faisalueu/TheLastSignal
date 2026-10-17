package com.lastsignal.config;

import net.minecraftforge.common.ForgeConfigSpec;

/** All tunables. COMMON is synced from the server's config file; CLIENT is per-player presentation. */
public final class LastSignalConfig {
    public static final ForgeConfigSpec COMMON_SPEC;
    public static final ForgeConfigSpec CLIENT_SPEC;
    public static final Common COMMON;
    public static final Client CLIENT;

    static {
        ForgeConfigSpec.Builder b = new ForgeConfigSpec.Builder();
        COMMON = new Common(b);
        COMMON_SPEC = b.build();
        ForgeConfigSpec.Builder c = new ForgeConfigSpec.Builder();
        CLIENT = new Client(c);
        CLIENT_SPEC = c.build();
    }

    public static final class Common {
        public final ForgeConfigSpec.DoubleValue horrorIntensity;
        public final ForgeConfigSpec.DoubleValue eventFrequency;
        public final ForgeConfigSpec.DoubleValue entityAggression;
        public final ForgeConfigSpec.IntValue maxSimultaneousHostiles;
        public final ForgeConfigSpec.IntValue maxThreatStage;
        public final ForgeConfigSpec.BooleanValue enableEvents;
        public final ForgeConfigSpec.BooleanValue enableStructureEvents;
        public final ForgeConfigSpec.BooleanValue enableDimensions;
        public final ForgeConfigSpec.BooleanValue enableBosses;
        public final ForgeConfigSpec.BooleanValue sharedStory;
        public final ForgeConfigSpec.BooleanValue puzzleHints;
        public final ForgeConfigSpec.BooleanValue recoveryEnabled;
        public final ForgeConfigSpec.DoubleValue multiplayerHealthScaling;
        public final ForgeConfigSpec.DoubleValue deathThreatRelief;
        public final ForgeConfigSpec.IntValue minSecondsBetweenEvents;
        public final ForgeConfigSpec.BooleanValue allowJumpscares;

        Common(ForgeConfigSpec.Builder b) {
            b.push("general");
            horrorIntensity = b.comment("Global multiplier for how much the world reacts. 0 = nothing happens.")
                    .defineInRange("horrorIntensity", 1.0, 0.0, 3.0);
            eventFrequency = b.comment("Multiplier on how often events are rolled.")
                    .defineInRange("eventFrequency", 1.0, 0.0, 5.0);
            entityAggression = b.comment("Multiplier on monster damage and chase persistence.")
                    .defineInRange("entityAggression", 1.0, 0.1, 3.0);
            maxSimultaneousHostiles = b.comment("Max mod monsters allowed within 64 blocks of one player.")
                    .defineInRange("maxSimultaneousHostiles", 4, 1, 24);
            maxThreatStage = b.comment("Hard cap on the hidden threat stage (0-6).")
                    .defineInRange("maxThreatStage", 6, 0, 6);
            minSecondsBetweenEvents = b.comment("Minimum gap between any two events for one player.")
                    .defineInRange("minSecondsBetweenEvents", 75, 5, 3600);
            enableEvents = b.define("enableEvents", true);
            enableStructureEvents = b.comment("Allow events that place small procedural structures in the world.")
                    .define("enableStructureEvents", true);
            allowJumpscares = b.comment("Server-side switch for sudden-appearance events.").define("allowJumpscares", true);
            b.pop();
            b.push("progression");
            sharedStory = b.comment("true = one shared story/threat for the whole world; false = each player has their own.")
                    .define("sharedStory", false);
            puzzleHints = b.define("puzzleHints", true);
            deathThreatRelief = b.comment("Fraction of exposure removed when a player dies (prevents a death spiral).")
                    .defineInRange("deathThreatRelief", 0.25, 0.0, 1.0);
            b.pop();
            b.push("content");
            enableDimensions = b.define("enableDimensions", true);
            enableBosses = b.define("enableBosses", true);
            multiplayerHealthScaling = b.comment("Boss health bonus per additional nearby player (0.5 = +50%).")
                    .defineInRange("multiplayerHealthScaling", 0.5, 0.0, 3.0);
            recoveryEnabled = b.comment("Allow /lastsignal recover and Checkpoint Beacons to pull trapped players out.")
                    .define("recoveryEnabled", true);
            b.pop();
        }
    }

    public static final class Client {
        public final ForgeConfigSpec.BooleanValue disableFlashing;
        public final ForgeConfigSpec.BooleanValue disableScreenEffects;
        public final ForgeConfigSpec.BooleanValue disableJumpscares;
        public final ForgeConfigSpec.DoubleValue audioIntensity;

        Client(ForgeConfigSpec.Builder b) {
            b.push("accessibility");
            disableFlashing = b.define("disableFlashing", false);
            disableScreenEffects = b.comment("Turns off static/vignette overlays.").define("disableScreenEffects", false);
            disableJumpscares = b.comment("Suppresses sudden loud cues on this client.").define("disableJumpscares", false);
            audioIntensity = b.comment("Multiplier on mod horror sound volume.").defineInRange("audioIntensity", 1.0, 0.0, 1.0);
            b.pop();
        }
    }

    private LastSignalConfig() {}
}
