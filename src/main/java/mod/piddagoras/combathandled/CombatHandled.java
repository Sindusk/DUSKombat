package mod.piddagoras.combathandled;

import com.sun.istack.internal.Nullable;
import com.wurmonline.server.Features;
import com.wurmonline.server.Server;
import com.wurmonline.server.behaviours.Action;
import com.wurmonline.server.behaviours.ActionEntry;
import com.wurmonline.server.behaviours.Actions;
import com.wurmonline.server.behaviours.NoSuchActionException;
import com.wurmonline.server.combat.CombatMove;
import com.wurmonline.server.combat.Weapon;
import com.wurmonline.server.creatures.AttackAction;
import com.wurmonline.server.creatures.CombatHandler;
import com.wurmonline.server.creatures.Creature;
import com.wurmonline.server.items.Item;
import com.wurmonline.server.skills.NoSuchSkillException;
import com.wurmonline.server.skills.Skill;
import com.wurmonline.server.utils.CreatureLineSegment;
import com.wurmonline.shared.util.MulticolorLineSegment;

import java.util.*;
import java.util.logging.Level;
/*
Stances
6	&	7	&	1
5	&	0	&	2
4	&	10	&	3
DEFEND_HIGH : 12;
DEFEND_LEFT : 14;
DEFEND_LOW : 11;
DEFEND_RIGHT : 13;

Notes:
Parry bonuses granted by stances (returns 1.0f by default):
	Defensive stances grant a parry bonus by returning 0.8f
	Opposing stances grant a parry bonus by returning 0.9f
	Stances 8 and 9 (unused?) are never granted parry bonuses against any stance.
	Opposing pairs:
		1 & 6
		4 & 3
		5 & 2
		7 & 7
		0 & 0
		10 & 10
	Note that defending left defends AGAINST left stances, not the left side of your body,
	although messages sent to clients may simply indicated attacker-relative targetting.

	Soft spots increase crit chance.
	Soft spot list:
		0 is strong against 1 & 6
		5 is strong against 1 & 4
		2 is strong against 6 & 3
		1 is strong against 4
		6 is strong against 3 & 2
		3 is strong against 7
		4 is strong against 10
		7 and 10 have no soft spot bonuses.
*/

