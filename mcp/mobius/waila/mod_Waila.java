package mcp.mobius.waila;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.HashMap;
import java.util.Set;
import java.util.logging.Level;
import java.util.logging.Logger;

import com.google.common.eventbus.EventBus;
import com.google.common.eventbus.Subscribe;

import mcp.mobius.waila.api.IWailaRegistrar;
import mcp.mobius.waila.api.impl.ConfigHandler;
import mcp.mobius.waila.api.impl.MetaDataProvider;
import mcp.mobius.waila.api.impl.ModuleRegistrar;
import mcp.mobius.waila.client.WailaClientEventHandler;
import mcp.mobius.waila.network.WailaConnectionHandler;
import mcp.mobius.waila.network.WailaPacketHandler;
import mcp.mobius.waila.overlay.OverlayConfig;
import mcp.mobius.waila.server.ProxyServer;
import mcp.mobius.waila.tools.ModIdentification;
import mcp.mobius.waila.tools.Reflect;
import net.minecraft.client.resources.I18n;
import net.minecraft.enchantment.Enchantment;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;
import net.minecraftforge.common.Configuration;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.oredict.OreDictionary;
import codechicken.core.CommonUtils;
import codechicken.lib.lang.LangUtil;
import cpw.mods.fml.client.registry.KeyBindingRegistry;
import cpw.mods.fml.client.registry.KeyBindingRegistry.KeyHandler;
import cpw.mods.fml.common.FMLCommonHandler;
import cpw.mods.fml.common.FMLModContainer;
import cpw.mods.fml.common.Loader;
import cpw.mods.fml.common.Mod;
import cpw.mods.fml.common.Mod.EventHandler;
import cpw.mods.fml.common.Mod.IMCCallback;
import cpw.mods.fml.common.Mod.Init;
import cpw.mods.fml.common.Mod.Instance;
import cpw.mods.fml.common.Mod.PostInit;
import cpw.mods.fml.common.Mod.PreInit;
import cpw.mods.fml.common.ModContainer;
import cpw.mods.fml.common.SidedProxy;
import cpw.mods.fml.common.event.FMLInitializationEvent;
import cpw.mods.fml.common.event.FMLInterModComms;
import cpw.mods.fml.common.event.FMLInterModComms.IMCMessage;
import cpw.mods.fml.common.event.FMLLoadCompleteEvent;
import cpw.mods.fml.common.event.FMLPostInitializationEvent;
import cpw.mods.fml.common.event.FMLPreInitializationEvent;
import cpw.mods.fml.common.network.NetworkMod;
import cpw.mods.fml.common.registry.GameData;
import cpw.mods.fml.common.registry.ItemData;
import cpw.mods.fml.common.registry.LanguageRegistry;

@Mod(modid="Waila", name="Waila", version="1.4.5a", dependencies="required-after:NotEnoughItems")
@NetworkMod(channels = {"Waila"},clientSideRequired=false, serverSideRequired=false, connectionHandler = WailaConnectionHandler.class, 
			packetHandler = WailaPacketHandler.class, versionBounds="[1.3.0,)")

public class mod_Waila {
    // The instance of your mod that Forge uses.
	@Instance("Waila")
	public static mod_Waila instance;

	@SidedProxy(clientSide="mcp.mobius.waila.client.ProxyClient", serverSide="mcp.mobius.waila.server.ProxyServer")
	public static ProxyServer proxy;	
	public static Logger log = Logger.getLogger("Waila");
	public boolean serverPresent = false;
	
    /* INIT SEQUENCE */
    @EventHandler
    public void preInit(FMLPreInitializationEvent event) {
    	ConfigHandler.instance().loadDefaultConfig(event);
		OverlayConfig.updateColors();
		MinecraftForge.EVENT_BUS.register(new WailaClientEventHandler());		
	}	
	
    @EventHandler
    public void initialize(FMLInitializationEvent event) {
        try {
            Field eBus = FMLModContainer.class.getDeclaredField("eventBus");
            eBus.setAccessible(true);
            EventBus FMLbus = (EventBus) eBus.get(FMLCommonHandler.instance().findContainerFor(this));
            FMLbus.register(this);
        } catch (Throwable t) {}
	}

