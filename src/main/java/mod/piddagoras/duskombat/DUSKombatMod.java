package mod.piddagoras.duskombat;

import com.wurmonline.server.creatures.Creature;
import com.wurmonline.server.creatures.Creatures;
import com.wurmonline.server.items.Materials;
import javassist.ClassPool;
import javassist.CtClass;
import javassist.NotFoundException;
import javassist.bytecode.Descriptor;
import mod.sin.lib.Prop;
import mod.sin.lib.Util;
import org.gotti.wurmunlimited.modloader.classhooks.HookException;
import org.gotti.wurmunlimited.modloader.classhooks.HookManager;
import org.gotti.wurmunlimited.modloader.interfaces.*;

import java.util.Properties;
import java.util.logging.Logger;

public class DUSKombatMod
implements WurmServerMod, Configurable, PreInitable, ItemTemplatesCreatedListener, ServerStartedListener, ServerPollListener {
	public static Logger logger = Logger.getLogger(DUSKombatMod.class.getName());

	public static float minimumSwingTimer = 3.0f;
	public static boolean useEpicBloodthirst = true;
	public static boolean showItemCombatInformation = true;
	public static boolean disablePlayerSkillLoss = false;

	// Damage Multipliers
    public static float playerToEnvironmentDamageMultiplier = 1.0f;
    public static float environmentToPlayerDamageMultiplier = 1.0f;
    public static float playerToPlayerDamageMultiplier = 1.0f;

    public static void pollCreatureActionStacks(){
        for(Creature c : Creatures.getInstance().getCreatures()){
            if(c.isFighting()) {
                c.getActions().poll(c);
            }
        }
    }

    public static byte parseMaterialType(String str){
	    byte mat = Materials.convertMaterialStringIntoByte(str);
	    if(mat > 0){
	        return mat;
        }
        return Byte.parseByte(str);
    }

    @Override
	public void configure(Properties properties) {
		logger.info("Beginning configuration...");
		Prop.properties = properties;

		// Base Configuration
		minimumSwingTimer = Prop.getFloatProperty("minimumSwingTimer", minimumSwingTimer);
		useEpicBloodthirst = Prop.getBooleanProperty("useEpicBloodthirst", useEpicBloodthirst);
		showItemCombatInformation = Prop.getBooleanProperty("showItemCombatInformation", showItemCombatInformation);
        disablePlayerSkillLoss = Prop.getBooleanProperty("disablePlayerSkillLoss", disablePlayerSkillLoss);

		// Damage Multipliers
        playerToEnvironmentDamageMultiplier = Prop.getFloatProperty("playerToEnvironmentDamageMultiplier", playerToEnvironmentDamageMultiplier);
        environmentToPlayerDamageMultiplier = Prop.getFloatProperty("environmentToPlayerDamageMultiplier", environmentToPlayerDamageMultiplier);
        playerToPlayerDamageMultiplier = Prop.getFloatProperty("playerToPlayerDamageMultiplier", playerToPlayerDamageMultiplier);

    	for (String name : properties.stringPropertyNames()) {
            try {
                String value = properties.getProperty(name);
                switch (name) {
                    case "debug":
                    case "classname":
                    case "classpath":
                    case "sharedClassLoader":
                    case "depend.import":
                    case "depend.suggests":
                        break; //ignore
                    default:
                    	/*if (name.startsWith("weaponDamage")) {
                        	String[] split = value.split(",");
                            int weaponId = Integer.parseInt(split[0]);
                            float newVal = Float.parseFloat(split[1]);
                            weaponDamage.put(weaponId, newVal);
                        } else {
                            logger.warning("Unknown config property: " + name);
                        }*/
                }
            } catch (Exception e) {
                logger.severe("Error processing property " + name);
                e.printStackTrace();
            }
        }
        // Print values of configuration
        logger.info(" -- Mod Configuration -- ");
    	logger.info(String.format("Minimum Swing Timer: %.2f seconds", minimumSwingTimer));
    	logger.info(String.format("Use Epic Bloodthirst: %s", useEpicBloodthirst));
    	logger.info(String.format("Show Item Combat Information: %s", showItemCombatInformation));
    	logger.info(String.format("Disable Player Skill Loss: %s", disablePlayerSkillLoss));
    	logger.info("> Damage Multipliers <");
        logger.info(String.format("Player to Environment: %.2fx", playerToEnvironmentDamageMultiplier));
        logger.info(String.format("Environment to Player: %.2fx", environmentToPlayerDamageMultiplier));
        logger.info(String.format("Player to Player: %.2fx", playerToPlayerDamageMultiplier));
        //logger.log(Level.INFO, "enableNonPlayerCrits: " + enableNonPlayerCrits);
        logger.info(" -- Configuration complete -- ");
    }

	@Override
	public void preInit(){
		logger.info("Beginning preInit...");
        try{
            ClassPool classPool = HookManager.getInstance().getClassPool();
            final Class<DUSKombatMod> thisClass = DUSKombatMod.class;
            String replace;

		    Util.setReason("Debug attack method");
            CtClass ctCombatHandler = classPool.get("com.wurmonline.server.creatures.CombatHandler");
            CtClass ctCreature = classPool.get("com.wurmonline.server.creatures.Creature");
            CtClass ctAction = classPool.get("com.wurmonline.server.behaviours.Action");
            CtClass[] params4 = {
                    ctCreature,
                    CtClass.intType,
                    CtClass.booleanType,
                    CtClass.floatType,
                    ctAction
            };
            String desc4 = Descriptor.ofMethod(CtClass.booleanType, params4);
            replace = "{" +
                    "  return "+DUSKombat.class.getName()+".attackHandled($0.creature, $1, $2, $3, $4, $5);" +
                    "}";
            Util.setBodyDescribed(thisClass, ctCombatHandler, "attack", desc4, replace);

            Util.setReason("Poll creature action stacks on every update.");
            CtClass ctZones = classPool.get("com.wurmonline.server.zones.Zones");
            replace = DUSKombatMod.class.getName()+".pollCreatureActionStacks();";
            Util.insertBeforeDeclared(thisClass, ctZones, "pollNextZones", replace);

            Util.setReason("Insert examine method.");
            CtClass ctItem = classPool.get("com.wurmonline.server.items.Item");
            replace = ItemInfo.class.getName() + ".handleExamine($1, $0);";
            Util.insertAfterDeclared(thisClass, ctItem, "sendEnchantmentStrings", replace);

            Util.setReason("Replace the addWound method.");
            CtClass ctString = classPool.get("java.lang.String");
            CtClass ctBattle = classPool.get("com.wurmonline.server.combat.Battle");
            CtClass ctCombatEngine = classPool.get("com.wurmonline.server.combat.CombatEngine");
            // @Nullable Creature performer, Creature defender, byte type, int pos, double damage, float armourMod,
            // String attString, @Nullable Battle battle, float infection, float poison, boolean archery, boolean alreadyCalculatedResist
            CtClass[] params1 = {
                    ctCreature,
                    ctCreature,
                    CtClass.byteType,
                    CtClass.intType,
                    CtClass.doubleType,
                    CtClass.floatType,
                    ctString,
                    ctBattle,
                    CtClass.floatType,
                    CtClass.floatType,
                    CtClass.booleanType,
                    CtClass.booleanType
            };
            String desc1 = Descriptor.ofMethod(CtClass.booleanType, params1);
            replace = "{" +
                    " return "+DamageEngine.class.getName()+".addWound($1, $2, $3, $4, $5, $6, $7, $8, $9, $10, $11, $12, null, false, false);" +
                    "}";
            Util.setBodyDescribed(thisClass, ctCombatEngine, "addWound", desc1, replace);

            Util.setReason("Overwrite sending special move information.");
            replace = "{" +
                    SpecialMoves.class.getName()+".sendSpecialMoves($0.creature);" +
                    "}";
            Util.setBodyDeclared(thisClass, ctCombatHandler, "sendSpecialMoves", replace);

            Util.setReason("Overwrite actual special move handling");
            CtClass ctCreatureBehaviour = classPool.get("com.wurmonline.server.behaviours.CreatureBehaviour");
            replace = "{" +
                    "return "+SpecialMoves.class.getName()+".handleSpecialMove($1, $2, $3, $4);" +
                    "}";
            Util.setBodyDeclared(thisClass, ctCreatureBehaviour, "handle_SPECMOVE", replace);

            // Trigger Testing
            /*Trigger.Register(new Trigger("Cold Damage Taken", Event.DamageTaken,
                    new Circumstances(){
                        @Override
                        public boolean check(Map<String, Object> data) {
                            byte type = (byte) data.get("type");
                            logger.info(String.format("Checking type %s for trigger.", type));
                            return type == Wound.TYPE_COLD;
                        }
                    },
                    new Consequences(){
                        @Override
                        public void call (Map<String, Object> data){
                            logger.info(String.format("Took some cold damage! %s", data.get("type")));
                        }
                    }));*/

            if(disablePlayerSkillLoss) {
                Util.setReason("Disable player skill loss.");
                replace = "if(this.isPlayer()){" +
                        "  this.getCommunicator().sendSafeServerMessage(\"Your knowledge is kept safe.\");" +
                        "  return;" +
                        "}else{" +
                        "  this.getCommunicator().sendAlertServerMessage(\"You have died without a Resurrection Stone, resulting in some of your knowledge being lost.\");" +
                        "}";
                Util.insertBeforeDeclared(thisClass, ctCreature, "punishSkills", replace);

                Util.setReason("Disable player fight skill loss.");
                replace = "if(this.isPlayer()){" +
                        "  $_ = null;" +
                        "}else{" +
                        "  $_ = $proceed($$);" +
                        "}";
                Util.instrumentDeclaredCount(thisClass, ctCreature, "modifyFightSkill", "setKnowledge", 1, replace);
            }

        } catch ( NotFoundException | IllegalArgumentException | ClassCastException e) {
            throw new HookException(e);
        }
    }
	
	@Override
	public void onItemTemplatesCreated(){
		logger.info("Beginning onItemTemplatesCreated...");
	}
	
	@Override
	public void onServerStarted(){
		logger.info("Beginning onServerStarted...");
	}

    @Override
    public void onServerPoll() {
        DUSKombat.onServerPoll();
    }
}
