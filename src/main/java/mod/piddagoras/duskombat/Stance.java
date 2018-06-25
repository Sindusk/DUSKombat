package mod.piddagoras.duskombat;

public enum Stance {
    MID_CENTER(0),
    UPPER_RIGHT(1),
    MID_RIGHT(2),
    LOWER_RIGHT(3),
    LOWER_LEFT(4),
    MID_LEFT(5),
    UPPER_LEFT(6),
    UPPER_CENTER(7),
    LOWER_CENTER(10);
    protected int id;
    Stance(int id){
        this.id = id;
    }
    public int getId(){
        return id;
    }
}