    @EventHandler
    public void postInit(FMLPostInitializationEvent event) {
		proxy.registerHandlers();
    	ModIdentification.init();
        
        if (ConfigHandler.instance().getConfig(Configuration.CATEGORY_GENERAL, Constants.CFG_WAILA_KEYBIND, true)){
        
	        for (String key: ModIdentification.keyhandlerStrings.keySet()){
	        	String orig  = I18n.getString(key);
	        	if (orig.equals(key))
	        		orig = LanguageRegistry.instance().getStringLocalization(key);
	        	if (orig.equals(key))
	        		orig = LangUtil.translateG(key);	        	
	        	if (orig.isEmpty())
	        		orig = key;
	        	
	        	String modif;
	        	if (orig.startsWith("[") || orig.contains(":"))
	        		modif = orig;
	        	else{
	        		String id = ModIdentification.keyhandlerStrings.get(key);
	        		
	        		if (id.contains("."))
	        			id = id.split("\\.")[0];
	        		
	        		if (id.length() > 10)
	        			id = id.substring(0, 11);
	        		
	        		if (id.isEmpty())
	        			id = "????";
	        		
	        		modif = String.format("[%s] %s", id, orig);
	        	}
	
	        	LanguageRegistry.instance().addStringLocalization(key, modif);
	        }
        }
        
	}

    @Subscribe
    public void loadComplete(FMLLoadCompleteEvent event) {    
    	proxy.registerMods();
    	
    	String[] ores = OreDictionary.getOreNames();
    	for (String s : ores)
    		for (ItemStack stack : OreDictionary.getOres(s))
    			System.out.printf("%s : %s\n", s, stack);
    	
    }    
    	
    @EventHandler
	public void processIMC(FMLInterModComms.IMCEvent event)
	{
		for (IMCMessage imcMessage : event.getMessages()){
			if (!imcMessage.isStringMessage()) continue;
			
			if (imcMessage.key.equalsIgnoreCase("addconfig")){
				String[] params = imcMessage.getStringValue().split("\\$\\$");
				if (params.length != 3){
					mod_Waila.log.warning(String.format("Error while parsing config option from [ %s ] for %s", imcMessage.getSender(), imcMessage.getStringValue()));					
					continue;
				}
				mod_Waila.log.info(String.format("Receiving config request from [ %s ] for %s", imcMessage.getSender(), imcMessage.getStringValue()));				
				ConfigHandler.instance().addConfig(params[0], params[1], params[2]);
			}
			
			if (imcMessage.key.equalsIgnoreCase("register")){
				mod_Waila.log.info(String.format("Receiving registration request from [ %s ] for method %s", imcMessage.getSender(), imcMessage.getStringValue()));
				this.callbackRegistration(imcMessage.getStringValue(), imcMessage.getSender());
			}
		}
	}		
	
	public void callbackRegistration(String method, String modname){
		String[] splitName = method.split("\\.");
		String methodName = splitName[splitName.length-1];
		String className  = method.substring(0, method.length()-methodName.length()-1);
		
		mod_Waila.log.info(String.format("Trying to reflect %s %s", className, methodName));
		
		try{
			Class  reflectClass  = Class.forName(className);
			Method reflectMethod = reflectClass.getDeclaredMethod(methodName, IWailaRegistrar.class);
			reflectMethod.invoke(null, (IWailaRegistrar)ModuleRegistrar.instance());
			
			mod_Waila.log.info(String.format("Success in registering %s", modname));
			
		} catch (ClassNotFoundException e){
			mod_Waila.log.warning(String.format("Could not find class %s", className));
		} catch (NoSuchMethodException e){
			mod_Waila.log.warning(String.format("Could not find method %s", methodName));
		} catch (Exception e){
			mod_Waila.log.warning(String.format("Exception while trying to access the method : %s", e.toString()));
		}
		
	}
}