package mod.piddagoras.combathandled;

import com.wurmonline.server.*;
import com.wurmonline.server.behaviours.*;
import com.wurmonline.server.bodys.BodyTemplate;
import com.wurmonline.server.bodys.Wound;
import com.wurmonline.server.combat.*;
import com.wurmonline.server.creatures.*;
import com.wurmonline.server.deities.Deities;
import com.wurmonline.server.items.*;
import com.wurmonline.server.modifiers.DoubleValueModifier;
import com.wurmonline.server.players.ItemBonus;
import com.wurmonline.server.players.Player;
import com.wurmonline.server.players.Titles;
import com.wurmonline.server.skills.NoSuchSkillException;
import com.wurmonline.server.skills.Skill;
import com.wurmonline.server.skills.SkillList;
import com.wurmonline.server.skills.Skills;
import com.wurmonline.server.sounds.SoundPlayer;
import com.wurmonline.server.spells.SpellEffect;
import com.wurmonline.server.utils.CreatureLineSegment;
import com.wurmonline.server.zones.VirtualZone;
import com.wurmonline.shared.constants.Enchants;
import com.wurmonline.shared.util.MulticolorLineSegment;
import mod.sin.lib.WoundAssist;
import org.gotti.wurmunlimited.modloader.ReflectionUtil;

import java.lang.reflect.InvocationTargetException;
import java.util.*;
import java.util.logging.Level;
import java.util.logging.Logger;
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
*/
public class CombatHandled{
    public static final Logger logger = Logger.getLogger(CombatHandled.class.getName());
    public static HashMap<Creature, CombatHandled> combatMap = new HashMap<>();

    public static boolean attackHandled(Creature attacker, Creature opponent, int combatCounter, boolean opportunity, float actionCounter, Action act) {
        CombatHandled ch;
        if(combatMap.containsKey(attacker)){
            ch = combatMap.get(attacker);
        }else{
            ch = new CombatHandled();
            combatMap.put(attacker, ch);
        }
        //logger.info(String.format("Running attack loop for attacker %s against %s, counter = %.2f", attacker.getName(), opponent.getName(), actionCounter));
        return ch.attackLoop(attacker, opponent, combatCounter, opportunity, actionCounter, act);
    }

    public static CombatHandled getCombatHandled(Creature creature){
        if(combatMap.containsKey(creature)){
            return combatMap.get(creature);
        }else{
            CombatHandled ch = new CombatHandled();
            combatMap.put(creature, ch);
            return ch;
        }
    }

    protected float lastTimeStamp=1.0f;
    protected byte currentStance=15; //Need to look into what stances are.
    protected byte currentStrength=1;//Depends on aggressive/normal/defensive style active currently.
    protected byte opportunityAttacks=0;//Need to look closely at how opportunities work.
	protected HashSet<Item> secattacks;//Probably updates in other methods besides attackLoop.
    //protected boolean turned = false; // No longer used, turning attacker instead of opponents.

    protected boolean crit = false;
    protected boolean dead = false;

    protected static final List<ActionEntry> selectStanceList = new LinkedList<>();
    private static final List<ActionEntry> standardDefences = new LinkedList<>();


    protected static double manouvreMod = 0.0D;
    private boolean hasSpiritFervor = false;
    private byte battleratingPenalty = 0;
    //private boolean hasRodEffect = false;
    private static float parryBonus = 1.0F;
    private int usedShieldThisRound = 0;


    private static String attString = "";


    public boolean receivedFStyleSkill = false;
    private boolean receivedWeaponSkill = false;
    private boolean receivedSecWeaponSkill = false;
    private boolean receivedShieldSkill = false;
    private boolean addToSkills=false;

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

    public static final byte[] NO_COMBAT_OPTIONS = new byte[0];