/*
ActionEntries
    public static final short ATTACK_UPPER_LEFT_HARD = 287;
    public static final short ATTACK_UPPER_LEFT_NORMAL = 288;
    public static final short ATTACK_UPPER_LEFT_QUICK = 289;
    public static final short ATTACK_MID_LEFT_HARD = 290;
    public static final short ATTACK_MID_LEFT_NORMAL = 291;
    public static final short ATTACK_MID_LEFT_QUICK = 292;
    public static final short ATTACK_LOW_LEFT_HARD = 293;
    public static final short ATTACK_LOW_LEFT_NORMAL = 294;
    public static final short ATTACK_LOW_LEFT_QUICK = 295;
    public static final short ATTACK_LOW_HARD = 296;
    public static final short ATTACK_LOW_NORMAL = 297;
    public static final short ATTACK_LOW_QUICK = 298;
    public static final short ATTACK_HIGH_HARD = 299;
    public static final short ATTACK_HIGH_NORMAL = 300;
    public static final short ATTACK_HIGH_QUICK = 301;
    public static final short ATTACK_CENTER_HARD = 302;
    public static final short ATTACK_CENTER_NORMAL = 303;
    public static final short ATTACK_CENTER_QUICK = 304;
    public static final short ATTACK_UPPER_RIGHT_HARD = 305;
    public static final short ATTACK_UPPER_RIGHT_NORMAL = 306;
    public static final short ATTACK_UPPER_RIGHT_QUICK = 307;
    public static final short ATTACK_MID_RIGHT_HARD = 308;
    public static final short ATTACK_MID_RIGHT_NORMAL = 309;
    public static final short ATTACK_MID_RIGHT_QUICK = 310;
    public static final short ATTACK_LOW_RIGHT_HARD = 311;
    public static final short ATTACK_LOW_RIGHT_NORMAL = 312;
    public static final short ATTACK_LOW_RIGHT_QUICK = 313;
    public static final short DEFEND_HIGH = 314;
    public static final short DEFEND_LEFT = 315;
    public static final short DEFEND_LOW = 316;
    public static final short DEFEND_RIGHT = 317;
new ActionEntry(287, 8, "Left Hard", "attacking", new int[]{3, 12, 14, 17}, 6, false),
new ActionEntry(288, 8, "Left Normal", "attacking", new int[]{3, 12, 14, 17}, 6, false),
new ActionEntry(289, 8, "Left quick", "attacking", new int[]{3, 12, 14, 17}, 6, false),
new ActionEntry(290, 8, "Left Hard", "attacking", new int[]{3, 14, 17}, 6, false),
new ActionEntry(291, 8, "Left Normal", "attacking", new int[]{3, 14, 17}, 6, false),
new ActionEntry(292, 8, "Left Quick", "attacking", new int[]{3, 14, 17}, 6, false),
new ActionEntry(293, 8, "Left Hard", "attacking", new int[]{3, 13, 14, 17}, 6, false),
new ActionEntry(294, 8, "Left Normal", "attacking", new int[]{3, 13, 14, 17}, 6, false),
new ActionEntry(295, 8, "Left Quick", "attacking", new int[]{3, 13, 14, 17}, 6, false),
new ActionEntry(296, 8, "Hard", "attacking", new int[]{3, 13, 17}, 6, false),
new ActionEntry(297, 8, "Normal", "attacking", new int[]{3, 13, 17}, 6, false),
new ActionEntry(298, 8, "Quick", "attacking", new int[]{3, 13, 17}, 6, false),
new ActionEntry(299, 8, "Hard", "attacking", new int[]{3, 12, 17}, 6, false),
new ActionEntry(300, 8, "Normal", "attacking", new int[]{3, 12, 17}, 6, false),
new ActionEntry(301, 8, "Quick", "attacking", new int[]{3, 12, 17}, 6, false),
new ActionEntry(302, 8, "Hard", "attacking", new int[]{3, 17}, 6, false),
new ActionEntry(303, 8, "Normal", "attacking", new int[]{3, 17}, 6, false),
new ActionEntry(304, 8, "Quick", "attacking", new int[]{3, 17}, 6, false),
new ActionEntry(305, 8, "Right Hard", "attacking", new int[]{3, 12, 15, 17}, 6, false),
new ActionEntry(306, 8, "Right Normal", "attacking", new int[]{3, 12, 15, 17}, 6, false),
new ActionEntry(307, 8, "Right Quick", "attacking", new int[]{3, 12, 15, 17}, 6, false),
new ActionEntry(308, 8, "Right Hard", "attacking", new int[]{3, 15, 17}, 6, false),
new ActionEntry(309, 8, "Right Normal", "attacking", new int[]{3, 15, 17}, 6, false),
new ActionEntry(310, 8, "Right Quick", "attacking", new int[]{3, 15, 17}, 6, false),
new ActionEntry(311, 8, "Right Hard", "attacking", new int[]{3, 13, 15, 17}, 6, false),
new ActionEntry(312, 8, "Right Normal", "attacking", new int[]{3, 13, 15, 17}, 6, false),
new ActionEntry(313, 8, "Right Quick", "attacking", new int[]{3, 13, 15, 17}, 6, false),
new ActionEntry(314, 8, "High", "defending", new int[]{16}, 6, false),
new ActionEntry(315, 8, "Left", "defending", new int[]{16}, 6, false),
new ActionEntry(316, 8, "Low", "defending", new int[]{16}, 6, false),
new ActionEntry(317, 8, "Right", "defending", new int[]{16}, 6, false)
*/
public class CombatHandled{
    /*public static HashMap<Creature, CombatHandled> combatMap = new HashMap<>();

    public static boolean attack(Creature attacker, Creature opponent, int combatCounter, boolean opportunity, float actionCounter, Action act) {
        if(combatMap.containsKey(attacker)){
            return combatMap.get(attacker).attackLoop(attacker, opponent, combatCounter, opportunity, actionCounter, act);
        }else{
            CombatHandled ch = new CombatHandled();
            boolean result = ch.attackLoop(attacker, opponent, combatCounter, opportunity, actionCounter, act);
            return result;
        }
    }

    protected float lastTimeStamp=1.0f;
    protected byte currentStance=15; //Need to look into what stances are.
    protected byte currentStrength=1;//Depends on aggressive/normal/defensive style active currently.
    protected byte opportunityAttacks=0;//Need to look closely at how opportunities work.
	protected HashSet<Item> secattacks;//Probably updates in other methods besides attackLoop.
    protected static final byte[] standardSoftSpots = new byte[]{6, 1};
    protected static final byte[] lowCenterSoftSpots = new byte[]{5, 2};
    protected static final byte[] upperCenterSoftSpots = new byte[]{4, 5};
    protected static final byte[] midLeftSoftSpots = new byte[]{1, 4};
    protected static final byte[] midRightSoftSpots = new byte[]{6, 3};
    protected static final byte[] upperLeftSoftSpots = new byte[]{3, 2};
    protected static final byte[] upperRightSoftSpots = new byte[]{4};
    protected static final byte[] lowerRightSoftSpots = new byte[]{7};
    protected static final byte[] lowerLeftSoftSpots = new byte[]{10};
    protected static final byte[] emptyByteArray = new byte[0];


    protected boolean attackLoop(Creature attacker, Creature opponent, int combatCounter, boolean opportunity, float actionCounter, Action act){
        boolean isDead=false;
        stillAttacking : {
            opponent.addAttacker(attacker);
            float delta = Math.max(0.0f, actionCounter - this.lastTimeStamp);
            if(delta<0.1) return false;
            lastTimeStamp=actionCounter;
            Item weapon = attacker.getPrimWeapon();
            if (opponent.isPlayer()) {
                attacker.setSecondsToLogout(300);
            }else{
                attacker.setSecondsToLogout(180);
            }
            if (CombatHandler.prerequisitesFail(attacker, opponent, opportunity, weapon)) return true;
            if (act != null && act.justTickedSecond()) {
                attacker.getCommunicator().sendCombatStatus(CombatHandled.getDistdiff(weapon, attacker, opponent), this.getFootingModifier(attacker, weapon, opponent), this.currentStance);//Should maybe Hijack this and send different data.
            }
            if (this.isProne() || this.isOpen()) return false;

            attacker.opponentCounter = 30;//?

            //Check for free attack by opponent on attacker because movement or opportunity, only happens when combat is initiated.
            if (actionCounter != 1.0f || opportunity || !attacker.isMoving() || opponent.isMoving() || opponent.target != attacker.getWurmId()){
                opponent.attackTarget();
                if (opponent.opponent != attacker) break stillAttacking;
                attacker.sendToLoggers("Opponent strikes first", (byte) 2);
                ArrayList<MulticolorLineSegment> segments2 = new ArrayList<>();
                segments2.add(new CreatureLineSegment(opponent));
                segments2.add(new MulticolorLineSegment(" strike ", (byte) 0));
                segments2.add(new CreatureLineSegment(attacker));
                segments2.add(new MulticolorLineSegment(" as " + attacker.getHeSheItString() + " approaches!", (byte) 0));
                opponent.getCommunicator().sendColoredMessageCombat(segments2);
                segments2.get(1).setText(" strikes ");
                segments2.get(1).setText(" as you approach. ");
                attacker.getCommunicator().sendColoredMessageCombat(segments2);
                isDead = CombatHandled.attack(opponent, attacker, combatCounter, true, 2.0f, null);
                break stillAttacking;
            }

            if (opportunity) {
                this.opportunityAttacks = (byte)(this.opportunityAttacks + 1);
                attacker.sendToLoggers("YOU OPPORTUNITY", (byte) 2);
                opponent.sendToLoggers(attacker.getName() + " OPPORTUNITY", (byte) 2);
                if (opponent.spamMode()) {
                    //Warn the opponent that they gave an opportunity.
                    ArrayList<MulticolorLineSegment> segments = new ArrayList<>();
                    segments.add(new MulticolorLineSegment("You open yourself to an attack from ", (byte) 7));
                    segments.add(new CreatureLineSegment(attacker));
                    segments.add(new MulticolorLineSegment(".", (byte) 7));
                    opponent.getCommunicator().sendColoredMessageCombat(segments);
                }
                if (attacker.spamMode()) {
                    //Tell us we got an opportunity.
                    ArrayList<MulticolorLineSegment> segments = new ArrayList<>();
                    segments.add(new CreatureLineSegment(opponent));
                    segments.add(new MulticolorLineSegment(" opens " + opponent.getHimHerItString() + "self up to an easy attack.", (byte) 3));
                    attacker.getCommunicator().sendColoredMessageCombat(segments);
                }
                if (Server.rand.nextInt(3) == 0 && (secweapons = attacker.getSecondaryWeapons()).length > 0) {
                    //Pick a random weapon, 2/3 chance for primary, 1/3 for secondary (selected at random).
                    weapon = secweapons[Server.rand.nextInt(secweapons.length)];
                }
                //Attack with the weapon
                isDead = this.attack(opponent, weapon, false);
                break stillAttacking;
            }

            boolean performedAttack = false;
            if (attacker.combatRound > 1) {
                //Make sure we've actually been in combat a round.
                Item[] secweapons;
                for (Item lSecweapon : secweapons = attacker.getSecondaryWeapons()) {
                    if (attacker.opponent == null) continue; //this should be impossible, since creature.opponent==opponent is probably always true.
                    if (this.secattacks == null) {
                        this.secattacks = new HashSet<Item>();
                    }
                    if (this.secattacks.contains(lSecweapon) || (lSecweapon.getTemplateId() == 12 || lSecweapon.getTemplateId() == 17)) continue;
                    float time = this.getSpeed(attacker, lSecweapon);
                    float timer = attacker.addToWeaponUsed(lSecweapon, delta);
                    if (isDead || attacker.combatRound % 2 != 1 || timer <= time) continue; //Every other round, seems bursty.
                    attacker.deductFromWeaponUsed(lSecweapon, time);
                    attacker.sendToLoggers("YOU SECONDARY " + lSecweapon.getName(), 2);
                    opponent.sendToLoggers(attacker.getName() + " SECONDARY " + lSecweapon.getName() + "(" + lSecweapon.getWurmId() + ")", 2);
                    attacker.setHugeMoveCounter(2 + Server.rand.nextInt(4));//Probably does nothing, but leaving in case.
                    isDead = this.attack(opponent, lSecweapon, true);
                    performedAttack = true;
                    this.secattacks.add(lSecweapon);
                }
            }
            float time = this.getSpeed(attacker, weapon);
            float timer = attacker.addToWeaponUsed(weapon, delta);
            if !(isDead || timer <= time){
                attacker.deductFromWeaponUsed(weapon, time);
                attacker.sendToLoggers("YOU PRIMARY " + weapon.getName(), (byte) 2);
                opponent.sendToLoggers(attacker.getName() + " PRIMARY " + weapon.getName(), (byte) 2);
                isDead = this.attack(opponent, weapon, false);
                performedAttack = true;
                if (!attacker.isPlayer() || act == null || !act.justTickedSecond()) break stillAttacking;
                this.checkStanceChange(attacker, opponent);
                break stillAttacking;
            }
            if (!(performedAttack || isDead || attacker.isPlayer()
                    || attacker.getTarget() == null || attacker.getLayer() != opponent.getLayer()
                    || this.checkStanceChange(attacker, opponent) || (cmoves = attacker.getCombatMoves()).length <= 0)) {
                //Check to use an npc special move.
                for (int lCmove : cmoves) {
                    CombatMove c = CombatMove.getCombatMove(lCmove);
                    if (Server.rand.nextFloat() >= c.getRarity() || attacker.getHugeMoveCounter() != 0) continue;
                    attacker.sendToLoggers("YOU COMBAT MOVE", (byte) 2);
                    opponent.sendToLoggers(attacker.getName() + " COMBAT MOVE", (byte) 2);
                    attacker.setHugeMoveCounter(2 + Server.rand.nextInt(4));
                    c.perform(attacker);
                    break;
                }
            }
        }
        return isDead;
    }

    //Copy pasta
    protected float getFootingModifier(Creature attacker, Item weapon, Creature opponent){
        short[] steepness = Creature.getTileSteepness(attacker.getCurrentTile().tilex, attacker.getCurrentTile().tiley, attacker.isOnSurface());
        float footingMod = 0.0f;
        float heightDiff;
        heightDiff = Math.max(-1.45f, attacker.getStatus().getPositionZ() + attacker.getAltOffZ()) - Math.max(-1.45f, opponent.getStatus().getPositionZ() + opponent.getAltOffZ());
        if ((double)heightDiff > 0.5) {
            footingMod = (float)((double)footingMod + 0.1);
        } else if ((double)heightDiff < -0.5) {
            footingMod -= 0.1f;
        }
        if (attacker.isSubmerged()) {
            return 1.0f;
        }
        if (attacker.getVehicle() == -10) {
            if (weapon != null && opponent.getVehicle() != -10 && weapon.isTwoHanded() && !weapon.isWeaponBow()) {
                footingMod += 0.3f;
            }
            if (attacker.getStatus().getPositionZ() <= -1.45f) {
                return 0.2f + footingMod;
            }
            if (attacker.isPlayer() && (steepness[1] > 20 || steepness[1] < -20)) {
                Skill bcskill = null;
                try {
                    bcskill = attacker.getSkills().getSkill(104);
                }
                catch (NoSuchSkillException nss) {
                    bcskill = attacker.getSkills().learn(104, 1.0f);
                }
                if (bcskill != null && bcskill.skillCheck(Math.abs(Math.max(Math.min(steepness[1], 99), -99)), attacker.fightlevel * 10, true, 1.0f) > 0.0) {
                    return 1.0f + footingMod;
                }
                if (steepness[1] > 40 || steepness[1] < -40) {
                    if (steepness[1] > 60 || steepness[1] < -60) {
                        if (steepness[1] > 80 || steepness[1] < -80) {
                            if (steepness[1] > 100 || steepness[1] < -100) {
                                return 0.2f + footingMod;
                            }
                            return 0.4f + footingMod;
                        }
                        return 0.6f + footingMod;
                    }
                    return 0.8f + footingMod;
                }
                return 0.9f + footingMod;
            }
        } else if (opponent.isSubmerged()) {
            footingMod = 0.0f;
        }
        return 1.0f + footingMod;
    }

    //Copy pasta
    protected float getSpeed(Creature attacker, Item weapon) {
        float timeMod = 0.5f;
        if (this.currentStrength == 0) {
            timeMod = 1.5f;
        }
        float calcspeed = this.getWeaponSpeed(weapon);
        calcspeed += timeMod;
        if (weapon.getSpellSpeedBonus() != 0.0f) {
            calcspeed = (float)((double)calcspeed - 0.5 * (double)(weapon.getSpellSpeedBonus() / 100.0f));
        }
        if (!weapon.isArtifact() && attacker.getBonusForSpellEffect(39) > 0.0f) {
            calcspeed -= 0.5f; //Frantic Charge
        }
        if (weapon.isTwoHanded() && this.currentStrength == 3) {
            calcspeed *= 0.9f; //Aggressive stance
        }
        if (!Features.Feature.METALLIC_ITEMS.isEnabled() && weapon.getMaterial() == 57) {
            calcspeed *= 0.9f; //Glimmersteel
        }
        if (this.creature.getStatus().getStamina() < 2000) {
            calcspeed += 1.0f; //Low Stamina
        }
        calcspeed = (float)((double)calcspeed - attacker.getMovementScheme().getWebArmourMod() * 10.0);//Don't understand this.
        if (attacker.hasSpellEffect((byte) 66)) {
            calcspeed *= 2.0f; //Web armor
        }
        return Math.max(2.0f, calcspeed);
    }

    //Copy pasta
    protected float getWeaponSpeed(Creature attacker, Item _weapon) {
        float flspeed = 20.0f;
        float knowl = 0.0f;
        int spskillnum = 10052;
        if (_weapon.isBodyPartAttached()) {
            flspeed = attacker.getBodyWeaponSpeed(_weapon);
        } else {
            flspeed = Weapon.getBaseSpeedForWeapon(_weapon);
            try {
                spskillnum = _weapon.getPrimarySkill();
            }
            catch (NoSuchSkillException noSuchSkillException) {
                // empty catch block
            }
        }
        try {
            Skill wSkill = attacker.getSkills().getSkill(spskillnum);
            knowl = (float)wSkill.getRealKnowledge();
        }
        catch (NoSuchSkillException wSkill) {
            // empty catch block
        }
        if (!attacker.isGhost()) {
            flspeed -= flspeed * 0.1f * knowl / 100.0f; //Weapon skill increases attack speed.
        }
        return flspeed;
    }









    //Copy pasta, only returns true for autofighting or AI controlled.
    protected void checkStanceChange(Creature attacker, Creature opponent){
        if (attacker.isFighting()) {
            if (attacker.isPlayer()) {
                if (attacker.isAutofight() && Server.rand.nextInt(10) == 0) {
                    this.selectStance(attacker, opponent);
                    return true;
                }
            } else if (Server.rand.nextInt(5) == 0) {
                this.selectStance(attacker, opponent);
                return true;
            }
        }
        return false;
    }

    //Copy pasta
    protected void selectStance(Creature defender, Creature opponent) {
        boolean selectNewStance = false;
        try {
            if (!defender.getCurrentAction().isDefend() && !defender.getCurrentAction().isStanceChange()) {
                selectNewStance = true;
            }
        }
        catch (NoSuchActionException nsa) {
            selectNewStance = true;
        }
        if (!defender.isPlayer() && selectNewStance && Server.rand.nextInt((int)(11.0f - Math.min(10.0f, (float)defender.getAggressivity() * defender.getStatus().getAggTypeModifier() / 10.0f))) != 0) {
            selectNewStance = false;
        }
        if (selectNewStance) {
            selectStanceList.clear();
            float mycr = -1.0f;
            float oppcr = -1.0f;
            float knowl = -1.0f;
            if (defender.isFighting()) {
                if (defender.mayRaiseFightLevel() && defender.getMindLogical().getKnowledge(0.0) > 7.0) {
                    if (defender.isPlayer() || Server.rand.nextInt(100) < 30) {
                        selectNewStance = false;
                        selectStanceList.add(Actions.actionEntrys[340]);
                    } else {
                        selectStanceList.add(Actions.actionEntrys[340]);
                    }
                }
                if (defender.isPlayer() && this.getSpeed(defender.getPrimWeapon()) > (float)Server.rand.nextInt(10)) {
                    selectNewStance = false;
                }
                if (selectNewStance) {
                    mycr = combatMap.get(defender).getCombatRating(opponent, defender.getPrimWeapon(), false);//COMBAT RATING
                    oppcr = combatMap.get(opponent).getCombatRating(defender, opponent.getPrimWeapon(), false);
                    knowl = this.getCombatKnowledgeSkill();
                    if (knowl > 50.0f) {
                        selectStanceList.addAll(standardDefences);
                    }
                    if (!defender.isPlayer()) {
                        knowl += 20.0f;
                    }
                    selectStanceList.addAll(combatMap.get(defender).getHighAttacks(null, true, opponent, mycr, oppcr, knowl));
                    selectStanceList.addAll(combatMap.get(defender).getMidAttacks(null, true, opponent, mycr, oppcr, knowl));
                    selectStanceList.addAll(combatMap.get(defender).getLowAttacks(null, true, opponent, mycr, oppcr, knowl));
                }
            }
            if (selectStanceList.size() > 0) {
                this.selectStanceFromList(defender, opponent, mycr, oppcr, knowl);
            }
            if (!defender.isPlayer() && Server.rand.nextInt(10) == 0) {
                int randInt = Server.rand.nextInt(100);
                if ((float)randInt <= Math.max(10.0f, (float)(defender.getAggressivity() - 20) * defender.getStatus().getAggTypeModifier())) {
                    if (defender.getFightStyle() != 1) {
                        ArrayList<MulticolorLineSegment> segments = new ArrayList<MulticolorLineSegment>();
                        segments.add(new CreatureLineSegment(defender));
                        segments.add(new MulticolorLineSegment(" suddenly goes into a frenzy.", (byte) 0));
                        opponent.getCommunicator().sendColoredMessageCombat(segments);
                        defender.setFightingStyle(1); //Aggressive stance
                    }
                } else if ((float)randInt > Math.min(90.0f, ((float)defender.getAggressivity() * defender.getStatus().getAggTypeModifier() + 20.0f) * defender.getStatus().getAggTypeModifier())) {
                    if (defender.getFightStyle() != 2) {
                        ArrayList<MulticolorLineSegment> segments = new ArrayList<MulticolorLineSegment>();
                        segments.add(new CreatureLineSegment(defender));
                        segments.add(new MulticolorLineSegment(" cowers.", (byte) 0));
                        opponent.getCommunicator().sendColoredMessageCombat(segments);
                        defender.setFightingStyle(2); //Defensive stance
                    }
                } else {
                    if (defender.getFightStyle() == 1) {
                        ArrayList<MulticolorLineSegment> segments = new ArrayList<MulticolorLineSegment>();
                        segments.add(new CreatureLineSegment(defender));
                        segments.add(new MulticolorLineSegment(" calms down a bit.", (byte) 0));
                        opponent.getCommunicator().sendColoredMessageCombat(segments);
                    } else if (defender.getFightStyle() == 2) {
                        ArrayList<MulticolorLineSegment> segments = new ArrayList<MulticolorLineSegment>();
                        segments.add(new CreatureLineSegment(defender));
                        segments.add(new MulticolorLineSegment(" seems a little more brave now.", (byte) 0));
                        opponent.getCommunicator().sendColoredMessageCombat(segments);
                    }
                    if (defender.getFightStyle() != 0) {
                        defender.setFightingStyle((byte) 0); //Normal stance
                    }
                }
            }
        }
    }

    //Copy pasta
    protected final void selectStanceFromList(Creature defender, Creature opponent, float mycr, float oppcr, float knowl) {
        ActionEntry e = null;
        if (defender.isPlayer() || defender.getMindLogical().getKnowledge(0.0) > 17.0) {
            if (oppcr - mycr > 3.0f) {
                if (Server.rand.nextInt(2) == 0) {
                    if (defender.mayRaiseFightLevel()) {
                        e = Actions.actionEntrys[340];
                    }
                } else if (defender.opponent == opponent && (e = CombatHandled.getDefensiveActionEntry(combatMap.get(opponent).currentStance)) == null) {
                    e = CombatHandled.getOpposingActionEntry(combatMap.get(opponent).currentStance);
                }
            }
            if (e == null) {
                if (defender.combatRound > 2 && Server.rand.nextInt(2) == 0) {
                    if (defender.mayRaiseFightLevel()) {
                        e = Actions.actionEntrys[340];
                    }
                } else if (mycr - oppcr > 2.0f || combatMap.get(defender).getSpeed(defender.getPrimWeapon()) < 3.0f) {
                    if (CombatHandled.existsBetterOffensiveStance(combatMap.get(defender).currentStance, combatMap.get(opponent).currentStance) && (e = CombatHandled.changeToBestOffensiveStance(combatMap.get(defender).currentStance, combatMap.get(opponent).currentStance)) == null) {
                        e = CombatHandled.getNonDefensiveActionEntry(combatMap.get(opponent).currentStance);
                    }
                } else if (mycr >= oppcr) {
                    if (defender.getStatus().damage < opponent.getStatus().damage) {
                        if (CombatHandled.existsBetterOffensiveStance(combatMap.get(defender).currentStance, combatMap.get(opponent).currentStance) && (e = CombatHandled.changeToBestOffensiveStance(combatMap.get(defender).currentStance, combatMap.get(opponent).currentStance)) == null) {
                            e = CombatHandled.getNonDefensiveActionEntry(combatMap.get(opponent).currentStance);
                        }
                    } else {
                        e = CombatHandled.getDefensiveActionEntry(combatMap.get(opponent).currentStance);
                        if (e == null) {
                            e = CombatHandled.getOpposingActionEntry(combatMap.get(opponent).currentStance);
                        }
                    }
                }
            }
        } else if (e == null) {
            if (!Server.rand.nextBoolean() || defender.getShield() == null) {
                int num = Server.rand.nextInt(selectStanceList.size());
                e = selectStanceList.get(num);
                e = Actions.actionEntrys[e.getNumber()];
            } else {
                e = Actions.actionEntrys[105];
            }
        }
        if (e != null && e.getNumber() > 0) {
            try {
                if (Creature.rangeTo(defender, opponent) <= e.getRange()) {//Most of this branching is redundant.
                    if (e.getNumber() == 105) {
                        defender.setAction(new Action(defender, -1, opponent.getWurmId(), e.getNumber(), defender.getPosX(), defender.getPosY(), defender.getPositionZ() + defender.getAltOffZ(), defender.getStatus().getRotation()));
                    } else if (e.isStanceChange() && e.getNumber() != 340) {
                        if (CombatHandled.getStanceForAction(e) != this.currentStance) {
                            defender.setAction(new Action(defender, -1, opponent.getWurmId(), e.getNumber(), defender.getPosX(), defender.getPosY(), defender.getPositionZ() + defender.getAltOffZ(), defender.getStatus().getRotation()));
                        }
                    } else if (defender.mayRaiseFightLevel() && e.getNumber() == 340) {
                        defender.setAction(new Action(defender, -1, opponent.getWurmId(), e.getNumber(), defender.getPosX(), defender.getPosY(), defender.getPositionZ() + defender.getAltOffZ(), defender.getStatus().getRotation()));
                    } else {
                        defender.setAction(new Action(defender, -1, opponent.getWurmId(), e.getNumber(), defender.getPosX(), defender.getPosY(), defender.getPositionZ() + defender.getAltOffZ(), defender.getStatus().getRotation()));
                    }
                } else {
                    logger.log(Level.INFO, defender.getName() + " too far away for stance " + e.getActionString() + " attacking " + opponent.getName() + " with range " + Creature.rangeTo(defender, opponent));
                }
            }
            catch (Exception fe) {
                logger.log(Level.WARNING, defender.getName() + " failed:" + fe.getMessage(), fe);
            }
        }
    }

    //Copy pasta
    protected boolean isOpen() {
        return this.currentStance == 9;
    }

    //Copy pasta
    protected boolean isProne() {
        return this.currentStance == 8;
    }

    //Copy pasta
    protected static final byte getStanceForAction(ActionEntry entry) {
        if (entry.isAttackHigh()) {
            if (entry.isAttackLeft()) {
                return 6;
            }
            if (entry.isAttackRight()) {
                return 1;
            }
            return 7;
        }
        if (entry.isAttackLow()) {
            if (entry.isAttackLeft()) {
                return 4;
            }
            if (entry.isAttackRight()) {
                return 3;
            }
            return 10;
        }
        if (entry.isAttackLeft()) {
            return 5;
        }
        if (entry.isAttackRight()) {
            return 2;
        }
        if (entry.isDefend()) {
            switch (entry.getNumber()) {
                case 314: {
                    return 12;
                }
                case 315: {
                    return 14;
                }
                case 316: {
                    return 11;
                }
                case 317: {
                    return 13;
                }
            }
            return 0;
        }
        return 0;
    }

    //Copy pasta
    protected static final ActionEntry getDefensiveActionEntry(byte opponentStance) {
        ListIterator<ActionEntry> it = selectStanceList.listIterator();
        while (it.hasNext()) {
            ActionEntry e = it.next();
            if (!CombatHandler.isStanceParrying(CombatHandler.getStanceForAction(e = Actions.actionEntrys[e.getNumber()]), opponentStance) || CombatHandler.isAtSoftSpot(CombatHandler.getStanceForAction(e), opponentStance)) continue;
            return e;
        }
        return null;
    }

    //Copy pasta
    protected static final ActionEntry getOpposingActionEntry(byte opponentStance) {
        ListIterator<ActionEntry> it = selectStanceList.listIterator();
        while (it.hasNext()) {
            ActionEntry e = it.next();
            if (!CombatHandler.isStanceOpposing(CombatHandler.getStanceForAction(e = Actions.actionEntrys[e.getNumber()]), opponentStance) || CombatHandler.isAtSoftSpot(CombatHandler.getStanceForAction(e), opponentStance)) continue;
            return e;
        }
        return null;
    }

    //Copy pasta
    protected static final ActionEntry getNonDefensiveActionEntry(byte opponentStance) {
        for (int x = 0; x < selectStanceList.size(); ++x) {
            int num = Server.rand.nextInt(selectStanceList.size());
            ActionEntry e = selectStanceList.get(num);
            if (CombatHandler.isStanceParrying(CombatHandler.getStanceForAction(e = Actions.actionEntrys[e.getNumber()]), opponentStance) || CombatHandler.isStanceOpposing(CombatHandler.getStanceForAction(e), opponentStance) || CombatHandler.isAtSoftSpot(CombatHandler.getStanceForAction(e), opponentStance)) continue;
            return e;
        }
        return null;
    }

    //Copy pasta
    protected List<ActionEntry> getHighAttacks(@Nullable Item weapon, boolean auto, Creature opponent, float mycr, float oppcr, float primweaponskill) {
        LinkedList<ActionEntry> tempList = new LinkedList<>();
        if (primweaponskill > (float)CombatHandler.getAttackSkillCap((short) 300)) {
            this.addToList(tempList, weapon, 300, opponent, mycr, oppcr, primweaponskill);
        }
        if (primweaponskill > (float)CombatHandler.getAttackSkillCap((short) 288)) {
            this.addToList(tempList, weapon, 288, opponent, mycr, oppcr, primweaponskill);
        }
        if (primweaponskill > (float)CombatHandler.getAttackSkillCap((short) 306)) {
            this.addToList(tempList, weapon, 306, opponent, mycr, oppcr, primweaponskill);
        }
        if (!auto && tempList.size() > 0) {
            tempList.addFirst(new ActionEntry((short)(- tempList.size()), "High", "high"));
        }
        return tempList;
    }

    //Copy pasta
    protected List<ActionEntry> getMidAttacks(@Nullable Item weapon, boolean auto, Creature opponent, float mycr, float oppcr, float primweaponskill) {
        LinkedList<ActionEntry> tempList = new LinkedList<>();
        this.addToList(tempList, weapon, 303, opponent, mycr, oppcr, primweaponskill);
        if (primweaponskill > (float)CombatHandler.getAttackSkillCap((short) 291)) {
            this.addToList(tempList, weapon, 291, opponent, mycr, oppcr, primweaponskill);
        }
        if (primweaponskill > (float)CombatHandler.getAttackSkillCap((short) 309)) {
            this.addToList(tempList, weapon, 309, opponent, mycr, oppcr, primweaponskill);
        }
        if (!auto && tempList.size() > 0) {
            tempList.addFirst(new ActionEntry((short)(- tempList.size()), "Mid", "Mid"));
        }
        return tempList;
    }

    //Copy pasta
    protected List<ActionEntry> getLowAttacks(@Nullable Item weapon, boolean auto, Creature opponent, float mycr, float oppcr, float primweaponskill) {
        LinkedList<ActionEntry> tempList = new LinkedList<>();
        if (primweaponskill > (float)CombatHandled.getAttackSkillCap((short) 297)) {
            this.addToList(tempList, weapon, 297, opponent, mycr, oppcr, primweaponskill);
        }
        if (primweaponskill > (float)CombatHandled.getAttackSkillCap((short) 294)) {
            this.addToList(tempList, weapon, 294, opponent, mycr, oppcr, primweaponskill);
        }
        if (primweaponskill > (float)CombatHandled.getAttackSkillCap((short) 312)) {
            this.addToList(tempList, weapon, 312, opponent, mycr, oppcr, primweaponskill);
        }
        if (!auto && tempList.size() > 0) {
            tempList.addFirst(new ActionEntry((short)(- tempList.size()), "Low", "Low"));
        }
        return tempList;
    }

    //Copy pasta
    protected static final int getAttackSkillCap(short action) {
        switch (action) {
            case 303: {
                return 0;
            }
            case 291: {
                return 3;
            }
            case 309: {
                return 2;
            }
            case 300: {
                return 15;
            }
            case 288: {
                return 13;
            }
            case 306: {
                return 12;
            }
            case 297: {
                return 9;
            }
            case 294: {
                return 7;
            }
            case 312: {
                return 5;
            }
        }
        return 0;
    }

    //Copy pasta
    protected static final boolean isNextGoodStance(byte currentStance, byte nextStance, byte opponentStance) {
        if (CombatHandled.isAtSoftSpot(nextStance, opponentStance)) {
            return false;
        }
        if (CombatHandled.isAtSoftSpot(opponentStance, currentStance)) {
            return false;
        }
        if (CombatHandled.isAtSoftSpot(opponentStance, nextStance)) {
            return true;
        }
        if (currentStance == 0) {
            return nextStance == 5 || nextStance == 2;
        }
        if (currentStance == 5) {
            return nextStance == 6 || nextStance == 4;
        }
        if (currentStance == 2) {
            return nextStance == 1 || nextStance == 3;
        }
        if (currentStance == 1 || currentStance == 6) {
            return nextStance == 7;
        }
        if (currentStance == 3 || currentStance == 4) {
            return nextStance == 10;
        }
        return false;
    }

    //Copy pasta
    protected static final boolean isAtSoftSpot(byte stanceChecked, byte stanceUnderAttack) {
        byte[] opponentSoftSpots;
        for (byte spot : opponentSoftSpots = CombatHandler.getSoftSpots(stanceChecked)) {
            if (spot != stanceUnderAttack) continue;
            return true;
        }
        return false;
    }

    //Copy pasta
    protected static final byte[] getSoftSpots(byte currentStance) {
        if (currentStance == 0) {
            return standardSoftSpots;
        }
        if (currentStance == 5) {
            return midLeftSoftSpots;
        }
        if (currentStance == 2) {
            return midRightSoftSpots;
        }
        if (currentStance == 1) {
            return upperRightSoftSpots;
        }
        if (currentStance == 6) {
            return upperLeftSoftSpots;
        }
        if (currentStance == 3) {
            return lowerRightSoftSpots;
        }
        if (currentStance == 4) {
            return lowerLeftSoftSpots;
        }
        return emptyByteArray;
    }

    //Copy pasta;  It appears as though isNextGoodStance calls in this method uses wrong argument order.
    protected static final boolean existsBetterOffensiveStance(byte _currentStance, byte opponentStance) {
        if (CombatHandler.isAtSoftSpot(opponentStance, _currentStance)) {
            return false;
        }
        boolean isOpponentAtSoftSpot = CombatHandler.isAtSoftSpot(_currentStance, opponentStance);
        if (isOpponentAtSoftSpot || !CombatHandler.isStanceParrying(_currentStance, opponentStance) && !CombatHandler.isStanceOpposing(_currentStance, opponentStance)) {
            for (int x = 0; x < selectStanceList.size(); ++x) {
                int num = Server.rand.nextInt(selectStanceList.size());
                ActionEntry e = selectStanceList.get(num);
                byte nextStance = CombatHandler.getStanceForAction(e = Actions.actionEntrys[e.getNumber()]);
                if (!CombatHandler.isNextGoodStance(_currentStance, nextStance, opponentStance)) continue;
                return true;
            }
            return false;
        }
        for (int x = 0; x < selectStanceList.size(); ++x) {
            int num = Server.rand.nextInt(selectStanceList.size());
            ActionEntry e = selectStanceList.get(num);
            byte nextStance = CombatHandler.getStanceForAction(e = Actions.actionEntrys[e.getNumber()]);
            if (CombatHandler.isStanceParrying(_currentStance, nextStance) || CombatHandler.isStanceOpposing(_currentStance, nextStance)) continue;
            return true;
        }
        return false;
    }
    //Copy pasta;  It appears as though isNextGoodStance calls in this method uses wrong argument order.
    protected static final ActionEntry changeToBestOffensiveStance(byte _currentStance, byte opponentStance) {
        for (int x = 0; x < selectStanceList.size(); ++x) {
            int num = Server.rand.nextInt(selectStanceList.size());
            ActionEntry e = selectStanceList.get(num);
            byte nextStance = CombatHandler.getStanceForAction(e = Actions.actionEntrys[e.getNumber()]);
            if (!CombatHandler.isNextGoodStance(_currentStance, nextStance, opponentStance)) continue;
            return e;
        }
        return null;
    }
    //Copy pasta
    protected static final float getDistdiff(Creature creature, Creature opponent, AttackAction atk) {
        if (atk != null && !atk.isUsingWeapon()) {
            float idealDist = 10 + atk.getAttackValues().getAttackReach() * 3;
            float dist = Creature.rangeToInDec(creature, opponent);
            return idealDist - dist;
        }
        Item wpn = creature.getPrimWeapon();
        return CombatHandled.getDistdiff(wpn, creature, opponent);
    }*/
}