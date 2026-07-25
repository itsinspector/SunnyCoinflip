package org.ItsInspector.sunnyCoinflip.models;

import java.util.UUID;

public final class BedfightCoinflip {
    private final UUID creator;
    private final String creatorName;
    private final double amount;
    private final long createdAt;
    private UUID opponent;
    private String opponentName;
    private State state;
    private boolean firstBedAlive;
    private boolean opponentBedAlive;

    public BedfightCoinflip(UUID creator, String creatorName, double amount) {
        this.state = BedfightCoinflip.State.WAITING;
        this.firstBedAlive = true;
        this.opponentBedAlive = true;
        this.creator = creator;
        this.creatorName = creatorName;
        this.amount = amount;
        this.createdAt = System.currentTimeMillis();
    }

    public UUID getCreator() {
        return this.creator;
    }

    public String getCreatorName() {
        return this.creatorName;
    }

    public double getAmount() {
        return this.amount;
    }

    public long getCreatedAt() {
        return this.createdAt;
    }

    public UUID getOpponent() {
        return this.opponent;
    }

    public String getOpponentName() {
        return this.opponentName;
    }

    public void setOpponent(UUID opponent, String opponentName) {
        this.opponent = opponent;
        this.opponentName = opponentName;
    }

    public State getState() {
        return this.state;
    }

    public void setState(State state) {
        this.state = state;
    }

    public boolean isFirstBedAlive() {
        return this.firstBedAlive;
    }

    public void setFirstBedAlive(boolean firstBedAlive) {
        this.firstBedAlive = firstBedAlive;
    }

    public boolean isOpponentBedAlive() {
        return this.opponentBedAlive;
    }

    public void setOpponentBedAlive(boolean opponentBedAlive) {
        this.opponentBedAlive = opponentBedAlive;
    }

    public boolean includes(UUID playerId) {
        return this.creator.equals(playerId) || this.opponent != null && this.opponent.equals(playerId);
    }

    public UUID getOtherParticipant(UUID playerId) {
        if (this.creator.equals(playerId)) {
            return this.opponent;
        } else {
            return this.opponent != null && this.opponent.equals(playerId) ? this.creator : null;
        }
    }

    public String getName(UUID playerId) {
        if (this.creator.equals(playerId)) {
            return this.creatorName;
        } else {
            return this.opponent != null && this.opponent.equals(playerId) ? this.opponentName : "Sconosciuto";
        }
    }

    public static enum State {
        WAITING,
        COUNTDOWN,
        ACTIVE,
        FINISHED;

        // $FF: synthetic method
        private static State[] $values() {
            return new State[]{WAITING, COUNTDOWN, ACTIVE, FINISHED};
        }
    }
}
