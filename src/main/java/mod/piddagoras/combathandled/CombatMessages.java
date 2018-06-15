package mod.piddagoras.combathandled;

import com.wurmonline.server.Server;
import com.wurmonline.server.creatures.Creature;
import com.wurmonline.server.items.Item;
import com.wurmonline.server.sounds.SoundPlayer;
import com.wurmonline.server.utils.CreatureLineSegment;
import com.wurmonline.shared.util.MulticolorLineSegment;

import java.util.ArrayList;
import java.util.logging.Logger;

public class CombatMessages {
    public static Logger logger = Logger.getLogger(CombatMessages.class.getName());

    public static final byte YOU_COLOR = 11; // Cyan
    public static final byte POSITIVE_COLOR = 9; // Green
    public static final byte NEUTRAL_COLOR = 8; // Yellow
    public static final byte NEGATIVE_COLOR = 7; // Orange
    public static final byte EFFECT_COLOR = 14; // Gray
    public static final byte ITEM_COLOR = 15; // Light Gray

    public static void playMissEffects(Creature attacker, Creature opponent, Item weapon, double attackCheck){
        logger.info(String.format("%s's attack has missed %s due to hit chance failure. (Rolled %.2f)", attacker.getName(), opponent.getName(), attackCheck));
        String sstring = attackCheck < -50.0 ? "sound.combat.miss.med" : "sound.combat.miss.light";
        SoundPlayer.playSound(sstring, attacker, 1.6F);
        ArrayList<MulticolorLineSegment> segments = new ArrayList<>();
        segments.add(new MulticolorLineSegment("You", YOU_COLOR));
        segments.add(new MulticolorLineSegment(" ", ItemInfo.COLOR_WHITE));
        segments.add(new MulticolorLineSegment("miss", NEUTRAL_COLOR));
        segments.add(new MulticolorLineSegment(" with the ", ItemInfo.COLOR_WHITE));
        segments.add(new MulticolorLineSegment(weapon.getName(), ITEM_COLOR));
        segments.add(new MulticolorLineSegment(".", ItemInfo.COLOR_WHITE));
        if (attacker.spamMode()) {
            attacker.getCommunicator().sendColoredMessageCombat(segments);
        }
        if(opponent.spamMode()) {
            segments.set(0, new CreatureLineSegment(attacker));
            segments.set(2, new MulticolorLineSegment("misses", NEUTRAL_COLOR));
            opponent.getCommunicator().sendColoredMessageCombat(segments);
        }
        attacker.sendToLoggers("YOU MISS " + weapon.getName(), (byte)2);
        opponent.sendToLoggers(attacker.getName() + " MISS " + weapon.getName(), (byte)2);
    }
    public static void playDodgeEffects(Creature attacker, Creature opponent, Item weapon, double dodgeCheck){
        logger.info(String.format("%s's attack was dodged by %s. (Rolled %.2f)", attacker.getName(), opponent.getName(), dodgeCheck));
        String sstring = dodgeCheck > 50.0 ? "sound.combat.miss.heavy" : "sound.combat.miss.med";
        SoundPlayer.playSound(sstring, attacker, 1.6f);
        opponent.playAnimation("dodge", false);
        ArrayList<MulticolorLineSegment> segments = new ArrayList<>();
        segments.add(new CreatureLineSegment(opponent));
        segments.add(new MulticolorLineSegment(" ", ItemInfo.COLOR_WHITE));
        segments.add(new MulticolorLineSegment("dodges", NEUTRAL_COLOR));
        segments.add(new MulticolorLineSegment(" your ", ItemInfo.COLOR_WHITE));
        segments.add(new MulticolorLineSegment(weapon.getName(), ITEM_COLOR));
        segments.add(new MulticolorLineSegment(".", ItemInfo.COLOR_WHITE));
        if(attacker.spamMode()){
            attacker.getCommunicator().sendColoredMessageCombat(segments);
        }
        if(opponent.spamMode()){
            segments.set(0, new MulticolorLineSegment("You", YOU_COLOR));
            segments.set(2, new MulticolorLineSegment("dodge", NEUTRAL_COLOR));
            segments.set(3, new MulticolorLineSegment(" the ", ItemInfo.COLOR_WHITE));
            segments.set(5, new MulticolorLineSegment(" from ", ItemInfo.COLOR_WHITE));
            segments.add(new CreatureLineSegment(attacker));
            segments.add(new MulticolorLineSegment(".", ItemInfo.COLOR_WHITE));
            opponent.getCommunicator().sendColoredMessageCombat(segments);
        }
    }
    public static String getParrySound() {
        int x = Server.rand.nextInt(3);
        return x == 0 ? "sound.combat.parry2" : (x == 1 ? "sound.combat.parry3" : "sound.combat.parry1");
    }
    public static void playParryEffects(Creature attacker, Creature opponent, Item weapon, double parryCheck){
        logger.info(String.format("%s parries the attack from %s. (Rolled %.2f)", opponent.getName(), attacker.getName(), parryCheck));
        SoundPlayer.playSound(getParrySound(), opponent, 1.6F);
        opponent.playAnimation("parry.weapon", false);
        ArrayList<MulticolorLineSegment> segments = new ArrayList<>();
        segments.add(new CreatureLineSegment(opponent)); // 0
        segments.add(new MulticolorLineSegment(" ", ItemInfo.COLOR_WHITE)); // 1
        segments.add(new MulticolorLineSegment("parries", NEUTRAL_COLOR)); // 2
        segments.add(new MulticolorLineSegment(" your ", ItemInfo.COLOR_WHITE)); // 3
        segments.add(new MulticolorLineSegment(weapon.getName(), ITEM_COLOR)); // 4
        segments.add(new MulticolorLineSegment(" with their ", ItemInfo.COLOR_WHITE)); // 5
        segments.add(new MulticolorLineSegment(opponent.getPrimWeapon().getName(), ITEM_COLOR)); // 6
        if(attacker.spamMode()){
            attacker.getCommunicator().sendColoredMessageCombat(segments);
        }
        if(opponent.spamMode()){
            segments.set(0, new MulticolorLineSegment("You", YOU_COLOR));
            segments.set(2, new MulticolorLineSegment("parry", NEUTRAL_COLOR));
            segments.set(3, new MulticolorLineSegment(" the ", ItemInfo.COLOR_WHITE));
            segments.set(5, new MulticolorLineSegment(" from ", ItemInfo.COLOR_WHITE));
            segments.set(6, new CreatureLineSegment(attacker));
            segments.add(new MulticolorLineSegment(" with your ", ItemInfo.COLOR_WHITE)); // 7
            segments.add(new MulticolorLineSegment(opponent.getPrimWeapon().getName(), ITEM_COLOR)); // 8
            segments.add(new MulticolorLineSegment(".", ItemInfo.COLOR_WHITE)); // 9
            opponent.getCommunicator().sendColoredMessageCombat(segments);
        }
    }
    public static void playShieldBlockEffects(Creature attacker, Creature opponent, Item weapon, double shieldCheck){
        logger.info(String.format("%s blocks the attack from %s. (Rolled %.2f)", opponent.getName(), attacker.getName(), shieldCheck));
        Item defShield = opponent.getShield();
        if(defShield == null){
            logger.info("Shield block effects are to be played but shield is null?");
            return;
        }
        String sstring = "sound.combat.shield.metal";
        if(defShield.isWood()){
            sstring = "sound.combat.shield.wood";
        }
        SoundPlayer.playSound(sstring, opponent, 1.6F);
        opponent.playAnimation("parry.shield", false);
        ArrayList<MulticolorLineSegment> segments = new ArrayList<>();
        segments.add(new CreatureLineSegment(opponent)); // 0
        segments.add(new MulticolorLineSegment(" ", ItemInfo.COLOR_WHITE)); // 1
        segments.add(new MulticolorLineSegment("blocks", NEUTRAL_COLOR)); // 2
        segments.add(new MulticolorLineSegment(" your ", ItemInfo.COLOR_WHITE)); // 3
        segments.add(new MulticolorLineSegment(weapon.getName(), ITEM_COLOR)); // 4
        segments.add(new MulticolorLineSegment(" with their ", ItemInfo.COLOR_WHITE)); // 5
        segments.add(new MulticolorLineSegment(defShield.getName(), ITEM_COLOR)); // 6
        if(attacker.spamMode()){
            attacker.getCommunicator().sendColoredMessageCombat(segments);
        }
        if(opponent.spamMode()){
            segments.set(0, new MulticolorLineSegment("You", YOU_COLOR));
            segments.set(2, new MulticolorLineSegment("block", NEUTRAL_COLOR));
            segments.set(3, new MulticolorLineSegment(" the ", ItemInfo.COLOR_WHITE));
            segments.set(5, new MulticolorLineSegment(" from ", ItemInfo.COLOR_WHITE));
            segments.set(6, new CreatureLineSegment(attacker));
            segments.add(new MulticolorLineSegment(" with your ", ItemInfo.COLOR_WHITE));
            segments.add(new MulticolorLineSegment(defShield.getName(), ITEM_COLOR));
            segments.add(new MulticolorLineSegment(".", ItemInfo.COLOR_WHITE));
            opponent.getCommunicator().sendColoredMessageCombat(segments);
        }
    }

