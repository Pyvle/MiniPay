package com.minipay.installation;

import java.time.Instant;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "installation_state")
public class InstallationState {
    @Id
    private Long id;

    @Column(name = "initialized_at", nullable = false)
    private Instant initializedAt;

    protected InstallationState() {}

    public InstallationState(Instant initializedAt) {
        this.id = 1L;
        this.initializedAt = initializedAt;
    }

    public Long getId() {
        return id;
    }

    public Instant getInitializedAt() {
        return initializedAt;
    }
}
