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
        return creator;
    }

    public String getCreatorName() {
        return creatorName;
    }

    public double getAmount() {
        return amount;
    }

    public boolean isActive() {
        return active;
    }

    public void setActive(boolean active) {
        this.active = active;
    }
}