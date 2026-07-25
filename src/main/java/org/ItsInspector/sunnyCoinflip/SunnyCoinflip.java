package org.ItsInspector.sunnyCoinflip;

import java.util.logging.Logger;
import me.libs.serverlibs.ServiceSupport;
import net.milkbowl.vault.economy.Economy;
import org.ItsInspector.sunnyCoinflip.commands.CoinflipCommand;
import org.ItsInspector.sunnyCoinflip.commands.PillarSetupCommands;
import org.ItsInspector.sunnyCoinflip.commands.PillarsCommand;
import org.ItsInspector.sunnyCoinflip.integrations.BedwarsExpansion;
import org.ItsInspector.sunnyCoinflip.integrations.PillarExpansion;
import org.ItsInspector.sunnyCoinflip.listeners.BedfightListener;
import org.ItsInspector.sunnyCoinflip.listeners.ChatListener;
import org.ItsInspector.sunnyCoinflip.listeners.CommandBlockListener;
import org.ItsInspector.sunnyCoinflip.listeners.InventoryListener;
import org.ItsInspector.sunnyCoinflip.listeners.PillarItemSafetyListener;
import org.ItsInspector.sunnyCoinflip.listeners.PillarListener;
import org.ItsInspector.sunnyCoinflip.managers.BedfightManager;
import org.ItsInspector.sunnyCoinflip.managers.GameManager;
import org.bukkit.plugin.RegisteredServiceProvider;
import org.bukkit.plugin.java.JavaPlugin;

public final class SunnyCoinflip extends JavaPlugin {
    private static Economy econ;
    private static SunnyCoinflip instance;
    private GameManager gameManager;
    private BedfightManager bedfightManager;
    private ChatListener chatListener;
    private InventoryListener inventoryListener;
    private PillarListener pillarListener;

    public void onEnable() {
        ServiceSupport.inject((Object)this);
        long var10000 = 4442722799442181059L;
        instance = this;
        if (!this.setupEconomy()) {
            Logger.getLogger("Minecraft").severe(String.format("[%s] - Disabled due to no Vault dependency found!", this.getDescription().getName()));
            this.getServer().getPluginManager().disablePlugin(this);
        } else {
            this.saveDefaultConfig();
            this.gameManager = new GameManager();
            this.bedfightManager = new BedfightManager(this);
            this.chatListener = new ChatListener(this);
            this.getCommand("coinflip").setExecutor(new CoinflipCommand());
            PillarSetupCommands pillarSetup = new PillarSetupCommands();
            this.getCommand("setpillarsfirst").setExecutor(pillarSetup);
            this.getCommand("setpillarsopponent").setExecutor(pillarSetup);
            this.getCommand("pillars").setExecutor(new PillarsCommand());
            this.inventoryListener = new InventoryListener(this);
            this.getServer().getPluginManager().registerEvents(this.inventoryListener, this);
            this.getServer().getPluginManager().registerEvents(this.chatListener, this);
            this.pillarListener = new PillarListener(this);
            this.getServer().getPluginManager().registerEvents(this.pillarListener, this);
            this.getServer().getPluginManager().registerEvents(new PillarItemSafetyListener(this), this);
            this.getServer().getPluginManager().registerEvents(new BedfightListener(this), this);
            this.getServer().getPluginManager().registerEvents(new CommandBlockListener(), this);
            if (this.getServer().getPluginManager().isPluginEnabled("PlaceholderAPI")) {
                (new PillarExpansion(this)).register();
                (new BedwarsExpansion(this)).register();
            }

        }
    }

    public ChatListener getChatListener() {
        return this.chatListener;
    }

    public GameManager getGameManager() {
        return this.gameManager;
    }

    public BedfightManager getBedfightManager() {
        return this.bedfightManager;
    }

    public InventoryListener getInventoryListener() {
        return this.inventoryListener;
    }

    public PillarListener getPillarListener() {
        return this.pillarListener;
    }

    public void onDisable() {
        if (this.bedfightManager != null) {
            this.bedfightManager.handleShutdown();
        }

        if (this.pillarListener != null) {
            this.pillarListener.handleServerShutdown();
        }

    }

    private boolean setupEconomy() {
        if (this.getServer().getPluginManager().getPlugin("Vault") == null) {
            return false;
        } else {
            RegisteredServiceProvider<Economy> rsp = this.getServer().getServicesManager().getRegistration(Economy.class);
            if (rsp == null) {
                return false;
            } else {
                econ = (Economy)rsp.getProvider();
                return econ != null;
            }
        }
    }

    public static Economy getEconomy() {
        return econ;
    }

    public static SunnyCoinflip getInstance() {
        return instance;
    }
}
