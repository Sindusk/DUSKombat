package mod.piddagoras.duskombat;

import com.wurmonline.server.*;
import com.wurmonline.server.behaviours.*;
import com.wurmonline.server.bodys.BodyTemplate;
import com.wurmonline.server.bodys.Wound;
import com.wurmonline.server.combat.*;
import com.wurmonline.server.creatures.*;
import com.wurmonline.server.items.Item;
import com.wurmonline.server.items.ItemList;
import com.wurmonline.server.items.Materials;
import com.wurmonline.server.items.NoSpaceException;
import com.wurmonline.server.players.ItemBonus;
import com.wurmonline.server.players.Player;
import com.wurmonline.server.players.Titles;
import com.wurmonline.server.skills.NoSuchSkillException;
import com.wurmonline.server.skills.Skill;
import com.wurmonline.server.skills.SkillList;
import com.wurmonline.server.skills.Skills;
import com.wurmonline.server.sounds.SoundPlayer;
import com.wurmonline.server.spells.SpellEffect;
import com.wurmonline.server.spells.SpellResist;
import com.wurmonline.server.utils.CreatureLineSegment;
import com.wurmonline.shared.constants.BodyPartConstants;
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
public class DUSKombat {
    public static final Logger logger = Logger.getLogger(DUSKombat.class.getName());
    public static HashMap<Creature, DUSKombat> combatMap = new HashMap<>();

    public static boolean attackHandled(Creature attacker, Creature opponent, int combatCounter, boolean opportunity, float actionCounter, Action act) {
        DUSKombat ch;
        if(combatMap.containsKey(attacker)){
            ch = combatMap.get(attacker);
        }else{
            ch = new DUSKombat();
            combatMap.put(attacker, ch);
        }
        //logger.info(String.format("Running attack loop for attacker %s against %s, counter = %.2f", attacker.getName(), opponent.getName(), actionCounter));
        return ch.attackLoop(attacker, opponent, combatCounter, opportunity, actionCounter, act);
    }

    public static DUSKombat getCombatHandled(Creature creature){
        if(combatMap.containsKey(creature)){
            return combatMap.get(creature);
        }else{
            DUSKombat ch = new DUSKombat();
            combatMap.put(creature, ch);
            return ch;
        }
    }

    public static class ParryResistance{
        public Creature creature;
        public int templateId;
        public long lastUpdated;
        public double currentResistance;
        public long fullyExpires;
        public boolean isShield;
        public ParryResistance(Creature creature, int templateId, long lastUpdated, double currentResistance, boolean isShield){
            this.creature = creature;
            this.templateId = templateId;
            this.lastUpdated = lastUpdated;
            this.currentResistance = currentResistance;
            this.fullyExpires = lastUpdated;
            this.isShield = isShield;
        }
    }
    protected static final double PARRY_RECOVERY_SECOND = 0.04d;
    protected static final double BLOCK_RECOVERY_SECOND = 0.2d;

    protected static HashMap<Creature, ArrayList<ParryResistance>> parryResistance = new HashMap<>();
    protected static ParryResistance getParryResistanceFor(Creature creature, int templateId, boolean isShield){
        if(!parryResistance.containsKey(creature)){
            parryResistance.put(creature, new ArrayList<>());
        }
        for(ParryResistance res : parryResistance.get(creature)){
            if(res.templateId == templateId){
                return res;
            }
        }
        ParryResistance res = new ParryResistance(creature, templateId, System.currentTimeMillis(), 1, isShield);
        parryResistance.get(creature).add(res);
        return res;
    }
    protected static double updateParryResistance(Creature creature, Item item, double additionalResistance){
        int templateId = item.getTemplateId();
        if(item.isWeaponSword()){ // Swords have half parry penalty
            additionalResistance *= 0.5d;
        }
        if(!item.isBodyPart()) {
            try {
                Skill itemSkill = DamageMethods.getCreatureSkill(creature, item.getPrimarySkill());
                if (itemSkill != null) {
                    additionalResistance *= (1D - (itemSkill.getKnowledge() / 200D));
                }
            } catch (NoSuchSkillException e) {
                e.printStackTrace();
            }
        }
        ParryResistance res = getParryResistanceFor(creature, templateId, item.isShield());
        long timeDelta = System.currentTimeMillis() - res.lastUpdated;
        double secondsPassed = timeDelta / (double) TimeConstants.SECOND_MILLIS;
        double recovery = PARRY_RECOVERY_SECOND;
        if(item.isShield()){
            recovery = BLOCK_RECOVERY_SECOND;
        }
        res.currentResistance = Math.min(1d, res.currentResistance+(secondsPassed*recovery));
        res.currentResistance = Math.max(0d, res.currentResistance-additionalResistance);
        res.lastUpdated = System.currentTimeMillis();
        double secondsUntilFullyHealed = (1-(res.currentResistance))/recovery;
        res.fullyExpires = (long) (System.currentTimeMillis()+(secondsUntilFullyHealed*TimeConstants.SECOND_MILLIS));
        SpellEffectsEnum seff = SpellEffectsEnum.ITEM_DEBUFF_CLUMSINESS;
        if(item.isShield()){
            seff = SpellEffectsEnum.ITEM_DEBUFF_EXHAUSTION;
        }
        creature.getCommunicator().sendAddStatusEffect(seff, (int) secondsUntilFullyHealed);
        //logger.info(String.format("Returning %.3f resistance for parry on item template %s", res.currentResistance, templateId));
        return res.currentResistance;
    }

    public static long lastPolledParryResistance = 0;
    public static final long pollParryResistanceTime = TimeConstants.SECOND_MILLIS;
    public static void onServerPoll(){
        if(lastPolledParryResistance + pollParryResistanceTime < System.currentTimeMillis()){
            ArrayList<Creature> crets = new ArrayList<>(parryResistance.keySet());
            for(Creature cret : crets){
                ArrayList<ParryResistance> resists = parryResistance.get(cret);
                int i = 0;
                while(i < resists.size()){
                    ParryResistance res = resists.get(i);
                    if(res.fullyExpires < System.currentTimeMillis()){
                        if(res.creature != null){
                            SpellEffectsEnum seff = SpellEffectsEnum.ITEM_DEBUFF_CLUMSINESS;
                            if(res.isShield){
                                seff = SpellEffectsEnum.ITEM_DEBUFF_EXHAUSTION;
                            }
                            res.creature.getCommunicator().sendRemoveSpellEffect(seff);
                        }
                        resists.remove(i);
                    }else {
                        i++;
                    }
                }
            }
            lastPolledParryResistance = System.currentTimeMillis();
        }
    }

    protected long lastSecondaryAttack = 0;

