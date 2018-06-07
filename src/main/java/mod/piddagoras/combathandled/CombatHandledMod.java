package mod.piddagoras.combathandled;

import com.wurmonline.server.items.Materials;
import mod.sin.lib.Prop;
import org.gotti.wurmunlimited.modloader.interfaces.*;

import java.util.Properties;
import java.util.logging.Logger;

public class CombatHandledMod
implements WurmServerMod, Configurable, PreInitable, ItemTemplatesCreatedListener, ServerStartedListener {
	public static Logger logger = Logger.getLogger(CombatHandledMod.class.getName());

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
        // Print values of main.java.armoury.mod configuration
        logger.info(" -- Mod Configuration -- ");
        //logger.log(Level.INFO, "enableNonPlayerCrits: " + enableNonPlayerCrits);
        logger.info(" -- Configuration complete -- ");
    }

	@Override
	public void preInit(){
		logger.info("Beginning preInit...");
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
