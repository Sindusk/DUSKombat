package mod.piddagoras.duskombat;

import com.wurmonline.server.Server;
import com.wurmonline.server.behaviours.Action;
import com.wurmonline.server.combat.SpecialMove;
import com.wurmonline.server.creatures.CombatHandler;
import com.wurmonline.server.creatures.Communicator;
import com.wurmonline.server.creatures.Creature;
import com.wurmonline.server.items.Item;
import com.wurmonline.server.skills.NoSuchSkillException;

import java.util.logging.Level;
import java.util.logging.Logger;

public class SpecialMoves {
    public static Logger logger = Logger.getLogger(SpecialMoves.class.getName());

    private static void selectSpecialMove(Creature creature, SpecialMove[] specialmoves) {
        if (creature.isAutofight() && Server.rand.nextInt(3) == 0) {
            int sm = Server.rand.nextInt(specialmoves.length);
            try {
                //float chance = getChanceToHit(this.creature.opponent, this.creature.getPrimWeapon());
                if (/*chance > 50.0f && */creature.getStatus().getStamina() > specialmoves[sm].getStaminaCost()) {
                    creature.setAction(new Action(creature, -1, creature.getWurmId(), (short)(197 + sm), creature.getPosX(), creature.getPosY(), creature.getPositionZ() + creature.getAltOffZ(), creature.getStatus().getRotation()));
                }
            } catch (Exception fe) {
                logger.log(Level.WARNING, creature.getName() + " failed:" + fe.getMessage(), fe);
            }
        }
    }
    public static void sendSpecialMoves(Creature creature) {
        if (/*creature.combatRound > 3 &&*/ !creature.getPrimWeapon().isBodyPart()) {
            try {
                double fightskill = creature.getSkills().getSkill(creature.getPrimWeapon().getPrimarySkill()).getKnowledge(0.0);
                if (fightskill > 19.0) {
                    SpecialMove[] specialmoves = SpecialMove.getMovesForWeaponSkillAndStance(creature, creature.getPrimWeapon(), (int)fightskill);
                    if (specialmoves.length > 0) {
                        creature.getCommunicator().sendSpecialMove((short) -1, "");
                        if (!creature.isAutofight()) {
                            for (int sx = 0; sx < specialmoves.length; ++sx) {
                                creature.getCommunicator().sendSpecialMove((short)(197 + sx), specialmoves[sx].getName());
                            }
                        }
                        selectSpecialMove(creature, specialmoves);
                    } else {
                        creature.getCommunicator().sendSpecialMove((short) -1, "N/A");
                    }
                    return;
                }
                creature.getCommunicator().sendSpecialMove((short) -1, "N/A");
            }
            catch (NoSuchSkillException nss) {
                creature.getCommunicator().sendSpecialMove((short) -1, "N/A");
            }
        } else {
            creature.getCommunicator().sendSpecialMove((short) -1, "N/A");
        }
    }
    public static boolean handleSpecialMove(Creature performer, Creature target, short action, float counter) {
        double fightskill;
        Item primweapon;
        byte srcStance;
        Communicator comm = performer.getCommunicator();
        if (target == performer) {
            comm.sendCombatNormalMessage("You need to fight a real enemy to perform special moves.");
            logger.fine(performer.getName() + " tried to attack themself and was told to attack a real enemy for SpecialMove: " + action);
            return true;
        }
        if (target.isInvulnerable() && performer.getPower() < 5) {
            comm.sendNormalServerMessage(target.getNameWithGenus() + " is protected by the gods. You may not attack " + target.getHimHerItString() + ".");
            return true;
        }
        if (!performer.isFighting() || target != performer.opponent) {
            return true;
        }
        if (target.isDead()) {
            return true;
        }
        if (target.opponent == null) {
            target.setOpponent(performer);
        }
        if ((primweapon = performer.getPrimWeapon()) == null) {
            comm.sendCombatNormalMessage("You need to wield a weapon in order to perform a special move.");
            return true;
        }
        try {
            fightskill = performer.getSkills().getSkill(primweapon.getPrimarySkill()).getKnowledge(0.0);
        }
        catch (NoSuchSkillException nss) {
            comm.sendCombatNormalMessage("You are not proficient enough with the " + primweapon.getName() + " to perform such a feat.");
            return true;
        }
        if (fightskill <= 19.0) {
            return true;
        }
        CombatHandler tgtCmbtHndl = target.getCombatHandler();
        CombatHandler srcCmbtHndl = performer.getCombatHandler();
        byte tgtStance = tgtCmbtHndl.getCurrentStance();
        if (CombatHandler.isStanceOpposing(tgtStance, srcStance = srcCmbtHndl.getCurrentStance()) || CombatHandler.isStanceParrying(tgtStance, srcStance)) {
            comm.sendCombatNormalMessage(target.getNameWithGenus() + " is protecting that area.");
            return true;
        }
        SpecialMove[] specialmoves = SpecialMove.getMovesForWeaponSkillAndStance(performer, primweapon, (int)fightskill);
        if (specialmoves.length <= 0) {
            return true;
        }
        if (target.isDead()) {
            return true;
        }
        boolean done = false;
        if (counter == 1.0f) {
            /*if (performer.combatRound < 3) {
                comm.sendCombatNormalMessage("You have not moved into position yet.");
                return true;
            }*/
            try {
                SpecialMove tempmove = specialmoves[action - 197];
                if (tempmove != null) {
                    performer.specialMove = tempmove;
                    performer.sendActionControl(tempmove.getName(), true, tempmove.getSpeed() * 10);
                } else {
                    performer.specialMove = null;
                    comm.sendCombatNormalMessage("No such move available right now.");
                    done = true;
                }
            }
            catch (Exception e) {
                comm.sendCombatNormalMessage("No such move available right now.");
                done = true;
            }
            return done;
        }
        SpecialMove tempmove = performer.specialMove;
        if (tempmove == null) {
            return true;
        }
        if (tempmove.getWeaponType() != -1 && srcCmbtHndl.getType(primweapon, true) != tempmove.getWeaponType()) {
            comm.sendCombatNormalMessage("You can't perform a " + tempmove.getName() + " with the " + performer.getPrimWeapon().getName() + ".");
            return true;
        }
        if (counter < (float)tempmove.getSpeed()) {
            return false;
        }
        if (performer.getStatus().getStamina() < tempmove.getStaminaCost()) {
            comm.sendCombatNormalMessage("You have no stamina left to perform a " + tempmove.getName() + ".");
            return true;
        }
        try {
            double eff = performer.getSkills().getSkill(primweapon.getPrimarySkill()).skillCheck(tempmove.getDifficulty(), 0.0, primweapon.isWeaponBow(), 5.0f, performer, target);
            if (eff > 0.0) {
                comm.sendCombatNormalMessage("You try a " + tempmove.getName() + ".");
                tempmove.doEffect(performer, performer.getPrimWeapon(), performer.opponent, Math.max(20.0, eff));
            } else {
                performer.getStatus().modifyStamina((- tempmove.getStaminaCost()) / 3);
                comm.sendCombatNormalMessage("You try a " + tempmove.getName() + " but miss.");
                Server.getInstance().broadCastAction(performer.getNameWithGenus() + " tries a " + tempmove.getName() + " but misses.", performer, 2, true);
            }
        } catch (NoSuchSkillException nss) {
            comm.sendCombatNormalMessage("You fail to perform the attack.");
            logger.log(Level.WARNING, performer.getName() + " trying spec move with " + performer.getPrimWeapon().getName());
            return true;
        }
        return true;
    }
}
