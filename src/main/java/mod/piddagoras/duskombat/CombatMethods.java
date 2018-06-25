package mod.piddagoras.duskombat;

import com.wurmonline.server.Items;
import com.wurmonline.server.NoSuchItemException;
import com.wurmonline.server.combat.CombatEngine;
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

    public static double getHitCheck(Creature attacker, Creature opponent, Item weapon, boolean noSkillGain){
        Skill primWeaponSkill = DUSKombat.getCreatureWeaponSkill(attacker, weapon);
        // Calculate bonus
        double bonus = attacker.getFightingSkill().skillCheck(0, weapon, 0, true, 10f, attacker, opponent);
        // Current bonus: [-100 to 100]
        float heightDiff = attacker.getPositionZ() + attacker.getAltOffZ() - (opponent.getPositionZ() + opponent.getAltOffZ());
        bonus += Math.max(-10, Math.min(10, heightDiff*5));
        // Current bonus: [-110 to 110]
        byte fightlevel = DUSKombat.getCreatureFightLevel(attacker); // 0 - 5
        bonus += fightlevel * 3;
        // Current bonus: [-110 to 125]
        float attAngle = DUSKombat.getDirectionTo(attacker, opponent);
        //double angleBonus = 1.25-0.25*Math.pow((attAngle-180), 2)/1600;
        //double angleBonus = Math.max(0, 25*(1-Math.pow((attAngle-180),2)/3600));
        double angleBonus = Math.max(0, 25*(1-(Math.pow(Math.abs(attAngle-180)/60, 0.5))));
        bonus += angleBonus;
        // Current bonus: [-110 to 150]
        Item vehicle = getVehicleSafe(attacker);
        if(vehicle != null){
            bonus -= 20;
        }

        Creature mount = getMountSafe(attacker);
        if(mount != null){
            // TODO: Change values per mount, and give bonus for tamed mounts.
            bonus += 20;
        }

        double diff = 0;
        if(attacker.getBonusForSpellEffect(Enchants.CRET_TRUEHIT) > 0){
            diff -= attacker.getBonusForSpellEffect(Enchants.CRET_TRUEHIT)*0.05d;
        }
        if(weapon.getBonusForSpellEffect(Enchants.BUFF_NIMBLENESS) > 0){
            diff -= weapon.getBonusForSpellEffect(Enchants.BUFF_NIMBLENESS)*0.05d;
        }

        return primWeaponSkill.skillCheck(diff, weapon, bonus, noSkillGain, 10.0f);
    }
    public static double getDodgeCheck(Creature attacker, Creature opponent, Item weapon, double attackCheck){
        Skill fightingSkill;
        try {
            fightingSkill = opponent.getSkills().getSkill(SkillList.GROUP_FIGHTING);
        } catch (NoSuchSkillException e) {
            fightingSkill = opponent.getSkills().learn(SkillList.GROUP_FIGHTING, 1.0F);
            logger.warning(String.format("%s had no fighting skill. Weird.", opponent.getName()));
        }
        double bonus = opponent.getBodyControlSkill().skillCheck(attackCheck*0.5d, 0, true, 10.0f);
        // Current bonus: [-100 to 100]
        // TODO: Add bonus for lightweight armours etc.

        if(opponent.getBonusForSpellEffect(Enchants.CRET_WILLOWSPINE) > 0){
            attackCheck *= 1-(opponent.getBonusForSpellEffect(Enchants.CRET_WILLOWSPINE)*0.002d);
        }
        if(opponent.getBonusForSpellEffect(Enchants.CRET_EXCEL) > 0){
            attackCheck -= opponent.getBonusForSpellEffect(Enchants.CRET_EXCEL) * 0.05d;
        }

        double dodgeCheck = fightingSkill.skillCheck(attackCheck, bonus, true, 10.0f);
        if(opponent.isPlayer()){
            dodgeCheck = fightingSkill.skillCheck(attackCheck*1.5, bonus, true, 10.0f); // Reduce crazy dodges from players
        }

        //dodgeCheck += opponent.getBaseCombatRating()*0.2; // Attempt to allow high CR creatures to dodge more attacks.

        if(opponent.getBaseCombatRating() < 50){ // Creatures without high CR get a penalty to their dodge chance based on current stamina.
            double stamDodgeMult = 0.5d * (100d - opponent.getStatus().calcStaminaPercent()); // Up to -50 penalty to the check based on stamina.
            dodgeCheck -= stamDodgeMult;
        }

        return dodgeCheck;
    }
    public static double getCriticalChance(Creature attacker, Creature opponent, Item weapon){
        double critChance = Weapon.getCritChanceForWeapon(weapon);
        if (DUSKombat.isAtSoftSpot(opponent.getCombatHandler().getCurrentStance(), attacker.getCombatHandler().getCurrentStance())) {
            critChance += 0.05f;
        }
        if (CombatEngine.getEnchantBonus(weapon, opponent) > 0) {
            critChance += 0.03f;
        }
        return critChance;
    }
    public static double getParryCheck(Creature attacker, Creature opponent, Item weapon, double attackCheck){
        Item defWeapon = opponent.getPrimWeapon();
        if(defWeapon.isBodyPartAttached()){
            return -100; // Unarmed cannot parry.
        }else if(Weapon.getWeaponParryPercent(defWeapon) == 0){
            return -100; // Weapons with no parry chance also cannot parry.
        }
        Skill defWeaponSkill = DUSKombat.getCreatureWeaponSkill(opponent, defWeapon);
        // TODO: Calculate bonus
        double bonus = opponent.getFightingSkill().skillCheck(attackCheck*0.5d, weapon, 0, true, 10f);

        double parryCheck = defWeaponSkill.skillCheck(attackCheck, defWeapon, bonus, true, 10.0f);

        if(opponent.getBonusForSpellEffect(Enchants.CRET_EXCEL) > 0){
            parryCheck -= opponent.getBonusForSpellEffect(Enchants.CRET_EXCEL) * 0.05d;
        }
        if(defWeapon.getBonusForSpellEffect(Enchants.BUFF_NIMBLENESS) > 0){
            parryCheck -= defWeapon.getBonusForSpellEffect(Enchants.BUFF_NIMBLENESS)*0.05d;
        }

        // Apply a penalty to the parry based on the base parry percent.
        double parryPenalty = 50D*(1D-Weapon.getWeaponParryPercent(defWeapon));
        //logger.info(String.format("%s has penalty of %.2f for parry percent %.2f", defWeapon.getName(), parryPenalty, Weapon.getWeaponParryPercent(defWeapon)));
        parryCheck -= parryPenalty;

        double stamParryMult = 0.2d * (100d - opponent.getStatus().calcStaminaPercent()); // Up to -20 penalty to the check based on stamina.
        parryCheck -= stamParryMult;

        return parryCheck;
    }
    public static double getShieldCheck(Creature attacker, Creature opponent, Item weapon, double attackCheck){
        Item defShield = opponent.getShield(); // Cannot be null because check occurs before calling this method.
        int defShieldSkillNum = SkillList.GROUP_SHIELDS;
        try {
            defShieldSkillNum = defShield.getPrimarySkill();
        } catch (NoSuchSkillException ex) {
            logger.warning(String.format("Could not find proper skill for shield %s. Resorting to Shields group skill.", defShield.getName()));
        }
        Skill defShieldSkill = DamageMethods.getCreatureSkill(opponent, defShieldSkillNum);

        // TODO: Calculate bonus
        double bonus = opponent.getFightingSkill().skillCheck(attackCheck*0.5d, weapon, 0, true, 10f);

        double shieldCheck = defShieldSkill.skillCheck(attackCheck*0.2, defShield, bonus, true, 10.0F);

        double stamBlockMult = 0.5d * (100d - opponent.getStatus().calcStaminaPercent()); // Up to -50 penalty to the check based on stamina.
        shieldCheck -= stamBlockMult;

        return shieldCheck;
    }
}
