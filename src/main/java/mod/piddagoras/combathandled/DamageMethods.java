package mod.piddagoras.combathandled;

import com.wurmonline.server.Server;
import com.wurmonline.server.behaviours.Vehicle;
import com.wurmonline.server.behaviours.Vehicles;
import com.wurmonline.server.bodys.Wound;
import com.wurmonline.server.combat.Weapon;
import com.wurmonline.server.creatures.Creature;
import com.wurmonline.server.creatures.CreatureTemplateIds;
import com.wurmonline.server.items.Item;
import com.wurmonline.server.players.ItemBonus;
import com.wurmonline.server.skills.NoSuchSkillException;
import com.wurmonline.server.skills.Skill;
import com.wurmonline.server.skills.SkillList;
import com.wurmonline.shared.constants.Enchants;
import org.gotti.wurmunlimited.modloader.ReflectionUtil;

import java.lang.reflect.InvocationTargetException;
import java.util.logging.Level;
import java.util.logging.Logger;

public class DamageMethods {
    public static Logger logger = Logger.getLogger(DamageMethods.class.getName());

    protected static Skill getCreatureSkill(Creature creature, int skillnum){
        Skill attStrengthSkill;
        try {
            attStrengthSkill = creature.getSkills().getSkill(skillnum);
        } catch (NoSuchSkillException ex) {
            attStrengthSkill = creature.getSkills().learn(skillnum, 1.0F);
            logger.log(Level.WARNING, creature.getName() + " had no strength. Weird.");
        }
        return attStrengthSkill;
    }

    public static String getAttackString(Creature attacker, Item weapon, byte woundType) {
        if (weapon.isWeaponSword() && (woundType == Wound.TYPE_PIERCE || woundType == Wound.TYPE_SLASH)) {
            if(woundType == Wound.TYPE_PIERCE){
                return "pierce";
            }
            return "cut";
        } else if (weapon.isWeaponPierce() && woundType == Wound.TYPE_PIERCE) {
            return "pierce";
        } else if (weapon.isWeaponSlash() && woundType == Wound.TYPE_SLASH) {
            return "cut";
        } else if (weapon.isWeaponCrush() && woundType == Wound.TYPE_CRUSH) {
            return "maul";
        } else if (weapon.isBodyPart() && weapon.getAuxData() != 100){
            return attacker.getAttackStringForBodyPart(weapon);
        }
        if(!weapon.isBodyPart()) {
            if(woundType == Wound.TYPE_BURN){ // Salve of fire
                return "burn";
            }else if(woundType == Wound.TYPE_COLD){ // Salve of frost
                return "freeze";
            }else if(woundType == Wound.TYPE_ACID){ // Potion of acid
                return "corrode";
            }else if(woundType == Wound.TYPE_POISON){ // Venom
                return "poison";
            }
        }
        return "hit";
    }

