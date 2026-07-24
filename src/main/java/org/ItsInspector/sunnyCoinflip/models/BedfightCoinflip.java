package org.ItsInspector.sunnyCoinflip.models;

import java.util.UUID;

public final class BedfightCoinflip {
    public enum State { WAITING, COUNTDOWN, ACTIVE, FINISHED }

    private final UUID creator;
    private final String creatorName;
    private final double amount;
    private final long createdAt = System.currentTimeMillis();
    private UUID opponent;
    private String opponentName;
    private State state = State.WAITING;
    private boolean firstBedAlive = true;
    private boolean opponentBedAlive = true;

    public BedfightCoinflip(UUID creator, String creatorName, double amount) {
        this.creator = creator;
        this.creatorName = creatorName;
        this.amount = amount;
    }

    public UUID getCreator() { return creator; }
    public String getCreatorName() { return creatorName; }
    public double getAmount() { return amount; }
    public long getCreatedAt() { return createdAt; }
    public UUID getOpponent() { return opponent; }
    public String getOpponentName() { return opponentName; }
    public State getState() { return state; }
    public void setState(State state) { this.state = state; }
    public boolean isFirstBedAlive() { return firstBedAlive; }
    public void setFirstBedAlive(boolean value) { firstBedAlive = value; }
    public boolean isOpponentBedAlive() { return opponentBedAlive; }
    public void setOpponentBedAlive(boolean value) { opponentBedAlive = value; }

    public void setOpponent(UUID opponent, String opponentName) {
        this.opponent = opponent;
        this.opponentName = opponentName;
    }

    public boolean includes(UUID playerId) {
        return creator.equals(playerId) || (opponent != null && opponent.equals(playerId));
    }

    public UUID getOtherParticipant(UUID playerId) {
        if (creator.equals(playerId)) return opponent;
        if (opponent != null && opponent.equals(playerId)) return creator;
        return null;
    }

    public String getName(UUID playerId) {
        if (creator.equals(playerId)) return creatorName;
        if (opponent != null && opponent.equals(playerId)) return opponentName;
        return "Sconosciuto";
    }
}
