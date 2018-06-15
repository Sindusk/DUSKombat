package mod.piddagoras.combathandled;

import com.wurmonline.server.MessageServer;
import com.wurmonline.server.Server;
import com.wurmonline.server.bodys.DbWound;
import com.wurmonline.server.bodys.TempWound;
import com.wurmonline.server.bodys.Wound;
import com.wurmonline.server.combat.Battle;
import com.wurmonline.server.combat.BattleEvent;
import com.wurmonline.server.creatures.CombatHandler;
import com.wurmonline.server.creatures.Creature;
import com.wurmonline.server.utils.CreatureLineSegment;
import com.wurmonline.shared.constants.Enchants;
import com.wurmonline.shared.util.MulticolorLineSegment;

import java.util.ArrayList;
import java.util.logging.Logger;

public class DamageEngine {
    public static Logger logger = Logger.getLogger(DamageEngine.class.getName());

    public static String getRealDamageString(final double damage) {
        if (damage < 500.0) {
            return "tickle";
        }
        if (damage < 1000.0) {
            return "slap";
        }
        if (damage < 2500.0) {
            return "irritate";
        }
        if (damage < 5000.0) {
            return "hurt";
        }
        if (damage < 10000.0) {
            return "harm";
        }
        return "damage";
    }

    public static String getStrengthString(final double damage) {
        if (damage <= 0.0) {
            return "unnoticeably";
        }
        if (damage <= 1.0) {
            return "very lightly";
        }
        if (damage <= 2.0) {
            return "lightly";
        }
        if (damage <= 3.0) {
            return "pretty hard";
        }
        if (damage <= 6.0) {
            return "hard";
        }
        if (damage <= 10.0) {
            return "very hard";
        }
        if (damage <= 20.0) {
            return "extremely hard";
        }
        return "deadly hard";
    }

    public static boolean addWound(Creature performer, Creature defender, byte type, int pos, double damage, float armourMod, String attString, Battle battle, float infection, float poison, boolean archery, boolean alreadyCalculatedResist) {
        // Debug message
        String perfName = "VOID";
        String defName = "VOID";
        if(performer != null){
            perfName = performer.getName();
        }
        if(defender != null){
            defName = defender.getName();
        }
        logger.info(String.format("%s dealing wound to %s", perfName, defName));

        // Inform creature AI that wounds were inflicted
        if (performer != null && performer.getTemplate().getCreatureAI() != null) {
            damage = performer.getTemplate().getCreatureAI().causedWound(performer, defender, type, pos, armourMod, damage);
        }
        if (defender != null && defender.getTemplate().getCreatureAI() != null) {
            damage = defender.getTemplate().getCreatureAI().receivedWound(defender, performer, type, pos, armourMod, damage);
        }


        if (!alreadyCalculatedResist) {
            if ((type == 8 || type == 4 || type == 10) && defender.getCultist() != null && defender.getCultist().hasNoElementalDamage()) {
                return false;
            }
            if (defender.hasSpellEffect(Enchants.CRET_CONTINUUM)) {
                damage *= 0.8;
            }
            damage *= Wound.getResistModifier(defender, type);
        }
        boolean dead = false;
        if (DamageMethods.canDealDamage(damage, armourMod)) {
            if (defender.hasSpellEffect(Enchants.CRET_STONESKIN)) {
                defender.reduceStoneSkin();
                return false;
            }
            Wound wound = null;
            boolean foundWound = false;
            String broadCastString = "";
            final String otherString = (CombatHandler.getOthersString() == "") ? (attString + "s") : CombatHandler.getOthersString();
            CombatHandler.setOthersString("");
            if (Server.rand.nextInt(10) <= 6 && defender.getBody().getWounds() != null) {
                wound = defender.getBody().getWounds().getWoundTypeAtLocation((byte)pos, type);
            }
            if (wound != null) {
                defender.setWounded();
                if (infection > 0.0f) {
                    wound.setInfectionSeverity(Math.min(99.0f, wound.getInfectionSeverity() + Server.rand.nextInt((int)infection)));
                }
                if (poison > 0.0f) {
                    wound.setPoisonSeverity(Math.min(99.0f, wound.getPoisonSeverity() + poison));
                }
                wound.setBandaged(false);
                if (wound.getHealEff() > 0 && Server.rand.nextInt(2) == 0) {
                    wound.setHealeff((byte)0);
                }
                dead = wound.modifySeverity((int)(damage * armourMod), performer != null && performer.isPlayer());
                foundWound = true;
            }
            else {
                if (!defender.isPlayer()) {
                    wound = new TempWound(type, (byte)pos, (float)damage * armourMod, defender.getWurmId(), poison, infection);
                }
                else {
                    wound = new DbWound(type, (byte)pos, (float)damage * armourMod, defender.getWurmId(), poison, infection, performer != null && performer.isPlayer());
                }
                defender.setWounded();
            }
            if (performer != null) {
                ArrayList<MulticolorLineSegment> segments = new ArrayList<MulticolorLineSegment>();
                segments.add(new CreatureLineSegment(performer));
                segments.add(new MulticolorLineSegment(" " + otherString + " ", (byte)0));
                if (performer == defender) {
                    segments.add(new MulticolorLineSegment(performer.getHimHerItString() + "self", (byte)0));
                }
                else {
                    segments.add(new CreatureLineSegment(defender));
                }
                segments.add(new MulticolorLineSegment(" " + getStrengthString(damage / 1000.0) + " in the " + defender.getBody().getWoundLocationString(wound.getLocation()) + " and " + getRealDamageString(damage * armourMod), (byte)0));
                segments.add(new MulticolorLineSegment("s it.", (byte)0));
                MessageServer.broadcastColoredAction(segments, performer, defender, 5, true);
                for (final MulticolorLineSegment s : segments) {
                    broadCastString += s.getText();
                }
                if (performer != defender) {
                    for (final MulticolorLineSegment s : segments) {
                        s.setColor((byte)7);
                    }
                    defender.getCommunicator().sendColoredMessageCombat(segments);
                }
                segments.get(1).setText(" " + attString + " ");
                segments.get(4).setText(" it.");
                for (final MulticolorLineSegment s : segments) {
                    s.setColor((byte)3);
                }
                performer.getCommunicator().sendColoredMessageCombat(segments);
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
                final ArrayList<MulticolorLineSegment> segments = new ArrayList<MulticolorLineSegment>();
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
                final ArrayList<MulticolorLineSegment> segments = new ArrayList<MulticolorLineSegment>();
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
}
