package mod.piddagoras.duskombat;

import com.wurmonline.server.Server;
import com.wurmonline.server.WurmId;
import com.wurmonline.server.bodys.DbWound;
import com.wurmonline.server.bodys.TempWound;
import com.wurmonline.server.bodys.Wound;
import com.wurmonline.server.combat.Battle;
import com.wurmonline.server.combat.BattleEvent;
import com.wurmonline.server.creatures.CombatHandler;
import com.wurmonline.server.creatures.Creature;
import com.wurmonline.server.items.Item;
import com.wurmonline.server.utils.CreatureLineSegment;
import com.wurmonline.shared.constants.Enchants;
import com.wurmonline.shared.util.MulticolorLineSegment;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;
import java.util.logging.Logger;

public class DamageEngine {
    public static Logger logger = Logger.getLogger(DamageEngine.class.getName());

    // Map all damage dealt instances in a map. This is extremely useful in some cases.
    public static HashMap<Long, Map<Long, Double>> dealtDamage = new HashMap<>();
    public static void addDealtDamage(long defender, long attacker, double damage){
        if(dealtDamage.containsKey(defender)){
            Map<Long, Double> dealers = dealtDamage.get(defender);
            if(!dealers.containsKey(attacker)){
                dealers.put(attacker, damage);
            }else{
                double newDam = dealers.get(attacker);
                newDam += damage;
                dealers.put(attacker, newDam);
            }
        }else{
            Map<Long, Double> dealers = new HashMap<>();
            dealers.put(attacker, damage);
            dealtDamage.put(defender, dealers);
        }
    }

