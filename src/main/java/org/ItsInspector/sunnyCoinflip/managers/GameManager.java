package org.ItsInspector.sunnyCoinflip.managers;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import org.ItsInspector.sunnyCoinflip.SunnyCoinflip;
import org.ItsInspector.sunnyCoinflip.models.Coinflip;
import org.ItsInspector.sunnyCoinflip.models.PillarMatch;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.configuration.file.FileConfiguration;

public class GameManager {
    private final Map<UUID, Coinflip> normalCoinflips = Collections.synchronizedMap(new LinkedHashMap());
    private PillarMatch activePillarMatch = null;
    private Location pillarFirst;
    private Location pillarOpponent;
    private int maxHeight = -36;
    private double maxAmount = (double)1.0E7F;
    private double winMultiplier = 1.7;
    private int pillarCountdown = 10;
    private int itemDropInterval = 60;
    private final Map<UUID, Location> playerReturns = Collections.synchronizedMap(new HashMap());

    public GameManager() {
        this.loadLocations();
    }

    public void addCoinflip(Coinflip cf) {
        this.normalCoinflips.put(cf.getCreator(), cf);
    }

    public void removeCoinflip(UUID uuid) {
        this.normalCoinflips.remove(uuid);
    }

    public Coinflip getCoinflip(UUID uuid) {
        return (Coinflip)this.normalCoinflips.get(uuid);
    }

    public Collection<Coinflip> getAllCoinflips() {
        synchronized(this.normalCoinflips) {
            return new ArrayList(this.normalCoinflips.values());
        }
    }

    public PillarMatch getActivePillarMatch() {
        return this.activePillarMatch;
    }

    public void setActivePillarMatch(PillarMatch match) {
        this.activePillarMatch = match;
    }

    public Location getPillarFirst() {
        return this.pillarFirst;
    }

    public void setPillarFirst(Location loc) {
        this.pillarFirst = loc;
        this.saveLocation("pillarFirst", loc);
    }

    public Location getPillarOpponent() {
        return this.pillarOpponent;
    }

    public void setPillarOpponent(Location loc) {
        this.pillarOpponent = loc;
        this.saveLocation("pillarOpponent", loc);
    }

    public void setPlayerReturn(UUID uuid, Location loc) {
        this.playerReturns.put(uuid, loc);
    }

    public Location getPlayerReturn(UUID uuid) {
        return (Location)this.playerReturns.get(uuid);
    }

    public void removePlayerReturn(UUID uuid) {
        this.playerReturns.remove(uuid);
    }

    private void saveLocation(String path, Location loc) {
        SunnyCoinflip.getInstance().getConfig().set(path, loc);
        SunnyCoinflip.getInstance().saveConfig();
    }

    private void loadLocations() {
        FileConfiguration config = SunnyCoinflip.getInstance().getConfig();
        this.pillarFirst = config.getLocation("pillarFirst");
        this.pillarOpponent = config.getLocation("pillarOpponent");
        this.maxAmount = config.getDouble("coinflip.max-amount", (double)1.0E7F);
        this.winMultiplier = config.getDouble("coinflip.win-multiplier", 1.7);
        this.maxHeight = config.getInt("pillars.max-height", -36);
        this.pillarCountdown = config.getInt("pillars.countdown", 10);
        this.itemDropInterval = config.getInt("pillars.item-drop-interval", 60);
    }

    public double getMaxAmount() {
        return this.maxAmount;
    }

    public double getWinMultiplier() {
        return this.winMultiplier;
    }

    public int getPillarCountdown() {
        return this.pillarCountdown;
    }

    public int getItemDropInterval() {
        return this.itemDropInterval;
    }

    public int getMaxHeight() {
        return this.maxHeight;
    }

    public boolean isPillarWorld(World world) {
        if (world == null) {
            return false;
        } else if (this.pillarFirst != null && this.pillarFirst.getWorld() != null && this.pillarFirst.getWorld().getName().equals(world.getName())) {
            return true;
        } else {
            return this.pillarOpponent != null && this.pillarOpponent.getWorld() != null && this.pillarOpponent.getWorld().getName().equals(world.getName());
        }
    }

    public boolean isRestrictedWorld(World world) {
        if (world == null) {
            return false;
        } else {
            String worldName = world.getName();
            return this.isPillarWorld(world) || worldName.equals("arena-pvp-unranked");
        }
    }
}
