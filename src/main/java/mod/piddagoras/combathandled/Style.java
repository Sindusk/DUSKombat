package mod.piddagoras.combathandled;

public enum Style {
    DEFENSIVE(0),
    NORMAL(1),
    AGGRESSIVE(3);
    public int id;
    Style(int id){
        this.id = id;
    }
}