    public static void sendWebArmourMessages(Creature attacker, Creature opponent, Item armour){
        ArrayList<MulticolorLineSegment> segments = new ArrayList<>();
        segments.add(new CreatureLineSegment(opponent)); // 0
        segments.add(new MulticolorLineSegment(" is ", ItemInfo.COLOR_WHITE)); // 1
        segments.add(new MulticolorLineSegment("drained", POSITIVE_COLOR)); // 2
        segments.add(new MulticolorLineSegment(" by the effects of ", ItemInfo.COLOR_WHITE)); // 3
        segments.add(new MulticolorLineSegment("Web Armour", EFFECT_COLOR)); // 4
        segments.add(new MulticolorLineSegment(" on your ", ItemInfo.COLOR_WHITE)); // 5
        segments.add(new MulticolorLineSegment(armour.getName(), ITEM_COLOR)); // 6
        segments.add(new MulticolorLineSegment(".", ItemInfo.COLOR_WHITE)); // 7
        if(attacker.spamMode()){
            attacker.getCommunicator().sendColoredMessageCombat(segments);
        }
        if(opponent.spamMode()) {
            segments.set(0, new MulticolorLineSegment("You", YOU_COLOR));
            segments.set(1, new MulticolorLineSegment(" are ", ItemInfo.COLOR_WHITE));
            segments.set(5, new MulticolorLineSegment(" on ", NEUTRAL_COLOR));
            segments.set(6, new CreatureLineSegment(opponent));
            segments.set(7, new MulticolorLineSegment("'s ", ItemInfo.COLOR_WHITE));
            segments.add(new MulticolorLineSegment(armour.getName(), ITEM_COLOR)); // 8
            segments.add(new MulticolorLineSegment(".", ItemInfo.COLOR_WHITE)); // 9
            opponent.getCommunicator().sendColoredMessageCombat(segments);
        }
    }
    public static void sendBounceWoundIgnoreMessages(Creature attacker, Creature opponent, String bounceWoundName){
        ArrayList<MulticolorLineSegment> segments = new ArrayList<>();
        segments.add(new CreatureLineSegment(opponent)); // 0
        segments.add(new MulticolorLineSegment(" ", ItemInfo.COLOR_WHITE)); // 1
        segments.add(new MulticolorLineSegment("ignores", NEUTRAL_COLOR)); // 2
        segments.add(new MulticolorLineSegment(" the effects of ", ItemInfo.COLOR_WHITE)); // 3
        segments.add(new MulticolorLineSegment(bounceWoundName, EFFECT_COLOR)); // 4
        segments.add(new MulticolorLineSegment(".", ItemInfo.COLOR_WHITE)); // 5
        if(attacker.spamMode()){
            attacker.getCommunicator().sendColoredMessageCombat(segments);
        }
        if(opponent.spamMode()) {
            segments.set(0, new MulticolorLineSegment("You", YOU_COLOR));
            segments.set(2, new MulticolorLineSegment("ignore", NEUTRAL_COLOR));
            opponent.getCommunicator().sendColoredMessageCombat(segments);
        }
    }
}
