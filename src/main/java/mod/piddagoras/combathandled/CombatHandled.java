package mod.piddagoras.combathandled;

import com.sun.istack.internal.Nullable;
import com.wurmonline.server.*;
import com.wurmonline.server.behaviours.*;
import com.wurmonline.server.bodys.Wound;
import com.wurmonline.server.combat.CombatEngine;
import com.wurmonline.server.combat.CombatMove;
import com.wurmonline.server.combat.Weapon;
import com.wurmonline.server.creatures.AttackAction;
import com.wurmonline.server.creatures.CombatHandler;
import com.wurmonline.server.creatures.Creature;
import com.wurmonline.server.items.Item;
import com.wurmonline.server.items.Materials;
import com.wurmonline.server.players.ItemBonus;
import com.wurmonline.server.players.Player;
import com.wurmonline.server.skills.NoSuchSkillException;
import com.wurmonline.server.skills.Skill;
import com.wurmonline.server.skills.Skills;
import com.wurmonline.server.sounds.SoundPlayer;
import com.wurmonline.server.utils.CreatureLineSegment;
import com.wurmonline.shared.constants.Enchants;
import com.wurmonline.shared.util.MulticolorLineSegment;
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
    public static HashMap<Creature, CombatHandled> combatMap = new HashMap<>();

    public static boolean attack(Creature attacker, Creature opponent, int combatCounter, boolean opportunity, float actionCounter, Action act) {
        if(combatMap.containsKey(attacker)){
            return combatMap.get(attacker).attackLoop(attacker, opponent, combatCounter, opportunity, actionCounter, act);
        }else{
            CombatHandled ch = new CombatHandled();
            boolean result = ch.attackLoop(attacker, opponent, combatCounter, opportunity, actionCounter, act);
            return result;
        }
    }

    public static final Logger logger = Logger.getLogger(CombatHandled.class.getName());
    protected float lastTimeStamp=1.0f;
    protected byte currentStance=15; //Need to look into what stances are.
    protected byte currentStrength=1;//Depends on aggressive/normal/defensive style active currently.
    protected byte opportunityAttacks=0;//Need to look closely at how opportunities work.
	protected HashSet<Item> secattacks;//Probably updates in other methods besides attackLoop.
    protected boolean turned = false;

    protected static final List<ActionEntry> selectStanceList = new LinkedList<>();
    private static final List<ActionEntry> standardDefences = new LinkedList<>();


    protected static double manouvreMod = 0.0D;
    private boolean hasSpiritFervor = false;
    private byte battleratingPenalty = 0;
    private boolean hasRodEffect = false;
    private static float parryBonus = 1.0F;


    private static String attString = "";


    private boolean receivedFStyleSkill = false;
    private boolean receivedWeaponSkill = false;
    private boolean receivedSecWeaponSkill = false;
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
                //attacker.getCommunicator().sendCombatStatus(CombatHandled.getDistdiff(weapon, attacker, opponent), this.getFootingModifier(attacker, weapon, opponent), this.currentStance);//Should maybe Hijack this and send different data.
                try {
                    // Using ReflectionUtil to call a protected method with arguments.
                    ReflectionUtil.callPrivateMethod(attacker.getCommunicator(), ReflectionUtil.getMethod(attacker.getCommunicator().getClass(), "sendCombatStatus"), CombatHandled.getDistdiff(weapon, attacker, opponent), this.getFootingModifier(attacker, weapon, opponent), this.currentStance);
                } catch (IllegalAccessException | InvocationTargetException | NoSuchMethodException e) {
                    e.printStackTrace();
                }
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
                Item[] secweapons = attacker.getSecondaryWeapons();
                if (secweapons.length > 0) {
                    weapon = secweapons[Server.rand.nextInt(secweapons.length)];
                }
                //Attack with the weapon
                isDead = this.attack(attacker, weapon, opponent, false);
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
                    attacker.sendToLoggers("YOU SECONDARY " + lSecweapon.getName(), (byte) 2);
                    opponent.sendToLoggers(attacker.getName() + " SECONDARY " + lSecweapon.getName() + "(" + lSecweapon.getWurmId() + ")", (byte) 2);
                    attacker.setHugeMoveCounter(2 + Server.rand.nextInt(4));//Probably does nothing, but leaving in case.
                    isDead = this.attack(attacker, lSecweapon, opponent, true);
                    performedAttack = true;
                    this.secattacks.add(lSecweapon);
                }
            }
            float time = this.getSpeed(attacker, weapon);
            float timer = attacker.addToWeaponUsed(weapon, delta);
            if (!isDead && timer > time){
                attacker.deductFromWeaponUsed(weapon, time);
                attacker.sendToLoggers("YOU PRIMARY " + weapon.getName(), (byte) 2);
                opponent.sendToLoggers(attacker.getName() + " PRIMARY " + weapon.getName(), (byte) 2);
                isDead = this.attack(attacker, weapon, opponent, false);
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

    private boolean attack(Creature attacker, Item weapon, Creature opponent, boolean secondaryWeapon) {
        if (weapon.isWeaponBow()) {
            return false;
        } else {
            //resetFlags(opponent);
            if (!(opponent instanceof Player) || !opponent.hasLink()) {
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
            }
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
                if (this.addToSkills && opponent.isPlayer() && combatMap.get(opponent).currentStrength == 0) {
                    Skill def;
                    try {
                        def = opponent.getSkills().getSkill(10054);
                    } catch (NoSuchSkillException var5) {
                        def = opponent.getSkills().learn(10054, 1.0F);
                    }
                    if (opponent.getStatus().getStamina() > 2000 && def.skillCheck((double) (attacker.getBaseCombatRating() * 2.0F), 0.0D, attacker.isNoSkillFor(opponent) || combatMap.get(opponent).receivedFStyleSkill, 10.0F, opponent, attacker) > 0.0D) {
                        combatMap.get(opponent).receivedFStyleSkill = true;
                        defBonus += def.getKnowledge(0.0D) / 4.0D;
                    }
                }
                if (combatMap.get(opponent).currentStrength > 0 && opponent instanceof Player) {
                    if (opponent.isMoving()) {
                        defBonus -= (double) (combatMap.get(opponent).currentStrength * 15);
                    } else if (combatMap.get(opponent).currentStrength > 1) {
                        defBonus -= (double) (combatMap.get(opponent).currentStrength * 7);
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
            boolean crit=false;
            boolean miss=false;
            boolean dead=false;
            byte pos;

            setAttString(attacker, weapon, type);
            this.sendStanceAnimation(attacker, this.currentStance, true);
            float percent = this.checkShield(opponent, weapon);
            if (percent > 50.0F) {
                chanceToHit = 0.0F;
            } else if (percent > 0.0F) {
                chanceToHit *= 1.0F - percent / 100.0F;
            }

            float parrPercent = -1.0F;
            if ((opponent.getFightStyle() != 1 || Server.rand.nextInt(3) == 0) && chanceToHit > 0.0F) {
                parrPercent = this.checkDefenderParry(opponent, weapon); //Not sure what to do.  This method uses attCheck (from previous attack), but attCheck provides no useful information and is actually just random.
                if (parrPercent > 60.0F) {
                    chanceToHit = 0.0F;
                } else if (parrPercent > 0.0F) {
                    chanceToHit *= 1.0F - parrPercent / 200.0F;
                }
            }

            pos = 2;

            try {
                pos = this.getWoundPos(this.currentStance, opponent);
            } catch (Exception var9) {
                logger.log(Level.WARNING, attacker.getName() + " " + var9.getMessage(), var9);
            }

            double attCheck = (double)(Server.rand.nextFloat() * 100.0F) * (1.0D + attacker.getVisionMod());
            String combatDetails = " CHANCE:" + chanceToHit + ", roll=" + attCheck;
            if (attacker.spamMode() && Servers.isThisATestServer()) {
                attacker.getCommunicator().sendCombatSafeMessage(combatDetails);
            }

            attacker.sendToLoggers("YOU" + combatDetails, (byte)2);
            opponent.sendToLoggers(attacker.getName() + combatDetails, (byte)2);
            if (attCheck < (double)chanceToHit) {
                if (opponent.isPlayer()) {
                    float critChance = Weapon.getCritChanceForWeapon(weapon);
                    if (isAtSoftSpot(combatMap.get(opponent).currentStance, currentStance)) {
                        critChance += 0.05F;
                    }

                    int enchBon = CombatEngine.getEnchantBonus(weapon, opponent);
                    if (enchBon > 0) {
                        critChance += 0.03F;
                    }

                    if (!weapon.isArtifact() && Server.rand.nextFloat() < critChance) {
                        crit = true;
                    }
                }
            } else {
                miss = true;
            }

            if (!miss && !crit) {
                boolean keepGoing = true;
                double defCheck = (double)(Server.rand.nextFloat() * 100.0F) * combatMap.get(opponent).getDodgeMod();
                //defCheck *= (double)opponent.getStatus().getDodgeTypeModifier();
                try {
                    // Using ReflectionUtil to call a protected method to obtain a value.
                    float opponentDodgeTypeModifier = ReflectionUtil.callPrivateMethod(opponent.getStatus(), ReflectionUtil.getMethod(opponent.getStatus().getClass(), "getDodgeTypeModifier"));
                    defCheck *= (double)opponentDodgeTypeModifier;
                    if (opponent.getMovePenalty() != 0) {
                        defCheck *= (double)(1.0F + (float)opponent.getMovePenalty() / 10.0F);
                    }

                    defCheck *= 1.0D - opponent.getMovementScheme().armourMod.getModifier();
                    if (defCheck < opponent.getBodyControl() / 3.0D) {
                        if ((double)(opponentDodgeTypeModifier * 100.0F) < opponent.getBodyControl() / 3.0D) {
                            logger.log(Level.WARNING, opponent.getName() + " is impossible to hit except for crits: " + combatMap.get(opponent).getDodgeMod() * 100.0D + " is always less than " + opponent.getBodyControl());
                        }

                        this.sendDodgeMessage(opponent);
                        keepGoing = false;
                        String dodgeDetails = "Dodge=" + defCheck + "<" + opponent.getBodyControl() / 3.0D + " dodgemod=" + combatMap.get(opponent).getDodgeMod() + " dodgeType=" + opponentDodgeTypeModifier + " dodgeMovePenalty=" + opponent.getMovePenalty() + " armour=" + opponent.getMovementScheme().armourMod.getModifier();
                        if (attacker.spamMode() && Servers.isThisATestServer()) {
                            attacker.getCommunicator().sendCombatSafeMessage(dodgeDetails);
                        }

                        attacker.sendToLoggers(dodgeDetails, (byte)4);
                        checkIfHitVehicle(attacker, opponent);
                    }
                } catch (IllegalAccessException | InvocationTargetException | NoSuchMethodException e) {
                    e.printStackTrace();
                }

                if (keepGoing) {
                    hit = true;
                }
            }

            if (hit || crit) {
                attacker.sendToLoggers("YOU DAMAGE " + weapon.getName(), (byte)2);
                opponent.sendToLoggers(attacker.getName() + " DAMAGE " + weapon.getName(), (byte)2);
                dead = this.setDamage(opponent, weapon, damage, pos, type);
            }

            if (dead) {
                this.setKillEffects(attacker, opponent);
            }

            if (miss) {
                if (attacker.spamMode() && (chanceToHit > 0.0F || percent > 0.0F && parrPercent > 0.0F)) {
                    attacker.getCommunicator().sendCombatNormalMessage("You miss with the " + weapon.getName() + ".");
                    attacker.sendToLoggers("YOU MISS " + weapon.getName(), (byte)2);
                    opponent.sendToLoggers(attacker.getName() + " MISS " + weapon.getName(), (byte)2);
                }

                if (!attacker.isUnique() && attCheck - (double)chanceToHit > 50.0D && Server.rand.nextInt(10) == 0) {
                    justOpen = true;
                    this.setCurrentStance(-1, (byte)9);
                    ArrayList<MulticolorLineSegment> segments = new ArrayList<>();
                    segments.add(new CreatureLineSegment(attacker));
                    segments.add(new MulticolorLineSegment(" makes a bad move and is an easy target!.", (byte)0));
                    opponent.getCommunicator().sendColoredMessageCombat(segments);
                    segments.get(1).setText(" make a bad move, making you an easy target.");
                    attacker.getCommunicator().sendColoredMessageCombat(segments);
                    attacker.getCurrentTile().checkOpportunityAttacks(attacker);
                    opponent.getCurrentTile().checkOpportunityAttacks(attacker);
                } else if (Server.rand.nextInt(10) == 0) {
                    checkIfHitVehicle(attacker, opponent);
                }
            }

            this.addToSkills = false;
            return dead;
        }
    }

    //Copy pasta
    public float getChanceToHit(Creature attacker, Item weapon, Creature opponent, double attBonus, double defBonus) {
        float myCR = this.getCombatRating(attacker, weapon, opponent, true);
        float oppCR = combatMap.get(opponent).getCombatRating(opponent, opponent.getPrimWeapon(), attacker, false);
        if (attacker.isPlayer()) {
            float distdiff = Math.abs(getDistdiff(weapon, attacker, opponent));
            if (distdiff > 10.0F) {
                --myCR;
            }

            if (distdiff > 20.0F) {
                --myCR;
            }
        }

        parryBonus = this.getParryBonus(combatMap.get(opponent).currentStance, this.currentStance);
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
        if (!weapon.isWeaponSword() && weapon.getTemplateId() != 706) {
            if (weapon.getTemplateId() == 1115) {
                if (!rawType && Server.rand.nextInt(3) != 0) {
                    woundType = 0;
                } else {
                    woundType = 2;
                }
            } else if (weapon.isWeaponSlash()) {
                woundType = 1;
            } else if (weapon.isWeaponPierce()) {
                woundType = 2;
            } else if (weapon.isWeaponCrush()) {
                woundType = 0;
            } else if (weapon.isBodyPart()) {
                if (weapon.getTemplateId() == 17) {
                    woundType = 3;
                } else if (weapon.getTemplateId() == 12) {
                    woundType = 0;
                }
            }
        } else if (!rawType && Server.rand.nextInt(2) != 0) {
            woundType = 2;
        } else {
            woundType = 1;
        }

        return woundType;
    }
    //Copy pasta
    private double getDamage(Creature attacker, Item weapon, Creature opponent) {
        Skill attStrengthSkill;
        double damage;
        try {
            attStrengthSkill = attacker.getSkills().getSkill(102);
        } catch (NoSuchSkillException var13) {
            attStrengthSkill = attacker.getSkills().learn(102, 1.0F);
            logger.log(Level.WARNING, attacker.getName() + " had no strength. Weird.");
        }

        if (weapon.isBodyPartAttached()) {
            //damage = (double)(attacker.getCombatDamage(weapon) * 1000.0F * attacker.getStatus().getDamageTypeModifier());
            try {
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

            if (damage < 10000.0D && attacker.getBonusForSpellEffect((byte)24) > 0.0F) {
                damage += Server.getBuffedQualityEffect((double)(attacker.getBonusForSpellEffect((byte)24) / 100.0F)) * 5000.0D;
            }

            float randomizer = (50.0F + Server.rand.nextFloat() * 50.0F) / 100.0F;
            damage *= (double)randomizer;
        } else {
            damage = Weapon.getModifiedDamageForWeapon(weapon, attStrengthSkill, opponent.getTemplate().getTemplateId() == 116) * 1000.0D;
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
            }
        }

        if (attacker.getEnemyPresense() > 1200 && opponent.isPlayer() && !weapon.isArtifact()) {
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
                int num = 10053;
                if (this.currentStrength == 1) {
                    num = 10055;
                }

                fstyle = null;

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
            } catch (NoSuchSkillException var10) {
                ;
            }

            if (knowl < 50.0F) {
                damage = 0.800000011920929D * damage + 0.2D * (double)(knowl / 50.0F) * damage;
            }
        } else {
            damage *= (double)(0.85F + (float)this.currentStrength * 0.15F);
        }

        if (attacker.isStealth() && attacker.opponent != null && !attacker.isVisibleTo(opponent)) {
            ArrayList<MulticolorLineSegment> segments = new ArrayList<>();
            segments.add(new CreatureLineSegment(attacker));
            segments.add(new MulticolorLineSegment(" backstab ", (byte)0));
            segments.add(new CreatureLineSegment(opponent));
            attacker.getCommunicator().sendColoredMessageCombat(segments);
            damage = Math.min(50000.0D, damage * 4.0D);
        }

        if (attacker.getCitizenVillage() != null && attacker.getCitizenVillage().getFaithWarBonus() > 0.0F) {
            damage *= (double)(1.0F + attacker.getCitizenVillage().getFaithWarBonus() / 100.0F);
        }

        byte attackerFightLevel = getCreatureFightLevel(attacker);
        if(attackerFightLevel >= 4){
            damage *= 1.1D;
        }
        return damage;
    }


    //Copy pasta
    private float checkDefenderParry(Creature defender, Item attWeapon, double attCheck, double defBonus) {
        double defCheck = 0.0D;
        boolean parried = false;
        int parryTime = 200;
        if (defender.getFightStyle() == 2) {
            parryTime = 120;
        } else if (defender.getFightStyle() == 1) {
            parryTime = 360;
        }

        parryBonus = getParryBonus(combatMap.get(defender).currentStance, this.currentStance);

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
                        pdiff = Math.max(1.0D, (attCheck - defBonus + (double)((float)defParryWeapon.getWeightGrams() / 100.0F)) / (double)getWeaponParryBonus(defParryWeapon) * (1.0D - this.getParryMod()));
                        if (!defender.isPlayer()) {
                            pdiff *= (double)defender.getStatus().getParryTypeModifier();
                        }

                        defCheck = defPrimWeaponSkill.skillCheck(pdiff * (double)ItemBonus.getParryBonus(defender, defParryWeapon), defParryWeapon, 0.0D, defender.isNoSkillFor(defender) || defParryWeapon.isWeaponBow(), 1.0F, defender, defender);
                        defender.lastParry = WurmCalendar.currentTime;
                        defender.getStatus().modifyStamina(-300.0F);
                    }

                    if (defCheck < 0.0D && Server.rand.nextInt(20) == 0 && defLeftWeapon != null && !defLeftWeapon.equals(defParryWeapon)) {
                        Skill offhandWeaponSkill=getCreatureWeaponSkill(defender, defLeftWeapon);
                        if (!defender.isMoving() || defPrimWeaponSkill.getRealKnowledge() > 40.0D) {
                            pdiff = Math.max(1.0D, (attCheck - defBonus + (double)((float)defLeftWeapon.getWeightGrams() / 100.0F)) / (double)getWeaponParryBonus(defLeftWeapon) * this.getParryMod());
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
                        this.setParryEffects(defender, defWeapon, attWeapon, defCheck);
                    }
                }
            }
        }

        return (float)defCheck;
    }
    private void setParryEffects(Creature defender, Item defWeapon, Item attWeapon, double parryEff) {
        defender.lastParry = WurmCalendar.currentTime;
        if (aiming || this.creature.spamMode()) {
            ArrayList<MulticolorLineSegment> segments = new ArrayList();
            segments.add(new CreatureLineSegment(defender));
            segments.add(new MulticolorLineSegment(" " + CombatEngine.getParryString(parryEff) + " parries with " + defWeapon.getNameWithGenus() + ".", (byte)0));
            this.creature.getCommunicator().sendColoredMessageCombat(segments);
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

            if (this.creature.isPlayer()) {
                vulnerabilityModifier = 1.0F;
                if (defWeapon.isMetal() && Weapon.isWeaponDamByMetal(attWeapon)) {
                    vulnerabilityModifier = 4.0F;
                }

                if (attWeapon.isBodyPartAttached()) {
                    attWeapon.setDamage(attWeapon.getDamage() + 1.0E-7F * (float)damage * attWeapon.getDamageModifier() * vulnerabilityModifier);
                }
            }
        }

        this.creature.sendToLoggers(defender.getName() + " PARRY " + parryEff, (byte)2);
        defender.sendToLoggers("YOU PARRY " + parryEff, (byte)2);
        String lSstring = getParrySound(Server.rand);
        SoundPlayer.playSound(lSstring, defender, 1.6F);
        CombatEngine.checkEnchantDestruction(attWeapon, defWeapon, defender);
        defender.playAnimation("parry.weapon", false);
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
        return Math.max(2.0f, calcspeed);
    }
    //Copy pasta
    protected float getWeaponSpeed(Creature attacker, Item _weapon) {
        float flspeed;
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
        } catch (NoSuchSkillException var4) {
            ;
        }

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




    private static Skill getCreatureWeaponSkill(Creature creature, Item weapon) {
        Skills cSkills=creature.getSkills();
        Skill skill=null;
        if (weapon != null) {
            if (weapon.isBodyPart()) {
                try {
                    skill = cSkills.getSkill(10052);
                } catch (NoSuchSkillException var4) {
                    skill = cSkills.learn(10052, 1.0F);
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

    private static byte getCreatureFightLevel(Creature creature){
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
                    mycr = combatMap.get(defender).getCombatRating(defender, defender.getPrimWeapon(), opponent,  false);//COMBAT RATING
                    oppcr = combatMap.get(opponent).getCombatRating(opponent, opponent.getPrimWeapon(), defender,  false);
                    knowl = this.getCombatKnowledgeSkill(defender);
                    if (knowl > 50.0f) {
                        selectStanceList.addAll(standardDefences);
                    }
                    if (!defender.isPlayer()) {
                        knowl += 20.0f;
                    }
                    selectStanceList.addAll(combatMap.get(defender).getHighAttacks(null, true, defender, opponent, mycr, oppcr, knowl));
                    selectStanceList.addAll(combatMap.get(defender).getMidAttacks(null, true, defender, opponent, mycr, oppcr, knowl));
                    selectStanceList.addAll(combatMap.get(defender).getLowAttacks(null, true, defender, opponent, mycr, oppcr, knowl));
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
                } else if (defender.opponent == opponent && (e = CombatHandled.getDefensiveActionEntry(combatMap.get(opponent).currentStance)) == null) {
                    e = CombatHandled.getOpposingActionEntry(combatMap.get(opponent).currentStance);
                }
            }
            if (e == null) {
                if (defender.combatRound > 2 && Server.rand.nextInt(2) == 0) {
                    if (defender.mayRaiseFightLevel()) {
                        e = Actions.actionEntrys[340];
                    }
                } else if (mycr - oppcr > 2.0f || combatMap.get(defender).getSpeed(defender, defender.getPrimWeapon()) < 3.0f) {
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
    protected List<ActionEntry> getHighAttacks(@Nullable Item weapon, boolean auto,Creature attacker, Creature opponent, float mycr, float oppcr, float primweaponskill) {
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
    protected List<ActionEntry> getMidAttacks(@Nullable Item weapon, boolean auto, Creature attacker, Creature opponent, float mycr, float oppcr, float primweaponskill) {
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
    protected List<ActionEntry> getLowAttacks(@Nullable Item weapon, boolean auto, Creature attacker, Creature opponent, float mycr, float oppcr, float primweaponskill) {
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
    private void addToList(List<ActionEntry> list, @Nullable Item weapon, short number, Creature attacker, Creature opponent, float mycr, float oppcr, float primweaponskill) {
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
    public static float getMoveChance(Creature performer, @Nullable Item weapon, int stance, ActionEntry entry, float mycr, float oppcr, float primweaponskill) {
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
        byte performerFightLevel = getCreatureFightLevel(performer)
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
        byte attackerFightLevel = getCreatureFightLevel(attacker)
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