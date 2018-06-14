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

    public static void playMissEffects(Creature attacker, Creature opponent, Item weapon, double attackCheck){
        logger.info(String.format("%s's attack has missed %s due to hit chance failure. (Rolled %.2f)", attacker.getName(), opponent.getName(), attackCheck));
        String sstring = attackCheck < -50.0 ? "sound.combat.miss.med" : "sound.combat.miss.light";
        SoundPlayer.playSound(sstring, attacker, 1.6F);
        ArrayList<MulticolorLineSegment> segments = new ArrayList<>();
        segments.add(new MulticolorLineSegment("You", ItemInfo.COLOR_FORESTGREEN));
        segments.add(new MulticolorLineSegment(" ", ItemInfo.COLOR_WHITE));
        segments.add(new MulticolorLineSegment("miss", ItemInfo.COLOR_YELLOW));
        segments.add(new MulticolorLineSegment(" with the ", ItemInfo.COLOR_WHITE));
        segments.add(new MulticolorLineSegment(weapon.getName(), ItemInfo.COLOR_LIGHTGRAY));
        segments.add(new MulticolorLineSegment(".", ItemInfo.COLOR_WHITE));
        if (attacker.spamMode()) {
            attacker.getCommunicator().sendColoredMessageCombat(segments);
        }
        if(opponent.spamMode()) {
            segments.set(0, new CreatureLineSegment(attacker));
            segments.set(2, new MulticolorLineSegment("misses", ItemInfo.COLOR_YELLOW));
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
        segments.add(new MulticolorLineSegment("dodges", ItemInfo.COLOR_YELLOW));
        segments.add(new MulticolorLineSegment(" your ", ItemInfo.COLOR_WHITE));
        segments.add(new MulticolorLineSegment(weapon.getName(), ItemInfo.COLOR_LIGHTGRAY));
        segments.add(new MulticolorLineSegment(".", ItemInfo.COLOR_WHITE));
        if(attacker.spamMode()){
            attacker.getCommunicator().sendColoredMessageCombat(segments);
        }
        if(opponent.spamMode()){
            segments.set(0, new MulticolorLineSegment("You", ItemInfo.COLOR_FORESTGREEN));
            segments.set(2, new MulticolorLineSegment("dodge", ItemInfo.COLOR_YELLOW));
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
        segments.add(new CreatureLineSegment(opponent));
        segments.add(new MulticolorLineSegment(" ", ItemInfo.COLOR_WHITE));
        segments.add(new MulticolorLineSegment("parries", ItemInfo.COLOR_YELLOW));
        segments.add(new MulticolorLineSegment(" your ", ItemInfo.COLOR_WHITE));
        segments.add(new MulticolorLineSegment(weapon.getName(), ItemInfo.COLOR_LIGHTGRAY));
        segments.add(new MulticolorLineSegment(" with their ", ItemInfo.COLOR_WHITE));
        segments.add(new MulticolorLineSegment(opponent.getPrimWeapon().getName(), ItemInfo.COLOR_LIGHTGRAY));
        if(attacker.spamMode()){
            attacker.getCommunicator().sendColoredMessageCombat(segments);
        }
        if(opponent.spamMode()){
            segments.set(0, new MulticolorLineSegment("You", ItemInfo.COLOR_FORESTGREEN));
            segments.set(2, new MulticolorLineSegment("parry", ItemInfo.COLOR_YELLOW));
            segments.set(3, new MulticolorLineSegment(" the ", ItemInfo.COLOR_WHITE));
            segments.set(5, new MulticolorLineSegment(" from ", ItemInfo.COLOR_WHITE));
            segments.set(6, new CreatureLineSegment(attacker));
            segments.add(new MulticolorLineSegment(" with your ", ItemInfo.COLOR_WHITE));
            segments.add(new MulticolorLineSegment(opponent.getPrimWeapon().getName(), ItemInfo.COLOR_LIGHTGRAY));
            segments.add(new MulticolorLineSegment(".", ItemInfo.COLOR_WHITE));
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
        segments.add(new CreatureLineSegment(opponent));
        segments.add(new MulticolorLineSegment(" ", ItemInfo.COLOR_WHITE));
        segments.add(new MulticolorLineSegment("blocks", ItemInfo.COLOR_YELLOW));
        segments.add(new MulticolorLineSegment(" your ", ItemInfo.COLOR_WHITE));
        segments.add(new MulticolorLineSegment(weapon.getName(), ItemInfo.COLOR_LIGHTGRAY));
        segments.add(new MulticolorLineSegment(" with their ", ItemInfo.COLOR_WHITE));
        segments.add(new MulticolorLineSegment(defShield.getName(), ItemInfo.COLOR_LIGHTGRAY));
        if(attacker.spamMode()){
            attacker.getCommunicator().sendColoredMessageCombat(segments);
        }
        if(opponent.spamMode()){
            segments.set(0, new MulticolorLineSegment("You", ItemInfo.COLOR_FORESTGREEN));
            segments.set(2, new MulticolorLineSegment("block", ItemInfo.COLOR_YELLOW));
            segments.set(3, new MulticolorLineSegment(" the ", ItemInfo.COLOR_WHITE));
            segments.set(5, new MulticolorLineSegment(" from ", ItemInfo.COLOR_WHITE));
            segments.set(6, new CreatureLineSegment(attacker));
            segments.add(new MulticolorLineSegment(" with your ", ItemInfo.COLOR_WHITE));
            segments.add(new MulticolorLineSegment(defShield.getName(), ItemInfo.COLOR_LIGHTGRAY));
            segments.add(new MulticolorLineSegment(".", ItemInfo.COLOR_WHITE));
            opponent.getCommunicator().sendColoredMessageCombat(segments);
        }
    }
}