    public static boolean addWound(Creature performer, Creature defender, byte type, int pos, double damage, float armourMod, String attString, Battle battle, float infection, float poison, boolean archery, boolean alreadyCalculatedResist, boolean noMinimumDamage, boolean spell, Item weapon, boolean critical, boolean glance) {
        // Debug message
        /*String perfName = "VOID";
        String defName = "VOID";
        if(performer != null){
            perfName = performer.getName();
        }
        if(defender != null){
            defName = defender.getName();
        }
        logger.info(String.format("%s dealing wound to %s", perfName, defName));*/

        if(defender == null){
            logger.severe("Called addWound with a null defender.");
            return false;
        }

        // Custom damage multipliers from mod settings.
        if(performer != null){
            if((performer.isPlayer() || performer.isDominated()) && !defender.isPlayer() && !defender.isDominated()){
                damage *= DUSKombatMod.playerToEnvironmentDamageMultiplier;
            }
            if(!performer.isPlayer() && !performer.isDominated() && (defender.isPlayer() || defender.isDominated())){
                damage *= DUSKombatMod.environmentToPlayerDamageMultiplier;
            }
            if((performer.isPlayer() || performer.isDominated()) && (defender.isPlayer() || defender.isDominated())){
                damage *= DUSKombatMod.playerToPlayerDamageMultiplier;
            }
        }

        // Inform creature AI that wounds were inflicted
        if (performer != null && performer.getTemplate().getCreatureAI() != null) {
            damage = performer.getTemplate().getCreatureAI().causedWound(performer, defender, type, pos, armourMod, damage);
        }
        if (defender.getTemplate().getCreatureAI() != null) {
            damage = defender.getTemplate().getCreatureAI().receivedWound(defender, performer, type, pos, armourMod, damage);
        }

        // Calculate resistances and similar before applying damage.
        if (!alreadyCalculatedResist) {
            // Path of Power's Elemental Immunity special ability
            if(defender.getCultist() != null && defender.getCultist().hasNoElementalDamage()){
                if(type == Wound.TYPE_BURN || type == Wound.TYPE_COLD || type == Wound.TYPE_ACID){
                    byte level = defender.getCultist().getLevel();
                    float reduction = Math.max(0.0f, 1f-((level-8)*0.2f)); // 20% reduced damage per power
                    if(performer != null && (performer.isPlayer() || performer.isDominated())){ // Reduce effects by 50% in PvP
                        reduction = reduction + (1-reduction) * 0.5f;
                    }
                    if(reduction <= 0){
                        if(performer != null) {
                            CombatMessages.sendElementalWoundIgnoreAttackMessage(performer, defender, type);
                        }
                        CombatMessages.sendElementalWoundIgnoreDefenseMessage(defender, type);
                        return false;
                    }
                    damage *= reduction;
                }
            }

            // Continuum sorcery buff - reduces damage taken by 20%.
            if (defender.hasSpellEffect(Enchants.CRET_CONTINUUM)) {
                damage *= 0.8;
            }

            // Apply resistances/vulnerabilities
            damage *= Wound.getResistModifier(performer, defender, type);
        }

        boolean dead = false;
        if (DamageMethods.canDealDamage(damage, armourMod)) {
            if(performer != null) {
                addDealtDamage(defender.getWurmId(), performer.getWurmId(), damage);
            }
            // Trigger Testing
            /*HashMap<String, Object> data = new HashMap<>();
            data.put("type", type);
            Event.DamageTaken.occur(data);*/

            // Stoneskin sorcery - avoids up to 3 attacks.
            if (defender.hasSpellEffect(Enchants.CRET_STONESKIN)) {
                defender.reduceStoneSkin();
                return false;
            }
            Wound wound = null;
            boolean foundWound = false;
            String broadCastString = "";
            final String otherString = (CombatHandler.getOthersString() == "") ? (attString + "s") : CombatHandler.getOthersString();
            CombatHandler.setOthersString("");

            // 80% chance to stack the wound on an existing one.
            if (Server.rand.nextInt(10) <= 7 && defender.getBody().getWounds() != null) {
                wound = defender.getBody().getWounds().getWoundTypeAtLocation((byte)pos, type);
            }

            if (wound != null) { // Stack damage and other effects onto an existing wound
                defender.setWounded();
                if (infection > 0.0f) { // Add infection
                    wound.setInfectionSeverity(Math.min(99.0f, wound.getInfectionSeverity() + infection));
                }
                if (poison > 0.0f) { // Add poison
                    wound.setPoisonSeverity(Math.min(99.0f, wound.getPoisonSeverity() + poison));
                }
                wound.setBandaged(false); // Remove bandaging
                if (wound.getHealEff() > 0 && Server.rand.nextInt(2) == 0) { // 50% chance to remove healing covers
                    wound.setHealeff((byte)0);
                }
                dead = wound.modifySeverity((int)(damage * armourMod), performer != null && (performer.isPlayer() || performer.isDominated()), spell);
                foundWound = true;
            }
            else {
                if (!defender.isPlayer()) {
                    wound = new TempWound(type, (byte)pos, (float)damage * armourMod, defender.getWurmId(), poison, infection, spell);
                } else {
                    wound = new DbWound(type, (byte)pos, (float)damage * armourMod, defender.getWurmId(), poison, infection, performer != null && performer.isPlayer(), spell);
                }
                defender.setWounded();
            }
            if (performer != null) {
                // Send combat messages only if the target has been attacked by another creature
                String woundLoc = defender.getBody().getWoundLocationString(wound.getLocation());
                CombatMessages.sendDamageMessages(performer, defender, weapon, attString, damage, armourMod, type, woundLoc, critical, glance);
            }
            if (defender.isDominated()) {
                if (!archery) {
                    if (!defender.isReborn() || defender.getMother() != -10L) {
                        defender.modifyLoyalty(-((int)(damage * armourMod) * defender.getBaseCombatRating() / 200000.0f));
                    }
                }
                else if (defender.getDominator() == performer) {
                    defender.modifyLoyalty(-((int)(damage * armourMod) * defender.getBaseCombatRating() / 200000.0f));
                }
            }
            if (infection > 0.0f && performer != null) {
                final ArrayList<MulticolorLineSegment> segments = new ArrayList<>();
                segments.add(new MulticolorLineSegment("Your weapon", (byte)3));
                segments.add(new MulticolorLineSegment(" infects ", (byte)3));
                segments.add(new CreatureLineSegment(defender));
                segments.add(new MulticolorLineSegment(" with a disease.", (byte)3));
                performer.getCommunicator().sendColoredMessageCombat(segments);
                segments.set(0, new CreatureLineSegment(performer));
                for (final MulticolorLineSegment s : segments) {
                    s.setColor((byte)7);
                }
                defender.getCommunicator().sendColoredMessageCombat(segments);
            }
            if (poison > 0.0f && performer != null) {
                final ArrayList<MulticolorLineSegment> segments = new ArrayList<>();
                segments.add(new MulticolorLineSegment("Your weapon", (byte)3));
                segments.add(new MulticolorLineSegment(" poisons ", (byte)3));
                segments.add(new CreatureLineSegment(defender));
                segments.add(new MulticolorLineSegment(".", (byte)3));
                performer.getCommunicator().sendColoredMessageCombat(segments);
                segments.set(0, new CreatureLineSegment(performer));
                for (final MulticolorLineSegment s : segments) {
                    s.setColor((byte)7);
                }
                defender.getCommunicator().sendColoredMessageCombat(segments);
            }
            if (battle != null && performer != null) {
                battle.addEvent(new BattleEvent((short)114, performer.getName(), defender.getName(), broadCastString));
            }
            if (!foundWound) {
                dead = defender.getBody().addWound(wound);
            }
        }
        return dead;
    }

