package org.ItsInspector.sunnyCoinflip;

import org.ItsInspector.sunnyCoinflip.commands.CoinflipCommand;
import org.ItsInspector.sunnyCoinflip.commands.PillarSetupCommands;
import org.ItsInspector.sunnyCoinflip.integrations.PillarExpansion;
import org.ItsInspector.sunnyCoinflip.listeners.ChatListener;
import org.ItsInspector.sunnyCoinflip.listeners.CommandBlockListener;
import org.ItsInspector.sunnyCoinflip.listeners.InventoryListener;
import org.ItsInspector.sunnyCoinflip.managers.GameManager;
import net.milkbowl.vault.economy.Economy;
import org.bukkit.plugin.RegisteredServiceProvider;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.logging.Logger;

public final class SunnyCoinflip extends JavaPlugin {

    private static Economy econ = null;
    private static SunnyCoinflip instance;
    private GameManager gameManager;
    private ChatListener chatListener;
    private InventoryListener inventoryListener;
    private org.ItsInspector.sunnyCoinflip.listeners.PillarListener pillarListener;

    @Override
    public void onEnable() {
        instance = this;
        if (!setupEconomy()) {
            Logger.getLogger("Minecraft").severe(String.format("[%s] - Disabled due to no Vault dependency found!", getDescription().getName()));
            getServer().getPluginManager().disablePlugin(this);
            return;
        }
        
        saveDefaultConfig();
        this.gameManager = new GameManager();
        this.chatListener = new ChatListener(this);
        
        getCommand("coinflip").setExecutor(new CoinflipCommand());
        PillarSetupCommands pillarSetup = new PillarSetupCommands();
        getCommand("setpillarsfirst").setExecutor(pillarSetup);
        getCommand("setpillarsopponent").setExecutor(pillarSetup);
        getCommand("pillars").setExecutor(new org.ItsInspector.sunnyCoinflip.commands.PillarsCommand());
        
        this.inventoryListener = new InventoryListener(this);
        getServer().getPluginManager().registerEvents(inventoryListener, this);
        getServer().getPluginManager().registerEvents(chatListener, this);
        this.pillarListener = new org.ItsInspector.sunnyCoinflip.listeners.PillarListener(this);
        getServer().getPluginManager().registerEvents(pillarListener, this);
        getServer().getPluginManager().registerEvents(new CommandBlockListener(), this);

        if (getServer().getPluginManager().isPluginEnabled("PlaceholderAPI")) {
            new PillarExpansion(this).register();
        }
    }

    public ChatListener getChatListener() {
        return chatListener;
    }

    public GameManager getGameManager() {
        return gameManager;
    }

    public InventoryListener getInventoryListener() {
        return inventoryListener;
    }

    public org.ItsInspector.sunnyCoinflip.listeners.PillarListener getPillarListener() {
        return pillarListener;
    }

    @Override
    public void onDisable() {
        if (pillarListener != null) {
            pillarListener.handleServerShutdown();
        }
    }

    private boolean setupEconomy() {
        if (getServer().getPluginManager().getPlugin("Vault") == null) {
            return false;
        }
        RegisteredServiceProvider<Economy> rsp = getServer().getServicesManager().getRegistration(Economy.class);
        if (rsp == null) {
            return false;
        }
        econ = rsp.getProvider();
        return econ != null;
    }

    public static Economy getEconomy() {
        return econ;
    }

    public static SunnyCoinflip getInstance() {
        return instance;
    }
}
