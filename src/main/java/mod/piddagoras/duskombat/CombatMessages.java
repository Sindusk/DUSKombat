package mod.piddagoras.duskombat;

import com.wurmonline.server.MessageServer;
import com.wurmonline.server.Server;
import com.wurmonline.server.bodys.Wound;
import com.wurmonline.server.creatures.Creature;
import com.wurmonline.server.items.Item;
import com.wurmonline.server.sounds.SoundPlayer;
import com.wurmonline.server.utils.CreatureLineSegment;
import com.wurmonline.shared.util.MulticolorLineSegment;
import mod.sin.lib.WoundAssist;

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

    public static String getAttackString(Creature attacker, Item weapon, byte woundType) {
        if(!weapon.isBodyPart()){
            if(woundType == Wound.TYPE_BURN){
                return "ignite";
            }else if(woundType == Wound.TYPE_COLD){
                return "freeze";
            }else if(woundType == Wound.TYPE_ACID){
                return "corrode";
            }else if(woundType == Wound.TYPE_POISON){
                return "poison";
            }
        }
        if (weapon.isWeaponSword()) {
            return woundType == Wound.TYPE_PIERCE ? "pierce" : "cut";
        } else if (weapon.isWeaponPierce()) {
            return "pierce";
        } else if (weapon.isWeaponSlash()) {
            return "cut";
        } else if (weapon.isWeaponCrush()) {
            return "maul";
        } else if (weapon.isBodyPart() && weapon.getAuxData() != 100){
            return attacker.getAttackStringForBodyPart(weapon);
        }
        return "hit";
    }

    public static String getStrengthString(double damage) {
        if(damage <= 100){
            return "unnoticeably";
        }else if(damage <= 300){
            return "very lightly";
        }else if(damage <= 700){
            return "lightly";
        }else if(damage <= 1500){
            return "hard";
        }else if(damage <= 2500){
            return "very hard";
        }else if(damage <= 4500){
            return "extremely hard";
        }else if(damage <= 7000){
            return "deadly hard";
        }else if(damage <= 12000){
            return "brutally hard";
        }else if(damage <= 18000){
            return "fiercely";
        }else if(damage <= 25000){
            return "viciously";
        }else if(damage <= 40000){
            return "savagely";
        }else if(damage <= 55000){
            return "ferociously";
        }else if(damage <= 70000){
            return "with ungodly force";
        }
        return "with titanic might";
    }

    public static String getDamageString(double damage) {
        if (damage < 100.0) {
            return "tickle";
        }else if (damage < 500.0) {
            return "slap";
        }else if (damage < 1000.0){
            return "irritate";
        }else if (damage < 2000.0){
            return "hurt";
        }else if (damage < 3000.0){
            return "harm";
        }else if (damage < 4500.0){
            return "damage";
        }else if(damage < 7500.0){
            return "injure";
        }else if(damage < 12000.0){
            return "cripple";
        }else if(damage < 20000.0){
            return "maim";
        }else if(damage < 35000.0){
            return "mutilate";
        }else if(damage < 55000.0){
            return "ravage";
        }
        return "ruin";
    }

    public static void playMissEffects(Creature attacker, Creature opponent, Item weapon, double attackCheck){
        //logger.info(String.format("%s's attack has missed %s due to hit chance failure. (Rolled %.2f)", attacker.getName(), opponent.getName(), attackCheck));
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
        //logger.info(String.format("%s's attack was dodged by %s. (Rolled %.2f)", attacker.getName(), opponent.getName(), dodgeCheck));
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
        //logger.info(String.format("%s parries the attack from %s. (Rolled %.2f)", opponent.getName(), attacker.getName(), parryCheck));
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
        //logger.info(String.format("%s blocks the attack from %s. (Rolled %.2f)", opponent.getName(), attacker.getName(), shieldCheck));
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

    public static void sendDamageMessages(Creature attacker, Creature defender, Item weapon, String attString, double damage, float armourMod, byte type, String woundLoc, boolean critical, boolean glance){
        ArrayList<MulticolorLineSegment> segments = new ArrayList<>();

        // Critical & Glance message modifier setup
        String modifier = "";
        if(critical && glance){
            modifier = "critically and glancingly ";
        }else if(critical){
            modifier = "critically ";
        }else if(glance){
            modifier = "glancingly ";
        }

        // Defender iteration of message
        segments.add(new CreatureLineSegment(attacker)); // 0
        segments.add(new MulticolorLineSegment(" ", ItemInfo.COLOR_WHITE)); // 1
        segments.add(new MulticolorLineSegment(modifier+attString+"s", NEGATIVE_COLOR)); // 2
        /*if(critical) {
            segments.add(new MulticolorLineSegment("critically "+attString + "s", NEGATIVE_COLOR)); // 2
        }else{
            segments.add(new MulticolorLineSegment(attString+"s", NEGATIVE_COLOR)); // 2
        }*/
        segments.add(new MulticolorLineSegment(" ", ItemInfo.COLOR_WHITE)); // 3
        if (attacker == defender) {
            segments.add(new MulticolorLineSegment(attacker.getHimHerItString() + "self", new CreatureLineSegment(attacker).getColor(defender))); // 4
        }else{
            segments.add(new MulticolorLineSegment("you", YOU_COLOR)); // 4
        }
        segments.add(new MulticolorLineSegment(" ", ItemInfo.COLOR_WHITE)); // 5
        segments.add(new MulticolorLineSegment(getStrengthString(damage), NEGATIVE_COLOR)); // 6
        segments.add(new MulticolorLineSegment(" in the ", NEGATIVE_COLOR)); // 7
        segments.add(new MulticolorLineSegment(woundLoc, ITEM_COLOR)); // 8
        if(weapon != null) {
            segments.add(new MulticolorLineSegment(" with their ", NEGATIVE_COLOR)); // 9
            segments.add(new MulticolorLineSegment(weapon.getName(), ITEM_COLOR)); // 10
        }
        segments.add(new MulticolorLineSegment(" and ", NEGATIVE_COLOR)); // 11 or 9
        segments.add(new MulticolorLineSegment(getDamageString(damage*armourMod)+"s", NEGATIVE_COLOR)); // 12 or 10
        if(!critical) {
            segments.add(new MulticolorLineSegment(" it.", NEGATIVE_COLOR)); // 13 or 11
        }else{
            segments.add(new MulticolorLineSegment(" it!", NEGATIVE_COLOR)); // 13 or 11
        }
        defender.getCommunicator().sendColoredMessageCombat(segments);

        // Broadcast message
        segments.get(2).setColor(ItemInfo.COLOR_WHITE); // 2
        segments.set(4, new CreatureLineSegment(defender)); // 4
        segments.get(6).setColor(ItemInfo.COLOR_WHITE); // 6
        segments.get(7).setColor(ItemInfo.COLOR_WHITE); // 7
        if(weapon != null) {
            segments.get(9).setColor(ItemInfo.COLOR_WHITE); // 9
            segments.get(11).setColor(ItemInfo.COLOR_WHITE); // 11
            segments.get(12).setColor(ItemInfo.COLOR_WHITE); // 12
            segments.get(13).setColor(ItemInfo.COLOR_WHITE); // 13
        }else{
            segments.get(9).setColor(ItemInfo.COLOR_WHITE); // 9
            segments.get(11).setColor(ItemInfo.COLOR_WHITE); // 11
        }
        MessageServer.broadcastColoredAction(segments, attacker, defender, 10, true);

        // Performer iteration of message
        segments.set(0, new MulticolorLineSegment("You", YOU_COLOR)); // 0
        segments.set(2, new MulticolorLineSegment(modifier+attString, POSITIVE_COLOR)); // 2
        /*if(critical) {
            segments.set(2, new MulticolorLineSegment("critically "+attString, POSITIVE_COLOR)); // 2
        }else{
            segments.set(2, new MulticolorLineSegment(attString, POSITIVE_COLOR)); // 2
        }*/
        segments.get(6).setColor(POSITIVE_COLOR); // 6
        segments.get(7).setColor(POSITIVE_COLOR); // 7
        if(weapon != null) {
            segments.set(9, new MulticolorLineSegment(" with your ", POSITIVE_COLOR)); // 9
            segments.get(11).setColor(POSITIVE_COLOR); // 11
            segments.set(12, new MulticolorLineSegment(getDamageString(damage * armourMod), POSITIVE_COLOR)); // 12
            segments.get(13).setColor(POSITIVE_COLOR); // 13
        }else{
            segments.get(9).setColor(POSITIVE_COLOR); // 9
            segments.set(10, new MulticolorLineSegment(getDamageString(damage * armourMod), POSITIVE_COLOR)); // 12
            segments.get(11).setColor(POSITIVE_COLOR); // 11
        }
        attacker.getCommunicator().sendColoredMessageCombat(segments);

        /*for (MulticolorLineSegment s : segments) {
            broadCastString += s.getText();
        }
        if (performer != defender) {
            for (MulticolorLineSegment s : segments) {
                s.setColor((byte)7);
            }
            defender.getCommunicator().sendColoredMessageCombat(segments);
        }
        segments.get(1).setText(" " + attString + " ");
        segments.get(4).setText(" it.");
        for (MulticolorLineSegment s : segments) {
            s.setColor((byte)3);
        }
        performer.getCommunicator().sendColoredMessageCombat(segments);*/
    }
    public static void sendWebArmourMessages(Creature attacker, Creature opponent, Item armour){
        ArrayList<MulticolorLineSegment> segments = new ArrayList<>();
        segments.add(new MulticolorLineSegment("You", YOU_COLOR)); // 0
        segments.add(new MulticolorLineSegment(" are ", ItemInfo.COLOR_WHITE)); // 1
        segments.add(new MulticolorLineSegment("drained", NEGATIVE_COLOR)); // 2
        segments.add(new MulticolorLineSegment(" by the effects of ", ItemInfo.COLOR_WHITE)); // 3
        segments.add(new MulticolorLineSegment("Web Armour", EFFECT_COLOR)); // 4
        segments.add(new MulticolorLineSegment(" on ", ItemInfo.COLOR_WHITE)); // 5
        segments.add(new CreatureLineSegment(opponent)); // 6
        segments.add(new MulticolorLineSegment("'s ", ItemInfo.COLOR_WHITE)); // 7
        segments.add(new MulticolorLineSegment(armour.getName(), ITEM_COLOR)); // 8
        segments.add(new MulticolorLineSegment("!", ItemInfo.COLOR_WHITE)); // 9
        if(attacker.spamMode()){
            attacker.getCommunicator().sendColoredMessageCombat(segments);
        }
        if(opponent.spamMode()) {
            segments.set(0, new CreatureLineSegment(attacker)); // 0
            segments.set(1, new MulticolorLineSegment(" is ", ItemInfo.COLOR_WHITE));
            segments.get(2).setColor(POSITIVE_COLOR);
            segments.set(5, new MulticolorLineSegment(" on your ", ItemInfo.COLOR_WHITE));
            segments.set(6, new MulticolorLineSegment(armour.getName(), ITEM_COLOR));
            segments.set(7, new MulticolorLineSegment("!", ItemInfo.COLOR_WHITE));
            segments.remove(9);
            segments.remove(8);
            opponent.getCommunicator().sendColoredMessageCombat(segments);
        }
    }
    public static void sendElementalWoundIgnoreDefenseMessage(Creature opponent, byte type){
        if(opponent.spamMode()){
            String woundName = WoundAssist.getWoundName(type);
            ArrayList<MulticolorLineSegment> segments = new ArrayList<>();
            segments.add(new MulticolorLineSegment("You", YOU_COLOR)); // 0
            segments.add(new MulticolorLineSegment(" ", ItemInfo.COLOR_WHITE)); // 1
            segments.add(new MulticolorLineSegment("ignore", NEUTRAL_COLOR)); // 2
            segments.add(new MulticolorLineSegment(" the ", ItemInfo.COLOR_WHITE)); // 3
            segments.add(new MulticolorLineSegment(woundName, EFFECT_COLOR)); // 4
            segments.add(new MulticolorLineSegment(" damage.", ItemInfo.COLOR_WHITE)); // 5
            opponent.getCommunicator().sendColoredMessageCombat(segments);
        }
    }
    public static void sendElementalWoundIgnoreAttackMessage(Creature attacker, Creature opponent, byte type){
        if(attacker.spamMode()){
            String woundName = WoundAssist.getWoundName(type);
            ArrayList<MulticolorLineSegment> segments = new ArrayList<>();
            segments.add(new CreatureLineSegment(opponent)); // 0
            segments.add(new MulticolorLineSegment(" ", ItemInfo.COLOR_WHITE)); // 1
            segments.add(new MulticolorLineSegment("ignores", NEUTRAL_COLOR)); // 2
            segments.add(new MulticolorLineSegment(" the ", ItemInfo.COLOR_WHITE)); // 3
            segments.add(new MulticolorLineSegment(woundName, EFFECT_COLOR)); // 4
            segments.add(new MulticolorLineSegment(" damage.", ItemInfo.COLOR_WHITE)); // 5
            attacker.getCommunicator().sendColoredMessageCombat(segments);
        }
    }
    public static void sendBounceWoundIgnoreMessages(Creature attacker, Creature opponent, String bounceWoundName){
        ArrayList<MulticolorLineSegment> segments = new ArrayList<>();
        segments.add(new MulticolorLineSegment("You", YOU_COLOR)); // 0
        segments.add(new MulticolorLineSegment(" ", ItemInfo.COLOR_WHITE)); // 1
        segments.add(new MulticolorLineSegment("ignore", NEUTRAL_COLOR)); // 2
        segments.add(new MulticolorLineSegment(" the effects of ", ItemInfo.COLOR_WHITE)); // 3
        segments.add(new MulticolorLineSegment(bounceWoundName, EFFECT_COLOR)); // 4
        segments.add(new MulticolorLineSegment(".", ItemInfo.COLOR_WHITE)); // 5
        if(attacker.spamMode()){
            attacker.getCommunicator().sendColoredMessageCombat(segments);
        }
        if(opponent.spamMode()) {
            segments.set(0, new CreatureLineSegment(opponent)); // 0
            segments.set(2, new MulticolorLineSegment("ignores", NEUTRAL_COLOR)); // 2
            opponent.getCommunicator().sendColoredMessageCombat(segments);
        }
    }

    public static String getAttackAnimationString(Creature attacker, Item weapon, String attString){
        if(weapon.isBodyPartAttached()){
            if(attString.equals("hit")){
                return "fight_strike";
            }
            if(attString.equals("headbutt")){
                return "fight_headbutt";
            }
            if(attString.equals("bite")){
                return "fight_bite";
            }
            if(attString.equals("kick")){
                return "fight_kick";
            }
            if(attString.equals("maul")){
                return "fight_maul";
            }
            if(attString.equals("claw")){
                return "fight_claw";
            }
            if(attString.equals("tailwhip")){
                return "fight_tailwhip";
            }
            if(attString.equals("wingbuff")){
                return "fight_wingbuff";
            }
            if(attString.equals("firebreath")){
                return "fight_firebreath";
            }
            if(attString.equals("sweep")){
                return "fight_sweep";
            }
        }else if(weapon.isWeapon()){
            if(attString.equals("pierce")){
                return "fight_pierce";
            }else if(attString.equals("slash")){
                return "fight_slash";
            }else if(attString.equals("maul")){
                return "fight_maul";
            }
        }
        return "fight_strike";
    }
}
