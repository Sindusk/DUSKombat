package mod.piddagoras.combathandled;

import com.wurmonline.server.Items;
import com.wurmonline.server.NoSuchItemException;
import com.wurmonline.server.combat.Weapon;
import com.wurmonline.server.creatures.Creature;
import com.wurmonline.server.creatures.Creatures;
import com.wurmonline.server.creatures.NoSuchCreatureException;
import com.wurmonline.server.items.Item;
import com.wurmonline.server.skills.NoSuchSkillException;
import com.wurmonline.server.skills.Skill;
import com.wurmonline.server.skills.SkillList;
import com.wurmonline.shared.constants.Enchants;

import java.util.logging.Logger;

public class CombatMethods {
    public static Logger logger = Logger.getLogger(CombatMethods.class.getName());

    public static Item getVehicleSafe(Creature pilot) {
        try {
            if (pilot.getVehicle() != -10)
                return Items.getItem(pilot.getVehicle());
        } catch (NoSuchItemException ignored) { }
        return null;
    }
    public static Creature getMountSafe(Creature pilot) {
        try {
            if (pilot.getVehicle() != -10)
                return Creatures.getInstance().getCreature(pilot.getVehicle());
        } catch (NoSuchCreatureException ignored) { }
        return null;
    }

    public static double getHitCheck(Creature attacker, Creature opponent, Item weapon){
        Skill primWeaponSkill = CombatHandled.getCreatureWeaponSkill(attacker, weapon);
        // Calculate bonus
        double bonus = attacker.getFightingSkill().skillCheck(0, weapon, 0, true, 1f, attacker, opponent)*0.2d;
        // Current bonus: [-20 to 20]
        float heightDiff = attacker.getPositionZ() + attacker.getAltOffZ() - (opponent.getPositionZ() + opponent.getAltOffZ());
        bonus += Math.max(-10, Math.min(10, heightDiff*5));
        // Current bonus: [-30 to 30]
        byte fightlevel = CombatHandled.getCreatureFightLevel(attacker); // 0 - 5
        bonus += fightlevel * 3;
        // Current bonus: [-30 to 45]
        float attAngle = CombatHandled.getDirectionTo(attacker, opponent);
        //double angleBonus = 1.25-0.25*Math.pow((attAngle-180), 2)/1600;
        //double angleBonus = Math.max(0, 25*(1-Math.pow((attAngle-180),2)/3600));
        double angleBonus = Math.max(0, 25*(1-(Math.pow(Math.abs(attAngle-180)/60, 0.5))));
        bonus += angleBonus;
        // Current bonus: [-30 to 70]
        Item vehicle = getVehicleSafe(attacker);
        if(vehicle != null){
            // -5 and -30
        }

        Creature mount = getMountSafe(attacker);
        if(mount != null){
            // 5 - 15
            // taming increases bonus
        }

        double diff = 0;
        if(attacker.getBonusForSpellEffect(Enchants.CRET_TRUEHIT) > 0){
            diff -= attacker.getBonusForSpellEffect(Enchants.CRET_TRUEHIT)*0.05d;
        }
        if(weapon.getBonusForSpellEffect(Enchants.BUFF_NIMBLENESS) > 0){
            diff -= weapon.getBonusForSpellEffect(Enchants.BUFF_NIMBLENESS)*0.05d;
        }

        return primWeaponSkill.skillCheck(diff, weapon, bonus, false, 10.0f);
    }
    public static double getDodgeCheck(Creature attacker, Creature opponent, Item weapon, double attackCheck){
        Skill fightingSkill;
        try {
            fightingSkill = opponent.getSkills().getSkill(SkillList.GROUP_FIGHTING);
        } catch (NoSuchSkillException e) {
            fightingSkill = opponent.getSkills().learn(SkillList.GROUP_FIGHTING, 1.0F);
            logger.warning(String.format("%s had no fighting skill. Weird.", opponent.getName()));
        }
        double bonus = opponent.getBodyControlSkill().skillCheck(attackCheck*0.5d, 0, true, 10.0f)*0.2d;
        // Current bonus: [-20 to 20]

        if(opponent.getBonusForSpellEffect(Enchants.CRET_WILLOWSPINE) > 0){
            attackCheck *= 1-(opponent.getBonusForSpellEffect(Enchants.CRET_WILLOWSPINE)*0.002d);
        }
        if(opponent.getBonusForSpellEffect(Enchants.CRET_EXCEL) > 0){
            attackCheck -= opponent.getBonusForSpellEffect(Enchants.CRET_EXCEL) * 0.05d;
        }

        return fightingSkill.skillCheck(attackCheck*2d, bonus, true, 10.0f);
    }
    public static double getCriticalChance(Creature attacker, Creature opponent, Item weapon){
        return (double)Weapon.getCritChanceForWeapon(weapon);
    }
    public static double getParryCheck(Creature attacker, Creature opponent, Item weapon, double attackCheck){
        Item oppWeapon = opponent.getPrimWeapon();
        if(oppWeapon.isBodyPartAttached()){
            return -100; // Unarmed cannot parry.
        }else if(Weapon.getWeaponParryPercent(oppWeapon) == 0){
            return -100; // Weapons with no parry chance also cannot parry.
        }
        Skill oppWeaponSkill = CombatHandled.getCreatureWeaponSkill(opponent, oppWeapon);
        // TODO: Calculate bonus
        double bonus = 0;

        double parryCheck = oppWeaponSkill.skillCheck(attackCheck, oppWeapon, bonus, true, 10.0f);

        // Apply a penalty to the parry based on the base parry percent.
        double parryPenalty = 100D*(1D-Weapon.getWeaponParryPercent(oppWeapon));
        logger.info(String.format("%s has penalty of %.2f for parry percent %.2f", oppWeapon.getName(), parryPenalty, Weapon.getWeaponParryPercent(oppWeapon)));
        parryCheck -= parryPenalty;

        return parryCheck;
    }
}