    protected boolean attackLoop(Creature attacker, Creature opponent, int combatCounter, boolean opportunity, float actionCounter, Action act){
        boolean isDead=false;
        stillAttacking : {
            // Action counter must always be greater than lastTimeStamp for proper calculation of the delta.
            // Added check to ensure the timestamp is reset when the player resets in combat.
            if(actionCounter < this.lastTimeStamp){
                this.lastTimeStamp = actionCounter;
            }
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
            if (CombatHandler.prerequisitesFail(attacker, opponent, opportunity, weapon)){
                logger.info(String.format("Prerequisites failed on loop for attacker %s (combat disengaged).", attacker.getName()));
                return true;
            }
            this.currentStance = attacker.getCombatHandler().getCurrentStance(); // Required to set properly because it's adjusted in the CombatHandler through public methods.
            if (act != null && act.justTickedSecond()) {
                // Sending combat status to the attacker.
                //attacker.getCommunicator().sendCombatStatus(CombatHandled.getDistdiff(weapon, attacker, opponent), this.getFootingModifier(attacker, weapon, opponent), this.currentStance);//Should maybe Hijack this and send different data.
                try {
                    // Using ReflectionUtil to call a protected method with arguments.
                    ReflectionUtil.callPrivateMethod(attacker.getCommunicator(), ReflectionUtil.getMethod(attacker.getCommunicator().getClass(), "sendCombatStatus"), CombatHandled.getDistdiff(weapon, attacker, opponent), this.getFootingModifier(attacker, weapon, opponent), this.currentStance);
                } catch (IllegalAccessException | InvocationTargetException | NoSuchMethodException e) {
                    e.printStackTrace();
                }
            }
            if (this.isProne() || this.isOpen()){
                logger.info(String.format("Creature %s is prone or open, and skips their combat loop.", attacker.getName()));
                return false;
            }

            attacker.opponentCounter = 30;//?

            // TODO: Re-implement, it causes a stack overflow because it always calls the CobmbatHandled.attackHandled in a recursive loop.
            //Check for free attack by opponent on attacker because movement or opportunity, only happens when combat is initiated.
            /*if (actionCounter != 1.0f || opportunity || !attacker.isMoving() || opponent.isMoving() || opponent.target != attacker.getWurmId()){
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
                isDead = CombatHandled.attackHandled(opponent, attacker, combatCounter, true, 2.0f, null);
                break stillAttacking;
            }*/

            // Opportunity attack for when someone enters combat without having the attacker targeted.
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
                Item[] secweapons = attacker.getSecondaryWeapons();
                if (secweapons.length > 0) {
                    weapon = secweapons[Server.rand.nextInt(secweapons.length)];
                }
                //Attack with the weapon
                isDead = this.swingWeapon(attacker, weapon, opponent, false);
                break stillAttacking;
            }

            boolean performedAttack = false;
            if (attacker.combatRound > 1) {
                //Make sure we've actually been in combat a round.
                for (Item lSecweapon : attacker.getSecondaryWeapons()) {
                    if (attacker.opponent == null) continue; //this should be impossible, since creature.opponent==opponent is probably always true.
                    if (this.secattacks == null) {
                        this.secattacks = new HashSet<>();
                    }
                    if (this.secattacks.contains(lSecweapon) || (lSecweapon.getTemplateId() == 12 || lSecweapon.getTemplateId() == 17)) continue;
                    float time = this.getSpeed(attacker, lSecweapon);
                    float timer = attacker.addToWeaponUsed(lSecweapon, delta);
                    if (isDead || attacker.combatRound % 2 != 1 || timer <= time) continue; //Every other round, seems bursty.
                    attacker.deductFromWeaponUsed(lSecweapon, time);
                    attacker.sendToLoggers("YOU SECONDARY " + lSecweapon.getName(), (byte) 2);
                    opponent.sendToLoggers(attacker.getName() + " SECONDARY " + lSecweapon.getName() + "(" + lSecweapon.getWurmId() + ")", (byte) 2);
                    attacker.setHugeMoveCounter(2 + Server.rand.nextInt(4));//Probably does nothing, but leaving in case.
                    isDead = this.swingWeapon(attacker, lSecweapon, opponent, true);
                    performedAttack = true;
                    this.secattacks.add(lSecweapon);
                }
            }
            float time = this.getSpeed(attacker, weapon);
            float timer = attacker.addToWeaponUsed(weapon, delta);
            if (!isDead && timer > time){
                logger.info(String.format("Weapon swing timer for %s's %s has come up, performing swing (%.2f second swing timer, timer is at %.2f).", attacker.getName(), weapon.getName(), time, timer));
                attacker.deductFromWeaponUsed(weapon, time);
                attacker.sendToLoggers("YOU PRIMARY " + weapon.getName(), (byte) 2);
                opponent.sendToLoggers(attacker.getName() + " PRIMARY " + weapon.getName(), (byte) 2);
                isDead = this.swingWeapon(attacker, weapon, opponent, false);
                performedAttack = true;
                if (!attacker.isPlayer() || act == null || !act.justTickedSecond()) break stillAttacking;
                this.checkStanceChange(attacker, opponent);
                break stillAttacking;
            }
            int[] cmoves = attacker.getCombatMoves();
            if (!(performedAttack || isDead || attacker.isPlayer()
                    || attacker.getTarget() == null || attacker.getLayer() != opponent.getLayer()
                    || this.checkStanceChange(attacker, opponent) || (cmoves = attacker.getCombatMoves()).length <= 0)) {
                //Check to use an npc special moves
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

    private boolean swingWeapon(Creature attacker, Item weapon, Creature opponent, boolean secondaryWeapon) {
        if (weapon.isWeaponBow()) {
            return false;
        } else {
            //resetFlags(opponent);

            // Instead of having the opponent turn when they get attacked, we're going to turn the attacker when they swing.
            if(!attacker.isPlayer() || !attacker.hasLink()){
                attacker.turnTowardsCreature(opponent);
                // Inlined version of setting attacker when a player gets attacked.
                if(!opponent.isFighting() && (attacker.isPlayer() || attacker.isDominated())){
                    opponent.setTarget(attacker.getWurmId(), true);
                }
            }
            // Old method of turning creatures
            /*if (!(opponent instanceof Player) || !opponent.hasLink()) {
                if (!this.turned) {
                    if (opponent.getTarget() == null || opponent.getTarget() == attacker) {
                        opponent.turnTowardsCreature(attacker);
                    }

                    this.turned = true;
                }

                boolean switchOpp = false;
                if (!opponent.isFighting() && (attacker.isPlayer() || attacker.isDominated())) {
                    switchOpp = true;
                }

                opponent.setTarget(attacker.getWurmId(), switchOpp);
            }*/
            double attBonus;
            double defBonus;
            computeBonuses:
            {
                attBonus = (double) attacker.zoneBonus - (double) attacker.getMovePenalty() * 0.5D;
                if (this.currentStrength == 0) {
                    attBonus -= 20.0D;
                }
                defBonus = (double) (opponent.zoneBonus - (float) opponent.getMovePenalty());
                attBonus += (double) CombatEngine.getEnchantBonus(weapon, opponent);
                if (this.addToSkills && opponent.isPlayer() && getCombatHandled(opponent).currentStrength == 0) {
                    Skill def;
                    try {
                        def = opponent.getSkills().getSkill(10054);
                    } catch (NoSuchSkillException var5) {
                        def = opponent.getSkills().learn(10054, 1.0F);
                    }
                    if (opponent.getStatus().getStamina() > 2000 && def.skillCheck((double) (attacker.getBaseCombatRating() * 2.0F), 0.0D, attacker.isNoSkillFor(opponent) || getCombatHandled(opponent).receivedFStyleSkill, 10.0F, opponent, attacker) > 0.0D) {
                        getCombatHandled(opponent).receivedFStyleSkill = true;
                        defBonus += def.getKnowledge(0.0D) / 4.0D;
                    }
                }
                if (getCombatHandled(opponent).currentStrength > 0 && opponent instanceof Player) {
                    if (opponent.isMoving()) {
                        defBonus -= (double) (getCombatHandled(opponent).currentStrength * 15);
                    } else if (getCombatHandled(opponent).currentStrength > 1) {
                        defBonus -= (double) (getCombatHandled(opponent).currentStrength * 7);
                    }
                }
                if (opponent.isOnHostileHomeServer()) {
                    defBonus -= 20.0D;
                } else if (attacker.isMoving() && attacker instanceof Player) {
                    attBonus -= 15.0D;
                }
            }
            attacker.getStatus().modifyStamina((float)((int)((float)(-weapon.getWeightGrams()) / 10.0F * (1.0F + (float)this.currentStrength * 0.5F))));
            this.addToSkills = true;
            float chanceToHit = this.getChanceToHit(attacker, weapon, opponent, attBonus, defBonus);
            byte type=this.getType(attacker, weapon, false);
            double damage=this.getDamage(attacker, weapon, opponent);
            boolean hit=false;
            crit=false; //Has to be global due to being set in multiple different methods
            boolean miss=false;
            dead=false; //Has to be global due to being set in multiple different methods
            byte pos = 0;
            boolean justOpen=false;

            double attCheck = (double)(Server.rand.nextFloat() * 100.0F) * (1.0D + attacker.getVisionMod());
            double defCheck = (double)(Server.rand.nextFloat() * 100.0F) * getCombatHandled(opponent).getDodgeMod(opponent);

            setAttString(attacker, weapon, type);
            this.sendStanceAnimation(attacker, this.currentStance, true);
            Item defShield = opponent.getShield();
            float percent = this.checkShield(attacker, opponent, weapon, damage, type, pos, chanceToHit, defBonus);
            logger.info(String.format("Rolling %s's shield block chance. Rolled %.2f power on the block.", opponent.getName(), percent));
            if (percent > 50.0F) {
                logger.info(String.format("- %s shield blocked successfully (rolled %.2f), setting chance to hit to 0.", opponent.getName(), percent));
                chanceToHit = 0.0F;
            } else if (percent > 0.0F) {
                float chanceToHitOld = chanceToHit;
                chanceToHit *= 1.0F - percent / 100.0F;
                logger.info(String.format("- %s half-shield blocked? Rolled %.2f, and set hit chance from %.2f%% to %.2f%%", opponent.getName(), percent, chanceToHitOld, chanceToHit));
            } else{
                logger.info(String.format("- %s shield block either failed or defender has no shield. (Rolled %.2f)", opponent.getName(), percent));
            }

            float parrPercent = -1.0F;
            int parryRoll = Server.rand.nextInt(3);
            logger.info(String.format("Rolling %s's parry chance. Conditions: fightStyle not 1 (%s) or random value is equal to 0 (%s). Must also have above 0 hit chance (%.2f).", opponent.getName(), opponent.getFightStyle(), parryRoll, chanceToHit));
            if ((opponent.getFightStyle() != 1 || parryRoll == 0) && chanceToHit > 0.0F) {
                parrPercent = this.checkDefenderParry(attacker, opponent, weapon, defShield, attCheck, defBonus, damage); //Not sure what to do.  This method uses attCheck (from previous attack), but attCheck provides no useful information and is actually just random.
                if (parrPercent > 60.0F) {
                    logger.info(String.format("- %s parried successfully (rolled %.2f), setting hit chance to 0.", opponent.getName(), parrPercent));
                    chanceToHit = 0.0F;
                } else if (parrPercent > 0.0F) {
                    float chanceToHitOld = chanceToHit;
                    chanceToHit *= 1.0F - parrPercent / 200.0F;
                    logger.info(String.format("- %s half-parried? Rolled %.2f, and set hit chance from %.2f%% to %.2f%%", opponent.getName(), parrPercent, chanceToHitOld, chanceToHit));
                } else {
                    logger.info(String.format("- %s's parry either failed or defender has no parry chance. (Rolled %.2f)", opponent.getName(), parrPercent));
                }
            }

            pos = BodyTemplate.torso;

            try {
                // Obtain wound position based on their current targeting
                pos = this.getWoundPos(this.currentStance, opponent);
            } catch (Exception var9) {
                logger.log(Level.WARNING, attacker.getName() + " " + var9.getMessage(), var9);
            }

            String combatDetails = " CHANCE:" + chanceToHit + ", roll=" + attCheck;
            if (attacker.spamMode() && Servers.isThisATestServer()) {
                attacker.getCommunicator().sendCombatSafeMessage(combatDetails);
            }

            attacker.sendToLoggers("YOU" + combatDetails, (byte)2);
            opponent.sendToLoggers(attacker.getName() + combatDetails, (byte)2);
            logger.info(String.format("Rolling %s's hit chance. If attCheck (%.2f) is less than chanceToHit (%.2f), it's a hit.", attacker.getName(), attCheck, chanceToHit));
            if (attCheck < (double)chanceToHit) {
                logger.info(String.format("- %s hit chance roll succeeded.", attacker.getName()));
                if (opponent.isPlayer()) {
                    float critChance = Weapon.getCritChanceForWeapon(weapon);
                    logger.info(String.format("Base crit chance is %.2f%% for %s's weapon: %s", critChance, attacker.getName(), weapon.getName()));
                    if (isAtSoftSpot(getCombatHandled(opponent).currentStance, currentStance)) {
                        critChance += 0.05F;
                        logger.info(String.format("- Attack is at soft spot, increasing crit chance by 5.00%% to %.2f%%", critChance*100f));
                    }

                    int enchBon = CombatEngine.getEnchantBonus(weapon, opponent);
                    if (enchBon > 0) {
                        critChance += 0.03F;
                        logger.info(String.format("- Enchant bonus found, increasing crit chance by 3.00%% to %.2f%%", critChance*100f));
                    }

                    float rollCrit = Server.rand.nextFloat();
                    logger.info(String.format("- %s rolling for critical against %s, chance is %.2f%%", attacker.getName(), opponent.getName(), critChance*100f));
                    if (!weapon.isArtifact() && rollCrit < critChance) {
                        logger.info(String.format("- %s landed a critical strike on %s! (%.2f < %.2f)", attacker.getName(), opponent.getName(), rollCrit*100f, critChance*100f));
                        crit = true;
                    }
                }
            } else {
                logger.info(String.format("- Due to a comparison of attCheck (%.2f) being greater than or equal to chanceToHit (%.2f), %s's attack is guaranteed to miss.", attCheck, chanceToHit, attacker.getName()));
                miss = true;
            }

            if (!miss && !crit) {
                logger.info(String.format("%s did not miss or crit, so now we calculate %s's chance to dodge.", attacker.getName(), opponent.getName()));
                boolean keepGoing = true;
                //defCheck *= (double)opponent.getStatus().getDodgeTypeModifier();
                try {
                    // Using ReflectionUtil to call a protected method to obtain a value.
                    float opponentDodgeTypeModifier = ReflectionUtil.callPrivateMethod(opponent.getStatus(), ReflectionUtil.getMethod(opponent.getStatus().getClass(), "getDodgeTypeModifier"));
                    defCheck *= (double)opponentDodgeTypeModifier;
                    if (opponent.getMovePenalty() != 0) {
                        defCheck *= (double)(1.0F + (float)opponent.getMovePenalty() / 10.0F);
                    }

                    defCheck *= 1.0D - opponent.getMovementScheme().armourMod.getModifier();
                    logger.info(String.format("- Comparing %s's defCheck (%.2f) less than 1/3 Body Control (%.2f) to see if %s dodges.", opponent.getName(), defCheck, opponent.getBodyControl() / 3D, opponent.getName()));
                    if (defCheck < opponent.getBodyControl() / 3.0D) {
                        logger.info(String.format("- %s successfully dodges the attack due to body control.", opponent.getName()));
                        if ((double)(opponentDodgeTypeModifier * 100.0F) < opponent.getBodyControl() / 3.0D) {
                            logger.log(Level.WARNING, opponent.getName() + " is impossible to hit except for crits: " + getCombatHandled(opponent).getDodgeMod(opponent) * 100.0D + " is always less than " + opponent.getBodyControl());
                        }

                        this.sendDodgeMessage(attacker, opponent, defCheck, pos);
                        keepGoing = false;
                        String dodgeDetails = "Dodge=" + defCheck + "<" + opponent.getBodyControl() / 3.0D + " dodgemod=" + getCombatHandled(opponent).getDodgeMod(opponent) + " dodgeType=" + opponentDodgeTypeModifier + " dodgeMovePenalty=" + opponent.getMovePenalty() + " armour=" + opponent.getMovementScheme().armourMod.getModifier();
                        if (attacker.spamMode() && Servers.isThisATestServer()) {
                            attacker.getCommunicator().sendCombatSafeMessage(dodgeDetails);
                        }

                        attacker.sendToLoggers(dodgeDetails, (byte)4);
                        checkIfHitVehicle(attacker, opponent, damage);
                    }
                } catch (IllegalAccessException | InvocationTargetException | NoSuchMethodException e) {
                    e.printStackTrace();
                }

                if (keepGoing) {
                    logger.info(String.format("- %s failed to dodge the attack, and now %s is ensured a hit.", opponent.getName(), attacker.getName()));
                    hit = true;
                }
            }

            if (hit || crit) {
                logger.info(String.format("%s's attack is successful. Beginning damage calculation...", attacker.getName()));
                attacker.sendToLoggers("YOU DAMAGE " + weapon.getName(), (byte)2);
                opponent.sendToLoggers(attacker.getName() + " DAMAGE " + weapon.getName(), (byte)2);
                dead = this.setDamage(attacker, opponent, weapon, damage, pos, type);
            }

            if (dead) {
                this.setKillEffects(attacker, attacker, opponent);
            }

            if (miss) {
                logger.info(String.format("%s's attack has either missed or was blocked/parried. Communicating that they're a failure.", attacker.getName()));
                if (attacker.spamMode() && (chanceToHit > 0.0F || percent > 0.0F && parrPercent > 0.0F)) {
                    attacker.getCommunicator().sendCombatNormalMessage("You miss with the " + weapon.getName() + ".");
                    attacker.sendToLoggers("YOU MISS " + weapon.getName(), (byte)2);
                    opponent.sendToLoggers(attacker.getName() + " MISS " + weapon.getName(), (byte)2);
                }

                if (!attacker.isUnique() && attCheck - (double)chanceToHit > 50.0D && Server.rand.nextInt(10) == 0) {
                    justOpen = true;
                    this.setCurrentStance(attacker, -1, (byte)9);
                    ArrayList<MulticolorLineSegment> segments = new ArrayList<>();
                    segments.add(new CreatureLineSegment(attacker));
                    segments.add(new MulticolorLineSegment(" makes a bad move and is an easy target!.", (byte)0));
                    opponent.getCommunicator().sendColoredMessageCombat(segments);
                    segments.get(1).setText(" make a bad move, making you an easy target.");
                    attacker.getCommunicator().sendColoredMessageCombat(segments);
                    attacker.getCurrentTile().checkOpportunityAttacks(attacker);
                    opponent.getCurrentTile().checkOpportunityAttacks(attacker);
                } else if (Server.rand.nextInt(10) == 0) {
                    checkIfHitVehicle(attacker, opponent, damage);
                }
            }

            this.addToSkills = false;
            return dead;
        }
    }

    //Copy pasta
    public float getChanceToHit(Creature attacker, Item weapon, Creature opponent, double attBonus, double defBonus) {
        float myCR = this.getCombatRating(attacker, weapon, opponent, true);
        float oppCR = getCombatHandled(opponent).getCombatRating(opponent, opponent.getPrimWeapon(), attacker, false);
        if (attacker.isPlayer()) {
            float distdiff = Math.abs(getDistdiff(weapon, attacker, opponent));
            if (distdiff > 10.0F) {
                --myCR;
            }

            if (distdiff > 20.0F) {
                --myCR;
            }
        }

        parryBonus = this.getParryBonus(getCombatHandled(opponent).currentStance, this.currentStance);
        byte opponentFightLevel = getCreatureFightLevel(opponent);
        if (opponentFightLevel> 0) {
            parryBonus -= (float)(opponentFightLevel) / 100.0F;
        }

        double m = 1.0D;
        if (attBonus != 0.0D) {
            m = 1.0D + attBonus / 100.0D;
        }

        Seat s = opponent.getSeat();
        if (s != null) {
            m *= (double)s.cover;
        }
        myCR=Math.min(100.0F, Math.max(1.0F, myCR));
        oppCR=Math.min(100.0F, Math.max(1.0F, oppCR));

        float chance = (float)((double)(myCR / (oppCR+myCR)) * m * parryBonus);
        float rest = Math.max(0.01F, 1.0F - chance);
        return 100.0F * Math.max(0.01F, (float)Server.getBuffedQualityEffect((double)(1.0F - rest)));
    }



    //Copy pasta
    public byte getType(Creature attacker, Item weapon, boolean rawType) {
        //byte woundType = attacker.getCombatDamageType();
        // getCombatDamageType() calls template.getCombatDamageType(). This call is identical and not private.
        byte woundType = attacker.getTemplate().getCombatDamageType();
        if (!weapon.isWeaponSword() && weapon.getTemplateId() != ItemList.halberd) {
            if (weapon.getTemplateId() == ItemList.crowbar) {
                if (!rawType && Server.rand.nextInt(3) != 0) {
                    woundType = Wound.TYPE_CRUSH;
                } else {
                    woundType = Wound.TYPE_PIERCE;
                }
            } else if (weapon.isWeaponSlash()) {
                woundType = Wound.TYPE_SLASH;
            } else if (weapon.isWeaponPierce()) {
                woundType = Wound.TYPE_PIERCE;
            } else if (weapon.isWeaponCrush()) {
                woundType = Wound.TYPE_CRUSH;
            } else if (weapon.isBodyPart()) {
                if (weapon.getTemplateId() == ItemList.bodyFace) { // Bite
                    woundType = Wound.TYPE_BITE;
                } else if (weapon.getTemplateId() == ItemList.bodyHead) { // Headbutt
                    woundType = Wound.TYPE_CRUSH;
                }
            }
        } else if (!rawType && Server.rand.nextInt(2) != 0) {
            woundType = Wound.TYPE_PIERCE;
        } else {
            woundType = Wound.TYPE_SLASH;
        }
        logger.info(String.format("Setting %s's attack damage type to %s (%s).", attacker.getName(), woundType, WoundAssist.getWoundName(woundType)));
        return woundType;
    }
    //Copy pasta
    private double getDamage(Creature attacker, Item weapon, Creature opponent) {
        Skill attStrengthSkill;
        double damage;
        /*try {
            attStrengthSkill = attacker.getSkills().getSkill(SkillList.BODY_STRENGTH);
        } catch (NoSuchSkillException var13) {
            attStrengthSkill = attacker.getSkills().learn(SkillList.BODY_STRENGTH, 1.0F);
            logger.log(Level.WARNING, attacker.getName() + " had no strength. Weird.");
        }*/

        if (weapon.isBodyPartAttached()) {
            damage = DamageMethods.getBaseUnarmedDamage(attacker, weapon);
            logger.info(String.format("%s is unarmed attacking with %s. Base damage: %.2f", attacker.getName(), weapon.getName(), damage));
            //damage = (double)(attacker.getCombatDamage(weapon) * 1000.0F * attacker.getStatus().getDamageTypeModifier());
            /*try {
                // Using ReflectionUtil to call a protected method to adjust a variable.
                float attackerDamageTypeModifier = ReflectionUtil.callPrivateMethod(attacker.getStatus(), ReflectionUtil.getMethod(attacker.getStatus().getClass(), "getDamageTypeModifier"));
                damage = (double)(attacker.getCombatDamage(weapon) * 1000.0F * attackerDamageTypeModifier);
            } catch (IllegalAccessException | InvocationTargetException | NoSuchMethodException e) {
                damage = (double)(attacker.getCombatDamage(weapon) * 1000.0F); // Assume it's 1.0F in the case that the reflection fails.
                e.printStackTrace();
            }
            if (attacker.isPlayer()) {
                Skill weaponLess = attacker.getWeaponLessFightingSkill();
                double modifier = 1.0D + 2.0D * weaponLess.getKnowledge() / 100.0D;
                damage *= modifier;
            }

            if (damage < 10000.0D && attacker.getBonusForSpellEffect(Enchants.CRET_BEARPAW) > 0.0F) {
                damage += Server.getBuffedQualityEffect((double)(attacker.getBonusForSpellEffect(Enchants.CRET_BEARPAW) / 100.0F)) * 5000.0D;
            }

            float randomizer = (50.0F + Server.rand.nextFloat() * 50.0F) / 100.0F;
            damage *= (double)randomizer;*/
        } else {
            damage = DamageMethods.getBaseWeaponDamage(attacker, opponent, weapon, false);
            logger.info(String.format("%s is using weapon %s. Base damage: %.2f", attacker.getName(), weapon.getName(), damage));
            /*damage = Weapon.getModifiedDamageForWeapon(weapon, attStrengthSkill, opponent.getTemplate().getTemplateId() == 116) * 1000.0D;
            if (!Servers.isThisAnEpicOrChallengeServer()) {
                damage += (double)(weapon.getCurrentQualityLevel() / 100.0F * weapon.getSpellExtraDamageBonus());
            }

            damage += Server.getBuffedQualityEffect((double)(weapon.getCurrentQualityLevel() / 100.0F)) * (double)Weapon.getBaseDamageForWeapon(weapon) * 2400.0D;
            damage *= Weapon.getMaterialDamageBonus(weapon.getMaterial());
            if (!opponent.isPlayer() && opponent.isHunter()) {
                damage *= Weapon.getMaterialHunterDamageBonus(weapon.getMaterial());
            }

            damage *= (double)ItemBonus.getWeaponDamageIncreaseBonus(attacker, weapon);
            if (Servers.isThisAnEpicOrChallengeServer()) {
                damage *= (double)(1.0F + weapon.getCurrentQualityLevel() / 100.0F * weapon.getSpellExtraDamageBonus() / 30000.0F);
            }*/
        }
        double mult = DamageMethods.getDamageMultiplier(this, attacker, opponent, weapon);
        damage *= mult;
        logger.info(String.format("Multiplying base damage by %.2f due to multipliers. Final damage: %.2f", mult, damage));

        /*if (attacker.getEnemyPresense() > 1200 && opponent.isPlayer() && !weapon.isArtifact()) {
            damage *= 1.15D;
        }

        if (!weapon.isArtifact() && this.hasRodEffect && opponent.isPlayer()) {
            damage *= 1.2D;
        }

        Vehicle vehicle = Vehicles.getVehicleForId(opponent.getVehicle());
        boolean mildStack = false;
        if (weapon.isWeaponPolearm() && (vehicle != null && vehicle.isCreature() || opponent.isRidden() && weapon.isWeaponPierce())) {
            damage *= 1.7D;
        } else if (weapon.isArtifact()) {
            mildStack = true;
        } else if (attacker.getCultist() != null && attacker.getCultist().doubleWarDamage()) {
            damage *= 1.5D;
            mildStack = true;
        } else if (attacker.getDeity() != null && attacker.getDeity().warrior && attacker.getFaith() >= 40.0F && attacker.getFavor() >= 20.0F) {
            damage *= 1.25D;
            mildStack = true;
        }

        if (attacker.isPlayer()) {
            if ((attacker.getFightStyle() != 2 || attStrengthSkill.getRealKnowledge() < 20.0D) && attStrengthSkill.getRealKnowledge() != 20.0D) {
                damage *= 1.0D + (attStrengthSkill.getRealKnowledge() - 20.0D) / 200.0D;
            }

            if (this.currentStrength == 0) {
                Skill fstyle;

                try {
                    fstyle = attacker.getSkills().getSkill(10054);
                } catch (NoSuchSkillException var12) {
                    fstyle = attacker.getSkills().learn(10054, 1.0F);
                }

                if (fstyle.skillCheck((double)(opponent.getBaseCombatRating() * 3.0F), 0.0D, this.receivedFStyleSkill || opponent.isNoSkillFor(attacker), 10.0F, attacker, opponent) > 0.0D) {
                    this.receivedFStyleSkill = true;
                    damage *= 0.8D;
                } else {
                    damage *= 0.5D;
                }
            }

            Skill fstyle;
            if (attacker.getStatus().getStamina() > 2000 && this.currentStrength >= 1 && !this.receivedFStyleSkill) {
                int num = SkillList.FIGHT_AGGRESSIVESTYLE;
                if (this.currentStrength == 1) {
                    num = SkillList.FIGHT_NORMALSTYLE;
                }

                try {
                    fstyle = attacker.getSkills().getSkill(num);
                } catch (NoSuchSkillException var11) {
                    fstyle = attacker.getSkills().learn(num, 1.0F);
                }

                if (fstyle.skillCheck((double)(opponent.getBaseCombatRating() * 3.0F), 0.0D, this.receivedFStyleSkill || opponent.isNoSkillFor(attacker), 10.0F, attacker, opponent) > 0.0D) {
                    this.receivedFStyleSkill = true;
                    if (this.currentStrength > 1) {
                        damage *= 1.0D + Server.getModifiedFloatEffect(fstyle.getRealKnowledge() / 100.0D) / (double)(mildStack ? 8.0F : 4.0F);
                    }
                }
            }

            float knowl = 1.0F;

            try {
                fstyle = attacker.getSkills().getSkill(weapon.getPrimarySkill());
                knowl = (float)fstyle.getRealKnowledge();
            } catch (NoSuchSkillException ignored) { }

            if (knowl < 50.0F) {
                damage = 0.8D * damage + 0.2D * (double)(knowl / 50.0F) * damage;
            }
        } else {
            damage *= (double)(0.85F + (float)this.currentStrength * 0.15F);
        }*/

        if (attacker.isStealth() && attacker.opponent != null && !attacker.isVisibleTo(opponent)) {
            ArrayList<MulticolorLineSegment> segments = new ArrayList<>();
            segments.add(new CreatureLineSegment(attacker));
            segments.add(new MulticolorLineSegment(" backstab ", (byte)0));
            segments.add(new CreatureLineSegment(opponent));
            attacker.getCommunicator().sendColoredMessageCombat(segments);
            damage = Math.min(50000.0D, damage * 4.0D);
        }

        /*if (attacker.getCitizenVillage() != null && attacker.getCitizenVillage().getFaithWarBonus() > 0.0F) {
            damage *= (double)(1.0F + attacker.getCitizenVillage().getFaithWarBonus() / 100.0F);
        }

        byte attackerFightLevel = getCreatureFightLevel(attacker);
        if(attackerFightLevel >= 4){
            damage *= 1.1D;
        }*/
        return damage;
    }


    //Copy pasta
    private float checkDefenderParry(Creature attacker, Creature defender, Item attWeapon, Item defShield, double attCheck, double defBonus, double damage) {
        double defCheck = 0.0D;
        boolean parried = false;
        int parryTime = 200;
        if (defender.getFightStyle() == 2) {
            parryTime = 120;
        } else if (defender.getFightStyle() == 1) {
            parryTime = 360;
        }

        parryBonus = getParryBonus(getCombatHandled(defender).currentStance, this.currentStance);

        byte defenderFightLevel = getCreatureFightLevel(defender);
        if(defenderFightLevel > 0){
            parryBonus -= (float)(defenderFightLevel * 4) / 100.0F;
        }

        if (defender.getPrimWeapon() != null) {
            parryBonus *= Weapon.getMaterialParryBonus(defender.getPrimWeapon().getMaterial());
        }

        parryTime = (int)((float)parryTime * parryBonus);
        if (WurmCalendar.currentTime > defender.lastParry + (long)Server.rand.nextInt(parryTime)) {
            Item defParryWeapon = defender.getPrimWeapon();
            if (Weapon.getWeaponParryPercent(defParryWeapon) > 0.0F) {
                if (defParryWeapon.isTwoHanded() && defShield != null) {
                    defParryWeapon = null;
                    parried = false;
                } else {
                    parried = true;
                }
            } else {
                defParryWeapon = null;
            }
            Item defLeftWeapon=null;
            if ((!parried || Server.rand.nextInt(3) == 0) && defShield == null) {
                defLeftWeapon = defender.getLefthandWeapon();
                if (defLeftWeapon != defParryWeapon) {
                    if (defLeftWeapon != null && (defLeftWeapon.getSizeZ() > defender.getSize() * 10 || Weapon.getWeaponParryPercent(defLeftWeapon) <= 0.0F)) {
                        defLeftWeapon = null;
                    }

                    if (defLeftWeapon != null) {
                        if (defParryWeapon != null && parried) {
                            if (defLeftWeapon.getSizeZ() > defParryWeapon.getSizeZ()) {
                                defParryWeapon = defLeftWeapon;
                            }
                        } else {
                            defParryWeapon = defLeftWeapon;
                        }
                    }
                }
            }

            Item defWeapon=defParryWeapon;
            if (defParryWeapon != null && Weapon.getWeaponParryPercent(defParryWeapon) > Server.rand.nextFloat()) {
                defCheck = -1.0D;
                if (defender.getStatus().getStamina() >= 300) {
                    Skill defPrimWeaponSkill=getCreatureWeaponSkill(defender, defParryWeapon);
                    double pdiff;
                    if (defPrimWeaponSkill != null && (!defender.isMoving() || defPrimWeaponSkill.getRealKnowledge() > 40.0D)) {
                        pdiff = Math.max(1.0D, (attCheck - defBonus + (double)((float)defParryWeapon.getWeightGrams() / 100.0F)) / (double)getWeaponParryBonus(defParryWeapon) * (1.0D - this.getParryMod(attacker)));
                        if (!defender.isPlayer()) {
                            //pdiff *= (double)defender.getStatus().getParryTypeModifier();
                            try {
                                float parryTypeModifier = ReflectionUtil.callPrivateMethod(defender.getStatus(), ReflectionUtil.getMethod(defender.getStatus().getClass(), "getParryTypeMOdifier"));
                                pdiff *= (double)parryTypeModifier;
                            } catch (IllegalAccessException | InvocationTargetException | NoSuchMethodException e) {
                                e.printStackTrace();
                            }
                        }

                        defCheck = defPrimWeaponSkill.skillCheck(pdiff * (double)ItemBonus.getParryBonus(defender, defParryWeapon), defParryWeapon, 0.0D, defender.isNoSkillFor(defender) || defParryWeapon.isWeaponBow(), 1.0F, defender, defender);
                        defender.lastParry = WurmCalendar.currentTime;
                        defender.getStatus().modifyStamina(-300.0F);
                    }

                    if (defCheck < 0.0D && Server.rand.nextInt(20) == 0 && defLeftWeapon != null && !defLeftWeapon.equals(defParryWeapon)) {
                        Skill offhandWeaponSkill=getCreatureWeaponSkill(defender, defLeftWeapon);
                        if (defPrimWeaponSkill != null && (!defender.isMoving() || defPrimWeaponSkill.getRealKnowledge() > 40.0D)) {
                            pdiff = Math.max(1.0D, (attCheck - defBonus + (double)((float)defLeftWeapon.getWeightGrams() / 100.0F)) / (double)getWeaponParryBonus(defLeftWeapon) * this.getParryMod(attacker));
                            //pdiff *= (double)defender.getStatus().getParryTypeModifier();
                            try {
                                // Using ReflectionUtil to call a protected method for variable adjustments.
                                float defenderParryTypeModifier = ReflectionUtil.callPrivateMethod(defender.getStatus(), ReflectionUtil.getMethod(defender.getStatus().getClass(), "getParryTypeModifier"));
                                pdiff *= (double)defenderParryTypeModifier;
                            } catch (IllegalAccessException | InvocationTargetException | NoSuchMethodException e) {
                                e.printStackTrace();
                            }
                            defCheck = defPrimWeaponSkill.skillCheck(pdiff * (double)ItemBonus.getParryBonus(defender, defParryWeapon), defLeftWeapon, 0.0D, defender.isNoSkillFor(defender) || defParryWeapon.isWeaponBow(), 1.0F, defender, defender);
                            defender.lastParry = WurmCalendar.currentTime;
                            defWeapon=defLeftWeapon;
                            defender.getStatus().modifyStamina(-300.0F);
                        }
                    }

                    if (defCheck > 0.0D) {
                        this.setParryEffects(attacker, defender, defWeapon, attWeapon, damage, defCheck);
                    }
                }
            }
        }

        return (float)defCheck;
    }
    private void setParryEffects(Creature creature, Creature defender, Item defWeapon, Item attWeapon, double damage, double parryEff) {
        defender.lastParry = WurmCalendar.currentTime;
        if (creature.spamMode()) {
            ArrayList<MulticolorLineSegment> segments = new ArrayList<>();
            segments.add(new CreatureLineSegment(defender));
            segments.add(new MulticolorLineSegment(" " + CombatEngine.getParryString(parryEff) + " parries with " + defWeapon.getNameWithGenus() + ".", (byte)0));
            creature.getCommunicator().sendColoredMessageCombat(segments);
        }

        if (defender.spamMode()) {
            defender.getCommunicator().sendCombatNormalMessage("You " + CombatEngine.getParryString(parryEff) + " parry with your " + defWeapon.getName() + ".");
        }

        if (!defWeapon.isBodyPart() || defWeapon.getAuxData() == 100) {
            float vulnerabilityModifier = 1.0F;
            if (defender.isPlayer()) {
                if (attWeapon.isMetal() && Weapon.isWeaponDamByMetal(defWeapon)) {
                    vulnerabilityModifier = 4.0F;
                }

                if (defWeapon.isWeaponSword()) {
                    defWeapon.setDamage(defWeapon.getDamage() + 1.0E-7F * (float)damage * defWeapon.getDamageModifier() * vulnerabilityModifier);
                } else {
                    defWeapon.setDamage(defWeapon.getDamage() + 2.0E-7F * (float)damage * defWeapon.getDamageModifier() * vulnerabilityModifier);
                }
            }

            if (creature.isPlayer()) {
                vulnerabilityModifier = 1.0F;
                if (defWeapon.isMetal() && Weapon.isWeaponDamByMetal(attWeapon)) {
                    vulnerabilityModifier = 4.0F;
                }

                if (attWeapon.isBodyPartAttached()) {
                    attWeapon.setDamage(attWeapon.getDamage() + 1.0E-7F * (float)damage * attWeapon.getDamageModifier() * vulnerabilityModifier);
                }
            }
        }

        creature.sendToLoggers(defender.getName() + " PARRY " + parryEff, (byte)2);
        defender.sendToLoggers("YOU PARRY " + parryEff, (byte)2);
        String lSstring = getParrySound();
        SoundPlayer.playSound(lSstring, defender, 1.6F);
        CombatEngine.checkEnchantDestruction(attWeapon, defWeapon, defender);
        defender.playAnimation("parry.weapon", false);
    }
    //Copy pasta
    static String getParrySound() {
        int x = Server.rand.nextInt(3);
        return x == 0 ? "sound.combat.parry2" : (x == 1 ? "sound.combat.parry3" : "sound.combat.parry1");
    }


    //Copy pasta
    private float checkShield(Creature creature, Creature defender, Item weapon, double damage, byte type, byte pos, float chanceToHit, double defBonus) {
        if (getCombatHandled(defender).usedShieldThisRound > 1) {
            return 0.0f;
        }
        Skills defenderSkills = defender.getSkills();
        Item defShield = defender.getShield();
        double defCheck = 0.0;
        float blockPercent = 0.0f;
        if (defShield != null) {
            Item defweapon = defender.getPrimWeapon();
            if (defweapon != null && defweapon.isTwoHanded()) {
                return 0.0f;
            }
            Item defSecondWeapon = defender.getLefthandWeapon();
            if (defSecondWeapon != null && defSecondWeapon.isTwoHanded()) {
                return 0.0f;
            }
            if (!defShield.isArtifact()) {
                //++defender.getCombatHandler().usedShieldThisRound;
                ++getCombatHandled(defender).usedShieldThisRound;
            }
            if (VirtualZone.isCreatureShieldedVersusTarget(creature, defender)) {
                Skill defShieldSkill;
                block16 : {
                    int skillnum = -10;
                    defShieldSkill = null;
                    try {
                        skillnum = defShield.getPrimarySkill();
                        defShieldSkill = defenderSkills.getSkill(skillnum);
                    }
                    catch (NoSuchSkillException nss) {
                        if (skillnum == -10) break block16;
                        defShieldSkill = defenderSkills.learn(skillnum, 1.0f);
                    }
                }
                if (defShieldSkill != null) {
                    if (pos == 9) {
                        blockPercent = 100.0f;
                        if (defender.spamMode() && Servers.isThisATestServer()) {
                            defender.getCommunicator().sendCombatNormalMessage("Blocking left underarm.");
                        }
                    } else if (!(defender.getStatus().getStamina() < 300 && Server.rand.nextInt(10) != 0 || defender.isMoving() && defShieldSkill.getRealKnowledge() <= 40.0)) {
                        double shieldModifier = (float)(defShield.getSizeY() + defShield.getSizeZ()) / 2.0f * (defShield.getCurrentQualityLevel() / 100.0f);
                        double diff = Math.max(1.0, (double)chanceToHit - shieldModifier) - defBonus;
                        blockPercent = (float)defShieldSkill.skillCheck(diff, defShield, defShield.isArtifact() ? 50.0 : 0.0, creature.isNoSkillFor(defender) || getCombatHandled(defender).receivedShieldSkill, (float)(damage / 1000.0), defender, creature);
                        getCombatHandled(defender).receivedShieldSkill = true;
                        if (defender.spamMode() && Servers.isThisATestServer()) {
                            defender.getCommunicator().sendCombatNormalMessage("Shield parrying difficulty=" + diff + " including defensive bonus " + defBonus + " vs " + defShieldSkill.getKnowledge(defShield, 0.0) + " " + defender.zoneBonus + ":" + defender.getMovePenalty() + " gave " + blockPercent + ">0");
                        }
                        defender.getStatus().modifyStamina((int)(-300.0f - (float) defShield.getWeightGrams() / 20.0f));
                    }
                    if (blockPercent > 0.0f) {
                        float damageMod = !weapon.isBodyPart() && weapon.isWeaponCrush() ? 1.5E-5f : (type == 0 ? 1.0E-6f : 5.0E-6f);
                        if (defender.isPlayer()) {
                            defShield.setDamage(defShield.getDamage() + Math.max(0.01f, damageMod * (float)damage * defShield.getDamageModifier()));
                        }
                        this.sendShieldMessage(creature, defender, weapon, blockPercent);
                    }
                }
            }
        }
        return blockPercent;
    }
    //Copy pasta
    public boolean setDamage(Creature creature, Creature defender, Item attWeapon, double ddamage, byte position, byte _type) {
        float armourMod = defender.getArmourMod();
        boolean metalArmour = false;
        Item armour = null;
        float bounceWoundPower = 0.0f;
        float evasionChance = Armour.getBlockOddsFor(defender.getArmourType(), armour, attWeapon, _type, armourMod);
        if (armourMod == 1.0f || defender.isVehicle() || defender.isKingdomGuard()) {
            try {
                armour = defender.getArmour((byte)Armour.getArmourPosForPos(position));
                armourMod = !defender.isKingdomGuard() ? Armour.getArmourModFor(armour, _type) : (armourMod *= Armour.getArmourModFor(armour, _type));
                defender.sendToLoggers("YOU ARMORMOD " + armourMod, (byte) 2);
                creature.sendToLoggers(defender.getName() + " ARMORMOD " + armourMod, (byte) 2);
                if (defender.isPlayer() || defender.isHorse()) {
                    armour.setDamage(armour.getDamage() + Math.max(0.01f, Math.min(1.0f, (float)(ddamage * Weapon.getMaterialArmourDamageBonus(attWeapon.getMaterial()) * (double)Armour.getArmourDamageModFor(armour, _type) / 1200000.0) * armour.getDamageModifier())));
                }
                CombatEngine.checkEnchantDestruction(attWeapon, armour, defender);
                if (armour.isMetal()) {
                    metalArmour = true;
                }
                evasionChance = !defender.isPlayer() ? Armour.getCreatureBlockOddsFor(_type, armour, defender.getArmourType()) : Armour.getBlockOddsFor(0, armour, attWeapon, _type, armourMod);
                evasionChance *= 1.0f + ItemBonus.getGlanceBonusFor(armour.getArmourType(), _type, attWeapon, defender);
            }
            catch (NoArmourException ignored) { }
            catch (NoSpaceException nsp) {
                logger.log(Level.WARNING, defender.getName() + " no armour space on loc " + position);
            }
            if (armour == null && defender.getBonusForSpellEffect(Enchants.CRET_OAKSHELL) > 0.0f) {
                if (!CombatEngine.isEye(position) || defender.isUnique()) {
                    float omod = 100.0f;
                    float minmod = 0.6f;
                    if (!defender.isPlayer()) {
                        omod = 300.0f;
                        minmod = 0.8f;
                    } else if (defender.getBonusForSpellEffect(Enchants.CRET_OAKSHELL) > 70.0f) {
                        bounceWoundPower = defender.getBonusForSpellEffect(Enchants.CRET_OAKSHELL);
                    }
                    if (armourMod >= 1.0f) {
                        armourMod = 0.2f + (float)(1.0 - Server.getBuffedQualityEffect(defender.getBonusForSpellEffect(Enchants.CRET_OAKSHELL) / omod)) * minmod;
                        evasionChance = (float)Server.getBuffedQualityEffect(defender.getBonusForSpellEffect(Enchants.CRET_OAKSHELL) / 100.0f) / 2.5f;
                    } else {
                        armourMod = Math.min(armourMod, 0.2f + (float)(1.0 - Server.getBuffedQualityEffect(defender.getBonusForSpellEffect(Enchants.CRET_OAKSHELL) / omod)) * minmod);
                    }
                }
            } else if (defender.isReborn()) {
                armourMod = (float)(1.0 - Server.getBuffedQualityEffect(defender.getStrengthSkill() / 100.0));
            }
        }
        if (defender.isUnique()) {
            evasionChance = 0.5f;
        }
        if (!attWeapon.isBodyPartAttached() && creature.isPlayer()) {
            boolean rust = defender.hasSpellEffect((byte) 70);
            if (rust) {
                creature.getCommunicator().sendAlertServerMessage("Your " + attWeapon.getName() + " takes excessive damage from " + defender.getNameWithGenus() + ".");
            }
            float mod = rust ? 5.0f : 1.0f;
            attWeapon.setDamage(attWeapon.getDamage() + Math.min(1.0f, (float)(ddamage * (double)armourMod / 1000000.0)) * attWeapon.getDamageModifier() * mod);
        }
        double defdamage = ddamage * (double)ItemBonus.getDamReductionBonusFor(armour != null ? armour.getArmourType() : (int)defender.getArmourType(), _type, attWeapon, defender);
        if (defender.isPlayer()) {
            if (((Player)defender).getAlcohol() > 50.0f) {
                defdamage *= 0.5;
            }
            if (getCreatureFightLevel(defender) >= 5) {
                defdamage *= 0.5;
            }
        }
        if (defender.hasTrait(2)) {
            defdamage *= 0.9;
        }
        if (creature.hasSpellEffect((byte) 67) && !attWeapon.isArtifact()) {
            crit = true;
        }
        if (crit && !defender.isUnique()) {
            armourMod *= 1.5f;
        }
        if (defender.getTemplate().isTowerBasher() && (creature.isSpiritGuard() || creature.isKingdomGuard())) {
            float mod = 1.0f / defender.getArmourMod();
            defdamage = Math.max((double)((float)(500 + Server.rand.nextInt(1000)) * mod), defdamage);
        }
        if (Server.rand.nextFloat() < evasionChance) {
            if (creature.spamMode()) {
                ArrayList<MulticolorLineSegment> segments = new ArrayList<>();
                segments.add(new MulticolorLineSegment("Your attack glances off ", (byte) 0));
                segments.add(new CreatureLineSegment(defender));
                segments.add(new MulticolorLineSegment("'s armour.", (byte) 0));
                creature.getCommunicator().sendColoredMessageCombat(segments);
            }
            if (defender.spamMode()) {
                defender.getCommunicator().sendCombatNormalMessage("The attack to the " + defender.getBody().getWoundLocationString(position) + " glances off your armour.");
            }
            creature.sendToLoggers(defender.getName() + " GLANCE", (byte) 2);
            defender.sendToLoggers("YOU GLANCE", (byte) 2);
        } else if (defdamage * (double)armourMod >= 500.0) {
            ItemSpellEffects speffs;
            float champMod;
            float extraDmg;
            SpellEffect speff;
            if (creature.hasSpellEffect(Enchants.CRET_TRUESTRIKE) && !attWeapon.isArtifact()) {
                creature.removeTrueStrike();
            }
            if (attWeapon != null && !attWeapon.isBodyPartRemoved() && !attWeapon.isWeaponBow()) {
                try {
                    int primweaponskill = 10052;
                    if (!attWeapon.isBodyPartAttached()) {
                        primweaponskill = attWeapon.getPrimarySkill();
                    }
                    try {
                        Skill pwsk = creature.getSkills().getSkill(primweaponskill);
                        double d = pwsk.skillCheck(pwsk.getKnowledge(), attWeapon, 0.0, defender.isNoSkillFor(creature), (float)defdamage * armourMod / 1000.0f);
                    }
                    catch (NoSuchSkillException nss1) {
                        creature.getSkills().learn(primweaponskill, 1.0f);
                    }
                }
                catch (NoSuchSkillException primweaponskill) {
                    // empty catch block
                }
            }
            if (creature.spamMode() && Servers.isThisATestServer()) {
                creature.getCommunicator().sendCombatSafeMessage("Damage=" + defdamage + "*" + armourMod + "=" + defdamage * (double)armourMod + " crit=" + crit);
            }
            if (defender.spamMode() && Servers.isThisATestServer()) {
                defender.getCommunicator().sendCombatAlertMessage("Damage=" + defdamage + "*" + armourMod + "=" + defdamage * (double)armourMod + " crit=" + crit);
            }
            creature.sendToLoggers(defender.getName() + " DAMAGED " + defdamage * (double)armourMod + " crit=" + crit, (byte) 2);
            defender.sendToLoggers("YOU DAMAGED " + defdamage * (double)armourMod + " crit=" + crit, (byte) 2);
            Battle battle = defender.getBattle();
            dead = false;
            float poisdam = attWeapon.getSpellVenomBonus();
            if (poisdam > 0.0f) {
                float half = Math.max(1.0f, poisdam / 2.0f);
                poisdam = half + (float)Server.rand.nextInt((int)half);
                _type = (byte)5;
                defdamage *= 0.8;
            }
            float f = champMod = defender.isChampion() ? 0.4f : 1.0f;
            if (armour != null && armour.getSpellPainShare() > 0.0f) {
                bounceWoundPower = armour.getSpellPainShare();
                int rarityModifier = Math.max(1, armour.getRarity() * 5);
                speff = armour.getSpellEffect(Enchants.BUFF_SHARED_PAIN);
                if (speff != null && Server.rand.nextInt(Math.max(2, (int)((float)rarityModifier * speff.power * 80.0f))) == 0) {
                    speff.setPower(speff.getPower() - 1.0f);
                    if (speff.getPower() <= 0.0f && (speffs = armour.getSpellEffects()) != null) {
                        speffs.removeSpellEffect(speff.type);
                    }
                }
            }
            if (defender.isUnique() && creature.isUnique() && defender.getStatus().damage > 10000) {
                defender.setTarget(-10, true);
                creature.setTarget(-10, true);
                defender.setOpponent(null);
                creature.setOpponent(null);
                try {
                    defender.checkMove();
                }
                catch (Exception rarityModifier) {
                    // empty catch block
                }
                try {
                    creature.checkMove();
                }
                catch (Exception rarityModifier) {
                    // empty catch block
                }
            }
            if (defender.isSparring(creature)) {
                if ((double)defender.getStatus().damage + defdamage * (double)armourMod * 2.0 > 65535.0) {
                    defender.setTarget(-10, true);
                    creature.setTarget(-10, true);
                    defender.setOpponent(null);
                    creature.setOpponent(null);
                    creature.getCommunicator().sendCombatSafeMessage("You win against " + defender.getName() + "! Congratulations!");
                    defender.getCommunicator().sendCombatNormalMessage("You lose against " + creature.getName() + " who stops just before finishing you off!");
                    Server.getInstance().broadCastAction(creature.getName() + " defeats " + defender.getName() + " while sparring!", creature, defender, 10);
                    creature.getCommunicator().sendCombatOptions(NO_COMBAT_OPTIONS, (short) 0);
                    creature.getCommunicator().sendSpecialMove((short) -1, "N/A");
                    creature.achievement(39);
                    return true;
                }
                if (bounceWoundPower > 0.0f && defdamage * (double)bounceWoundPower * (double)champMod / 300.0 > 500.0 && (double)creature.getStatus().damage + defdamage * (double)bounceWoundPower * (double)champMod / 300.0 > 65535.0) {
                    defender.setTarget(-10, true);
                    creature.setTarget(-10, true);
                    defender.setOpponent(null);
                    creature.setOpponent(null);
                    defender.getCommunicator().sendCombatSafeMessage("You win against " + creature.getName() + "! Congratulations!");
                    creature.getCommunicator().sendCombatNormalMessage("You lose against " + defender.getName() + " whose armour enchantment almost finished you off!");
                    Server.getInstance().broadCastAction(defender.getName() + " defeats " + creature.getName() + " while sparring!", defender, creature, 10);
                    creature.getCommunicator().sendCombatOptions(NO_COMBAT_OPTIONS, (short) 0);
                    creature.getCommunicator().sendSpecialMove((short) -1, "N/A");
                    creature.achievement(39);
                    return true;
                }
            }
            if (defender.getStaminaSkill().getKnowledge() < 2.0) {
                defender.die(false);
                creature.achievement(223);
                dead = true;
            } else if (attWeapon.getWeaponSpellDamageBonus() > 0.0f) {
                defdamage += defdamage * (double)attWeapon.getWeaponSpellDamageBonus() / 500.0;
                dead = CombatEngine.addWound(creature, defender, _type, position, defdamage, armourMod, attString, battle, Server.rand.nextInt((int)Math.max(1.0f, attWeapon.getWeaponSpellDamageBonus())), poisdam, false);
                if (attWeapon.isWeaponCrush() && attWeapon.getWeightGrams() > 4000 && armour != null && armour.getTemplateId() == ItemList.helmetGreat) {
                    defender.achievement(49);
                }
            } else {
                Wound[] w;
                int dmgBefore = defender.getStatus().damage;
                dead = CombatEngine.addWound(creature, defender, _type, position, defdamage, armourMod, attString, battle, 0.0f, poisdam, false);
                if (attWeapon.getSpellLifeTransferModifier() > 0.0f && dmgBefore != defender.getStatus().damage && defdamage * (double)armourMod * (double)attWeapon.getSpellLifeTransferModifier() / (double)(creature.isChampion() ? 1000.0f : 500.0f) > 500.0 && creature.getBody() != null && creature.getBody().getWounds() != null && (w = creature.getBody().getWounds().getWounds()).length > 0) {
                    w[0].modifySeverity(- (int)(defdamage * (double)attWeapon.getSpellLifeTransferModifier() / (double)(creature.isChampion() ? 1000.0f : (creature.getCultist() != null && creature.getCultist().healsFaster() ? 250.0f : 500.0f))));
                }
            }
            byte defenderFightLevel = getCreatureFightLevel(defender);
            if (creature.isPlayer() != defender.isPlayer() && defdamage > 10000.0 && defenderFightLevel > 0) {
                //defender.fightlevel = (byte)(defender.fightlevel - 1);
                try {
                    // Setting private field using reflection
                    ReflectionUtil.setPrivateField(defender, ReflectionUtil.getField(defender.getClass(), "fightlevel"), defenderFightLevel - 1);
                } catch (IllegalAccessException | NoSuchFieldException e) {
                    e.printStackTrace();
                }
                defender.getCommunicator().sendCombatNormalMessage("You lose some focus.");
                if (defender.isPlayer()) {
                    defender.getCommunicator().sendFocusLevel(defender.getWurmId());
                }
            }
            if (!dead && attWeapon.getSpellDamageBonus() > 0.0f && ((double)(attWeapon.getSpellDamageBonus() / 300.0f) * defdamage > 500.0 || crit)) {
                dead = defender.addWoundOfType(creature, Wound.TYPE_BURN, position, false, armourMod, false, (double)(attWeapon.getSpellDamageBonus() / 300.0f) * defdamage);
            }
            if (!dead && attWeapon.getSpellFrostDamageBonus() > 0.0f && ((double)(attWeapon.getSpellFrostDamageBonus() / 300.0f) * defdamage > 500.0 || crit)) {
                dead = defender.addWoundOfType(creature, Wound.TYPE_COLD, position, false, armourMod, false, (double)(attWeapon.getSpellFrostDamageBonus() / 300.0f) * defdamage);
            }
            if (!dead && Weapon.getMaterialExtraWoundMod(attWeapon.getMaterial()) > 0.0f && ((double)(extraDmg = Weapon.getMaterialExtraWoundMod(attWeapon.getMaterial())) * defdamage > 500.0 || crit)) {
                dead = defender.addWoundOfType(creature, Weapon.getMaterialExtraWoundType(attWeapon.getMaterial()), position, false, armourMod, false, (double)extraDmg * defdamage);
            }
            if (armour != null || bounceWoundPower > 0.0f) {
                if (bounceWoundPower > 0.0f) {
                    if (creature.isUnique()) {
                        if (armour != null) {
                            defender.getCommunicator().sendCombatNormalMessage("The " + creature.getName() + " ignores the effects of the " + armour.getName() + ".");
                        }
                    } else if (defdamage * (double)bounceWoundPower * (double)champMod / 300.0 > 500.0) {
                        CombatEngine.addBounceWound(defender, creature, _type, position, defdamage * (double)bounceWoundPower * (double)champMod / 300.0, armourMod);
                    }
                } else if (armour != null && armour.getSpellSlowdown() > 0.0f) {
                    if (creature.getMovementScheme().setWebArmourMod(true, armour.getSpellSlowdown())) {
                        creature.setWebArmourModTime(armour.getSpellSlowdown() / 10.0f);
                        creature.getCommunicator().sendCombatAlertMessage("Dark stripes spread along your " + attWeapon.getName() + " from " + defender.getNamePossessive() + " armour. You feel drained.");
                    }
                    int rm = Math.max(1, armour.getRarity() * 5);
                    speff = armour.getSpellEffect((byte) 46);
                    if (speff != null && Server.rand.nextInt(Math.max(2, (int)((float)rm * speff.power * 80.0f))) == 0) {
                        speff.setPower(speff.getPower() - 1.0f);
                        if (speff.getPower() <= 0.0f && (speffs = armour.getSpellEffects()) != null) {
                            speffs.removeSpellEffect(speff.type);
                        }
                    }
                }
            }
            if (!Players.getInstance().isOverKilling(creature.getWurmId(), defender.getWurmId()) && attWeapon.getSpellExtraDamageBonus() > 0.0f) {
                if (defender.isPlayer() && !defender.isNewbie()) {
                    SpellEffect speff2 = attWeapon.getSpellEffect((byte) 45);
                    float mod = 1.0f;
                    if (defdamage * (double)armourMod * (double)champMod < 5000.0) {
                        mod = (float)(defdamage * (double)armourMod * (double)champMod / 5000.0);
                    }
                    if (speff2 != null) {
                        speff2.setPower(Math.min(10000.0f, speff2.power + (dead ? 20.0f : 2.0f * mod)));
                    }
                } else if (!defender.isPlayer() && !defender.isGuard() && dead) {
                    SpellEffect speff3 = attWeapon.getSpellEffect((byte) 45);
                    float mod = 1.0f;
                    if (speff3.getPower() > 5000.0f && !Servers.isThisAnEpicOrChallengeServer()) {
                        mod = Math.max(0.5f, 1.0f - (speff3.getPower() - 5000.0f) / 5000.0f);
                    }
                    if (speff3 != null) {
                        speff3.setPower(Math.min(10000.0f, speff3.power + defender.getBaseCombatRating() * mod));
                    }
                }
            }
            if (dead) {
                if (battle != null) {
                    battle.addCasualty(creature, defender);
                }
                if (defender.isSparring(creature) && (double)defender.getStatus().damage + defdamage * (double)armourMod * 2.0 > 65535.0) {
                    creature.achievement(39);
                    creature.getCommunicator().sendCombatSafeMessage("You accidentally slay " + defender.getName() + "! Congratulations!");
                    defender.getCommunicator().sendCombatNormalMessage("You lose against " + creature.getName() + " who unfortunately fails to stop just before finishing you off!");
                    Server.getInstance().broadCastAction(creature.getName() + " defeats and accidentally slays " + defender.getName() + " while sparring!", creature, defender, 10);
                }
                if (creature.isDuelling(defender)) {
                    creature.achievement(37);
                }
            } else if (defdamage > 30000.0 && (double)Server.rand.nextInt(100000) < defdamage) {
                Skill defBodyControl = null;
                try {
                    defBodyControl = defender.getSkills().getSkill(104);
                }
                catch (NoSuchSkillException nss) {
                    defBodyControl = defender.getSkills().learn(104, 1.0f);
                }
                if (defBodyControl.skillCheck(defdamage / 10000.0, (double)(getCombatHandled(defender).getFootingModifier(defender, attWeapon, creature) * 10.0f), false, 10.0f, defender, creature) < 0.0) {
                    defender.getCombatHandler().setCurrentStance(-1, (byte) 8);
                    defender.getStatus().setStunned((byte)Math.max(3.0, defdamage / 10000.0), false);
                    ArrayList<MulticolorLineSegment> segments = new ArrayList<>();
                    segments.add(new CreatureLineSegment(defender));
                    segments.add(new MulticolorLineSegment(" is knocked senseless from the hit.", (byte) 0));
                    creature.getCommunicator().sendColoredMessageCombat(segments);
                    defender.getCommunicator().sendCombatNormalMessage("You are knocked senseless from the hit.");
                    segments.clear();
                    segments.add(new CreatureLineSegment(creature));
                    segments.add(new MulticolorLineSegment(" knocks ", (byte) 0));
                    segments.add(new CreatureLineSegment(defender));
                    segments.add(new MulticolorLineSegment(" senseless with " + creature.getHisHerItsString() + " hit!", (byte) 0));
                    MessageServer.broadcastColoredAction(segments, creature, defender, 5, true);
                }
            }
            int numsound = Server.rand.nextInt(3);
            if (defdamage > 10000.0) {
                if (numsound == 0) {
                    SoundPlayer.playSound("sound.combat.fleshbone1", defender, 1.6f);
                } else if (numsound == 1) {
                    SoundPlayer.playSound("sound.combat.fleshbone2", defender, 1.6f);
                } else if (numsound == 2) {
                    SoundPlayer.playSound("sound.combat.fleshbone3", defender, 1.6f);
                }
            } else if (metalArmour) {
                if (numsound == 0) {
                    SoundPlayer.playSound("sound.combat.fleshmetal1", defender, 1.6f);
                } else if (numsound == 1) {
                    SoundPlayer.playSound("sound.combat.fleshmetal2", defender, 1.6f);
                } else if (numsound == 2) {
                    SoundPlayer.playSound("sound.combat.fleshmetal3", defender, 1.6f);
                }
            } else if (numsound == 0) {
                SoundPlayer.playSound("sound.combat.fleshhit1", defender, 1.6f);
            } else if (numsound == 1) {
                SoundPlayer.playSound("sound.combat.fleshhit2", defender, 1.6f);
            } else if (numsound == 2) {
                SoundPlayer.playSound("sound.combat.fleshhit3", defender, 1.6f);
            }
            SoundPlayer.playSound(defender.getHitSound(), defender, 1.6f);
        } else {
            if (creature.spamMode()) {
                ArrayList<MulticolorLineSegment> segments = new ArrayList<>();
                segments.add(new CreatureLineSegment(defender));
                segments.add(new MulticolorLineSegment(" takes no real damage from the hit to the " + defender.getBody().getWoundLocationString(position) + ".", (byte) 0));
                creature.getCommunicator().sendColoredMessageCombat(segments);
            }
            if (defender.spamMode()) {
                defender.getCommunicator().sendCombatNormalMessage("You take no real damage from the blow to the " + defender.getBody().getWoundLocationString(position) + ".");
            }
            creature.sendToLoggers(defender.getName() + " NO DAMAGE", (byte) 2);
            defender.sendToLoggers("YOU TAKE NO DAMAGE", (byte) 2);
        }
        return dead;
    }
    //Copy pasta
    public void setKillEffects(Creature creature, Creature performer, Creature defender) {
        float ms;
        defender.setOpponent(null);
        defender.setTarget(-10, true);
        if (defender.getWurmId() == performer.target) {
            performer.setTarget(-10, true);
        }
        defender.getCombatHandler().setCurrentStance(-1, (byte) 15);
        performer.getCombatHandler().setCurrentStance(-1, (byte) 15);
        if (performer.isUndead()) {
            performer.healRandomWound(100);
            float nut = (float)(50 + Server.rand.nextInt(49)) / 100.0f;
            performer.getStatus().refresh(nut, true);
        }
        if (performer.getCitizenVillage() != null) {
            performer.getCitizenVillage().removeTarget(defender);
        }
        if (defender.isPlayer() && performer.isPlayer()) {
            if (!defender.isOkToKillBy(performer)) {
                if (performer.hasAttackedUnmotivated() && performer.getReputation() < 0) {
                    performer.setReputation(performer.getReputation() - 10);
                } else {
                    performer.setReputation(performer.getReputation() - 20);
                }
            }
            if (!defender.isFriendlyKingdom(performer.getKingdomId()) && !Players.getInstance().isOverKilling(performer.getWurmId(), defender.getWurmId())) {
                if (performer.getKingdomTemplateId() == 3 || performer.getDeity() != null && performer.getDeity().hateGod) {
                    performer.maybeModifyAlignment(-5.0f);
                } else {
                    performer.maybeModifyAlignment(5.0f);
                }
                if (getCombatHandled(performer).currentStrength == 0) {
                    performer.achievement(43);
                }
            }
        } else if (!defender.isPlayer() && !performer.isPlayer() && defender.isPrey() && performer.isCarnivore()) {
            performer.getStatus().modifyHunger(-65000, 99.0f);
        }
        if (!defender.isPlayer() && !defender.isReborn() && performer.isPlayer()) {
            if (defender.isKingdomGuard() && defender.getKingdomId() == performer.getKingdomId()) {
                performer.achievement(44);
            }
            try {
                int tid = defender.getTemplate().getTemplateId();
                if (CreatureTemplate.isDragon(tid)) {
                    performer.addTitle(Titles.Title.DragonSlayer);
                } else if (tid == CreatureTemplateIds.TROLL_CID || tid == CreatureTemplateIds.TROLL_KING_CID) {
                    performer.addTitle(Titles.Title.TrollSlayer);
                } else if (tid == CreatureTemplateIds.FOREST_GIANT_CID || tid == CreatureTemplateIds.CYCLOPS_CID) {
                    performer.addTitle(Titles.Title.GiantSlayer);
                }
            }
            catch (Exception ex) {
                logger.log(Level.WARNING, defender.getName() + " and " + performer.getName() + ":" + ex.getMessage(), ex);
            }
            if (performer.getDeity() != null && performer.getDeity().number == Deities.DEITY_MAGRANON) {
                performer.maybeModifyAlignment(0.5f);
            }
            if (performer.getDeity() != null && performer.getDeity().number == Deities.DEITY_LIBILA) {
                performer.maybeModifyAlignment(-0.5f);
            }
        }
        if (performer.getPrimWeapon() != null && (ms = performer.getPrimWeapon().getSpellMindStealModifier()) > 0.0f && !defender.isPlayer() && defender.getKingdomId() != performer.getKingdomId()) {
            Skills s = defender.getSkills();
            int r = Server.rand.nextInt(s.getSkills().length);
            Skill toSteal = s.getSkills()[r];
            float skillStolen = ms / 100.0f * 0.1f;
            try {
                Skill owned = creature.getSkills().getSkill(toSteal.getNumber());
                if (owned.getKnowledge() < toSteal.getKnowledge()) {
                    double smod = (toSteal.getKnowledge() - owned.getKnowledge()) / 100.0;
                    owned.setKnowledge(owned.getKnowledge() + (double)skillStolen * smod, false);
                    creature.getCommunicator().sendSafeServerMessage("The " + performer.getPrimWeapon().getName() + " steals some " + toSteal.getName() + ".");
                }
            }
            catch (NoSuchSkillException owned) {
                // empty catch block
            }
        }
    }



    //Copy pasta
    private double getParryMod(Creature creature) {
        try {
            // Unavoidable reflection on the base combat handler due to other classes adding modifiers via addParryModifier and removeParryModifier
            CombatHandler ch = creature.getCombatHandler();
            Set<DoubleValueModifier> parryModifiers = ReflectionUtil.getPrivateField(ch, ReflectionUtil.getField(ch.getClass(), "parryModifiers"));
            if (parryModifiers == null) {
                return 1.0;
            }
            double doubleModifier = 1.0;
            for (DoubleValueModifier lDoubleValueModifier : parryModifiers) {
                doubleModifier += lDoubleValueModifier.getModifier();
            }
            return doubleModifier;
        } catch (IllegalAccessException | NoSuchFieldException e) {
            e.printStackTrace();
        }
        return 1.0;
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
                Skill bcskill;
                try {
                    bcskill = attacker.getSkills().getSkill(104);
                }
                catch (NoSuchSkillException nss) {
                    bcskill = attacker.getSkills().learn(104, 1.0f);
                }
                    byte attackerFightLevel = getCreatureFightLevel(attacker);
                    if (bcskill.skillCheck(Math.abs(Math.max(Math.min(steepness[1], 99), -99)), attackerFightLevel * 10, true, 1.0f) > 0.0) {
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
        float calcspeed = this.getWeaponSpeed(attacker, weapon);
        calcspeed += timeMod;
        if (weapon.getSpellSpeedBonus() != 0.0f) {
            calcspeed = (float)((double)calcspeed - 0.5 * (double)(weapon.getSpellSpeedBonus() / 100.0f));
        }
        if (!weapon.isArtifact() && attacker.getBonusForSpellEffect(Enchants.CRET_CHARGE) > 0.0f) {
            calcspeed -= 0.5f; //Frantic Charge
        }
        if (weapon.isTwoHanded() && this.currentStrength == 3) {
            calcspeed *= 0.9f; //Aggressive stance
        }
        if (!Features.Feature.METALLIC_ITEMS.isEnabled() && weapon.getMaterial() == Materials.MATERIAL_GLIMMERSTEEL) {
            calcspeed *= 0.9f; //Glimmersteel
        }
        if (attacker.getStatus().getStamina() < 2000) {
            calcspeed += 1.0f; //Low Stamina
        }
        calcspeed = (float)((double)calcspeed - attacker.getMovementScheme().getWebArmourMod() * 10.0);//Don't understand this.
        //if (attacker.hasSpellEffect((byte) 66)) {
        if (attacker.hasSpellEffect(Enchants.CRET_KARMASLOW)) {
            calcspeed *= 2.0f; //Karma Slow
        }
        return Math.max(CombatHandledMod.minimumSwingTimer, calcspeed);
    }
    //Copy pasta
    protected float getWeaponSpeed(Creature attacker, Item _weapon) {
        float flspeed;
        float knowl = 0.0f;
        int spskillnum = SkillList.WEAPONLESS_FIGHTING;
        if (_weapon.isBodyPartAttached()) {
            flspeed = attacker.getBodyWeaponSpeed(_weapon);
        } else {
            flspeed = Weapon.getBaseSpeedForWeapon(_weapon);
            try {
                spskillnum = _weapon.getPrimarySkill();
            }
            catch (NoSuchSkillException ignored) { }
        }
        try {
            Skill wSkill = attacker.getSkills().getSkill(spskillnum);
            knowl = (float)wSkill.getRealKnowledge();
        }
        catch (NoSuchSkillException ignored) { }
        if (!attacker.isGhost()) {
            flspeed -= flspeed * 0.1f * knowl / 100.0f; //Weapon skill increases attack speed.
        }
        return flspeed;
    }
    //Copy pasta
    private float getFlankingModifier(Creature attacker, Creature opponent) {
        if (opponent == null) {
            return 1.0F;
        } else {
            float attAngle = this.getDirectionTo(attacker, opponent);
            if (opponent.getVehicle() > -10L) {
                Vehicle vehic = Vehicles.getVehicleForId(opponent.getVehicle());
                if (vehic != null && vehic.isCreature()) {
                    try {
                        Creature ridden = Server.getInstance().getCreature(opponent.getVehicle());
                        attAngle = this.getDirectionTo(attacker, ridden);
                    } catch (Exception var5) {
                        logger.log(Level.INFO, "No creature for id " + opponent.getVehicle());
                    }
                }
            }

            if (attAngle > 140.0F && attAngle < 220.0F) {
                return attAngle > 160.0F && attAngle < 200.0F ? 1.25F : 1.1F;
            } else {
                return 1.0F;
            }
        }
    }
    //Copy pasta
    private float getHeightModifier(Creature attacker, Creature opponent) {
        if (opponent == null) {
            return 1.0F;
        } else {
            float diff = attacker.getPositionZ() + attacker.getAltOffZ() - (opponent.getPositionZ() + opponent.getAltOffZ());
            if (diff > 1.0F) {
                return diff > 2.0F ? 1.1F : 1.05F;
            } else if (diff < -1.0F) {
                return diff < -2.0F ? 0.9F : 0.95F;
            } else {
                return 1.0F;
            }
        }
    }
    //Copy pasta
    private float getDirectionTo(Creature attacker, Creature opponent) {
        float defAngle = Creature.normalizeAngle(opponent.getStatus().getRotation());
        double newrot = Math.atan2((double)(attacker.getStatus().getPositionY() - opponent.getStatus().getPositionY()), (double)(attacker.getStatus().getPositionX() - opponent.getStatus().getPositionX()));
        float attAngle = (float)(newrot * 57.29577951308232D) + 90.0F;
        return Creature.normalizeAngle(attAngle - defAngle);
    }
    //Copy pasta
    public float getCombatKnowledgeSkill(Creature attacker) {
        float knowl = 0.0F;
        int primarySkill = 10052;

        Skill unarmed;
        try {
            if (!attacker.getPrimWeapon().isBodyPartAttached()) {
                primarySkill = attacker.getPrimWeapon().getPrimarySkill();
            }

            unarmed = attacker.getSkills().getSkill(primarySkill);
            knowl = (float)unarmed.getKnowledge(attacker.getPrimWeapon(), 0.0D);
        } catch (NoSuchSkillException ignored) { }

        if (knowl == 0.0F && !attacker.isPlayer()) {
            unarmed = attacker.getFightingSkill();
            knowl = (float)unarmed.getKnowledge(0.0D);
        }

        if (attacker.getPrimWeapon().isBodyPartAttached()) {
            // Bearpaws
            knowl += attacker.getBonusForSpellEffect(Enchants.CRET_BEARPAW) / 5.0F;
        }

        Seat s = attacker.getSeat();
        if (s != null) {
            knowl *= s.manouvre;
        }

        if (attacker.isOnHostileHomeServer()) {
            knowl *= 0.525F;
        }

        return knowl;
    }
    //Copy pasta
    private float getAlcMod(Creature attacker) {
        if (attacker.isPlayer()) {
            float alc = ((Player)attacker).getAlcohol();
            return alc < 20.0F ? (100.0F + alc) / 100.0F : Math.max(40.0F, 100.0F - alc) / 80.0F;
        } else {
            return 1.0F;
        }
    }
    //Copy pasta
    protected float getParryBonus(byte defenderStance, byte attackerStance) {
        if (isStanceParrying(defenderStance, attackerStance)) {
            return 0.8F;
        } else {
            return isStanceOpposing(defenderStance, attackerStance) ? 0.9F : 1.0F;
        }
    }
    //Copy pasta
    private byte getWoundPos(byte aStance, Creature aCreature) throws Exception {
        return aCreature.getBody().getRandomWoundPos(aStance);
    }
    //Copy pasta
    private void sendDodgeMessage(Creature creature, Creature defender, double defCheck, byte pos) {
        double power = (float)(defender.getBodyControl() / 3.0 - defCheck);
        String sstring = power > 20.0 ? "sound.combat.miss.heavy" : (power > 10.0 ? "sound.combat.miss.med" : "sound.combat.miss.light");
        SoundPlayer.playSound(sstring, creature, 1.6f);
        ArrayList<MulticolorLineSegment> segments = new ArrayList<>();
        segments.add(new CreatureLineSegment(defender));
        segments.add(new MulticolorLineSegment(" " + CombatEngine.getParryString(power) + " evades the blow to the " + defender.getBody().getWoundLocationString(pos) + ".", (byte) 0));
        if (creature.spamMode()) {
            creature.getCommunicator().sendColoredMessageCombat(segments);
        }
        if (defender.spamMode()) {
            segments.get(1).setText(" " + CombatEngine.getParryString(power) + " evade the blow to the " + defender.getBody().getWoundLocationString(pos) + ".");
            defender.getCommunicator().sendColoredMessageCombat(segments);
        }
        creature.sendToLoggers(defender.getName() + " EVADE", (byte) 2);
        defender.sendToLoggers("You EVADE", (byte) 2);
        defender.playAnimation("dodge", false);
    }
    //Copy pasta
    private void sendShieldMessage(Creature creature, Creature defender, Item weapon, float blockPercent) {
        ArrayList<MulticolorLineSegment> segments = new ArrayList<>();
        segments.add(new CreatureLineSegment(defender));
        segments.add(new MulticolorLineSegment(" raises " + defender.getHisHerItsString() + " shield and parries your " + attString + ".", (byte) 0));
        if (creature.spamMode()) {
            creature.getCommunicator().sendColoredMessageCombat(segments);
        }
        if (defender.spamMode()) {
            segments.get(1).setText(" raise your shield and parry against ");
            segments.add(new CreatureLineSegment(creature));
            segments.add(new MulticolorLineSegment("'s " + attString + ".", (byte) 0));
            defender.getCommunicator().sendColoredMessageCombat(segments);
        }
        Item defShield = defender.getShield();
        if (defShield.isWood()) {
            Methods.sendSound(defender, "sound.combat.shield.wood");
        } else {
            Methods.sendSound(defender, "sound.combat.shield.metal");
        }
        CombatEngine.checkEnchantDestruction(weapon, defShield, defender);
        creature.sendToLoggers(defender.getName() + " SHIELD " + blockPercent, (byte) 2);
        defender.sendToLoggers("You SHIELD " + blockPercent, (byte) 2);
        defender.playAnimation("parry.shield", false);
    }




    private static Skill getCreatureWeaponSkill(Creature creature, Item weapon) {
        Skills cSkills=creature.getSkills();
        Skill skill=null;
        if (weapon != null) {
            if (weapon.isBodyPart()) {
                try {
                    skill = cSkills.getSkill(SkillList.WEAPONLESS_FIGHTING);
                } catch (NoSuchSkillException var4) {
                    skill = cSkills.learn(SkillList.WEAPONLESS_FIGHTING, 1.0F);
                }
            } else {
                int skillnum=-10;
                try {
                    skillnum = weapon.getPrimarySkill();
                    skill = cSkills.getSkill(skillnum);
                } catch (NoSuchSkillException var3) {
                    if (skillnum != -10) {
                        skill = cSkills.learn(skillnum, 1.0F);
                    }
                }
            }
        }
        return skill;
    }

    public static byte getCreatureFightLevel(Creature creature){
        try {
            return ReflectionUtil.getPrivateField(creature, ReflectionUtil.getField(creature.getClass(), "fightlevel"));
        } catch (IllegalAccessException | NoSuchFieldException e) {
            return 0;
        }
    }


    //Copy pasta, only returns true for autofighting or AI controlled.
    protected boolean checkStanceChange(Creature attacker, Creature opponent){
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
                if (defender.isPlayer() && this.getSpeed(defender, defender.getPrimWeapon()) > (float)Server.rand.nextInt(10)) {
                    selectNewStance = false;
                }
                if (selectNewStance) {
                    mycr = getCombatHandled(defender).getCombatRating(defender, defender.getPrimWeapon(), opponent,  false);//COMBAT RATING
                    oppcr = getCombatHandled(opponent).getCombatRating(opponent, opponent.getPrimWeapon(), defender,  false);
                    knowl = this.getCombatKnowledgeSkill(defender);
                    if (knowl > 50.0f) {
                        selectStanceList.addAll(standardDefences);
                    }
                    if (!defender.isPlayer()) {
                        knowl += 20.0f;
                    }
                    selectStanceList.addAll(getCombatHandled(defender).getHighAttacks(null, true, defender, opponent, mycr, oppcr, knowl));
                    selectStanceList.addAll(getCombatHandled(defender).getMidAttacks(null, true, defender, opponent, mycr, oppcr, knowl));
                    selectStanceList.addAll(getCombatHandled(defender).getLowAttacks(null, true, defender, opponent, mycr, oppcr, knowl));
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
                        defender.setFightingStyle((byte) 1); //Aggressive stance
                    }
                } else if ((float)randInt > Math.min(90.0f, ((float)defender.getAggressivity() * defender.getStatus().getAggTypeModifier() + 20.0f) * defender.getStatus().getAggTypeModifier())) {
                    if (defender.getFightStyle() != 2) {
                        ArrayList<MulticolorLineSegment> segments = new ArrayList<MulticolorLineSegment>();
                        segments.add(new CreatureLineSegment(defender));
                        segments.add(new MulticolorLineSegment(" cowers.", (byte) 0));
                        opponent.getCommunicator().sendColoredMessageCombat(segments);
                        defender.setFightingStyle((byte) 2); //Defensive stance
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
                } else if (defender.opponent == opponent && (e = CombatHandled.getDefensiveActionEntry(getCombatHandled(opponent).currentStance)) == null) {
                    e = CombatHandled.getOpposingActionEntry(getCombatHandled(opponent).currentStance);
                }
            }
            if (e == null) {
                if (defender.combatRound > 2 && Server.rand.nextInt(2) == 0) {
                    if (defender.mayRaiseFightLevel()) {
                        e = Actions.actionEntrys[340];
                    }
                } else if (mycr - oppcr > 2.0f || getCombatHandled(defender).getSpeed(defender, defender.getPrimWeapon()) < 3.0f) {
                    if (CombatHandled.existsBetterOffensiveStance(getCombatHandled(defender).currentStance, getCombatHandled(opponent).currentStance) && (e = CombatHandled.changeToBestOffensiveStance(getCombatHandled(defender).currentStance, getCombatHandled(opponent).currentStance)) == null) {
                        e = CombatHandled.getNonDefensiveActionEntry(getCombatHandled(opponent).currentStance);
                    }
                } else if (mycr >= oppcr) {
                    if (defender.getStatus().damage < opponent.getStatus().damage) {
                        if (CombatHandled.existsBetterOffensiveStance(getCombatHandled(defender).currentStance, getCombatHandled(opponent).currentStance) && (e = CombatHandled.changeToBestOffensiveStance(getCombatHandled(defender).currentStance, getCombatHandled(opponent).currentStance)) == null) {
                            e = CombatHandled.getNonDefensiveActionEntry(getCombatHandled(opponent).currentStance);
                        }
                    } else {
                        e = CombatHandled.getDefensiveActionEntry(getCombatHandled(opponent).currentStance);
                        if (e == null) {
                            e = CombatHandled.getOpposingActionEntry(getCombatHandled(opponent).currentStance);
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
    protected List<ActionEntry> getHighAttacks(Item weapon, boolean auto,Creature attacker, Creature opponent, float mycr, float oppcr, float primweaponskill) {
        LinkedList<ActionEntry> tempList = new LinkedList<>();
        if (primweaponskill > (float)CombatHandler.getAttackSkillCap((short) 300)) {
            this.addToList(tempList, weapon, (short) 300, attacker, opponent, mycr, oppcr, primweaponskill);
        }
        if (primweaponskill > (float)CombatHandler.getAttackSkillCap((short) 288)) {
            this.addToList(tempList, weapon, (short) 288, attacker, opponent, mycr, oppcr, primweaponskill);
        }
        if (primweaponskill > (float)CombatHandler.getAttackSkillCap((short) 306)) {
            this.addToList(tempList, weapon, (short) 306, attacker, opponent, mycr, oppcr, primweaponskill);
        }
        if (!auto && tempList.size() > 0) {
            tempList.addFirst(new ActionEntry((short)(- tempList.size()), "High", "high"));
        }
        return tempList;
    }
    //Copy pasta
    protected List<ActionEntry> getMidAttacks(Item weapon, boolean auto, Creature attacker, Creature opponent, float mycr, float oppcr, float primweaponskill) {
        LinkedList<ActionEntry> tempList = new LinkedList<>();
        this.addToList(tempList, weapon, (short) 303, attacker, opponent, mycr, oppcr, primweaponskill);
        if (primweaponskill > (float)CombatHandler.getAttackSkillCap((short) 291)) {
            this.addToList(tempList, weapon, (short) 291, attacker, opponent, mycr, oppcr, primweaponskill);
        }
        if (primweaponskill > (float)CombatHandler.getAttackSkillCap((short) 309)) {
            this.addToList(tempList, weapon, (short) 309, attacker, opponent, mycr, oppcr, primweaponskill);
        }
        if (!auto && tempList.size() > 0) {
            tempList.addFirst(new ActionEntry((short)(- tempList.size()), "Mid", "Mid"));
        }
        return tempList;
    }
    //Copy pasta
    protected List<ActionEntry> getLowAttacks(Item weapon, boolean auto, Creature attacker, Creature opponent, float mycr, float oppcr, float primweaponskill) {
        LinkedList<ActionEntry> tempList = new LinkedList<>();
        if (primweaponskill > (float)CombatHandled.getAttackSkillCap((short) 297)) {
            this.addToList(tempList, weapon, (short) 297, attacker, opponent, mycr, oppcr, primweaponskill);
        }
        if (primweaponskill > (float)CombatHandled.getAttackSkillCap((short) 294)) {
            this.addToList(tempList, weapon, (short) 294, attacker, opponent, mycr, oppcr, primweaponskill);
        }
        if (primweaponskill > (float)CombatHandled.getAttackSkillCap((short) 312)) {
            this.addToList(tempList, weapon, (short) 312, attacker, opponent, mycr, oppcr, primweaponskill);
        }
        if (!auto && tempList.size() > 0) {
            tempList.addFirst(new ActionEntry((short)(- tempList.size()), "Low", "Low"));
        }
        return tempList;
    }
    //Copy pasta
    private void addToList(List<ActionEntry> list, Item weapon, short number, Creature attacker, Creature opponent, float mycr, float oppcr, float primweaponskill) {
        float movechance;
        if (attacker.isPlayer()) {
            movechance = getMoveChance(attacker, weapon, this.currentStance, Actions.actionEntrys[number], mycr, oppcr, primweaponskill);
        } else {
            movechance = getMoveChance(attacker, weapon, this.currentStance, Actions.actionEntrys[number], mycr, oppcr, primweaponskill);
        }

        if (movechance > 0.0F) {
            list.add(new ActionEntry(number, (int) movechance + "%, " + Actions.actionEntrys[number].getActionString(), "attack"));
        }
    }
    //Copy pasta
    private static final void checkIfHitVehicle(Creature creature, Creature opponent, double damage) {
        Vehicle vehic;
        if (creature.isBreakFence() && opponent.getVehicle() > -10 && (vehic = Vehicles.getVehicleForId(opponent.getVehicle())) != null && !vehic.creature) {
            try {
                Item i = Items.getItem(opponent.getVehicle());
                Server.getInstance().broadCastAction(creature.getNameWithGenus() + " hits the " + i.getName() + " with huge force!", creature, 10, true);
                i.setDamage(i.getDamage() + (float)(damage / 300000.0));
            }
            catch (NoSuchItemException i) {
                // empty catch block
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
    public static boolean isHigh(int stance) {
        return stance == 6 || stance == 1 || stance == 7;
    }
    //Copy pasta
    public static boolean isLow(int stance) {
        return stance == 4 || stance == 3 || stance == 10 || stance == 8;
    }
    //Copy pasta
    public static boolean isLeft(int stance) {
        return stance == 4 || stance == 5 || stance == 6;
    }
    //Copy pasta
    public static boolean isRight(int stance) {
        return stance == 3 || stance == 2 || stance == 1 || stance == 11;
    }
    //Copy pasta
    public static boolean isCenter(int stance) {
        return stance == 0 || stance == 9 || stance == 13 || stance == 14 || stance == 12;
    }
    //Copy pasta
    public static boolean isDefend(int stance) {
        return stance == 13 || stance == 14 || stance == 12 || stance == 11;
    }
    //Copy pasta
    protected static byte getStanceForAction(ActionEntry entry) {
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
    protected static ActionEntry getDefensiveActionEntry(byte opponentStance) {
        ListIterator<ActionEntry> it = selectStanceList.listIterator();
        while (it.hasNext()) {
            ActionEntry e = it.next();
            if (!CombatHandled.isStanceParrying(CombatHandled.getStanceForAction(e = Actions.actionEntrys[e.getNumber()]), opponentStance) || CombatHandled.isAtSoftSpot(CombatHandled.getStanceForAction(e), opponentStance)) continue;
            return e;
        }
        return null;
    }
    //Copy pasta
    protected static ActionEntry getOpposingActionEntry(byte opponentStance) {
        ListIterator<ActionEntry> it = selectStanceList.listIterator();
        while (it.hasNext()) {
            ActionEntry e = it.next();
            if (!CombatHandled.isStanceOpposing(CombatHandled.getStanceForAction(e = Actions.actionEntrys[e.getNumber()]), opponentStance) || CombatHandled.isAtSoftSpot(CombatHandled.getStanceForAction(e), opponentStance)) continue;
            return e;
        }
        return null;
    }
    //Copy pasta
    protected static ActionEntry getNonDefensiveActionEntry(byte opponentStance) {
        for (int x = 0; x < selectStanceList.size(); ++x) {
            int num = Server.rand.nextInt(selectStanceList.size());
            ActionEntry e = selectStanceList.get(num);
            if (CombatHandled.isStanceParrying(CombatHandled.getStanceForAction(e = Actions.actionEntrys[e.getNumber()]), opponentStance) || CombatHandled.isStanceOpposing(CombatHandled.getStanceForAction(e), opponentStance) || CombatHandled.isAtSoftSpot(CombatHandled.getStanceForAction(e), opponentStance)) continue;
            return e;
        }
        return null;
    }
    //Copy pasta
    public static boolean isStanceParrying(byte defenderStance, byte attackerStance) {
        if (attackerStance != 8 && attackerStance != 9) {
            if (defenderStance != 8 && defenderStance != 9) {
                if (defenderStance == 11) {
                    return attackerStance == 3 || attackerStance == 4 || attackerStance == 10;
                } else if (defenderStance == 12) {
                    return attackerStance == 1 || attackerStance == 6 || attackerStance == 7;
                } else if (defenderStance == 14) {
                    return attackerStance == 5 || attackerStance == 6 || attackerStance == 4;
                } else if (defenderStance != 13) {
                    return false;
                } else {
                    return attackerStance == 2 || attackerStance == 1 || attackerStance == 3;
                }
            } else {
                return false;
            }
        } else {
            return true;
        }
    }
    //Copy pasta
    public static boolean isStanceOpposing(byte defenderStance, byte attackerStance) {
        if (attackerStance != 8 && attackerStance != 9) {
            if (defenderStance != 8 && defenderStance != 9) {
                if (defenderStance == 1) {
                    return attackerStance == 6;
                } else if (defenderStance == 6) {
                    return attackerStance == 1;
                } else if (defenderStance == 4) {
                    return attackerStance == 3;
                } else if (defenderStance == 3) {
                    return attackerStance == 4;
                } else if (defenderStance == 5) {
                    return attackerStance == 2;
                } else if (defenderStance == 2) {
                    return attackerStance == 5;
                } else if (defenderStance == 7) {
                    return attackerStance == 7;
                } else if (defenderStance == 0) {
                    return attackerStance == 0;
                } else if (defenderStance == 10) {
                    return attackerStance == 10;
                } else {
                    return false;
                }
            } else {
                return false;
            }
        } else {
            return true;
        }
    }
    //Copy pasta
    public static float getMoveChance(Creature performer, Item weapon, int stance, ActionEntry entry, float mycr, float oppcr, float primweaponskill) {
        float basechance = 100.0F - oppcr * 2.0F + mycr + primweaponskill;
        float cost = 0.0F;
        if (isHigh(stance)) {
            if (entry.isAttackHigh()) {
                cost += 5.0F;
            } else if (entry.isAttackLow()) {
                cost += 10.0F;
            } else {
                cost += 3.0F;
            }
        } else if (isLow(stance)) {
            if (entry.isAttackHigh()) {
                cost += 10.0F;
            } else if (entry.isAttackLow()) {
                cost += 5.0F;
            } else {
                cost += 3.0F;
            }
        } else if (entry.isAttackHigh()) {
            cost += 5.0F;
        } else if (entry.isAttackLow()) {
            cost += 5.0F;
        }

        if (isRight(stance)) {
            if (entry.isAttackRight()) {
                cost += 3.0F;
            } else if (entry.isAttackLeft()) {
                cost += 10.0F;
            } else {
                cost += 3.0F;
            }
        } else if (isLeft(stance)) {
            if (entry.isAttackRight()) {
                cost += 10.0F;
            } else if (entry.isAttackLeft()) {
                cost += 3.0F;
            } else {
                cost += 3.0F;
            }
        } else if (entry.isAttackLeft()) {
            cost += 5.0F;
        } else if (entry.isAttackRight()) {
            cost += 5.0F;
        } else {
            cost += 10.0F;
        }

        if (entry.isAttackHigh() && !entry.isAttackLeft() && !entry.isAttackRight()) {
            cost += 3.0F;
        } else if (entry.isAttackLow() && !entry.isAttackLeft() && !entry.isAttackRight()) {
            cost += 3.0F;
        }

        cost = (float)((double)cost * (1.0D - manouvreMod));
        if (weapon != null) {
            cost += Weapon.getBaseSpeedForWeapon(weapon);
        }

        /*if (performer.fightlevel >= 2) {
            cost -= 10.0F;
        }*/
        byte performerFightLevel = getCreatureFightLevel(performer);
        if (performerFightLevel >= 2) {
            cost -= 10.0F;
        }

        return Math.min(100.0F, Math.max(0.0F, basechance - cost));
    }
    //Copy pasta
    protected static int getAttackSkillCap(short action) {
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
    protected static boolean isNextGoodStance(byte currentStance, byte nextStance, byte opponentStance) {
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
    public void setCurrentStance(Creature creature, int actNum, byte aStance) {
        this.currentStance = aStance;
        if (actNum > 0) {
            creature.sendStance(this.currentStance);
        } else if (aStance == 15) {
            creature.sendStance(this.currentStance);
        } else if (aStance == 8) {
            creature.playAnimation("stancerebound", true);
        } else if (aStance == 9) {
            creature.getStatus().setStunned(3.0f, false);
            creature.playAnimation("stanceopen", false);
        } else if (aStance == 0) {
            creature.sendStance(this.currentStance);
        }
    }
    //Copy pasta
    protected static boolean isAtSoftSpot(byte stanceChecked, byte stanceUnderAttack) {
        byte[] opponentSoftSpots;
        for (byte spot : opponentSoftSpots = CombatHandled.getSoftSpots(stanceChecked)) {
            if (spot != stanceUnderAttack) continue;
            return true;
        }
        return false;
    }
    //Copy pasta
    protected static byte[] getSoftSpots(byte currentStance) {
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
    protected static boolean existsBetterOffensiveStance(byte _currentStance, byte opponentStance) {
        if (CombatHandled.isAtSoftSpot(opponentStance, _currentStance)) {
            return false;
        }
        boolean isOpponentAtSoftSpot = CombatHandled.isAtSoftSpot(_currentStance, opponentStance);
        if (isOpponentAtSoftSpot || !CombatHandled.isStanceParrying(_currentStance, opponentStance) && !CombatHandled.isStanceOpposing(_currentStance, opponentStance)) {
            for (int x = 0; x < selectStanceList.size(); ++x) {
                int num = Server.rand.nextInt(selectStanceList.size());
                ActionEntry e = selectStanceList.get(num);
                byte nextStance = CombatHandled.getStanceForAction(e = Actions.actionEntrys[e.getNumber()]);
                if (!CombatHandled.isNextGoodStance(_currentStance, nextStance, opponentStance)) continue;
                return true;
            }
            return false;
        }
        for (int x = 0; x < selectStanceList.size(); ++x) {
            int num = Server.rand.nextInt(selectStanceList.size());
            ActionEntry e = selectStanceList.get(num);
            byte nextStance = CombatHandled.getStanceForAction(e = Actions.actionEntrys[e.getNumber()]);
            if (CombatHandled.isStanceParrying(_currentStance, nextStance) || CombatHandled.isStanceOpposing(_currentStance, nextStance)) continue;
            return true;
        }
        return false;
    }
    //Copy pasta;  It appears as though isNextGoodStance calls in this method uses wrong argument order.
    protected static ActionEntry changeToBestOffensiveStance(byte _currentStance, byte opponentStance) {
        for (int x = 0; x < selectStanceList.size(); ++x) {
            int num = Server.rand.nextInt(selectStanceList.size());
            ActionEntry e = selectStanceList.get(num);
            byte nextStance = CombatHandled.getStanceForAction(e = Actions.actionEntrys[e.getNumber()]);
            if (!CombatHandled.isNextGoodStance(_currentStance, nextStance, opponentStance)) continue;
            return e;
        }
        return null;
    }
    //Copy pasta
    protected static float getDistdiff(Creature creature, Creature opponent, AttackAction atk) {
        if (atk != null && !atk.isUsingWeapon()) {
            float idealDist = 10 + atk.getAttackValues().getAttackReach() * 3;
            float dist = Creature.rangeToInDec(creature, opponent);
            return idealDist - dist;
        }
        Item wpn = creature.getPrimWeapon();
        return CombatHandled.getDistdiff(wpn, creature, opponent);
    }
    //Copy pasta
    protected static float getDistdiff(Item weapon, Creature creature, Creature opponent) {
        float idealDist = (float)(10 + Weapon.getReachForWeapon(weapon) * 3);
        float dist = Creature.rangeToInDec(creature, opponent);
        return idealDist - dist;
    }
    //Copy pasta
    public static void setAttString(Creature _creature, Item _weapon, byte _type) {
        attString = CombatEngine.getAttackString(_creature, _weapon, _type);
    }
    //Copy pasta
    public void sendStanceAnimation(Creature attacker, byte aStance, boolean attack) {
        if (aStance == 8) {
            attacker.sendToLoggers(attacker.getName() + ": " + "stancerebound", (byte)2);
            attacker.playAnimation("stancerebound", false);
        } else if (aStance == 9) {
            attacker.getStatus().setStunned(3.0F, false);
            attacker.playAnimation("stanceopen", false);
            attacker.sendToLoggers(attacker.getName() + ": " + "stanceopen", (byte)2);
        } else {
            StringBuilder sb = new StringBuilder();
            sb.append("fight");
            if (attack) {
                if (attString.equals("hit")) {
                    sb.append("_strike");
                } else {
                    sb.append("_" + attString);
                }
            }

            if (!attacker.isUnique() || attacker.getHugeMoveCounter() == 2) {
                attacker.playAnimation(sb.toString(), !attack);
            }

            attacker.sendToLoggers(attacker.getName() + ": " + sb.toString(), (byte)2);
        }

    }
    //Copy pasta
    private double getDodgeMod(Creature creature) {
        try {
            // Unavoidable reflection due to other classes adding/removing dodge modifiers through addDodgeModifier and removeDodgeModifier
            CombatHandler ch = creature.getCombatHandler();
            Set<DoubleValueModifier> dodgeModifiers = ReflectionUtil.getPrivateField(ch, ReflectionUtil.getField(ch.getClass(), "dodgeModifiers"));
            float diff = creature.getTemplate().getWeight() / creature.getWeight();
            if (creature.isPlayer()) {
                diff = creature.getTemplate().getWeight() / (creature.getWeight() + creature.getBody().getBodyItem().getFullWeight() + creature.getInventory().getFullWeight());
            }
            diff = 0.8f + diff * 0.2f;
            if (dodgeModifiers == null) {
                return 1.0f * diff;
            }
            double doubleModifier = 1.0;
            for (DoubleValueModifier lDoubleValueModifier : dodgeModifiers) {
                doubleModifier += lDoubleValueModifier.getModifier();
            }
            return doubleModifier * (double)diff;
        } catch (IllegalAccessException | NoSuchFieldException e) {
            e.printStackTrace();
        }
        return 1.0;
    }
    //Copy pasta
    private static float getWeaponParryBonus(Item weapon) {
        return weapon.isWeaponSword() ? 2.0F : 1.0F;
    }




    public float getCombatRating(Creature attacker, Item weapon, Creature opponent, boolean attacking) {
        float combatRating = attacker.getBaseCombatRating();
        if (this.hasSpiritFervor) {
            ++combatRating;
        }

        if (attacker.isKing() && attacker.isEligibleForKingdomBonus()) {
            combatRating += 3.0F;
        }

        if (attacker.hasTrait(0)) {
            ++combatRating;
        }

        if (attacking) {
            combatRating += 1.0F + attacker.getBonusForSpellEffect((byte)30) / 30.0F;
        } else {
            combatRating += 1.0F + attacker.getBonusForSpellEffect((byte)28) / 30.0F;
        }

        if (attacker.getCultist() != null && attacker.getCultist().hasFearEffect()) {
            combatRating += 2.0F;
        }

        if (attacker.isPlayer()) {
            int antiGankBonus = Math.max(0, attacker.getAttackers() - 1);
            combatRating += (float)antiGankBonus;
            attacker.sendToLoggers("Adding " + antiGankBonus + " to combat rating due to attackers.");
        }

        if (attacker.isHorse() && attacker.getLeader() != null && attacker.getLeader().isPlayer()) {
            combatRating -= 5.0F;
        }

        if (attacker.hasSpellEffect((byte)97)) {
            combatRating -= 4.0F;
        }

        if (attacker.isSpiritGuard()) {
            if (Servers.localServer.isChallengeServer()) {
                if (opponent.isPlayer() && opponent.getKingdomId() != attacker.getKingdomId()) {
                    combatRating = 10.0F;
                }
            } else if (attacker.getCitizenVillage() != null && attacker.getCitizenVillage().plan.isUnderSiege()) {
                combatRating += (float)(attacker.getCitizenVillage().plan.getSiegeCount() / 3);
            }
        }

        float bon = weapon.getSpellNimbleness();
        if (bon > 0.0F) {
            combatRating += bon / 30.0F;
        }

        if (attacker.isPlayer() && opponent.isPlayer()) {
            if (attacker.isRoyalExecutioner() && attacker.isEligibleForKingdomBonus()) {
                combatRating += 2.0F;
            } else if (attacker.hasCrownInfluence()) {
                ++combatRating;
            }

            combatRating += Players.getInstance().getCRBonus(attacker.getKingdomId());
            if (attacker.isInOwnDuelRing()) {
                if (opponent.getKingdomId() != attacker.getKingdomId()) {
                    combatRating += 4.0F;
                }
            } else if (opponent.isInOwnDuelRing() && opponent.getKingdomId() != attacker.getKingdomId()) {
                combatRating -= 4.0F;
            }

            if (Servers.localServer.PVPSERVER && attacker.getNumberOfFollowers() > 1) {
                combatRating -= 10.0F;
            }
        }

        if (attacker.isPlayer() && attacker.hasBattleCampBonus()) {
            combatRating += 3.0F;
        }

        combatRating += ItemBonus.getCRBonus(attacker);
        float crmod = 1.0F;
        if (attacking) {
            if (attacker.isPlayer() && this.currentStrength >= 1 && attacker.getStatus().getStamina() > 2000) {
                int num = 10053;
                if (this.currentStrength == 1) {
                    num = 10055;
                }

                Skill def;

                try {
                    def = attacker.getSkills().getSkill(num);
                } catch (NoSuchSkillException var13) {
                    def = attacker.getSkills().learn(num, 1.0F);
                }

                if (def.skillCheck((double)(attacker.getBaseCombatRating() * 2.0F), 0.0D, true, 10.0F, attacker, opponent) > 0.0D) {
                    combatRating = (float)((double)combatRating + (double)((float)this.currentStrength / 2.0F) * Server.getModifiedFloatEffect(def.getRealKnowledge() / 100.0D));
                }
            }
        } else if (attacker.isPlayer() && this.currentStrength > 1) {
            Skill def;

            try {
                def = attacker.getSkills().getSkill(10053);
            } catch (NoSuchSkillException var12) {
                def = attacker.getSkills().learn(10053, 1.0F);
            }

            if (def.skillCheck(Server.getModifiedFloatEffect(70.0D), 0.0D, true, 10.0F, attacker, opponent) < 0.0D) {
                combatRating = (float)((double)combatRating - (double)this.currentStrength * Server.getModifiedFloatEffect((100.0D - def.getRealKnowledge()) / 100.0D));
            }
        }

        if (attacker.isPlayer()) {
            combatRating = (float)((double)combatRating - Weapon.getSkillPenaltyForWeapon(weapon));
            combatRating += (float)attacker.getCRCounterBonus();
        }

        if (attacker.isPlayer()) {
            if (opponent.isPlayer()) {
                combatRating = (float)((double)combatRating + attacker.getFightingSkill().getKnowledge(0.0D) / 5.0D);
            } else {
                combatRating = (float)((double)combatRating + attacker.getFightingSkill().getRealKnowledge() / 10.0D);
            }
        }

        if (this.battleratingPenalty > 0) {
            combatRating -= (float)this.battleratingPenalty;
        }

        crmod *= this.getFlankingModifier(attacker, opponent);
        crmod *= this.getHeightModifier(attacker, opponent);
        crmod *= this.getAlcMod(attacker);
        if (attacker.getCitizenVillage() != null) {
            crmod *= 1.0F + attacker.getCitizenVillage().getFaithWarBonus() / 100.0F;
        }

        combatRating *= crmod;
        byte attackerFightLevel = getCreatureFightLevel(attacker);
        if (attackerFightLevel >= 3) {
            combatRating += (float)(attackerFightLevel * 2);
        }

        if (attacker.isPlayer()) {
            combatRating *= Servers.localServer.getCombatRatingModifier();
        }

        combatRating *= this.getFootingModifier(attacker, weapon, opponent);
        if (attacker.isOnHostileHomeServer()) {
            combatRating *= 0.7F;
        }

        if (this.isOpen()) {
            combatRating *= 0.7F;
        } else if (this.isProne()) {
            combatRating *= 0.5F;
        } else {
            try {
                Action act = attacker.getCurrentAction();
                if (act.isVulnerable()) {
                    combatRating *= 0.5F;
                } else if (attacker.isLinked()) {
                    Creature linkedTo = attacker.getCreatureLinkedTo();
                    if (linkedTo != null) {
                        try {
                            linkedTo.getCurrentAction().isSpell();
                            combatRating *= 0.7F;
                        } catch (NoSuchActionException var10) {
                            ;
                        }
                    }
                }
            } catch (NoSuchActionException var11) {
                ;
            }
        }

        if (attacker.hasAttackedUnmotivated()) {
            combatRating = Math.min(4.0F, combatRating);
        }

        return Math.min(100.0F, Math.max(1.0F, combatRating));
    }

}