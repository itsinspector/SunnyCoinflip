package org.ItsInspector.sunnyCoinflip.models;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import org.bukkit.Location;

public class PillarMatch {
    private final UUID creator;
    private final Location startLocation;
    private final double amount;
    private UUID opponent;
    private long startTime;
    private boolean started;
    private boolean playing;
    private boolean deathmatch = false;
    private long creatorJoinTime;
    private long opponentJoinTime;
    private final Map<UUID, Double> creatorBets = new HashMap();
    private final Map<UUID, Double> opponentBets = new HashMap();

    public PillarMatch(UUID creator, Location startLocation, double amount) {
        this.creator = creator;
        this.startLocation = startLocation;
        this.amount = amount;
        this.started = false;
    }

    public double getAmount() {
        return this.amount;
    }

    public UUID getCreator() {
        return this.creator;
    }

    public Location getStartLocation() {
        return this.startLocation;
    }

    public UUID getOpponent() {
        return this.opponent;
    }

    public void setOpponent(UUID opponent) {
        this.opponent = opponent;
    }

    public long getStartTime() {
        return this.startTime;
    }

    public void setStartTime(long startTime) {
        this.startTime = startTime;
    }

    public boolean isStarted() {
        return this.started;
    }

    public void setStarted(boolean started) {
        this.started = started;
    }

    public boolean isPlaying() {
        return this.playing;
    }

    public void setPlaying(boolean playing) {
        this.playing = playing;
    }

    public long getCreatorJoinTime() {
        return this.creatorJoinTime;
    }

    public void setCreatorJoinTime(long creatorJoinTime) {
        this.creatorJoinTime = creatorJoinTime;
    }

    public long getOpponentJoinTime() {
        return this.opponentJoinTime;
    }

    public void setOpponentJoinTime(long opponentJoinTime) {
        this.opponentJoinTime = opponentJoinTime;
    }

    public boolean isDeathmatch() {
        return this.deathmatch;
    }

    public void setDeathmatch(boolean deathmatch) {
        this.deathmatch = deathmatch;
    }

    public Map<UUID, Double> getCreatorBets() {
        return this.creatorBets;
    }

    public Map<UUID, Double> getOpponentBets() {
        return this.opponentBets;
    }
}