    protected float lastTimeStamp=1.0f;
    protected float lastCheckedStance=1.0f;
    protected byte currentStance=15; //Need to look into what stances are.
    protected byte currentStyle =1;//Depends on aggressive/normal/defensive style active currently.
    protected byte opportunityAttacks=0;//Need to look closely at how opportunities work.
	//protected HashSet<Item> secattacks;//Probably updates in other methods besides attackLoop.
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
            if(delta < 0.1) return false;
            lastTimeStamp = actionCounter;
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
                //attacker.getCommunicator().sendCombatStatus(DUSKombat.getDistdiff(weapon, attacker, opponent), this.getFootingModifier(attacker, weapon, opponent), this.currentStance);//Should maybe Hijack this and send different data.
                try {
                    // Using ReflectionUtil to call a protected method with arguments.
                    ReflectionUtil.callPrivateMethod(attacker.getCommunicator(), ReflectionUtil.getMethod(attacker.getCommunicator().getClass(), "sendCombatStatus"), DUSKombat.getDistdiff(weapon, attacker, opponent), this.getFootingModifier(attacker, weapon, opponent), this.currentStance);
                } catch (IllegalAccessException | InvocationTargetException | NoSuchMethodException e) {
                    e.printStackTrace();
                }
            }

            // Not sure if this ever occurs
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
                isDead = DUSKombat.attackHandled(opponent, attacker, combatCounter, true, 2.0f, null);
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
            // Secondary weapon attacks
            Item[] secweapons = attacker.getSecondaryWeapons();
            for (Item lSecweapon : secweapons) {
                if (attacker.opponent == null){
                    continue; //this should be impossible, since creature.opponent==opponent is probably always true.
                }
                //if (lSecweapon.getTemplateId() == ItemList.bodyHead || lSecweapon.getTemplateId() == ItemList.bodyFace) continue; // Ignore face and head for secondary attacks
                if(attacker.isPlayer() && weapon != null && lSecweapon.isBodyPartAttached()){
                    continue; // Ignore weaponless secondary attacks if the player is using a weapon.
                }
                if(secweapons.length > 1 && System.currentTimeMillis() < lastSecondaryAttack + TimeConstants.SECOND_MILLIS*2){
                    // Adding extra cooldown to non-player attacks to avoid burst damage.
                    continue;
                }
                float timeMod = 1.2f + secweapons.length; // Creatures with lots of secondary weapons should be using them slower.
                if(secweapons.length > 1) { // Add minor variation so all secondaries don't "spike" damage
                    if (lSecweapon.getTemplateId() == ItemList.bodyHead) {
                        timeMod += 0.31f;
                    } else if (lSecweapon.getTemplateId() == ItemList.bodyFace) {
                        timeMod += 0.13f;
                    } else if (lSecweapon.getTemplateId() == ItemList.bodyLegs) {
                        timeMod += 0.42f;
                    }
                }
                float time = this.getSpeed(attacker, lSecweapon) * timeMod; // Multiply to make secondary weapons slower.
                float timer = attacker.addToWeaponUsed(lSecweapon, delta);
                //if (isDead || attacker.combatRound % 2 != 1 || timer <= time) continue; //Every other round, seems bursty.
                if(isDead || timer <= time) continue;
                // Actually swing the secondary weapon
                attacker.deductFromWeaponUsed(lSecweapon, time);
                lastSecondaryAttack = System.currentTimeMillis();
                attacker.sendToLoggers("YOU SECONDARY " + lSecweapon.getName(), (byte) 2);
                opponent.sendToLoggers(attacker.getName() + " SECONDARY " + lSecweapon.getName() + "(" + lSecweapon.getWurmId() + ")", (byte) 2);
                attacker.setHugeMoveCounter(2 + Server.rand.nextInt(4));//Probably does nothing, but leaving in case.
                //logger.info(String.format("> Secondary weapon swing timer for %s's %s has come up, performing swing (%.2f second swing timer, timer is at %.2f).", attacker.getName(), lSecweapon.getName(), time, timer));
                isDead = this.swingWeapon(attacker, lSecweapon, opponent, true);
                if(isDead){
                    setKillEffects(attacker, opponent);
                }
                performedAttack = true;
                //this.secattacks.add(lSecweapon);
            }
            //}
            float time = this.getSpeed(attacker, weapon);
            float timer = attacker.addToWeaponUsed(weapon, delta);
            if (!isDead && timer > time){
                attacker.deductFromWeaponUsed(weapon, time);
                attacker.sendToLoggers("YOU PRIMARY " + weapon.getName(), (byte) 2);
                opponent.sendToLoggers(attacker.getName() + " PRIMARY " + weapon.getName(), (byte) 2);
                //logger.info(String.format("> Weapon swing timer for %s's %s has come up, performing swing (%.2f second swing timer, timer is at %.2f).", attacker.getName(), weapon.getName(), time, timer));
                isDead = this.swingWeapon(attacker, weapon, opponent, false);
                if(isDead){
                    setKillEffects(attacker, opponent);
                }
                performedAttack = true;
                if (!attacker.isPlayer() || act == null || !act.justTickedSecond()) break stillAttacking;
                this.checkStanceChange(attacker, opponent);
                break stillAttacking;
            }
            int[] cmoves = attacker.getCombatMoves();

            if(lastCheckedStance > actionCounter){ // Resets lastCheckedStance in a new combat
                lastCheckedStance = actionCounter;
            }
            if(lastCheckedStance < actionCounter-2) {
                //logger.info(String.format("Checking stance and special moves for %s.", attacker.getName()));
                lastCheckedStance = actionCounter;
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
        }
        return isDead;
    }

    public static boolean blockSkillFrom(Creature attacker, Creature defender, double damage, float armourMod){
        if(defender == null || attacker == null){
            return false;
        }
        if(defender.isPlayer() && defender.getTarget() != attacker){
            return true;
        }
        if(defender.isPlayer()){
            Item weap = defender.getPrimWeapon();
            if(weap != null && weap.isWeapon()){
                double dam = DamageMethods.getBaseWeaponDamage(defender, attacker, defender.getPrimWeapon(), true);
                if(attacker.getArmourMod() < 0.2f || attacker.getBaseCombatRating() > 20){
                    return false;
                }
                if(dam * attacker.getArmourMod() < 3000){
                    return true;
                }
            }else{
                if(defender.getBonusForSpellEffect(Enchants.CRET_BEARPAW) < 50f){
                    return true;
                }
            }
        }
        try {
            if(defender.isPlayer() && attacker.getArmour(BodyPartConstants.TORSO) != null){
                return true;
            }
        } catch (NoArmourException | NoSpaceException ignored) {
        }
        if(attacker.isPlayer()){
            if(armourMod < 0.2f || defender.getBaseCombatRating() > 20){
                return false;
            }
            if(damage * armourMod < 3000){
                return true;
            }
        }
        return false;
    }

    private boolean swingWeapon(Creature attacker, Item weapon, Creature opponent, boolean secondaryWeapon) {
        if (weapon.isWeaponBow()) {
            return false;
        }

        // Update currentStyle to latest value
        try {
            this.currentStyle = ReflectionUtil.getPrivateField(attacker.getCombatHandler(), ReflectionUtil.getField(attacker.getCombatHandler().getClass(), "currentStrength"));
        } catch (IllegalAccessException | NoSuchFieldException e) {
            logger.info(String.format("Failed to get currentStyle for %s from combat handler.", attacker.getName()));
            e.printStackTrace();
        }

        //logger.info(String.format("- %s vs %s -", attacker.getName(), opponent.getName()));

        // Instead of having the opponent turn when they get attacked, we're going to turn the attacker when they swing.
        if(!attacker.isPlayer() || !attacker.hasLink()){
            attacker.turnTowardsCreature(opponent);
            // Inlined version of setting attacker when a player gets attacked.
            if(!opponent.isFighting() && (attacker.isPlayer() || attacker.isDominated())){
                opponent.setTarget(attacker.getWurmId(), true);
            }
        }

        dead=false; //Has to be global due to being set in multiple different methods

        byte pos = BodyTemplate.torso;
        try { // Obtain wound position based on their current targeting
            pos = opponent.getBody().getRandomWoundPos(this.currentStance); // Inlined from getWoundPos
        } catch (Exception ex) {
            logger.log(Level.WARNING, attacker.getName() + " " + ex.getMessage(), ex);
        }

        // Calculations for new combat system
        byte type = this.getDamageType(attacker, weapon, false);
        double damage = DamageMethods.getDamage(this, attacker, weapon, opponent);
        //setAttString(attacker, weapon, type);
        attString = CombatEngine.getAttackString(attacker, weapon, type); // Inlined from setAttString
        this.sendStanceAnimation(attacker, weapon, this.currentStance, true);

        float armourMod = getArmourMod(opponent, pos, type);

        boolean noSkillGain = blockSkillFrom(attacker, opponent, damage, armourMod);
        //logger.info("No skill gain = "+noSkillGain);

        // Reduce stamina from the attacker on swing
        //attacker.getStatus().modifyStamina((float)((int)((float)(-weapon.getWeightGrams()) / 10.0F * (1.0F + (float)this.currentStyle * 0.5F))));
        float staminaCost = -600F - weapon.getWeightGrams() / 10F;
        if(this.currentStyle == Style.AGGRESSIVE.id){
            staminaCost *= 1.3f;
        }else if(this.currentStyle == Style.DEFENSIVE.id){
            staminaCost *= 0.7f;
        }
        attacker.getStatus().modifyStamina(staminaCost);

        // Ensure the attack didn't miss
        double attackCheck = CombatMethods.getHitCheck(attacker, opponent, weapon, noSkillGain);
        //logger.info(String.format("[%s] %s's attackCheck: %.2f", attacker.getName(), attacker.getName(), attackCheck));
        if(attackCheck >= 0) {
            // Begin calculation for dodge.
            double dodgeCheck = CombatMethods.getDodgeCheck(this, attacker, opponent, weapon, attackCheck);
            //logger.info(String.format("[%s] %s dodgeCheck: %.2f", attacker.getName(), opponent.getName(), dodgeCheck));
            if(dodgeCheck < 0){
                // Begin calculation for critical strike
                double critChance = CombatMethods.getCriticalChance(this, attacker, opponent, weapon);
                double critRoll = Server.rand.nextDouble();
                //logger.info(String.format("[%s] %s critRoll: %.2f [%.2f%% chance]", attacker.getName(), attacker.getName(), critRoll*100d, critChance*100d));
                if(critRoll <= critChance || attacker.getBonusForSpellEffect(Enchants.CRET_TRUESTRIKE) > 0){
                    // Critical strike landed
                    //logger.info(String.format("%s has landed a critical hit on %s! Applying double damage and skipping to damage calculations.", attacker.getName(), opponent.getName()));
                    if(attacker.getBonusForSpellEffect(Enchants.CRET_TRUESTRIKE) > 0){
                        attacker.removeTrueStrike();
                    }
                    if (this.currentStyle == Style.DEFENSIVE.id){
                        damage *= 1.4D;
                    }else {
                        damage *= 1.5D;
                    }
                    dead = this.dealDamage(attacker, opponent, weapon, damage, armourMod, pos, type, noSkillGain, true);
                    return dead;
                }else{
                    if(opponent.getShield() != null){
                        // Test for shield block.
                        double shieldCheck = CombatMethods.getShieldCheck(this, attacker, opponent, weapon, attackCheck);
                        double blockRes = updateParryResistance(opponent, opponent.getShield(), 0);
                        double blockReduction = (80*(1-blockRes));
                        shieldCheck -= blockReduction;
                        if(shieldCheck >= 0){
                            // Shield block
                            //logger.info(String.format("%s blocks attack by %s. (Rolled %.2f)", opponent.getName(), attacker.getName(), shieldCheck));
                            Item defShield = opponent.getShield(); // Cannot be null because check occurs before calling this method.
                            int defShieldSkillNum = SkillList.GROUP_SHIELDS;
                            try {
                                defShieldSkillNum = defShield.getPrimarySkill();
                            } catch (NoSuchSkillException ex) {
                                logger.warning(String.format("Could not find proper skill for shield %s. Resorting to Shields group skill.", defShield.getName()));
                            }
                            Skill defShieldSkill = DamageMethods.getCreatureSkill(opponent, defShieldSkillNum);
                            defShieldSkill.skillCheck(attackCheck, defShield, 0, noSkillGain, 10.0F);
                            updateParryResistance(opponent, defShield, damage*0.0001);
                            CombatMessages.playShieldBlockEffects(attacker, opponent, weapon, shieldCheck);
                            float damageMod = !weapon.isBodyPart() && weapon.isWeaponCrush() ? 1.5E-5f : (type == 0 ? 1.0E-6f : 5.0E-6f);
                            defShield.setDamage(defShield.getDamage() + (damageMod * (float)damage * defShield.getDamageModifier()));
                            float blockCost = -1500f;
                            if(this.currentStyle == Style.DEFENSIVE.id){
                                blockCost *= 0.5f;
                            }else if(this.currentStyle == Style.AGGRESSIVE.id){
                                blockCost *= 1.3f;
                            }
                            opponent.getStatus().modifyStamina(blockCost);
                            return dead;
                        }
                    }
                    // Begin calculation for parry chance
                    double parryCheck = CombatMethods.getParryCheck(this, attacker, opponent, weapon, attackCheck);
                    double parryRes = updateParryResistance(opponent, opponent.getPrimWeapon(), 0);
                    double parryReduction = (50*(1-parryRes));
                    parryCheck -= parryReduction;
                    //logger.info(String.format("[%s] %s parryCheck: %.2f [-%.2f from parry resistance]", attacker.getName(), opponent.getName(), parryCheck, parryReduction));
                    if(parryCheck < 0){
                        // Begin calculation for secondary parry, if applicable
                        if(opponent.getSecondaryWeapons().length > 0){
                            // Calculate secondary parries
                            for(Item weap : opponent.getSecondaryWeapons()){
                                if(!weap.isBodyPartAttached()){
                                    double secondaryParryCheck = CombatMethods.getParryCheck(this, attacker, opponent, weapon, attackCheck*2);
                                    double secondaryParryRes = updateParryResistance(opponent, weap, 0);
                                    double secondaryParryReduction = (100*(1-secondaryParryRes));
                                    secondaryParryCheck -= secondaryParryReduction;
                                    //logger.info(String.format("[%s] %s parryCheck with %s: %.2f [-%.2f from parry resistance]", attacker.getName(), opponent.getName(), weap.getName(), secondaryParryCheck, secondaryParryReduction));
                                    if(secondaryParryCheck > 0){
                                        // Opponent parried the attack with a secondary
                                        //Item defWeapon = opponent.getPrimWeapon();
                                        Skill defWeaponSkill = DUSKombat.getCreatureWeaponSkill(opponent, weap);
                                        defWeaponSkill.skillCheck(attackCheck, weap, 0, noSkillGain, 10.0f);
                                        updateParryResistance(opponent, weap, damage*0.0001);
                                        CombatMessages.playParryEffects(attacker, opponent, weapon, secondaryParryCheck);
                                        return dead;
                                    }
                                }
                            }
                            //logger.info(String.format("%s secondary weapons failed to parry. Proceeding to damage.", opponent.getName()));
                            dead = this.dealDamage(attacker, opponent, weapon, damage, armourMod, pos, type, noSkillGain, false);
                            return dead;
                        }else{
                            // They have nothing else to defend themselves with, deal damage.
                            //logger.info("No further defenses available, moving to damage.");
                            dead = this.dealDamage(attacker, opponent, weapon, damage, armourMod, pos, type, noSkillGain, false);
                            return dead;
                        }
                    }else{
                        // Opponent parried the attack
                        Item defWeapon = opponent.getPrimWeapon();
                        Skill defWeaponSkill = DUSKombat.getCreatureWeaponSkill(opponent, defWeapon);
                        defWeaponSkill.skillCheck(attackCheck, defWeapon, 0, noSkillGain, 10.0f);
                        updateParryResistance(opponent, defWeapon, damage*0.0001);
                        CombatMessages.playParryEffects(attacker, opponent, weapon, parryCheck);
                        return dead;
                    }
                }
            }else{
                // Opponent dodged the attack.
                CombatMessages.playDodgeEffects(attacker, opponent, weapon, dodgeCheck);
                return dead;
            }
        }else{
            // Attack missed.
            CombatMessages.playMissEffects(attacker, opponent, weapon, attackCheck);

            if (!attacker.isUnique() && attackCheck < -80D) {
                this.setCurrentStance(attacker, -1, (byte)9);
                ArrayList<MulticolorLineSegment> segments = new ArrayList<>();
                segments.add(new CreatureLineSegment(attacker));
                segments.add(new MulticolorLineSegment(" makes a bad move and is an easy target!", (byte)0));
                opponent.getCommunicator().sendColoredMessageCombat(segments);
                segments.get(1).setText(" make a bad move, making you an easy target!");
                attacker.getCommunicator().sendColoredMessageCombat(segments);
                attacker.getCurrentTile().checkOpportunityAttacks(attacker);
                opponent.getCurrentTile().checkOpportunityAttacks(attacker);
            } else if (Server.rand.nextInt(10) == 0) {
                checkIfHitVehicle(attacker, opponent, damage);
            }
            return dead;
        }
    }

    public byte getDamageType(Creature attacker, Item weapon, boolean rawType) {
        byte woundType = attacker.getTemplate().getCombatDamageType();
        if (weapon.isWeaponSword() || weapon.getTemplateId() == ItemList.halberd) {
            if(rawType || Server.rand.nextInt(2) == 0){
                woundType = Wound.TYPE_SLASH;
            }else{
                woundType = Wound.TYPE_PIERCE;
            }
        } else if (weapon.getTemplateId() == ItemList.crowbar) {
            if(rawType || Server.rand.nextInt(3) == 0){
                woundType = Wound.TYPE_PIERCE;
            }else{
                woundType = Wound.TYPE_CRUSH;
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

        // Override the damage type if it's using damage type enchants
        if(weapon.isWeapon() && weapon.enchantment != 0){
            if(weapon.enchantment == Enchants.ACID_DAM){ // Potion of acid
                woundType = Wound.TYPE_ACID;
            }else if(weapon.enchantment == Enchants.FIRE_DAM){ // Salve of fire
                woundType = Wound.TYPE_BURN;
            }else if(weapon.enchantment == Enchants.FROST_DAM){ // Salve of frost
                woundType = Wound.TYPE_COLD;
            }
        }

        if (weapon.getSpellVenomBonus() > 0) {
            woundType = Wound.TYPE_POISON;
        }

        // Check for Bloodthirst wound
        if (weapon.getSpellExtraDamageBonus() > 0.0F) {
            float bloodthirstPower = weapon.getSpellExtraDamageBonus();
            if (Server.rand.nextFloat() * 100000.0F <= bloodthirstPower) {
                woundType = Wound.TYPE_INFECTION;
            }
        }
        //logger.info(String.format("Setting %s's attack damage type to %s (%s).", attacker.getName(), woundType, WoundAssist.getWoundName(woundType)));
        return woundType;
    }

    public float getArmourMod(Creature defender, byte position, byte woundType){
        float armourMod = defender.getArmourMod(); // Template armour
        Item armour = null;
        try {
            armour = defender.getArmour(ArmourTemplate.getArmourPosition(position));
        } catch (NoArmourException ignored) {
        } catch (NoSpaceException e) {
            logger.log(Level.WARNING, defender.getName() + " no armour space on loc " + position);
        }
        if(armour != null){
            if(armourMod < 1.0f) { // Use the best DR between natural armour and worn armour if the creature has natural armour.
                armourMod = Math.min(armourMod, ArmourTemplate.calculateDR(armour, woundType));
            }else{ // Multiply armour value by natural armour if they are supposed to take extra damage.
                armourMod *= ArmourTemplate.calculateDR(armour, woundType);
            }
        }else{
            float omod = 100.0f;
            float minmod = 0.6f;
            if (!defender.isPlayer()) {
                omod = 300.0f;
                minmod = 0.6f;
            }
            float oakshellPower = defender.getBonusForSpellEffect(Enchants.CRET_OAKSHELL);
            if (armourMod >= 1.0f) {
                armourMod = 0.2f + (float)(1.0 - Server.getBuffedQualityEffect(oakshellPower / omod)) * minmod;
            } else {
                armourMod = Math.min(armourMod, 0.2f + (float)(1.0 - Server.getBuffedQualityEffect(oakshellPower / omod)) * minmod);
            }
        }
        return armourMod;
    }

    //Copy pasta
    public boolean dealDamage(Creature creature, Creature defender, Item attWeapon, double baseDamage, float armourMod, byte position, byte woundType, boolean blockSkill, boolean critical) {
        Item armour = null;
        boolean metalArmour = false;
        try {
            armour = defender.getArmour(ArmourTemplate.getArmourPosition(position));
        } catch (NoArmourException ignored) {
        } catch (NoSpaceException e) {
            logger.log(Level.WARNING, defender.getName() + " no armour space on loc " + position);
            e.printStackTrace();
        }

        float bounceWoundPower = 0.0f;
        String bounceWoundName = "";
        boolean glance = false;
        if(armour != null){
            // Calculate damage dealt to the armour
            float baseArmourDamage = (float) (baseDamage * Weapon.getMaterialArmourDamageBonus(attWeapon.getMaterial()));
            float woundTypeMultiplier = ArmourTemplate.getArmourDamageModFor(armour, woundType);
            armour.setDamage(armour.getDamage() + Math.max(0.01f, Math.min(1.0f, baseArmourDamage * woundTypeMultiplier / 600000.0f) * armour.getDamageModifier()));
            CombatEngine.checkEnchantDestruction(attWeapon, armour, defender);

            // Aura of Shared Pain
            float aospPower = Math.min(DUSKombatMod.getCombatEnchantCap(), armour.getSpellPainShare());
            if (aospPower > 0.0f) {
                bounceWoundPower = aospPower;
                bounceWoundName = "Aura of Shared Pain";
            }

            if (armour.isMetal()) {
                metalArmour = true;
            }

            // Glancing strike
            float baseGlanceRate = 0.05f;
            float armourGlanceModifier = armour.getArmourType().getGlanceRate(woundType, armour.getMaterial());
            float glanceChance = baseGlanceRate + armourGlanceModifier * (float)Server.getBuffedQualityEffect(armour.getCurrentQualityLevel() / 100.0f);
            glanceChance *= 1.0f + ItemBonus.getGlanceBonusFor(armour.getArmourType(), woundType, attWeapon, defender);
            float glanceRoll = Server.rand.nextFloat();
            //logger.info(String.format("%s glance chance: %.2f [%.2f roll]", defender.getName(), glanceChance, glanceRoll));
            if(glanceRoll < glanceChance){
                // Attack glances
                glance = true;
                double reduction = (150-armour.getCurrentQualityLevel())/150;
                //logger.info(String.format("Glance from %s multiplies damage by %.2f%%", armour.getName(), reduction));
                baseDamage *= reduction;
            }

            // Logger stuff
            defender.sendToLoggers("YOU ARMORMOD " + armourMod, (byte) 2);
            creature.sendToLoggers(defender.getName() + " ARMORMOD " + armourMod, (byte) 2);
        }else{
            float oakshellPower = defender.getBonusForSpellEffect(Enchants.CRET_OAKSHELL);
            if (oakshellPower > 70.0f) {
                bounceWoundPower = oakshellPower;
                bounceWoundName = "Thornshell";
            }

            if(!defender.isPlayer()) { // Creature glance rates
                float baseGlanceRate = 0.05f;
                float glanceChance = defender.getArmourType().getGlanceRate(woundType, Materials.MATERIAL_IRON);
                float glanceRoll = Server.rand.nextFloat();
                //logger.info(String.format("%s glance chance: %.2f [%.2f roll]", defender.getName(), glanceChance, glanceRoll));
                if(glanceRoll < glanceChance){
                    // Attack glances
                    glance = true;
                    baseDamage *= 0.4;
                }
            }
        }

        // Deal damage to the attacker's weapon
        if (!attWeapon.isBodyPartAttached()) {
            boolean rust = defender.hasSpellEffect(Enchants.CRET_RUSTMONSTER);
            if (rust) {
                creature.getCommunicator().sendAlertServerMessage("Your " + attWeapon.getName() + " takes excessive damage from " + defender.getNameWithGenus() + ".");
            }
            float mod = rust ? 5.0f : 1.0f;
            attWeapon.setDamage(attWeapon.getDamage() + Math.min(1.0f, (float)(baseDamage * (double)armourMod / 1000000.0)) * attWeapon.getDamageModifier() * mod);
        }

        // Rift item damage reduction bonus
        //double damReductionBonus = ItemBonus.getDamReductionBonusFor(armour != null ? armour.getArmourType() : defender.getArmourType(), woundType, attWeapon, defender);
        double defdamage = baseDamage;// * damReductionBonus;

        if (defender.isPlayer()) {
            if (((Player)defender).getAlcohol() > 50.0f) {
                defdamage *= 0.5;
            }
            if(getCreatureFightLevel(defender) > 0){ // Reduce damage by 0.9^focus
                defdamage *= Math.pow(0.95, getCreatureFightLevel(defender));
            }
        }
        if (defender.hasTrait(Traits.TRAIT_TOUGH)) { // 10% damage reduction from Tough trait.
            defdamage *= 0.9;
        }

        if (DamageMethods.canDealDamage(defdamage, armourMod)) {
            float champMod = defender.isChampion() ? 0.4f : 1.0f;
            Battle battle = defender.getBattle();
            dead = false;

            // Apply skill gain
            Skill fstyle = DamageMethods.getCreatureSkill(creature, SkillList.FIGHT_NORMALSTYLE);
            if(this.currentStyle == Style.AGGRESSIVE.id){
                fstyle = DamageMethods.getCreatureSkill(creature, SkillList.FIGHT_AGGRESSIVESTYLE);
            }else if(this.currentStyle == Style.DEFENSIVE.id){
                fstyle = DamageMethods.getCreatureSkill(creature, SkillList.FIGHT_DEFENSIVESTYLE);
            }
            fstyle.skillCheck(0, 0, blockSkill, 10f);

            // Venom
            float poisdam = attWeapon.getSpellVenomBonus();
            if (poisdam > 0.0f) {
                float half = Math.max(1.0f, poisdam / 2.0f);
                poisdam = half + (float)Server.rand.nextInt((int)half);
                defdamage *= 0.8;
            }

            // Untame uniques that are fighting eachother.
            if (defender.isUnique() && creature.isUnique() && defender.getStatus().damage > 10000) {
                defender.setTarget(-10, true);
                creature.setTarget(-10, true);
                defender.setOpponent(null);
                creature.setOpponent(null);
                try {
                    defender.checkMove();
                } catch (Exception ignored) { }
                try {
                    creature.checkMove();
                }
                catch (Exception ignored) { }
            }

            // Sparring stuff
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

            // One shot death if it's a rooster etc.
            if (defender.getStaminaSkill().getKnowledge() < 2.0) {
                defender.die(false, "Critter death by attack");
                creature.achievement(223);
                dead = true;
                return dead;
            }

            // Deal the initial wound
            //dead = CombatEngine.addWound(creature, defender, woundType, position, defdamage, armourMod, attString, battle, Server.rand.nextInt((int)Math.max(1.0f, attWeapon.getWeaponSpellDamageBonus())), poisdam, false);
            dead = DamageEngine.addWound(creature, defender, woundType, position, defdamage, armourMod, attString, battle, Server.rand.nextInt((int)Math.max(1.0f, attWeapon.getWeaponSpellDamageBonus())), poisdam, false, false, true, false, attWeapon, critical, glance);

            // BONK! achievement check
            if (attWeapon.isWeaponCrush() && attWeapon.getWeightGrams() > 4000 && armour != null && armour.getTemplateId() == ItemList.helmetGreat) {
                defender.achievement(49);
            }

            // Life transfer
            if(creature.getBody() != null && creature.getBody().getWounds() != null && creature.getBody().getWounds().getWounds() != null) { // Ensure wounds are initialized
                Wound[] w = creature.getBody().getWounds().getWounds();
                if(w.length > 0) { // Ensure the player has at least one wound.
                    float lifeTransferPower = Math.min(DUSKombatMod.getCombatEnchantCap(), Math.max(attWeapon.getSpellLifeTransferModifier(), attWeapon.getSpellEssenceDrainModifier() / 3.0F));
                    if(lifeTransferPower > 0) {
                        float lifeTransferChampMod = creature.isChampion() ? 500.0f : 250.0f;
                        if(creature.getCultist() != null && creature.getCultist().healsFaster()){
                            lifeTransferChampMod *= 0.5f;
                        }

                        float lifeTransferDamageHealed = ((float) defdamage) * armourMod * lifeTransferPower / lifeTransferChampMod;

                        // Calculate resistance effects
                        double resistance = SpellResist.getSpellResistance(creature, Actions.SPELL_ITEMBUFF_LIFETRANSFER);
                        lifeTransferDamageHealed *= resistance;

                        if (DamageMethods.canDealDamage(lifeTransferDamageHealed, armourMod)) {
                            Wound targetWound = w[0];
                            int var29 = w.length;

                            for (Wound wound : w) {
                                if (wound.getSeverity() > targetWound.getSeverity()) {
                                    targetWound = wound;
                                }
                            }

                            byte type = targetWound.getType();
                            if(type == Wound.TYPE_BURN || type == Wound.TYPE_COLD || type == Wound.TYPE_ACID){ // Elemental wounds heal 50% slower
                                lifeTransferDamageHealed *= 0.5f;
                            }else if(type == Wound.TYPE_INTERNAL || type == Wound.TYPE_INFECTION || type == Wound.TYPE_POISON || type == Wound.TYPE_WATER){ // External wounds heal 70% slower
                                lifeTransferDamageHealed *= 0.3f;
                            }
                            // Reduce healing by 50% if it's PvP combat
                            if(Servers.localServer.PVPSERVER && (defender.isDominated() || defender.isPlayer()) && creature.isPlayer()){
                                lifeTransferDamageHealed *= 0.5f;
                            }

                            SpellResist.addSpellResistance(creature, Actions.SPELL_ITEMBUFF_LIFETRANSFER, Math.min((double)targetWound.getSeverity(), lifeTransferDamageHealed));
                            targetWound.modifySeverity(-((int)lifeTransferDamageHealed));
                        }
                    }
                }
            }

            // Reduce focus if the hit is heavy enough
            byte defenderFightLevel = getCreatureFightLevel(defender);
            if (creature.isPlayer() != defender.isPlayer() && defdamage > 10000.0 && defenderFightLevel > 0) {
                //defender.fightlevel = (byte)(defender.fightlevel - 1);
                try {
                    // Setting private field using reflection
                    ReflectionUtil.setPrivateField(defender, ReflectionUtil.getField(defender.getClass(), "fightlevel"), (byte) (defenderFightLevel - 1));
                } catch (IllegalAccessException | NoSuchFieldException e) {
                    e.printStackTrace();
                }
                defender.getCommunicator().sendCombatNormalMessage("You lose some focus.");
                if (defender.isPlayer()) {
                    defender.getCommunicator().sendFocusLevel(defender.getWurmId());
                }
            }

            // Flaming Aura wound
            float flamingAuraPower = Math.min(DUSKombatMod.getCombatEnchantCap(), attWeapon.getSpellDamageBonus());
            if(flamingAuraPower > 0) {
                double flamingAuraDamage = defdamage * flamingAuraPower * 0.003d; // 0.30% damage per power
                if (!dead && DamageMethods.canDealDamage(flamingAuraDamage, armourMod)) {
                    //dead = DamageEngine.addWound(creature, defender, Wound.TYPE_BURN, position, flamingAuraDamage, armourMod, "ignite", battle, 0, 0, false, false);
                    //dead = defender.addWoundOfType(creature, Wound.TYPE_BURN, position, false, armourMod, false, flamingAuraDamage);
                    dead = DamageEngine.addFireWound(creature, defender, position, flamingAuraDamage, armourMod, true);
                }
            }

            // Frostbrand wound
            float frostbrandPower = Math.min(DUSKombatMod.getCombatEnchantCap(), attWeapon.getSpellFrostDamageBonus());
            if(frostbrandPower > 0) {
                double frostbrandDamage = defdamage * frostbrandPower * 0.003d; // 0.30% damage per power
                if (!dead && DamageMethods.canDealDamage(frostbrandDamage, armourMod)) {
                    //dead = defender.addWoundOfType(creature, Wound.TYPE_COLD, position, false, armourMod, false, frostbrandDamage);
                    dead = DamageEngine.addColdWound(creature, defender, position, frostbrandDamage, armourMod, true);
                }
            }

            // Essence Drain wound
            float essenceDrainPower = Math.min(DUSKombatMod.getCombatEnchantCap(), attWeapon.getSpellEssenceDrainModifier());
            if (essenceDrainPower > 0) {
                double essenceDrainDamage = defdamage * essenceDrainPower * 0.001d; // 0.10% damage per power
                if (!dead && DamageMethods.canDealDamage(essenceDrainDamage, armourMod)) {
                    dead = defender.addWoundOfType(creature, (byte) 9, position, false, armourMod, false, (double) (attWeapon.getSpellEssenceDrainModifier() / 1000.0F) * defdamage, 0.0F, 0.0F, true, true);
                }
            }

            // Material damage wound
            float materialDamagePower = Weapon.getMaterialExtraWoundMod(attWeapon.getMaterial());
            if(materialDamagePower > 0) {
                double materialDamage = defdamage * materialDamagePower; // Damage modifier is calculated in
                if (!dead && DamageMethods.canDealDamage(materialDamage, armourMod)) {
                    byte materialDamageType = Weapon.getMaterialExtraWoundType(attWeapon.getMaterial());
                    dead = defender.addWoundOfType(creature, materialDamageType, position, false, armourMod, false, materialDamage, 0f, 0f, true, false);
                }
            }

            // Reflect wound (Thornshell/Aura of Shared Pain)
            if (bounceWoundPower > 0.0f) {
                float bounceDamage = (float) (bounceWoundPower * defdamage * champMod * 0.0035); // 0.35% damage reflected per power
                float spellResist = 1.0f;
                if(!creature.isPlayer()) {
                    spellResist = creature.addSpellResistance(Actions.SPELL_ITEMBUFF_PAINSHARE); // Aura of Shared Pain action/spell number
                    bounceDamage *= spellResist;
                }
                if (creature.isUnique()) { // Uniques ignore effects of AOSP/Thornshell always.
                    CombatMessages.sendBounceWoundIgnoreMessages(creature, defender, bounceWoundName);
                }else if (DamageMethods.canDealDamage(bounceDamage, spellResist)) { // Doesn't use "dead" because affecting attacker
                    CombatEngine.addBounceWound(defender, creature, woundType, position, bounceDamage, armourMod, 0f, 0f, true, true);
                }
            }

            // Web Armour
            if (armour != null && armour.getSpellSlowdown() > 0.0f) {
                float webArmourPower = Math.min(DUSKombatMod.getCombatEnchantCap(), armour.getSpellSlowdown());
                if (creature.getMovementScheme().setWebArmourMod(true, webArmourPower)) {
                    creature.setWebArmourModTime(webArmourPower / 10.0f);
                    CombatMessages.sendWebArmourMessages(creature, defender, armour);
                    //creature.getCommunicator().sendCombatAlertMessage("Dark stripes spread along your " + attWeapon.getName() + " from " + defender.getNamePossessive() + " armour. You feel drained.");
                }
            }

            // Overkilling stuff?
            if (!Players.getInstance().isOverKilling(creature.getWurmId(), defender.getWurmId()) && attWeapon.getSpellExtraDamageBonus() > 0.0f) {
                if (defender.isPlayer() && !defender.isNewbie()) {
                    SpellEffect speff2 = attWeapon.getSpellEffect(Enchants.BUFF_BLOODTHIRST);
                    float mod = 1.0f;
                    if (defdamage * (double)armourMod * (double)champMod < 5000.0) {
                        mod = (float)(defdamage * (double)armourMod * (double)champMod / 5000.0);
                    }
                    if (speff2 != null) {
                        float divisor = Math.max(1, speff2.getPower()/1000);
                        speff2.setPower(Math.min(17000.0f, speff2.power + ((dead ? 20.0f : 2.0f * mod)/divisor)));
                    }
                } else if (!defender.isPlayer() && !defender.isGuard() && dead) {
                    SpellEffect speff3 = attWeapon.getSpellEffect(Enchants.BUFF_BLOODTHIRST);
                    float mod = 1.0f;
                    if(defender.isUnique() || defender.getBaseCombatRating() > 50){
                        mod = defender.getBaseCombatRating()/50; // Give large bonus for difficult creatures.
                    }else if(speff3.getPower() >= 15000.0f){
                        mod = 0f; // No bonus for > 15k unless against difficult creatures.
                    }else if(speff3.getPower() >= 10000.0f){
                        mod = Math.max(0.01f, 0.5f - (speff3.getPower() - 10000.0f) / 10000.0f); // Gains between 1% and 50% for 10k - 15k.
                    }else if (speff3.getPower() > 5000.0f) {
                        mod = Math.max(0.5f, 1.0f - (speff3.getPower() - 5000.0f) / 5000.0f); // Gains between 50% and 100% for 5k - 10k
                    }
                    if (speff3 != null) {
                        speff3.setPower(Math.min(17000.0f, speff3.power + defender.getBaseCombatRating() * mod));
                    }
                }
            }

            // Finishing touches
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
            } else if (defdamage > 30000.0 && (double)Server.rand.nextInt(100000) < defdamage) { // Stunning hit
                Skill defBodyControl = DamageMethods.getCreatureSkill(defender, SkillList.BODY_CONTROL);

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
        } else { // Doesn't deal enough damage to inflict a hit
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
    public void setKillEffects(Creature performer, Creature defender) {
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
                if (performer.getKingdomTemplateId() == 3 || performer.getDeity() != null && performer.getDeity().isHateGod()) {
                    performer.maybeModifyAlignment(-5.0f);
                } else {
                    performer.maybeModifyAlignment(5.0f);
                }
                if (getCombatHandled(performer).currentStyle == Style.DEFENSIVE.id) {
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
            // Apply alignment gains for killing to all gods.
            if (performer.getDeity() != null) {
                if(performer.getDeity().isHateGod()) {
                    performer.maybeModifyAlignment(-0.5f);
                }else{
                    performer.maybeModifyAlignment(0.5f);
                }
            }
        }
        if (performer.getPrimWeapon() != null && (ms = performer.getPrimWeapon().getSpellMindStealModifier()) > 0.0f && !defender.isPlayer() && defender.getKingdomId() != performer.getKingdomId()) {
            Skills s = defender.getSkills();
            int r = Server.rand.nextInt(s.getSkills().length);
            Skill toSteal = s.getSkills()[r];
            float skillStolen = ms / 100.0f * 0.1f;
            try {
                Skill owned = performer.getSkills().getSkill(toSteal.getNumber());
                if (owned.getKnowledge() < toSteal.getKnowledge()) {
                    double smod = (toSteal.getKnowledge() - owned.getKnowledge()) / 100.0;
                    owned.setKnowledge(owned.getKnowledge() + (double)skillStolen * smod, false);
                    performer.getCommunicator().sendSafeServerMessage("The " + performer.getPrimWeapon().getName() + " steals some " + toSteal.getName() + ".");
                }
            }
            catch (NoSuchSkillException owned) {
                // empty catch block
            }
        }
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
        if (this.currentStyle == Style.DEFENSIVE.id) {
            timeMod = 1.0f;
        }

        float calcspeed = this.getWeaponSpeed(attacker, weapon);
        calcspeed += timeMod;
        if (weapon.getRarity() > 0) {
            calcspeed -= weapon.getRarity() * 0.2f; // Rarity bonus
        }

        // Frantic Charge
        if (!weapon.isArtifact() && attacker.getBonusForSpellEffect(Enchants.CRET_CHARGE) > 0.0f) {
            float maxBonus = calcspeed * 0.1f; // 10% of the swing speed
            float percentage = attacker.getBonusForSpellEffect(Enchants.CRET_CHARGE) / 100f; // Percentage to reduce by
            calcspeed -= maxBonus * percentage;
        }

        // Wind of Ages or Blessings of the Dark
        if (weapon.getSpellSpeedBonus() > 0.0f) {
            float maxBonus = calcspeed * 0.1f; // 10% of the swing speed
            float speedBonus = Math.min(DUSKombatMod.getCombatEnchantCap(), weapon.getSpellSpeedBonus());
            float percentage = speedBonus / 100.0f;
            calcspeed -= maxBonus * percentage;
        }

        if (weapon.isTwoHanded() && this.currentStyle == Style.AGGRESSIVE.id) {
            calcspeed *= 0.9f; //Aggressive stance
        }

        if (!Features.Feature.METALLIC_ITEMS.isEnabled() && weapon.getMaterial() == Materials.MATERIAL_GLIMMERSTEEL) {
            calcspeed *= 0.9f; //Glimmersteel
        }

        if (attacker.getStatus().getStamina() < 2000) {
            calcspeed += 1.0f; //Low Stamina
        }

        //calcspeed = (float)((double)calcspeed - attacker.getMovementScheme().getWebArmourMod() * 10.0); //Web armour

        // Web Armour mod is -0.5 at 100 power, -1.0 at 200 power, etc.
        // To cause it to double the swing timer per 200 power,
        // we simply need to reverse it's value and multiply that to the calcspeed.
        float waMult = (float) (attacker.getMovementScheme().getWebArmourMod() * -1d); // Value is 0.5 at 100 power, 1.0 at 200 power, etc.
        calcspeed *= 1f + waMult; // No change at 0. Doubles swing speed per 200 power.

        if (attacker.hasSpellEffect(Enchants.CRET_KARMASLOW)) {
            calcspeed *= 2.0f; //Karma Slow
        }
        return Math.max(DUSKombatMod.minimumSwingTimer, calcspeed);
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
            float attAngle = getDirectionTo(attacker, opponent);
            if (opponent.getVehicle() > -10L) {
                Vehicle vehic = Vehicles.getVehicleForId(opponent.getVehicle());
                if (vehic != null && vehic.isCreature()) {
                    try {
                        Creature ridden = Server.getInstance().getCreature(opponent.getVehicle());
                        attAngle = getDirectionTo(attacker, ridden);
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
    public static float getDirectionTo(Creature attacker, Creature opponent) {
        float defAngle = Creature.normalizeAngle(opponent.getStatus().getRotation());
        double newrot = Math.atan2((double)(attacker.getStatus().getPositionY() - opponent.getStatus().getPositionY()), (double)(attacker.getStatus().getPositionX() - opponent.getStatus().getPositionX()));
        //float attAngle = (float)(newrot * 57.29577951308232D) + 90.0F;
        float attAngle = (float) (Math.toDegrees(newrot) + 90F);
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

    public static Skill getCreatureWeaponSkill(Creature creature, Item weapon) {
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
                } else if (defender.opponent == opponent && (e = DUSKombat.getDefensiveActionEntry(getCombatHandled(opponent).currentStance)) == null) {
                    e = DUSKombat.getOpposingActionEntry(getCombatHandled(opponent).currentStance);
                }
            }
            if (e == null) {
                if (defender.combatRound > 2 && Server.rand.nextInt(2) == 0) {
                    if (defender.mayRaiseFightLevel()) {
                        e = Actions.actionEntrys[340];
                    }
                } else if (mycr - oppcr > 2.0f || getCombatHandled(defender).getSpeed(defender, defender.getPrimWeapon()) < 3.0f) {
                    if (DUSKombat.existsBetterOffensiveStance(getCombatHandled(defender).currentStance, getCombatHandled(opponent).currentStance) && (e = DUSKombat.changeToBestOffensiveStance(getCombatHandled(defender).currentStance, getCombatHandled(opponent).currentStance)) == null) {
                        e = DUSKombat.getNonDefensiveActionEntry(getCombatHandled(opponent).currentStance);
                    }
                } else if (mycr >= oppcr) {
                    if (defender.getStatus().damage < opponent.getStatus().damage) {
                        if (DUSKombat.existsBetterOffensiveStance(getCombatHandled(defender).currentStance, getCombatHandled(opponent).currentStance) && (e = DUSKombat.changeToBestOffensiveStance(getCombatHandled(defender).currentStance, getCombatHandled(opponent).currentStance)) == null) {
                            e = DUSKombat.getNonDefensiveActionEntry(getCombatHandled(opponent).currentStance);
                        }
                    } else {
                        e = DUSKombat.getDefensiveActionEntry(getCombatHandled(opponent).currentStance);
                        if (e == null) {
                            e = DUSKombat.getOpposingActionEntry(getCombatHandled(opponent).currentStance);
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
                        if (DUSKombat.getStanceForAction(e) != this.currentStance) {
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
        if (primweaponskill > (float)DUSKombat.getAttackSkillCap((short) 297)) {
            this.addToList(tempList, weapon, (short) 297, attacker, opponent, mycr, oppcr, primweaponskill);
        }
        if (primweaponskill > (float)DUSKombat.getAttackSkillCap((short) 294)) {
            this.addToList(tempList, weapon, (short) 294, attacker, opponent, mycr, oppcr, primweaponskill);
        }
        if (primweaponskill > (float)DUSKombat.getAttackSkillCap((short) 312)) {
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
            if (!DUSKombat.isStanceParrying(DUSKombat.getStanceForAction(e = Actions.actionEntrys[e.getNumber()]), opponentStance) || DUSKombat.isAtSoftSpot(DUSKombat.getStanceForAction(e), opponentStance)) continue;
            return e;
        }
        return null;
    }
    //Copy pasta
    protected static ActionEntry getOpposingActionEntry(byte opponentStance) {
        ListIterator<ActionEntry> it = selectStanceList.listIterator();
        while (it.hasNext()) {
            ActionEntry e = it.next();
            if (!DUSKombat.isStanceOpposing(DUSKombat.getStanceForAction(e = Actions.actionEntrys[e.getNumber()]), opponentStance) || DUSKombat.isAtSoftSpot(DUSKombat.getStanceForAction(e), opponentStance)) continue;
            return e;
        }
        return null;
    }
    //Copy pasta
    protected static ActionEntry getNonDefensiveActionEntry(byte opponentStance) {
        for (int x = 0; x < selectStanceList.size(); ++x) {
            int num = Server.rand.nextInt(selectStanceList.size());
            ActionEntry e = selectStanceList.get(num);
            if (DUSKombat.isStanceParrying(DUSKombat.getStanceForAction(e = Actions.actionEntrys[e.getNumber()]), opponentStance) || DUSKombat.isStanceOpposing(DUSKombat.getStanceForAction(e), opponentStance) || DUSKombat.isAtSoftSpot(DUSKombat.getStanceForAction(e), opponentStance)) continue;
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
        if (DUSKombat.isAtSoftSpot(nextStance, opponentStance)) {
            return false;
        }
        if (DUSKombat.isAtSoftSpot(opponentStance, currentStance)) {
            return false;
        }
        if (DUSKombat.isAtSoftSpot(opponentStance, nextStance)) {
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
        for (byte spot : opponentSoftSpots = DUSKombat.getSoftSpots(stanceChecked)) {
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
        if (DUSKombat.isAtSoftSpot(opponentStance, _currentStance)) {
            return false;
        }
        boolean isOpponentAtSoftSpot = DUSKombat.isAtSoftSpot(_currentStance, opponentStance);
        if (isOpponentAtSoftSpot || !DUSKombat.isStanceParrying(_currentStance, opponentStance) && !DUSKombat.isStanceOpposing(_currentStance, opponentStance)) {
            for (int x = 0; x < selectStanceList.size(); ++x) {
                int num = Server.rand.nextInt(selectStanceList.size());
                ActionEntry e = selectStanceList.get(num);
                byte nextStance = DUSKombat.getStanceForAction(e = Actions.actionEntrys[e.getNumber()]);
                if (!DUSKombat.isNextGoodStance(_currentStance, nextStance, opponentStance)) continue;
                return true;
            }
            return false;
        }
        for (int x = 0; x < selectStanceList.size(); ++x) {
            int num = Server.rand.nextInt(selectStanceList.size());
            ActionEntry e = selectStanceList.get(num);
            byte nextStance = DUSKombat.getStanceForAction(e = Actions.actionEntrys[e.getNumber()]);
            if (DUSKombat.isStanceParrying(_currentStance, nextStance) || DUSKombat.isStanceOpposing(_currentStance, nextStance)) continue;
            return true;
        }
        return false;
    }
    //Copy pasta;  It appears as though isNextGoodStance calls in this method uses wrong argument order.
    protected static ActionEntry changeToBestOffensiveStance(byte _currentStance, byte opponentStance) {
        for (int x = 0; x < selectStanceList.size(); ++x) {
            int num = Server.rand.nextInt(selectStanceList.size());
            ActionEntry e = selectStanceList.get(num);
            byte nextStance = DUSKombat.getStanceForAction(e = Actions.actionEntrys[e.getNumber()]);
            if (!DUSKombat.isNextGoodStance(_currentStance, nextStance, opponentStance)) continue;
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
        return DUSKombat.getDistdiff(wpn, creature, opponent);
    }
    //Copy pasta
    protected static float getDistdiff(Item weapon, Creature creature, Creature opponent) {
        float idealDist = (float)(10 + Weapon.getReachForWeapon(weapon) * 3);
        float dist = Creature.rangeToInDec(creature, opponent);
        return idealDist - dist;
    }
    //Copy pasta
    public void sendStanceAnimation(Creature attacker, Item weapon, byte aStance, boolean attack) {
        if (aStance == 8) {
            attacker.sendToLoggers(attacker.getName() + ": " + "stancerebound", (byte)2);
            attacker.playAnimation("stancerebound", false);
        } else if (aStance == 9) {
            attacker.getStatus().setStunned(3.0F, false);
            attacker.playAnimation("stanceopen", false);
            attacker.sendToLoggers(attacker.getName() + ": " + "stanceopen", (byte)2);
        } else {
            StringBuilder sb = new StringBuilder();
            if(attack) {
                sb.append(CombatMessages.getAttackAnimationString(attacker, weapon, attString));
            }else{
                sb.append("fight");
            }
            /*sb.append("fight");
            if (attack) {
                if(weapon.isBodyPartAttached()){
                    if(attString.equals("headbutt")){
                        sb.append("_headbutt");
                    }
                }
                if (attString.equals("hit")) {
                    sb.append("_strike");
                } else if(attString.equals("pierce")) {
                    sb.append("_" + attString);
                }
            }*/

            //if (!attacker.isUnique() || attacker.getHugeMoveCounter() == 2) {
                attacker.playAnimation(sb.toString(), !attack);
            //}

            attacker.sendToLoggers(attacker.getName() + ": " + sb.toString(), (byte)2);
        }

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

        if (attacker.hasSpellEffect(Enchants.ITEM_DEBUFF_CLUMSINESS)) {
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

        float bon = Math.min(DUSKombatMod.getCombatEnchantCap(), weapon.getSpellNimbleness());
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
            if (attacker.isPlayer() && this.currentStyle >= 1 && attacker.getStatus().getStamina() > 2000) {
                int num = SkillList.FIGHT_AGGRESSIVESTYLE;
                if (this.currentStyle == Style.NORMAL.id) {
                    num = SkillList.FIGHT_NORMALSTYLE;
                }

                Skill def;

                try {
                    def = attacker.getSkills().getSkill(num);
                } catch (NoSuchSkillException var13) {
                    def = attacker.getSkills().learn(num, 1.0F);
                }

                if (def.skillCheck((double)(attacker.getBaseCombatRating() * 2.0F), 0.0D, true, 10.0F, attacker, opponent) > 0.0D) {
                    combatRating = (float)((double)combatRating + (double)((float)this.currentStyle / 2.0F) * Server.getModifiedFloatEffect(def.getRealKnowledge() / 100.0D));
                }
            }
        } else if (attacker.isPlayer() && this.currentStyle > 1) {
            Skill def;

            try {
                def = attacker.getSkills().getSkill(10053);
            } catch (NoSuchSkillException var12) {
                def = attacker.getSkills().learn(10053, 1.0F);
            }

            if (def.skillCheck(Server.getModifiedFloatEffect(70.0D), 0.0D, true, 10.0F, attacker, opponent) < 0.0D) {
                combatRating = (float)((double)combatRating - (double)this.currentStyle * Server.getModifiedFloatEffect((100.0D - def.getRealKnowledge()) / 100.0D));
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
                        } catch (NoSuchActionException ignored) { }
                    }
                }
            } catch (NoSuchActionException ignored) { }
        }

        if (attacker.hasAttackedUnmotivated()) {
            combatRating = Math.min(4.0F, combatRating);
        }

        return Math.min(100.0F, Math.max(1.0F, combatRating));
    }

}