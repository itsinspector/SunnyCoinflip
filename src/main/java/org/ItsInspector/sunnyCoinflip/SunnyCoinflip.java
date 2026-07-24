package org.ItsInspector.sunnyCoinflip;

import net.milkbowl.vault.economy.Economy;
import org.ItsInspector.sunnyCoinflip.commands.CoinflipCommand;
import org.ItsInspector.sunnyCoinflip.commands.PillarSetupCommands;
import org.ItsInspector.sunnyCoinflip.commands.PillarsCommand;
import org.ItsInspector.sunnyCoinflip.integrations.BedwarsExpansion;
import org.ItsInspector.sunnyCoinflip.integrations.PillarExpansion;
import org.ItsInspector.sunnyCoinflip.listeners.*;
import org.ItsInspector.sunnyCoinflip.managers.BedfightManager;
import org.ItsInspector.sunnyCoinflip.managers.GameManager;
import org.bukkit.plugin.RegisteredServiceProvider;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.Objects;

public final class SunnyCoinflip extends JavaPlugin {
    private static Economy economy;
    private static SunnyCoinflip instance;
    private GameManager gameManager;
    private BedfightManager bedfightManager;
    private ChatListener chatListener;
    private InventoryListener inventoryListener;
    private PillarListener pillarListener;

    @Override
    public void onEnable() {
        instance = this;
        if (!setupEconomy()) {
            getLogger().severe("Plugin disabilitato: Vault/Economy non disponibile.");
            getServer().getPluginManager().disablePlugin(this);
            return;
        }
        saveDefaultConfig();
        gameManager = new GameManager();
        bedfightManager = new BedfightManager(this);
        chatListener = new ChatListener(this);
        inventoryListener = new InventoryListener(this);
        pillarListener = new PillarListener(this);

        Objects.requireNonNull(getCommand("coinflip")).setExecutor(new CoinflipCommand());
        PillarSetupCommands setup = new PillarSetupCommands();
        Objects.requireNonNull(getCommand("setpillarsfirst")).setExecutor(setup);
        Objects.requireNonNull(getCommand("setpillarsopponent")).setExecutor(setup);
        Objects.requireNonNull(getCommand("pillars")).setExecutor(new PillarsCommand());

        getServer().getPluginManager().registerEvents(inventoryListener, this);
        getServer().getPluginManager().registerEvents(chatListener, this);
        getServer().getPluginManager().registerEvents(pillarListener, this);
        getServer().getPluginManager().registerEvents(new PillarItemSafetyListener(this), this);
        getServer().getPluginManager().registerEvents(new BedfightListener(this), this);
        getServer().getPluginManager().registerEvents(new CommandBlockListener(), this);
        getServer().getPluginManager().registerEvents(new SpecialEggListener(this), this);

        if (getServer().getPluginManager().isPluginEnabled("PlaceholderAPI")) {
            new PillarExpansion(this).register();
            new BedwarsExpansion(this).register();
        }
    }

    @Override
    public void onDisable() {
        if (bedfightManager != null) bedfightManager.handleShutdown();
        if (pillarListener != null) pillarListener.handleServerShutdown();
    }

    private boolean setupEconomy() {
        if (getServer().getPluginManager().getPlugin("Vault") == null) return false;
        RegisteredServiceProvider<Economy> registration = getServer().getServicesManager().getRegistration(Economy.class);
        if (registration == null) return false;
        economy = registration.getProvider();
        return economy != null;
    }

    public static SunnyCoinflip getInstance() { return instance; }
    public static Economy getEconomy() { return economy; }
    public GameManager getGameManager() { return gameManager; }
    public BedfightManager getBedfightManager() { return bedfightManager; }
    public ChatListener getChatListener() { return chatListener; }
    public InventoryListener getInventoryListener() { return inventoryListener; }
    public PillarListener getPillarListener() { return pillarListener; }
}
