package cz.domca.elections.elections;

import java.time.Instant;

public class Election {
    private final int id;
    private final String regionId;
    private ElectionPhase phase;
    private final Instant startTime;
    private Instant endTime;
    
    public Election(int id, String regionId, ElectionPhase phase, Instant startTime, Instant endTime) {
        this.id = id;
        this.regionId = regionId;
        this.phase = phase;
        this.startTime = startTime;
        this.endTime = endTime;
    }
    
    public int getId() {
        return id;
    }
    
    public String getRegionId() {
        return regionId;
    }
    
    public ElectionPhase getPhase() {
        return phase;
    }
    
    public void setPhase(String phaseName) {
        try {
            this.phase = ElectionPhase.valueOf(phaseName.toUpperCase());
        } catch (IllegalArgumentException e) {
            // Default to REGISTRATION if invalid phase
            this.phase = ElectionPhase.REGISTRATION;
        }
    }
    
    public void setPhase(ElectionPhase phase) {
        this.phase = phase;
    }
    
    public Instant getStartTime() {
        return startTime;
    }
    
    public Instant getEndTime() {
        return endTime;
    }
    
    public void setEndTime(Instant endTime) {
        this.endTime = endTime;
    }
    
    public boolean isEnded() {
        return endTime != null && Instant.now().isAfter(endTime);
    }
    
    public boolean isActive() {
        return !isEnded();
    }
}
