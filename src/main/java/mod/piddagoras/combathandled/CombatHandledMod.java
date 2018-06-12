package mod.piddagoras.combathandled;

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

public class CombatHandledMod
implements WurmServerMod, Configurable, PreInitable, ItemTemplatesCreatedListener, ServerStartedListener {
	public static Logger logger = Logger.getLogger(CombatHandledMod.class.getName());

	public static float minimumSwingTimer = 3.0f;
	public static boolean useEpicBloodthirst = true;
	public static boolean showItemCombatInformation = true;

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
		minimumSwingTimer = Prop.getFloatProperty("minimumSwingTimer", minimumSwingTimer);
		useEpicBloodthirst = Prop.getBooleanProperty("useEpicBloodthirst", useEpicBloodthirst);
		showItemCombatInformation = Prop.getBooleanProperty("showItemCombatInformation", showItemCombatInformation);

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
        //logger.log(Level.INFO, "enableNonPlayerCrits: " + enableNonPlayerCrits);
        logger.info(" -- Configuration complete -- ");
    }

	@Override
	public void preInit(){
		logger.info("Beginning preInit...");
        try{
            ClassPool classPool = HookManager.getInstance().getClassPool();
            final Class<CombatHandledMod> thisClass = CombatHandledMod.class;
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
                    "  return "+CombatHandled.class.getName()+".attackHandled($0.creature, $1, $2, $3, $4, $5);" +
                    "}";
            Util.setBodyDescribed(thisClass, ctCombatHandler, "attack", desc4, replace);

            Util.setReason("Insert examine method.");
            CtClass ctItem = classPool.get("com.wurmonline.server.items.Item");
            replace = ItemInfo.class.getName() + ".handleExamine($1, $0);";
            Util.insertAfterDeclared(thisClass, ctItem, "sendEnchantmentStrings", replace);

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
}
