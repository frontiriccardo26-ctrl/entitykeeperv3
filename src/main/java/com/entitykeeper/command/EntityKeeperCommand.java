package com.entitykeeper.command;

import com.entitykeeper.data.EntityKeeperRule;
import com.entitykeeper.data.EntityKeeperSavedData;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.BoolArgumentType;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.suggestion.SuggestionProvider;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.commands.arguments.ResourceLocationArgument;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.EntityType;

import java.util.Collection;
import java.util.stream.Collectors;

public class EntityKeeperCommand {

    private static final SuggestionProvider<CommandSourceStack> SUGGEST_ENTITY_TYPES =
        (ctx, builder) -> SharedSuggestionProvider.suggestResource(
            BuiltInRegistries.ENTITY_TYPE.stream()
                .map(BuiltInRegistries.ENTITY_TYPE::getKey)
                .filter(rl -> rl != null),
            builder
        );

    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {

        dispatcher.register(
            Commands.literal("entitykeeper")
                .requires(src -> src.hasPermission(2))

                .then(Commands.literal("set")
                    .then(Commands.argument("entityType", ResourceLocationArgument.id())
                        .suggests(SUGGEST_ENTITY_TYPES)
                        .then(Commands.argument("forceLoad", BoolArgumentType.bool())
                            .then(Commands.argument("despawnSeconds", IntegerArgumentType.integer(-1))
                                .executes(ctx -> {
                                    ResourceLocation type = ResourceLocationArgument.getId(ctx, "entityType");
                                    boolean fl      = BoolArgumentType.getBool(ctx, "forceLoad");
                                    int     seconds = IntegerArgumentType.getInteger(ctx, "despawnSeconds");
                                    return executeSet(ctx.getSource(), type, fl, seconds);
                                })
                            )
                        )
                    )
                )

                .then(Commands.literal("remove")
                    .then(Commands.argument("entityType", ResourceLocationArgument.id())
                        .suggests(SUGGEST_ENTITY_TYPES)
                        .executes(ctx -> {
                            ResourceLocation type = ResourceLocationArgument.getId(ctx, "entityType");
                            return executeRemove(ctx.getSource(), type);
                        })
                    )
                )

                .then(Commands.literal("list")
                    .executes(ctx -> executeList(ctx.getSource()))
                )

                .then(Commands.literal("generation")
                    .then(Commands.argument("entityType", ResourceLocationArgument.id())
                        .suggests(SUGGEST_ENTITY_TYPES)
                        .then(Commands.argument("allow", BoolArgumentType.bool())
                            .executes(ctx -> {
                                ResourceLocation type = ResourceLocationArgument.getId(ctx, "entityType");
                                boolean allow = BoolArgumentType.getBool(ctx, "allow");
                                return executeGeneration(ctx.getSource(), type, allow);
                            })
                        )
                    )
                )
        );
    }

    private static int executeSet(CommandSourceStack src, ResourceLocation entityType, boolean forceLoad, int despawnSeconds) {
        if (!isKnownEntityType(entityType)) {
            src.sendFailure(Component.literal("[EntityKeeper] Unknown entity type: " + entityType));
            return 0;
        }
        ServerLevel level = src.getLevel();
        EntityKeeperSavedData data = EntityKeeperSavedData.getOrCreate(level);
        int despawnTicks = despawnSeconds < 0 ? -1 : EntityKeeperRule.secondsToTicks(despawnSeconds);
        data.setRule(entityType.toString(), forceLoad, despawnTicks);
        String despawnMsg = despawnSeconds < 0 ? "no forced despawn" : "despawn after " + despawnSeconds + "s";
        src.sendSuccess(() -> Component.literal(
            "[EntityKeeper] Rule set for §e" + entityType + "§r: forceLoad=" + forceLoadColor(forceLoad) + forceLoad + "§r, " + despawnMsg + "."
        ), true);
        return 1;
    }

    private static int executeRemove(CommandSourceStack src, ResourceLocation entityType) {
        EntityKeeperSavedData data = EntityKeeperSavedData.getOrCreate(src.getLevel());
        if (data.removeRule(entityType.toString())) {
            src.sendSuccess(() -> Component.literal("[EntityKeeper] Rule removed for §e" + entityType + "§r."), true);
            return 1;
        } else {
            src.sendFailure(Component.literal("[EntityKeeper] No rule found for '" + entityType + "'."));
            return 0;
        }
    }

    private static int executeList(CommandSourceStack src) {
        EntityKeeperSavedData data = EntityKeeperSavedData.getOrCreate(src.getLevel());
        Collection<EntityKeeperRule> rules = data.getAllRules();
        Collection<String> blocked = data.getAllBlocked();

        if (rules.isEmpty() && blocked.isEmpty()) {
            src.sendSuccess(() -> Component.literal("[EntityKeeper] No rules configured."), false);
            return 0;
        }

        StringBuilder sb = new StringBuilder();
        if (!rules.isEmpty()) {
            sb.append("§6[EntityKeeper] Active rules:\n");
            for (EntityKeeperRule rule : rules) {
                String despawn = rule.getDespawnTicks() < 0 ? "never" : EntityKeeperRule.ticksToSeconds(rule.getDespawnTicks()) + "s";
                sb.append("  §e").append(rule.getEntityType()).append("§r")
                  .append(" | forceLoad=").append(forceLoadColor(rule.isForceLoad())).append(rule.isForceLoad()).append("§r")
                  .append(" | despawn=").append(despawn).append("\n");
            }
        }
        if (!blocked.isEmpty()) {
            sb.append("§6[EntityKeeper] Blocked entity types:\n");
            for (String type : blocked) sb.append("  §c").append(type).append("§r\n");
        }

        String msg = sb.toString().trim();
        src.sendSuccess(() -> Component.literal(msg), false);
        return rules.size() + blocked.size();
    }

    private static int executeGeneration(CommandSourceStack src, ResourceLocation entityType, boolean allow) {
        if (!isKnownEntityType(entityType)) {
            src.sendFailure(Component.literal("[EntityKeeper] Unknown entity type: " + entityType));
            return 0;
        }
        EntityKeeperSavedData data = EntityKeeperSavedData.getOrCreate(src.getLevel());
        data.setBlocked(entityType.toString(), !allow);
        if (!allow) {
            src.sendSuccess(() -> Component.literal("[EntityKeeper] Generation of §c" + entityType + "§r is now §cBLOCKED§r."), true);
        } else {
            src.sendSuccess(() -> Component.literal("[EntityKeeper] Generation of §e" + entityType + "§r is now §aALLOWED§r."), true);
        }
        return 1;
    }

    private static boolean isKnownEntityType(ResourceLocation rl) {
        return BuiltInRegistries.ENTITY_TYPE.containsKey(rl);
    }

    private static String forceLoadColor(boolean v) {
        return v ? "§a" : "§c";
    }
}
