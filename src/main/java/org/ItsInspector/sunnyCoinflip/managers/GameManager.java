package org.ItsInspector.sunnyCoinflip.managers;

import org.ItsInspector.sunnyCoinflip.models.Coinflip;
import org.ItsInspector.sunnyCoinflip.models.PillarMatch;
import org.bukkit.Location;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.ItsInspector.sunnyCoinflip.SunnyCoinflip;

import java.io.File;
import java.io.IOException;
import java.util.*;

public class GameManager {
    private final Map<UUID, Coinflip> normalCoinflips = Collections.synchronizedMap(new LinkedHashMap<>());
    private PillarMatch activePillarMatch = null;
    private Location pillarFirst;
    private Location pillarOpponent;
    private int maxHeight = -36;
    private double maxAmount = 10000000;
    private double winMultiplier = 1.7;
    private int pillarCountdown = 10;
    private int itemDropInterval = 60;
    private final Map<UUID, Location> playerReturns = Collections.synchronizedMap(new HashMap<>());

    public GameManager() {
        loadLocations();
    }

    public void addCoinflip(Coinflip cf) {
        normalCoinflips.put(cf.getCreator(), cf);
    }

    public void removeCoinflip(UUID uuid) {
        normalCoinflips.remove(uuid);
    }

    public Coinflip getCoinflip(UUID uuid) {
        return normalCoinflips.get(uuid);
    }

    public Collection<Coinflip> getAllCoinflips() {
        synchronized (normalCoinflips) {
            return new ArrayList<>(normalCoinflips.values());
        }
    }

    public PillarMatch getActivePillarMatch() {
        return activePillarMatch;
    }

    public void setActivePillarMatch(PillarMatch match) {
        this.activePillarMatch = match;
    }

    public Location getPillarFirst() {
        return pillarFirst;
    }

    public void setPillarFirst(Location loc) {
        this.pillarFirst = loc;
        saveLocation("pillarFirst", loc);
    }

    public Location getPillarOpponent() {
        return pillarOpponent;
    }

    public void setPillarOpponent(Location loc) {
        this.pillarOpponent = loc;
        saveLocation("pillarOpponent", loc);
    }

    public void setPlayerReturn(UUID uuid, Location loc) {
        playerReturns.put(uuid, loc);
    }

    public Location getPlayerReturn(UUID uuid) {
        return playerReturns.get(uuid);
    }

    public void removePlayerReturn(UUID uuid) {
        playerReturns.remove(uuid);
    }

    private void saveLocation(String path, Location loc) {
        SunnyCoinflip.getInstance().getConfig().set(path, loc);
        SunnyCoinflip.getInstance().saveConfig();
    }

    private void loadLocations() {
        FileConfiguration config = SunnyCoinflip.getInstance().getConfig();
        pillarFirst = config.getLocation("pillarFirst");
        pillarOpponent = config.getLocation("pillarOpponent");
        
        maxAmount = config.getDouble("coinflip.max-amount", 10000000.0);
        winMultiplier = config.getDouble("coinflip.win-multiplier", 1.7);
        
        maxHeight = config.getInt("pillars.max-height", -36);
        pillarCountdown = config.getInt("pillars.countdown", 10);
        itemDropInterval = config.getInt("pillars.item-drop-interval", 60);
    }

    public double getMaxAmount() {
        return maxAmount;
    }

    public double getWinMultiplier() {
        return winMultiplier;
    }

    public int getPillarCountdown() {
        return pillarCountdown;
    }

    public int getItemDropInterval() {
        return itemDropInterval;
    }

    public int getMaxHeight() {
        return maxHeight;
    }

    public boolean isPillarWorld(org.bukkit.World world) {
        if (world == null) return false;
        if (pillarFirst != null && pillarFirst.getWorld() != null) {
            if (pillarFirst.getWorld().getName().equals(world.getName())) return true;
        }
        if (pillarOpponent != null && pillarOpponent.getWorld() != null) {
            if (pillarOpponent.getWorld().getName().equals(world.getName())) return true;
        }
        return false;
    }

    public boolean isRestrictedWorld(org.bukkit.World world) {
        if (world == null) return false;
        String worldName = world.getName();
        return isPillarWorld(world) || worldName.equals("arena-pvp-unranked");
    }
}