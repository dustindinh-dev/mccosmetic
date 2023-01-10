package io.lumine.cosmetics;

import com.ticxo.playeranimator.PlayerAnimatorImpl;
import io.lumine.cosmetics.commands.BaseCommand;
import io.lumine.cosmetics.commands.EmotesCommand;
import io.lumine.cosmetics.commands.SprayCommand;
import io.lumine.cosmetics.commands.SprayRemoveCommand;
import io.lumine.cosmetics.commands.SpraysCommand;
import io.lumine.cosmetics.commands.admin.AdminCommand;
import io.lumine.cosmetics.compat.CompatibilityManager;
import io.lumine.cosmetics.compat.WorldGuardSupport;
import io.lumine.cosmetics.config.Configuration;
import io.lumine.cosmetics.listeners.PlayerListeners;
import io.lumine.cosmetics.logging.MCLogger;
import io.lumine.cosmetics.managers.CosmeticsExecutor;
import io.lumine.cosmetics.managers.back.BackManager;
import io.lumine.cosmetics.managers.gestures.GestureManager;
import io.lumine.cosmetics.managers.hats.HatManager;
import io.lumine.cosmetics.managers.modelengine.MEGManager;
import io.lumine.cosmetics.managers.offhand.OffhandManager;
import io.lumine.cosmetics.managers.sprays.SprayManager;
import io.lumine.cosmetics.menus.MenuManager;
import io.lumine.cosmetics.metrics.bStats;
import io.lumine.cosmetics.nms.VolatileCodeDisabled;
import io.lumine.cosmetics.nms.VolatileCodeHandler;
import io.lumine.cosmetics.players.ProfileManager;
import io.lumine.cosmetics.players.wardrobe.WardrobeExecutor;
import io.lumine.utils.chat.ColorString;
import io.lumine.utils.logging.ConsoleColor;
import io.lumine.utils.logging.Log;
import io.lumine.utils.plugin.LuminePlugin;
import io.lumine.utils.version.ServerVersion;
import lombok.Getter;
import org.bukkit.Bukkit;

public class MCCosmeticsPlugin extends LuminePlugin {

    private static MCCosmeticsPlugin plugin;

    @Getter private Configuration configuration;
    @Getter private CompatibilityManager compatibility;
    @Getter private WorldGuardSupport worldGuardSupport;
    
    @Getter private ProfileManager profiles;
    @Getter private MenuManager menuManager;
    
    @Getter private BaseCommand baseCommand;
    @Getter private AdminCommand adminCommand;
    
    @Getter private CosmeticsExecutor cosmetics;
    
    @Getter private HatManager hatManager;
    @Getter private BackManager backManager;
    @Getter private SprayManager sprayManager;
    @Getter private OffhandManager offhandManager;
    @Getter private MEGManager megManager;
    @Getter private GestureManager gestureManager;
    @Getter private WardrobeExecutor wardrobeManager;

    private VolatileCodeHandler volatileCodeHandler;

    /*
     * Other Shit
     */ 
    @Getter private Boolean isUpdateAvailable = false;
    
    /** 
     * Bukkit Loader
     * @author Ashijin
     * @exclude
     */
    @Override
    public void load() {

        plugin = this;

        if(Bukkit.getPluginManager().getPlugin("WorldGuard") != null) {
            worldGuardSupport = new WorldGuardSupport(this);
        }
    }  
    
