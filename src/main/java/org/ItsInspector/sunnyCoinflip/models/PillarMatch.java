package org.ItsInspector.sunnyCoinflip.models;

import org.bukkit.Location;
import java.util.UUID;

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
    private final java.util.Map<java.util.UUID, Double> creatorBets = new java.util.HashMap<>();
    private final java.util.Map<java.util.UUID, Double> opponentBets = new java.util.HashMap<>();

    public PillarMatch(UUID creator, Location startLocation, double amount) {
        this.creator = creator;
        this.startLocation = startLocation;
        this.amount = amount;
        this.started = false;
    }

    public double getAmount() {
        return amount;
    }

    public UUID getCreator() {
        return creator;
    }

    public Location getStartLocation() {
        return startLocation;
    }

    public UUID getOpponent() {
        return opponent;
    }

    public void setOpponent(UUID opponent) {
        this.opponent = opponent;
    }

    public long getStartTime() {
        return startTime;
    }

    public void setStartTime(long startTime) {
        this.startTime = startTime;
    }

    public boolean isStarted() {
        return started;
    }

    public void setStarted(boolean started) {
        this.started = started;
    }

    public boolean isPlaying() {
        return playing;
    }

    public void setPlaying(boolean playing) {
        this.playing = playing;
    }

    public long getCreatorJoinTime() {
        return creatorJoinTime;
    }

    public void setCreatorJoinTime(long creatorJoinTime) {
        this.creatorJoinTime = creatorJoinTime;
    }

    public long getOpponentJoinTime() {
        return opponentJoinTime;
    }

    public void setOpponentJoinTime(long opponentJoinTime) {
        this.opponentJoinTime = opponentJoinTime;
    }

    public boolean isDeathmatch() {
        return deathmatch;
    }

    public void setDeathmatch(boolean deathmatch) {
        this.deathmatch = deathmatch;
    }

    public java.util.Map<java.util.UUID, Double> getCreatorBets() {
        return creatorBets;
    }

    public java.util.Map<java.util.UUID, Double> getOpponentBets() {
        return opponentBets;
    }
}