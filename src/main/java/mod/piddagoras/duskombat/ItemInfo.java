package mod.piddagoras.duskombat;

import com.wurmonline.server.Server;
import com.wurmonline.server.bodys.Wound;
import com.wurmonline.server.combat.Armour;
import com.wurmonline.server.combat.ArmourTypes;
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
        if(performer != null && performer.getPower() >= 5 || DUSKombatMod.showItemCombatInformation){
            if(target.isWeapon()) {
                // Weapon damage, speed, and DPS
                double baseDamage = DamageMethods.getBaseWeaponDamage(performer, null, target, true);
                DUSKombat ch = DUSKombat.getCombatHandled(performer);
                float speed = ch.getSpeed(performer, target);
                double critChance = Weapon.getCritChanceForWeapon(target);
                //double parryChance = Weapon.getWeaponParryPercent(target)*Weapon.getMaterialParryBonus(target.getMaterial());

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
                /*segments.clear();
                segments.add(new MulticolorLineSegment("Parry: ", COLOR_FORESTGREEN));
                segments.add(new MulticolorLineSegment(String.format("%.2f%%", parryChance*100d), COLOR_WHITE));
                comm.sendColoredMessageEvent(segments);*/
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
                float totalPhysRed = 0;
                float totalPhysGlance = 0;
                for(byte woundType : physWounds){
                    totalPhysRed += Armour.getArmourModFor(target, woundType);
                    float glanceRate = ArmourTypes.getArmourGlanceModifier(target.getArmourType(), target.getMaterial(), woundType) * (float)Server.getBuffedQualityEffect(target.getCurrentQualityLevel() / 100.0f);
                    totalPhysGlance += 0.05f + glanceRate;
                }
                float averagePhysRed = totalPhysRed / physWounds.length;
                float averagePhysGlance = totalPhysGlance / physWounds.length;
                ArrayList<MulticolorLineSegment> segments = new ArrayList<>();
                segments.add(new MulticolorLineSegment("Physical: ", COLOR_FORESTGREEN));
                segments.add(new MulticolorLineSegment(String.format("%.1f%% DR, %.1f%% Glance", (1f-averagePhysRed)*100f, (1f-averagePhysGlance)*100f), COLOR_WHITE));
                comm.sendColoredMessageEvent(segments);

                // Elemental reduction
                byte[] eleWounds = {
                        Wound.TYPE_BURN,
                        Wound.TYPE_COLD,
                        Wound.TYPE_ACID
                };
                float totalEleRed = 0;
                float totalEleGlance = 0;
                for(byte woundType : eleWounds){
                    totalEleRed += Armour.getArmourModFor(target, woundType);
                    float glanceRate = ArmourTypes.getArmourGlanceModifier(target.getArmourType(), target.getMaterial(), woundType) * (float)Server.getBuffedQualityEffect(target.getCurrentQualityLevel() / 100.0f);
                    totalEleGlance += 0.05f + glanceRate;
                }
                float averageEleRed = totalEleRed / eleWounds.length;
                float averageEleGlance = totalEleGlance / eleWounds.length;
                segments.clear();
                segments.add(new MulticolorLineSegment("Elemental: ", COLOR_FORESTGREEN));
                segments.add(new MulticolorLineSegment(String.format("%.1f%% DR, %.1f%% Glance", (1f-averageEleRed)*100f, (1f-averageEleGlance)*100f), COLOR_WHITE));
                comm.sendColoredMessageEvent(segments);

                // Other reduction
                byte[] otherWounds = {
                        Wound.TYPE_POISON,
                        Wound.TYPE_INFECTION,
                        Wound.TYPE_WATER,
                        Wound.TYPE_INTERNAL
                };
                float totalOtherRed = 0;
                float totalOtherGlance = 0;
                for(byte woundType : otherWounds){
                    totalOtherRed += Armour.getArmourModFor(target, woundType);
                    float glanceRate = ArmourTypes.getArmourGlanceModifier(target.getArmourType(), target.getMaterial(), woundType) * (float)Server.getBuffedQualityEffect(target.getCurrentQualityLevel() / 100.0f);
                    totalOtherGlance += 0.05f + glanceRate;
                }
                float averageOtherRed = totalOtherRed / otherWounds.length;
                float averageOtherGlance = totalOtherGlance / otherWounds.length;
                segments.clear();
                segments.add(new MulticolorLineSegment("Other: ", COLOR_FORESTGREEN));
                segments.add(new MulticolorLineSegment(String.format("%.1f%% DR, %.1f%% Glance", (1f-averageOtherRed)*100f, (1f-averageOtherGlance)*100f), COLOR_WHITE));
                comm.sendColoredMessageEvent(segments);
            }
        }
    }
}
