package org.ItsInspector.sunnyCoinflip.models;

import java.util.UUID;

public class Coinflip {
    private final UUID creator;
    private final String creatorName;
    private final double amount;
    private boolean active;

    public Coinflip(UUID creator, String creatorName, double amount) {
        this.creator = creator;
        this.creatorName = creatorName;
        this.amount = amount;
        this.active = false;
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

    public boolean isActive() {
        return this.active;
    }

    public void setActive(boolean active) {
        this.active = active;
    }
}
