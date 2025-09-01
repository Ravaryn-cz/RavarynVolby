package cz.domca.elections.elections;

import java.time.Instant;

public class Election {
    private final int id;
    private final String regionId;
    private final ElectionPhase phase;
    private final Instant startTime;
    private final Instant endTime;
    
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
    
    public Instant getStartTime() {
        return startTime;
    }
    
    public Instant getEndTime() {
        return endTime;
    }
    
    public boolean isEnded() {
        return endTime != null && Instant.now().isAfter(endTime);
    }
    
    public boolean isActive() {
        return !isEnded();
    }
}
