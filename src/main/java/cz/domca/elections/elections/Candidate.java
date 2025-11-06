package cz.domca.elections.elections;

public class Candidate {
    private final int id;
    private final String playerUuid;
    private final String playerName;
    private final String role;
    private final String slogan;
    private final int votes;
    
    public Candidate(int id, String playerUuid, String playerName, String role, String slogan, int votes) {
        this.id = id;
        this.playerUuid = playerUuid;
        this.playerName = playerName;
        this.role = role;
        this.slogan = slogan;
        this.votes = votes;
    }
    
    public int getId() {
        return id;
    }
    
    public String getPlayerUuid() {
        return playerUuid;
    }
    
    public String getPlayerName() {
        return playerName;
    }
    
    public String getRole() {
        return role;
    }
    
    public String getSlogan() {
        return slogan;
    }
    
    public int getVotes() {
        return votes;
    }
}