    public static double getBaseUnarmedDamage(Creature attacker, Item weapon){
        double damage;
        try {
            // Use reflection to get the damage modifier for creature type and age (champion, venerable, etc.)
            float attackerDamageTypeModifier = ReflectionUtil.callPrivateMethod(attacker.getStatus(), ReflectionUtil.getMethod(attacker.getStatus().getClass(), "getDamageTypeModifier"));
            damage = (double)(attacker.getCombatDamage(weapon) * 1000.0F * attackerDamageTypeModifier);
        } catch (IllegalAccessException | InvocationTargetException | NoSuchMethodException e) {
            damage = (double)(attacker.getCombatDamage(weapon) * 1000.0F); // Assume it's 1.0F in the case that the reflection fails.
            e.printStackTrace();
        }

        if (attacker.isPlayer()) { // Multiply damage by up to 3x when at 100 weaponless fighting if it's a player.
            Skill weaponLess = attacker.getWeaponLessFightingSkill();
            double modifier = 1.0D + 2.0D * weaponLess.getKnowledge() / 100.0D;
            damage *= modifier;
        }

        if (damage < 10000.0D && attacker.getBonusForSpellEffect(Enchants.CRET_BEARPAW) > 0.0F) { // If they don't deal enough damage, apply bearpaws.
            damage += Server.getBuffedQualityEffect((double)(attacker.getBonusForSpellEffect(Enchants.CRET_BEARPAW) / 100.0F)) * 5000.0D;
        }

        float randomizer = (50.0F + Server.rand.nextFloat() * 50.0F) / 100.0F;
        damage *= (double)randomizer;
        return damage;
    }
    public static double getBaseWeaponDamage(Creature attacker, Creature opponent, Item weapon, boolean fullDamage){
        // Obtain Body Strength skill.
        Skill attStrengthSkill = getCreatureSkill(attacker, SkillList.BODY_STRENGTH);

        // Get weapon base damage
        double damage = Weapon.getModifiedDamageForWeapon(weapon, attStrengthSkill, fullDamage || (opponent != null && opponent.getTemplate().getTemplateId() == CreatureTemplateIds.WEAPON_TEST_DUMMY)) * 1000.0D;
        //if (!Servers.isThisAnEpicOrChallengeServer()) {
        if(!CombatHandledMod.useEpicBloodthirst){ // Additive Bloodthirst (standard)
            damage += (double)(weapon.getCurrentQualityLevel() / 100.0F * weapon.getSpellExtraDamageBonus());
        }

        damage += Server.getBuffedQualityEffect((double)(weapon.getCurrentQualityLevel() / 100.0F)) * (double)Weapon.getBaseDamageForWeapon(weapon) * 2400.0D;
        damage *= Weapon.getMaterialDamageBonus(weapon.getMaterial());
        if (opponent != null && !opponent.isPlayer() && opponent.isHunter()) {
            damage *= Weapon.getMaterialHunterDamageBonus(weapon.getMaterial());
        }

        damage *= (double)ItemBonus.getWeaponDamageIncreaseBonus(attacker, weapon);

        //if (Servers.isThisAnEpicOrChallengeServer()) {
        if(CombatHandledMod.useEpicBloodthirst){ // Multiplicative Bloodthirst (epic)
            damage *= (double)(1.0F + weapon.getCurrentQualityLevel() / 100.0F * weapon.getSpellExtraDamageBonus() / 30000.0F);
        }
        return damage;
    }
    public static double getDamageMultiplier(CombatHandled ch, Creature attacker, Creature opponent, Item weapon){
        double mult = 1.0D;
        // Multiply damage by 1.15 if they've been near enemy players for 1200 seconds (20 minutes)
        if (attacker.getEnemyPresense() > 1200 && opponent.isPlayer() && !weapon.isArtifact()) {
            mult *= 1.15D;
        }

        // Rod of Beguiling doesn't apply to us, so we ignore it since getting the effect requires reflection.
        /*if (!weapon.isArtifact() && this.hasRodEffect && opponent.isPlayer()) {
            mult *= 1.2D;
        }*/

        Vehicle vehicle = Vehicles.getVehicleForId(opponent.getVehicle());
        //boolean mildStack = false; //Fuck this variable
        if (weapon.isWeaponPolearm() && (vehicle != null && vehicle.isCreature() || opponent.isRidden() && weapon.isWeaponPierce())) {
            mult *= 1.7D; // 70% extra damage if using a polearm against a mount or mounted player.
        /*} else if (weapon.isArtifact()) {
            mildStack = true;*/
        } else if (attacker.getCultist() != null && attacker.getCultist().doubleWarDamage()) {
            mult *= 1.5D; // 50% extra damage if under the effects of hate war bonus.
            //mildStack = true;
        } else if (attacker.getDeity() != null && attacker.getDeity().warrior && attacker.getFaith() >= 40.0F && attacker.getFavor() >= 20.0F) {
            mult *= 1.25D; // 25% extra damage if following a warrior god with over 40 faith and 20 favor.
            //mildStack = true;
        }

        if (attacker.isPlayer()) {
            Skill attStrengthSkill = getCreatureSkill(attacker, SkillList.BODY_STRENGTH);

            if ((attacker.getFightStyle() != 2 || attStrengthSkill.getRealKnowledge() < 20.0D) && attStrengthSkill.getRealKnowledge() != 20.0D) {
                mult *= 1.0D + (attStrengthSkill.getRealKnowledge() - 20.0D) / 200.0D;
            }

            if (ch.currentStrength == 0) {
                Skill fstyle = getCreatureSkill(attacker, SkillList.FIGHT_DEFENSIVESTYLE);

                /*try {
                    fstyle = attacker.getSkills().getSkill(SkillList.FIGHT_DEFENSIVESTYLE);
                } catch (NoSuchSkillException var12) {
                    fstyle = attacker.getSkills().learn(SkillList.FIGHT_DEFENSIVESTYLE, 1.0F);
                }*/

                if (fstyle.skillCheck((double)(opponent.getBaseCombatRating() * 3.0F), 0.0D, ch.receivedFStyleSkill || opponent.isNoSkillFor(attacker), 10.0F, attacker, opponent) > 0.0D) {
                    ch.receivedFStyleSkill = true;
                    mult *= 0.8D;
                } else {
                    mult *= 0.5D;
                }
            }

            Skill fstyle;
            if (attacker.getStatus().getStamina() > 2000 && ch.currentStrength >= 1 && !ch.receivedFStyleSkill) {
                int num = SkillList.FIGHT_AGGRESSIVESTYLE;
                if (ch.currentStrength == 1) {
                    num = SkillList.FIGHT_NORMALSTYLE;
                }

                fstyle = getCreatureSkill(attacker, num);
                /*try {
                    fstyle = attacker.getSkills().getSkill(num);
                } catch (NoSuchSkillException var11) {
                    fstyle = attacker.getSkills().learn(num, 1.0F);
                }*/

                if (fstyle.skillCheck((double)(opponent.getBaseCombatRating() * 3.0F), 0.0D, ch.receivedFStyleSkill || opponent.isNoSkillFor(attacker), 10.0F, attacker, opponent) > 0.0D) {
                    ch.receivedFStyleSkill = true;
                    if (ch.currentStrength > 1) {
                        //mult *= 1.0D + Server.getModifiedFloatEffect(fstyle.getRealKnowledge() / 100.0D) / (double)(mildStack ? 8.0F : 4.0F);
                        mult *= 1.0D + Server.getModifiedFloatEffect(fstyle.getRealKnowledge() / 100.0D) / 4.0D;
                    }
                }
            }

            float knowl = 1.0F;

            try {
                fstyle = attacker.getSkills().getSkill(weapon.getPrimarySkill());
                knowl = (float)fstyle.getRealKnowledge();
            } catch (NoSuchSkillException ignored) { }

            if (knowl < 50.0F) {
                mult = 0.8D * mult + 0.2D * (double)(knowl / 50.0F) * mult;
            }
        } else {
            mult *= (double)(0.85F + (float)ch.currentStrength * 0.15F);
        }

        // Increase damage by up to 30% if the creature is part of a deed with a strong war bonus (through sacrifice)
        if (attacker.getCitizenVillage() != null && attacker.getCitizenVillage().getFaithWarBonus() > 0.0F) {
            mult *= (double)(1.0F + attacker.getCitizenVillage().getFaithWarBonus() / 100.0F);
        }

        // Increase damage by 10% if the creature is focused to level 4 or above.
        byte attackerFightLevel = CombatHandled.getCreatureFightLevel(attacker);
        if(attackerFightLevel >= 4){
            mult *= 1.1D;
        }

        return mult;
    }
}
