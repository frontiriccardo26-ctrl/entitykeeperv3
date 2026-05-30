package com.entitykeeper.command;

import com.entitykeeper.data.EntityKeeperRule;
import com.entitykeeper.data.EntityKeeperSavedData;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.BoolArgumentType;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;

import java.util.Collection;

public class EntityKeeperCommand {

    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {

        dispatcher.register(
            Commands.literal("entitykeeper")
                .requires(src -> src.hasPermission(2)) // operator only

                // /entitykeeper set <type> <forceload> <despawn_seconds>
                .then(Commands.literal("set")
                    .then(Commands.argument("entityType", StringArgumentType.string())
                        .then(Commands.argument("forceLoad", BoolArgumentType.bool())
                            .then(Commands.argument("despawnSeconds", IntegerArgumentType.integer(-1))
                                .executes(ctx -> {
                                    String  type    = StringArgumentType.getString(ctx, "entityType");
                                    boolean fl      = BoolArgumentType.getBool(ctx, "forceLoad");
                                    int     seconds = IntegerArgumentType.getInteger(ctx, "despawnSeconds");
                                    return executeSet(ctx.getSource(), type, fl, seconds);
                                })
                            )
                        )
                    )
                )

                // /entitykeeper remove <type>
                .then(Commands.literal("remove")
                    .then(Commands.argument("entityType", StringArgumentType.string())
                        .executes(ctx -> {
                            String type = StringArgumentType.getString(ctx, "entityType");
                            return executeRemove(ctx.getSource(), type);
                        })
                    )
                )

                // /entitykeeper list
                .then(Commands.literal("list")
                    .executes(ctx -> executeList(ctx.getSource()))
                )
        );
    }

    // ── /entitykeeper set ─────────────────────────────────────────────────────

    private static int executeSet(CommandSourceStack src,
                                  String entityType,
                                  boolean forceLoad,
                                  int despawnSeconds) {

        // Validate resource location format
        if (!isValidResourceLocation(entityType)) {
            src.sendFailure(Component.literal(
                "[EntityKeeper] Invalid entity type format. Use 'modid:entity_name' (e.g. minecraft:arrow)."
            ));
            return 0;
        }

        ServerLevel level = src.getLevel();
        EntityKeeperSavedData data = EntityKeeperSavedData.getOrCreate(level);

        int despawnTicks = despawnSeconds < 0 ? -1 : EntityKeeperRule.secondsToTicks(despawnSeconds);
        data.setRule(entityType, forceLoad, despawnTicks);

        String despawnMsg = despawnSeconds < 0
                ? "no forced despawn"
                : "despawn after " + despawnSeconds + "s";

        src.sendSuccess(() -> Component.literal(
            "[EntityKeeper] Rule set for §e" + entityType + "§r: " +
            "forceLoad=" + forceLoadColor(forceLoad) + forceLoad + "§r, " + despawnMsg + "."
        ), true);

        return 1;
    }

    // ── /entitykeeper remove ──────────────────────────────────────────────────

    private static int executeRemove(CommandSourceStack src, String entityType) {
        ServerLevel level = src.getLevel();
        EntityKeeperSavedData data = EntityKeeperSavedData.getOrCreate(level);

        if (data.removeRule(entityType)) {
            src.sendSuccess(() -> Component.literal(
                "[EntityKeeper] Rule removed for §e" + entityType + "§r."
            ), true);
            return 1;
        } else {
            src.sendFailure(Component.literal(
                "[EntityKeeper] No rule found for '" + entityType + "'."
            ));
            return 0;
        }
    }

    // ── /entitykeeper list ────────────────────────────────────────────────────

    private static int executeList(CommandSourceStack src) {
        ServerLevel level = src.getLevel();
        EntityKeeperSavedData data = EntityKeeperSavedData.getOrCreate(level);
        Collection<EntityKeeperRule> rules = data.getAllRules();

        if (rules.isEmpty()) {
            src.sendSuccess(() -> Component.literal("[EntityKeeper] No rules configured."), false);
            return 0;
        }

        StringBuilder sb = new StringBuilder("[EntityKeeper] Active rules:\n");
        for (EntityKeeperRule rule : rules) {
            String despawn = rule.getDespawnTicks() < 0
                    ? "never"
                    : EntityKeeperRule.ticksToSeconds(rule.getDespawnTicks()) + "s";
            sb.append("  §e").append(rule.getEntityType()).append("§r")
              .append(" | forceLoad=").append(rule.isForceLoad())
              .append(" | despawn=").append(despawn)
              .append("\n");
        }

        String msg = sb.toString().trim();
        src.sendSuccess(() -> Component.literal(msg), false);
        return rules.size();
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private static boolean isValidResourceLocation(String s) {
        try {
            new ResourceLocation(s);
            return s.contains(":");
        } catch (Exception e) {
            return false;
        }
    }

    private static String forceLoadColor(boolean v) {
        return v ? "§a" : "§c";
    }
}
