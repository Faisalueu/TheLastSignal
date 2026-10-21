package com.lastsignal.command;

import com.lastsignal.entity.HorrorEntity;
import com.lastsignal.entity.ModEntities;
import com.lastsignal.event.EventManager;
import com.lastsignal.event.HorrorSpawner;
import com.lastsignal.story.Endings;
import com.lastsignal.story.PlayerStory;
import com.lastsignal.story.StoryManager;
import com.lastsignal.world.Dimensions;
import com.lastsignal.world.StructureData;
import com.lastsignal.world.Structures;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;

import java.util.ArrayList;
import java.util.List;

/**
 * /lastsignal ... Player-safe commands need no permission (help, story, recover, ending, replay, status of
 * what the player already knows). Everything that spawns, forces events, travels or resets needs op level 2.
 */
public final class ModCommands {
    private static final String ROOT = "lastsignal";

    public static void register(CommandDispatcher<CommandSourceStack> d) {
        LiteralArgumentBuilder<CommandSourceStack> root = Commands.literal(ROOT);

        root.then(Commands.literal("help").executes(c -> help(c.getSource())));
        root.executes(c -> help(c.getSource()));

        root.then(Commands.literal("status").requires(s -> s.hasPermission(2)).executes(c -> status(c.getSource())));
        root.then(Commands.literal("events").requires(s -> s.hasPermission(2)).executes(c -> {
            c.getSource().sendSuccess(() -> Component.literal("Events (" + EventManager.EVENTS.size() + "): " + String.join(", ", EventManager.EVENTS.keySet())), false);
            return EventManager.EVENTS.size();
        }));
        root.then(Commands.literal("event").requires(s -> s.hasPermission(2))
                .then(Commands.argument("event_id", StringArgumentType.word())
                        .suggests((c, b) -> SharedSuggestionProvider.suggest(EventManager.EVENTS.keySet(), b))
                        .executes(c -> runEvent(c.getSource(), StringArgumentType.getString(c, "event_id")))));
        root.then(Commands.literal("spawn").requires(s -> s.hasPermission(2))
                .then(Commands.argument("entity_id", StringArgumentType.word())
                        .suggests((c, b) -> SharedSuggestionProvider.suggest(ModEntities.BY_ID.keySet(), b))
                        .executes(c -> spawn(c.getSource(), StringArgumentType.getString(c, "entity_id")))));
        root.then(Commands.literal("dimension").requires(s -> s.hasPermission(2))
                .then(Commands.argument("dimension_id", StringArgumentType.word())
                        .suggests((c, b) -> SharedSuggestionProvider.suggest(Dimensions.allIds(), b))
                        .executes(c -> dimension(c.getSource(), StringArgumentType.getString(c, "dimension_id")))));
        root.then(Commands.literal("locate").requires(s -> s.hasPermission(2))
                .then(Commands.argument("structure_id", StringArgumentType.word())
                        .suggests((c, b) -> SharedSuggestionProvider.suggest(java.util.stream.Stream.concat(Structures.IDS.stream(), com.lastsignal.world.Houses.BUILT.stream()), b))
                        .executes(c -> locate(c.getSource(), StringArgumentType.getString(c, "structure_id")))));
        root.then(Commands.literal("place").requires(s -> s.hasPermission(2))
                .then(Commands.argument("structure_id", StringArgumentType.word())
                        .suggests((c, b) -> SharedSuggestionProvider.suggest(java.util.stream.Stream.concat(Structures.IDS.stream(), com.lastsignal.world.Houses.BUILT.stream()), b))
                        .executes(c -> place(c.getSource(), StringArgumentType.getString(c, "structure_id")))));
        root.then(Commands.literal("story").executes(c -> story(c.getSource())));
        root.then(Commands.literal("debug").requires(s -> s.hasPermission(2)).executes(c -> {
            EventManager.debug = !EventManager.debug;
            c.getSource().sendSuccess(() -> Component.literal("Debug " + (EventManager.debug ? "on" : "off")), false);
            return 1;
        }));
        root.then(Commands.literal("resetstory").requires(s -> s.hasPermission(2)).executes(c -> {
            ServerPlayer p = c.getSource().getPlayerOrException();
            StoryManager.resetPlayer(p);
            c.getSource().sendSuccess(() -> Component.literal("Your Last Signal story data was reset. The world itself was not touched."), false);
            return 1;
        }));
        root.then(Commands.literal("reload").requires(s -> s.hasPermission(2)).executes(c -> {
            EventManager.shutdown();
            c.getSource().sendSuccess(() -> Component.literal("Event timers cleared. Config values are read live from config/lastsignal-common.toml (Forge reloads it when the file changes)."), true);
            return 1;
        }));
        root.then(Commands.literal("recover").executes(c -> {
            ServerPlayer p = c.getSource().getPlayerOrException();
            if (!com.lastsignal.config.LastSignalConfig.COMMON.recoveryEnabled.get()) {
                c.getSource().sendFailure(Component.literal("Recovery is disabled in the server config."));
                return 0;
            }
            Dimensions.recover(p);
            return 1;
        }));
        root.then(Commands.literal("ending")
                .then(Commands.argument("ending_id", StringArgumentType.word())
                        .suggests((c, b) -> SharedSuggestionProvider.suggest(Endings.IDS, b))
                        .executes(c -> ending(c.getSource(), StringArgumentType.getString(c, "ending_id")))));
        root.then(Commands.literal("replay").executes(c -> {
            ServerPlayer p = c.getSource().getPlayerOrException();
            if (!StoryManager.get(p).evidence.contains("ended")) {
                c.getSource().sendFailure(Component.literal("Finish an ending first. (Admins can use /lastsignal resetstory.)"));
                return 0;
            }
            Endings.replay(p);
            c.getSource().sendSuccess(() -> Component.literal("The signal begins again. Your world is unchanged."), false);
            return 1;
        }));
        d.register(root);
    }

