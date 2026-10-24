package dev.msdrop.model;

import java.util.UUID;

public class User {

    private final UUID uniqueId;
    private boolean enabled;

    public User(UUID uniqueId, boolean enabled) {
        this.uniqueId = uniqueId;
        this.enabled = enabled;
    }

    public UUID getUniqueId() {
        return uniqueId;
    }

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

}