    public static boolean addFireWound(Creature performer, Creature defender, int pos, double damage, float armourMod, boolean spell) {
        if(defender == null){
            logger.severe("Called addWound with a null defender.");
            return false;
        }

        if (performer != null && performer.getTemplate().getCreatureAI() != null) {
            damage = performer.getTemplate().getCreatureAI().causedWound(performer, defender, Wound.TYPE_BURN, pos, armourMod, damage);
        }
        if (defender.getTemplate().getCreatureAI() != null) {
            damage = defender.getTemplate().getCreatureAI().receivedWound(defender, performer, Wound.TYPE_BURN, pos, armourMod, damage);
        }
        if (defender.getCultist() != null && defender.getCultist().hasNoElementalDamage()) {
            return false;
        }
        boolean dead = false;
        if (DamageMethods.canDealDamage(damage, armourMod)) {
            if(performer != null) {
                addDealtDamage(defender.getWurmId(), performer.getWurmId(), damage);
            }
            if (defender.hasSpellEffect(Enchants.CRET_STONESKIN)) {
                defender.reduceStoneSkin();
                return false;
            }
            Wound wound = null;
            boolean foundWound = false;
            if (performer != null) {
                ArrayList<MulticolorLineSegment> segments = new ArrayList<>();
                segments.add(new MulticolorLineSegment("Your weapon", (byte) 3));
                segments.add(new MulticolorLineSegment(" burns ", (byte) 3));
                segments.add(new CreatureLineSegment(defender));
                segments.add(new MulticolorLineSegment(".", (byte) 3));
                performer.getCommunicator().sendColoredMessageCombat(segments);
                segments.set(0, new CreatureLineSegment(performer));
                for (MulticolorLineSegment s : segments) {
                    s.setColor((byte) 7);
                }
                defender.getCommunicator().sendColoredMessageCombat(segments);
            }
            if (defender.getBody().getWounds() != null && (wound = defender.getBody().getWounds().getWoundTypeAtLocation((byte)pos, Wound.TYPE_BURN)) != null) {
                if (wound.getType() == Wound.TYPE_COLD) {
                    defender.setWounded();
                    wound.setBandaged(false);
                    dead = wound.modifySeverity((int)(damage * (double)armourMod), performer != null && performer.isPlayer(), spell);
                    foundWound = true;
                } else {
                    wound = null;
                }
            }
            if (wound == null) {
                wound = WurmId.getType(defender.getWurmId()) == 1
                        ? new TempWound(Wound.TYPE_BURN, (byte)pos, (float)damage * armourMod, defender.getWurmId(), 0.0f, 0.0f, spell)
                        : new DbWound(Wound.TYPE_BURN, (byte)pos, (float)damage * armourMod, defender.getWurmId(), 0.0f, 0.0f, performer != null && performer.isPlayer(), spell);
            }
            if (!foundWound) {
                dead = defender.getBody().addWound(wound);
            }
        }
        return dead;
    }

    public static boolean addColdWound(Creature performer, Creature defender, int pos, double damage, float armourMod, boolean spell) {
        if(defender == null){
            logger.severe("Called addWound with a null defender.");
            return false;
        }

        if (performer != null && performer.getTemplate().getCreatureAI() != null) {
            damage = performer.getTemplate().getCreatureAI().causedWound(performer, defender, Wound.TYPE_COLD, pos, armourMod, damage);
        }
        if (defender.getTemplate().getCreatureAI() != null) {
            damage = defender.getTemplate().getCreatureAI().receivedWound(defender, performer, Wound.TYPE_COLD, pos, armourMod, damage);
        }
        if (defender.getCultist() != null && defender.getCultist().hasNoElementalDamage()) {
            return false;
        }
        boolean dead = false;
        if (DamageMethods.canDealDamage(damage, armourMod)) {
            if(performer != null) {
                addDealtDamage(defender.getWurmId(), performer.getWurmId(), damage);
            }

            if (defender.hasSpellEffect(Enchants.CRET_STONESKIN)) {
                defender.reduceStoneSkin();
                return false;
            }
            Wound wound = null;
            boolean foundWound = false;
            if (performer != null) {
                ArrayList<MulticolorLineSegment> segments = new ArrayList<>();
                segments.add(new MulticolorLineSegment("Your weapon", (byte) 3));
                segments.add(new MulticolorLineSegment(" freezes ", (byte) 3));
                segments.add(new CreatureLineSegment(defender));
                segments.add(new MulticolorLineSegment(".", (byte) 3));
                performer.getCommunicator().sendColoredMessageCombat(segments);
                segments.set(0, new CreatureLineSegment(performer));
                for (MulticolorLineSegment s : segments) {
                    s.setColor((byte) 7);
                }
                defender.getCommunicator().sendColoredMessageCombat(segments);
            }
            if (defender.getBody().getWounds() != null && (wound = defender.getBody().getWounds().getWoundTypeAtLocation((byte)pos, Wound.TYPE_COLD)) != null) {
                if (wound.getType() == Wound.TYPE_COLD) {
                    defender.setWounded();
                    wound.setBandaged(false);
                    dead = wound.modifySeverity((int)(damage * (double)armourMod), performer != null && performer.isPlayer(), spell);
                    foundWound = true;
                } else {
                    wound = null;
                }
            }
            if (wound == null) {
                wound = WurmId.getType(defender.getWurmId()) == 1
                        ? new TempWound(Wound.TYPE_COLD, (byte)pos, (float)damage * armourMod, defender.getWurmId(), 0.0f, 0.0f, spell)
                        : new DbWound(Wound.TYPE_COLD, (byte)pos, (float)damage * armourMod, defender.getWurmId(), 0.0f, 0.0f, performer != null && performer.isPlayer(), spell);
            }
            if (!foundWound) {
                dead = defender.getBody().addWound(wound);
            }
        }
        return dead;
    }
}