    private static int help(CommandSourceStack s) {
        s.sendSuccess(() -> Component.literal("""
                §6/lastsignal§7 commands:
                help, story, recover, ending <id>, replay  — everyone
                status, events, event <id>, spawn <entity>, dimension <id>, locate <structure>, place <structure>, debug, resetstory, reload — op (level 2)"""), false);
        return 1;
    }

    private static int status(CommandSourceStack s) throws com.mojang.brigadier.exceptions.CommandSyntaxException {
        ServerPlayer p = s.getPlayerOrException();
        PlayerStory st = StoryManager.get(p);
        int near = HorrorSpawner.countNear(p.serverLevel(), p);
        s.sendSuccess(() -> Component.literal(String.format(
                "phase=%s stage=%d exposure=%.1f recordings=%d notes=%d evidence=%s nights=%d encounters=%d hostilesNear=%d shared=%s",
                com.lastsignal.event.Director.phaseOf(st), st.stage(), st.exposure, st.recordings.size(), st.notes.size(), st.evidence, st.nights, st.encounters, near,
                com.lastsignal.config.LastSignalConfig.COMMON.sharedStory.get())), false);
        return 1;
    }

    private static int runEvent(CommandSourceStack s, String id) throws com.mojang.brigadier.exceptions.CommandSyntaxException {
        if (!EventManager.EVENTS.containsKey(id)) { s.sendFailure(Component.literal("Unknown event '" + id + "'. Use /lastsignal events.")); return 0; }
        boolean ok = EventManager.trigger(s.getPlayerOrException(), id, true);
        if (ok) s.sendSuccess(() -> Component.literal("Event " + id + " fired."), true);
        else s.sendFailure(Component.literal("Event " + id + " could not run here (no suitable location, missing item or story requirement)."));
        return ok ? 1 : 0;
    }

    private static int spawn(CommandSourceStack s, String id) throws com.mojang.brigadier.exceptions.CommandSyntaxException {
        if (!ModEntities.BY_ID.containsKey(id)) { s.sendFailure(Component.literal("Unknown entity '" + id + "'.")); return 0; }
        ServerPlayer p = s.getPlayerOrException();
        var look = p.getLookAngle();
        BlockPos at = BlockPos.containing(p.getX() + look.x * 6, p.getY(), p.getZ() + look.z * 6);
        HorrorEntity e = HorrorSpawner.spawnAt(p.serverLevel(), id, at, null);
        if (e == null) { s.sendFailure(Component.literal("No room to spawn there.")); return 0; }
        s.sendSuccess(() -> Component.literal("Spawned " + id), true);
        return 1;
    }