    /**
     * Bukkit Initializer
     * @author Ashijin
     * @exclude
     */
    @Override 
    public void enable() {
        MCLogger.log("Loading {0} for {1} {2}...", getDescription().getName(), ServerVersion.isPaper() ? "Paper" : "Spigot", ServerVersion.get().toString());

        if(ServerVersion.isPaper()) {
            MCLogger.log("The server is running PaperSpigot; enabled PaperSpigot exclusive functionality");
        } else {
            MCLogger.log("The server is running Spigot; disabled PaperSpigot exclusive functionality");
        }

        this.saveDefaultConfig();
        
        configuration = new Configuration(this);
        
        getConfiguration().load(this);
        MCLogger.log("MCCosmetics configuration file loaded successfully.");

        PlayerAnimatorImpl.initialize(this);

        /*
         * Plugin Components
         */

        volatileCodeHandler = getVolatileCodeHandler();
        compatibility = new CompatibilityManager(this);
        
        cosmetics = new CosmeticsExecutor(this);
        
        hatManager = new HatManager(this);
        backManager = new BackManager(this);
        sprayManager = new SprayManager(this);
        offhandManager = new OffhandManager(this);
        if(Bukkit.getPluginManager().getPlugin("ModelEngine") != null) {
            try {
                megManager = new MEGManager(this);
            } catch(Exception | Error ex) {
                megManager = null;
                Log.info("Incompatible ModelEngine found; disabling ModelEngine and MEG Accesstory Cosmetic features.");
            }
        } else {
            Log.info("ModelEngine not found; disabling ModelEngine and MEG Accesstory Cosmetic features.");
        }
        gestureManager = new GestureManager(this);
       
        profiles = new ProfileManager(this);
        
        menuManager = new MenuManager(this);
        wardrobeManager = new WardrobeExecutor(this);
        
        /*
         * Events
         */
        Bukkit.getPluginManager().registerEvents(new PlayerListeners(), this);

        /*
         * Commands 
         */
        this.baseCommand = new BaseCommand(this);
        this.adminCommand = new AdminCommand(this);

        this.registerCommand("cosmetics", baseCommand);
        this.registerCommand("mccosmetics", adminCommand);
        this.registerCommand("sprays", new SpraysCommand(this));
        this.registerCommand("spray", new SprayCommand(this));
        this.registerCommand("sprayremove", new SprayRemoveCommand(this));
        this.registerCommand("emote", new EmotesCommand(this));

        if(configuration.isAllowingMetrics())  {
            new bStats(this);
        }

        MCLogger.log("" + ConsoleColor.GREEN + ConsoleColor.CHECK_MARK + " MCCosmetics" + (isPremium() ? " Premium" : "") + " v" + getVersion() +  " (build "+ getBuildNumber() +") has been successfully loaded!" + ConsoleColor.RESET);
    }

    @Override
    /**
     * Bukkit Shutdown
     * @author Ashijin
     * @exclude
     */
    public void disable() {
        MCLogger.log("Disabling MCCosmetics...");

        profiles.unload();
        configuration.unload();
        compatibility.terminate(); 
        
        MCLogger.log("All active settings have been saved.");
    }
    
    /**
     * @return MCCosmetics Returns the active MCCosmetics instance.
     */
    public static MCCosmeticsPlugin inst()    {
        return plugin; 
    } 
    
    /** 
     * @exclude
     */
    private static Object p = amIPremium();

    /**
     * @exclude 
     */
    public static final boolean isPremium() {
        return p != null;
    }
    
    /**
     * @exclude 
     */
    private static Object amIPremium() {
        try {
            return Class.forName("io.lumine.cosmetics.CarsonJF");
        } catch (final ClassNotFoundException e) {
            return null;
        } catch (final Exception e) {
            throw new RuntimeException("An error occurred while enabling CarsonJF", e);
        }
    }

    /**
     * Grabs the active VolatileCodeHandler for the current version of Minecraft
     * @return {@link VolatileCodeHandler}
     */
    public VolatileCodeHandler getVolatileCodeHandler()  {
        if(this.volatileCodeHandler != null) return this.volatileCodeHandler;
        
        VolatileCodeHandler VCH = new VolatileCodeDisabled();
                
        String packageName = Bukkit.getServer().getClass().getPackage().getName();
        String version = packageName.substring(packageName.lastIndexOf('.') + 1);

        if (version.startsWith("v1_19")){
            if("1.19.2-R0.1-SNAPSHOT".equals(Bukkit.getBukkitVersion())) {
                version = "v1_19_R1_2";
            } else if ("1.19.3-R0.1-SNAPSHOT".equals(Bukkit.getBukkitVersion())){
                version = "v1_19_R2";
            }
        }
        
        try {
            final Class<?> clazz = Class.forName("io.lumine.cosmetics.nms.VolatileCodeEnabled_" + version);
            if (VolatileCodeHandler.class.isAssignableFrom(clazz)) {
                VCH = (VolatileCodeHandler) clazz.getConstructor(MCCosmeticsPlugin.class).newInstance(this);
            }
        } catch (final ClassNotFoundException e) {  
            MCLogger.error(ColorString.get("&6--====|||| &c&lMCCosmetics &6||||====--"));
            MCLogger.error("This version of MCCosmetics is not fully compatible with your version of Bukkit.");
            MCLogger.error("Some features may be limited or disabled until you use a compatible version.");  
        } catch (final Exception e) {
            throw new RuntimeException("Unknown exception loading version handler. Volatile code has been disabled.", e);
        }
        this.volatileCodeHandler = VCH;
        return VCH;
    }
 
    /**
     * Returns the plugin version
     * @return int
     */
    public String getVersion()  {
        return this.getDescription().getVersion().split("-")[0];
    }

    /**
     * Returns the development build version
     * @return int
     */
    public String getBuildNumber()  {
        final String[] split = this.getDescription().getVersion().split("-");
        if(split.length == 2)   {
            return split[1];
        } else if(split.length == 3)    {
            return split[2];
        } else  {
            return "????";
        }
    }
}
