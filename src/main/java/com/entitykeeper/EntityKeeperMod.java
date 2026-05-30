package com.entitykeeper;

import com.entitykeeper.command.EntityKeeperCommand;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.RegisterCommandsEvent;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.event.lifecycle.FMLCommonSetupEvent;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

@Mod(EntityKeeperMod.MOD_ID)
public class EntityKeeperMod {

    public static final String MOD_ID = "entitykeeper";
    public static final Logger LOGGER = LogManager.getLogger(MOD_ID);

    public EntityKeeperMod() {
        IEventBus modEventBus = FMLJavaModLoadingContext.get().getModEventBus();
        modEventBus.addListener(this::commonSetup);

        MinecraftForge.EVENT_BUS.register(this);
    }

    private void commonSetup(final FMLCommonSetupEvent event) {
        LOGGER.info("EntityKeeper initialized.");
    }

    @SubscribeEvent
    public void onRegisterCommands(RegisterCommandsEvent event) {
        EntityKeeperCommand.register(event.getDispatcher());
        LOGGER.info("EntityKeeper commands registered.");
    }
}