    private static int dimension(CommandSourceStack s, String id) throws com.mojang.brigadier.exceptions.CommandSyntaxException {
        if (!Dimensions.allIds().contains(id)) {
            var t = id.matches("\\d+") ? com.lastsignal.world.gen.Themes.byIndex(Integer.parseInt(id)) : null;
            if (t == null) { s.sendFailure(Component.literal("Unknown dimension '" + id + "'. Options: " + Dimensions.allIds())); return 0; }
            id = t.slug;
        }
        boolean ok = Dimensions.enter(s.getPlayerOrException(), id, true);
        if (!ok) s.sendFailure(Component.literal("That dimension is not loaded (is the datapack present?)."));
        return ok ? 1 : 0;
    }

    private static int locate(CommandSourceStack s, String id) throws com.mojang.brigadier.exceptions.CommandSyntaxException {
        ServerPlayer p = s.getPlayerOrException();
        StructureData.Entry e = Structures.data(p.serverLevel()).nearest(id, p.blockPosition(), 20000);
        if (e == null) { s.sendFailure(Component.literal("None generated yet in this dimension. Use /lastsignal place " + id + " to create one.")); return 0; }
        BlockPos b = e.blockPos();
        s.sendSuccess(() -> Component.literal(id + " at [" + b.getX() + ", " + b.getY() + ", " + b.getZ() + "] (" + (int) Math.sqrt(b.distSqr(p.blockPosition())) + " blocks)"), false);
        return 1;
    }

    private static int place(CommandSourceStack s, String id) throws com.mojang.brigadier.exceptions.CommandSyntaxException {
        ServerPlayer p = s.getPlayerOrException();
        var look = p.getLookAngle();
        BlockPos at = BlockPos.containing(p.getX() + look.x * 12, p.getY(), p.getZ() + look.z * 12);
        boolean ok = com.lastsignal.world.Houses.isHouse(id) ? com.lastsignal.world.Houses.place(p.serverLevel(), id, at) : Structures.place(p.serverLevel(), id, at);
        if (ok) s.sendSuccess(() -> Component.literal("Placed " + id + " at " + at.toShortString()), true);
        else s.sendFailure(Component.literal("Could not place '" + id + "' (unknown id, or player-made blocks are in the way)."));
        return ok ? 1 : 0;
    }

    private static int story(CommandSourceStack s) throws com.mojang.brigadier.exceptions.CommandSyntaxException {
        ServerPlayer p = s.getPlayerOrException();
        PlayerStory st = StoryManager.get(p);
        List<String> lines = new ArrayList<>();
        lines.add("§6Your story§7: " + st.recordings.size() + " recordings, " + st.notes.size() + " notes, bosses: " + (st.bossesDefeated.isEmpty() ? "none" : st.bossesDefeated)
                + ", endings seen: " + (st.endings.isEmpty() ? "none" : st.endings));
        if (st.evidence.contains("core_defeated")) {
            for (String id : Endings.IDS) {
                String m = Endings.missing(st, id);
                lines.add((m == null ? "§a" : "§8") + id + (m == null ? " — available: /lastsignal ending " + id : " — " + m));
            }
        }
        for (String l : lines) p.sendSystemMessage(Component.literal(l));
        return 1;
    }

    private static int ending(CommandSourceStack s, String id) throws com.mojang.brigadier.exceptions.CommandSyntaxException {
        ServerPlayer p = s.getPlayerOrException();
        if (!Endings.IDS.contains(id)) { s.sendFailure(Component.literal("Unknown ending. Options: " + Endings.IDS)); return 0; }
        String m = Endings.missing(StoryManager.get(p), id);
        if (m != null) { s.sendFailure(Component.literal(m)); return 0; }
        return Endings.apply(p, id) ? 1 : 0;
    }

    private ModCommands() {}
}
