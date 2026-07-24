package org.ItsInspector.sunnyCoinflip.managers;

import org.ItsInspector.sunnyCoinflip.SunnyCoinflip;
import org.ItsInspector.sunnyCoinflip.models.Coinflip;
import org.ItsInspector.sunnyCoinflip.models.PillarMatch;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.configuration.file.FileConfiguration;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

public class GameManager {
    private final Map<UUID, Coinflip> normalCoinflips = Collections.synchronizedMap(new LinkedHashMap<>());
    private final Map<UUID, Location> playerReturns = Collections.synchronizedMap(new HashMap<>());
    private PillarMatch activePillarMatch;
    private Location pillarFirst;
    private Location pillarOpponent;
    private int maxHeight;
    private double maxAmount;
    private double winMultiplier;
    private int pillarCountdown;
    private int itemDropInterval;

    public GameManager() { loadLocations(); }

    public void addCoinflip(Coinflip coinflip) { normalCoinflips.put(coinflip.getCreator(), coinflip); }
    public void removeCoinflip(UUID uuid) { normalCoinflips.remove(uuid); }
    public Coinflip getCoinflip(UUID uuid) { return normalCoinflips.get(uuid); }
    public Collection<Coinflip> getAllCoinflips() {
        synchronized (normalCoinflips) { return new ArrayList<>(normalCoinflips.values()); }
    }

    public PillarMatch getActivePillarMatch() { return activePillarMatch; }
    public void setActivePillarMatch(PillarMatch match) { activePillarMatch = match; }
    public Location getPillarFirst() { return pillarFirst == null ? null : pillarFirst.clone(); }
    public Location getPillarOpponent() { return pillarOpponent == null ? null : pillarOpponent.clone(); }
    public void setPillarFirst(Location location) { pillarFirst = location.clone(); saveLocation("pillarFirst", location); }
    public void setPillarOpponent(Location location) { pillarOpponent = location.clone(); saveLocation("pillarOpponent", location); }
    public void setPlayerReturn(UUID uuid, Location location) { playerReturns.put(uuid, location.clone()); }
    public Location getPlayerReturn(UUID uuid) { Location location = playerReturns.get(uuid); return location == null ? null : location.clone(); }
    public void removePlayerReturn(UUID uuid) { playerReturns.remove(uuid); }

    private void saveLocation(String path, Location location) {
        SunnyCoinflip.getInstance().getConfig().set(path, location);
        SunnyCoinflip.getInstance().saveConfig();
    }

    private void loadLocations() {
        FileConfiguration config = SunnyCoinflip.getInstance().getConfig();
        pillarFirst = config.getLocation("pillarFirst");
        pillarOpponent = config.getLocation("pillarOpponent");
        maxAmount = config.getDouble("coinflip.max-amount", 10_000_000.0);
        winMultiplier = config.getDouble("coinflip.win-multiplier", 1.7);
        maxHeight = config.getInt("pillars.max-height", -36);
        pillarCountdown = config.getInt("pillars.countdown", 10);
        itemDropInterval = config.getInt("pillars.item-drop-interval", 60);
    }

    public double getMaxAmount() { return maxAmount; }
    public double getWinMultiplier() { return winMultiplier; }
    public int getPillarCountdown() { return pillarCountdown; }
    public int getItemDropInterval() { return itemDropInterval; }
    public int getMaxHeight() { return maxHeight; }

    public boolean isPillarWorld(World world) {
        if (world == null) return false;
        return sameWorld(pillarFirst, world) || sameWorld(pillarOpponent, world);
    }

    public boolean isRestrictedWorld(World world) {
        return world != null && (isPillarWorld(world) || world.getName().equalsIgnoreCase("arena-pvp-unranked"));
    }

    private boolean sameWorld(Location location, World world) {
        return location != null && location.getWorld() != null && location.getWorld().getUID().equals(world.getUID());
    }
}
