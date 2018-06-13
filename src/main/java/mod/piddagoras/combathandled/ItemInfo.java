package mod.piddagoras.combathandled;

import com.wurmonline.server.bodys.Wound;
import com.wurmonline.server.combat.Armour;
import com.wurmonline.server.combat.Weapon;
import com.wurmonline.server.creatures.Communicator;
import com.wurmonline.server.items.Item;
import com.wurmonline.server.players.Player;
import com.wurmonline.shared.util.MulticolorLineSegment;

import java.util.ArrayList;
import java.util.logging.Logger;

public class ItemInfo {
    public static Logger logger = Logger.getLogger(ItemInfo.class.getName());

    public static final byte COLOR_WHITE = 0;
    public static final byte COLOR_BLACK = 1;
    public static final byte COLOR_BLUE = 2;
    public static final byte COLOR_GREEN = 3;
    public static final byte COLOR_RED = 4;
    public static final byte COLOR_DARKRED = 5;
    public static final byte COLOR_DARKPURPLE = 6;
    public static final byte COLOR_ORANGE = 7;
    public static final byte COLOR_YELLOW = 8;
    //public static final byte COLOR_GREEN2 = 9; // Same as 3
    public static final byte COLOR_FORESTGREEN = 10;
    public static final byte COLOR_CYAN = 11;
    //public static final byte COLOR_BLUE2 = 12; // Same as 2
    public static final byte COLOR_PINK = 13;
    public static final byte COLOR_GRAY = 14;
    public static final byte COLOR_LIGHTGRAY = 15;

    public static void handleExamine(Communicator comm, Item target) {
        Player performer = comm.getPlayer();
        if(performer != null && performer.getPower() >= 5 || CombatHandledMod.showItemCombatInformation){
            if(target.isWeapon()) {
                // Weapon damage, speed, and DPS
                double baseDamage = DamageMethods.getBaseWeaponDamage(performer, null, target, true);
                CombatHandled ch = CombatHandled.getCombatHandled(performer);
                float speed = ch.getSpeed(performer, target);
                double critChance = Weapon.getCritChanceForWeapon(target);
                double parryChance = Weapon.getWeaponParryPercent(target)*Weapon.getMaterialParryBonus(target.getMaterial());

                ArrayList<MulticolorLineSegment> segments = new ArrayList<>();
                segments.add(new MulticolorLineSegment("Attack: ", COLOR_FORESTGREEN));
                segments.add(new MulticolorLineSegment(String.format("%s damage / %.2f seconds [", (int)baseDamage, speed), COLOR_WHITE));
                segments.add(new MulticolorLineSegment(String.format("%.1f", baseDamage/speed), COLOR_CYAN));
                segments.add(new MulticolorLineSegment("]", COLOR_WHITE));
                comm.sendColoredMessageEvent(segments);
                segments.clear();
                segments.add(new MulticolorLineSegment("Critical: ", COLOR_FORESTGREEN));
                segments.add(new MulticolorLineSegment(String.format("%.2f%%", critChance*100d), COLOR_WHITE));
                comm.sendColoredMessageEvent(segments);
                segments.clear();
                segments.add(new MulticolorLineSegment("Parry: ", COLOR_FORESTGREEN));
                segments.add(new MulticolorLineSegment(String.format("%.2f%%", parryChance*100d), COLOR_WHITE));
                comm.sendColoredMessageEvent(segments);
                //comm.sendNormalServerMessage(String.format("Swing Speed: %.2f seconds", speed));
            }
            if(target.isArmour()){
                // Physical reduction
                byte[] physWounds = {
                        Wound.TYPE_CRUSH,
                        Wound.TYPE_SLASH,
                        Wound.TYPE_PIERCE,
                        Wound.TYPE_BITE
                };
                float totalPhys = 0;
                for(byte woundType : physWounds){
                    totalPhys += Armour.getArmourModFor(target, woundType);
                }
                float averagePhys = totalPhys / physWounds.length;
                ArrayList<MulticolorLineSegment> segments = new ArrayList<>();
                segments.add(new MulticolorLineSegment("Physical Reduction: ", COLOR_FORESTGREEN));
                segments.add(new MulticolorLineSegment(String.format("%.1f%%", (1f-averagePhys)*100f), COLOR_WHITE));
                comm.sendColoredMessageEvent(segments);

                // Elemental reduction
                byte[] eleWounds = {
                        Wound.TYPE_BURN,
                        Wound.TYPE_COLD,
                        Wound.TYPE_ACID
                };
                float totalEle = 0;
                for(byte woundType : eleWounds){
                    totalEle += Armour.getArmourModFor(target, woundType);
                }
                float averageEle = totalEle / eleWounds.length;
                segments.clear();
                segments.add(new MulticolorLineSegment("Elemental Reduction: ", COLOR_FORESTGREEN));
                segments.add(new MulticolorLineSegment(String.format("%.1f%%", (1f-averageEle)*100f), COLOR_WHITE));
                comm.sendColoredMessageEvent(segments);

                // Other reduction
                byte[] otherWounds = {
                        Wound.TYPE_POISON,
                        Wound.TYPE_INFECTION,
                        Wound.TYPE_WATER,
                        Wound.TYPE_INTERNAL
                };
                float totalOther = 0;
                for(byte woundType : otherWounds){
                    totalOther += Armour.getArmourModFor(target, woundType);
                }
                float averageOther = totalOther / otherWounds.length;
                segments.clear();
                segments.add(new MulticolorLineSegment("Other Reduction: ", COLOR_FORESTGREEN));
                segments.add(new MulticolorLineSegment(String.format("%.1f%%", (1f-averageOther)*100f), COLOR_WHITE));
                comm.sendColoredMessageEvent(segments);
            }
        }
    }
}
