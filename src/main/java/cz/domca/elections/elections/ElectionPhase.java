package cz.domca.elections.elections;

public enum ElectionPhase {
    REGISTRATION,
    VOTING,
    RESULTS;
    
    public ElectionPhase getNext() {
        switch (this) {
            case REGISTRATION:
                return VOTING;
            case VOTING:
                return RESULTS;
            case RESULTS:
                return null; // Election ends
            default:
                return null;
        }
    }
    
    public String getDisplayName() {
        switch (this) {
            case REGISTRATION:
                return "Registrace kandidátů";
            case VOTING:
                return "Hlasování";
            case RESULTS:
                return "Výsledky";
            default:
                return name();
        }
    }
}
