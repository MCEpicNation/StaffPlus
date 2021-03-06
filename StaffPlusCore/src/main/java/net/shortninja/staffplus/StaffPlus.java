package net.shortninja.staffplus;

import net.shortninja.staffplus.player.NodeUser;
import net.shortninja.staffplus.player.User;
import net.shortninja.staffplus.player.UserManager;
import net.shortninja.staffplus.player.attribute.SecurityHandler;
import net.shortninja.staffplus.player.attribute.TicketHandler;
import net.shortninja.staffplus.player.attribute.infraction.InfractionCoordinator;
import net.shortninja.staffplus.player.attribute.mode.ModeCoordinator;
import net.shortninja.staffplus.player.attribute.mode.handler.*;
import net.shortninja.staffplus.server.AlertCoordinator;
import net.shortninja.staffplus.server.PacketModifier;
import net.shortninja.staffplus.server.chat.ChatHandler;
import net.shortninja.staffplus.server.command.CmdHandler;
import net.shortninja.staffplus.server.compatibility.IProtocol;
import net.shortninja.staffplus.server.compatibility.v1_1x.*;
import net.shortninja.staffplus.server.compatibility.v1_7.Protocol_v1_7_R1;
import net.shortninja.staffplus.server.compatibility.v1_7.Protocol_v1_7_R2;
import net.shortninja.staffplus.server.compatibility.v1_7.Protocol_v1_7_R3;
import net.shortninja.staffplus.server.compatibility.v1_7.Protocol_v1_7_R4;
import net.shortninja.staffplus.server.compatibility.v1_8.Protocol_v1_8_R1;
import net.shortninja.staffplus.server.compatibility.v1_8.Protocol_v1_8_R2;
import net.shortninja.staffplus.server.compatibility.v1_8.Protocol_v1_8_R3;
import net.shortninja.staffplus.server.compatibility.v1_9.Protocol_v1_9_R1;
import net.shortninja.staffplus.server.compatibility.v1_9.Protocol_v1_9_R2;
import net.shortninja.staffplus.server.data.Load;
import net.shortninja.staffplus.server.data.MySQLConnection;
import net.shortninja.staffplus.server.data.Save;
import net.shortninja.staffplus.server.data.config.IOptions;
import net.shortninja.staffplus.server.data.config.Messages;
import net.shortninja.staffplus.server.data.config.Options;
import net.shortninja.staffplus.server.data.file.ChangelogFile;
import net.shortninja.staffplus.server.data.file.DataFile;
import net.shortninja.staffplus.server.data.file.LanguageFile;
import net.shortninja.staffplus.server.listener.*;
import net.shortninja.staffplus.server.listener.entity.EntityDamage;
import net.shortninja.staffplus.server.listener.entity.EntityDamageByEntity;
import net.shortninja.staffplus.server.listener.entity.EntityTarget;
import net.shortninja.staffplus.server.listener.player.*;
import net.shortninja.staffplus.util.MessageCoordinator;
import net.shortninja.staffplus.util.Metrics;
import net.shortninja.staffplus.util.PermissionHandler;
import net.shortninja.staffplus.util.lib.JavaUtils;
import org.bukkit.Bukkit;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.plugin.java.JavaPlugin;
import org.inventivetalent.apihelper.APIManager;
import org.inventivetalent.packetlistener.PacketListenerAPI;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

// TODO Add command to check e chests and offline player inventories

public class StaffPlus extends JavaPlugin implements IStaffPlus {
    private static StaffPlus plugin;

    public IProtocol versionProtocol;
    public PermissionHandler permission;
    public MessageCoordinator message;
    public Options options;
    public DataFile dataFile;
    public LanguageFile languageFile;
    public Messages messages;
    public UserManager userManager;
    public SecurityHandler securityHandler;
    public CpsHandler cpsHandler;
    public FreezeHandler freezeHandler;
    public GadgetHandler gadgetHandler;
    public ReviveHandler reviveHandler;
    public VanishHandler vanishHandler;
    public ChatHandler chatHandler;
    public TicketHandler ticketHandler;
    public CmdHandler cmdHandler;
    public ModeCoordinator modeCoordinator;
    public InfractionCoordinator infractionCoordinator;
    public AlertCoordinator alertCoordinator;
    public UUID consoleUUID = UUID.fromString("9c417515-22bc-46b8-be4d-538482992f8f");
    public Tasks tasks;
    public Map<UUID, User> users;
    private MySQLConnection mySQLConnection;
    public boolean ninePlus = false;
    public HashMap<Inventory,Block> viewedChest = new HashMap<>();
    public boolean twelvePlus = false;

    public static StaffPlus get() {
        return plugin;
    }

    @Override
    public void onLoad() {
        APIManager.require(PacketListenerAPI.class, this);
    }

    @Override
    public void onEnable() {
        plugin = this;
        saveDefaultConfig();
        permission = new PermissionHandler(this);
        message = new MessageCoordinator(this);
        options = new Options();
        APIManager.initAPI(PacketListenerAPI.class);
        start(System.currentTimeMillis());
        if (options.storageType.equalsIgnoreCase("mysql")) {
            mySQLConnection = new MySQLConnection();
            if (mySQLConnection.init())
                getLogger().info("Database created");

        }
        if (getConfig().getBoolean("metrics"))
            new Metrics(this);
    }

    public UserManager getUserManager() {
        return userManager;
    }

    @Override
    public void onDisable() {
        message.sendConsoleMessage("Staff+ is now disabling!", true);
        if (versionProtocol != null) {
            stop();
        } else
            stop();
    }

    public void saveUsers() {
        for (User user : userManager.getAll()) {
            new Save(new NodeUser(user));
        }

        dataFile.save();
    }

    protected void start(long start) {
        users = new HashMap<>();
        if (!setupVersionProtocol()) {
            message.sendConsoleMessage("This version of Minecraft is not supported! If you have just updated to a brand new server version, check the Spigot plugin page.", true);
            Bukkit.getPluginManager().disablePlugin(this);
            return;
        }
        String[] tmp = Bukkit.getServer().getVersion().split("MC: ");
        String version = tmp[tmp.length - 1].substring(0, 4);
        ninePlus = JavaUtils.parseMcVer(version)>=9;
        System.out.println("ninePlus is: "+ninePlus);
        twelvePlus = JavaUtils.parseMcVer(version) >= 12;
        System.out.println("twelvePlus is: "+twelvePlus);
        dataFile = new DataFile("data.yml");
        languageFile = new LanguageFile();
        messages = new Messages();
        userManager = new UserManager(this);
        securityHandler = new SecurityHandler();
        cpsHandler = new CpsHandler();
        freezeHandler = new FreezeHandler();
        gadgetHandler = new GadgetHandler();
        reviveHandler = new ReviveHandler();
        vanishHandler = new VanishHandler();
        chatHandler = new ChatHandler();
        ticketHandler = new TicketHandler();
        cmdHandler = new CmdHandler();
        modeCoordinator = new ModeCoordinator();
        infractionCoordinator = new InfractionCoordinator();
        alertCoordinator = new AlertCoordinator();
        tasks = new Tasks();
        for (Player player : Bukkit.getOnlinePlayers()) {
            new Load(player);
        }
        registerListeners();
        new ChangelogFile();

        if (!options.disablePackets || !options.animationPackets.isEmpty() || !options.soundNames.isEmpty()) {
            new PacketModifier();
        }

        message.sendConsoleMessage("Staff+ has been enabled! Initialization took " + (System.currentTimeMillis() - start) + "ms.", false);
        message.sendConsoleMessage("Plugin created by Shortninja.", false);
    }

    private boolean setupVersionProtocol() {
        String version = Bukkit.getServer().getClass().getPackage().getName();
        String formattedVersion = version.substring(version.lastIndexOf('.') + 1);

        switch (formattedVersion) {
            case "v1_7_R1":
                versionProtocol = new Protocol_v1_7_R1(this);
                break;
            case "v1_7_R2":
                versionProtocol = new Protocol_v1_7_R2(this);
                break;
            case "v1_7_R3":
                versionProtocol = new Protocol_v1_7_R3(this);
                break;
            case "v1_7_R4":
                versionProtocol = new Protocol_v1_7_R4(this);
                break;
            case "v1_8_R1":
                versionProtocol = new Protocol_v1_8_R1(this);
                break;
            case "v1_8_R2":
                versionProtocol = new Protocol_v1_8_R2(this);
                break;
            case "v1_8_R3":
                versionProtocol = new Protocol_v1_8_R3(this);
                break;
            case "v1_9_R1":
                versionProtocol = new Protocol_v1_9_R1(this);
                break;
            case "v1_9_R2":
                versionProtocol = new Protocol_v1_9_R2(this);
                break;
            case "v1_10_R1":
                versionProtocol = new Protocol_v1_10_R1(this);
                break;
            case "v1_11_R1":
                versionProtocol = new Protocol_v1_11_R1(this);
                break;
            case "v1_12_R1":
                versionProtocol = new Protocol_v1_12_R1(this);
                break;
            case "v1_13_R1":
                versionProtocol = new Protocol_v1_13_R1(this);
                break;
            case "v1_13_R2":
                versionProtocol = new Protocol_v1_13_R2(this);
                break;
            case "v1_14_R1":
                versionProtocol = new Protocol_v1_14_R1(this);
                break;
        }

        if (versionProtocol != null) {
            message.sendConsoleMessage("Version protocol set to '" + formattedVersion + "'.", false);
        }

        return versionProtocol != null;
    }

    private void registerListeners() {
        new EntityDamage();
        new EntityDamageByEntity();
        new EntityTarget();
        new AsyncPlayerChat();
        new PlayerCommandPreprocess();
        new PlayerDeath();
        new PlayerDropItem();
        new PlayerInteract();
        new PlayerJoin();
        new PlayerPickupItem();
        new PlayerQuit();
        new BlockBreak();
        new BlockPlace();
        new FoodLevelChange();
        new InventoryClick();
        new InventoryClose();
        new InventoryOpen();
        new PlayerWorldChange();
    }

    /*
     * Nullifying all of the instances is sort of an experimental thing to deal
     * with memory leaks that could occur on reloads (where instances could be
     * handled incorrectly)
     */


    private void stop() {
        saveUsers();
        tasks.cancel();
        APIManager.disableAPI(PacketListenerAPI.class);

        for (Player player : Bukkit.getOnlinePlayers()) {
            modeCoordinator.removeMode(player);
            vanishHandler.removeVanish(player);
        }

        if (options.storageType.equalsIgnoreCase("mysql"))
            MySQLConnection.kill();

        versionProtocol = null;
        permission = null;
        message = null;
        options = null;
        languageFile = null;
        userManager = null;
        securityHandler = null;
        cpsHandler = null;
        freezeHandler = null;
        gadgetHandler = null;
        reviveHandler = null;
        vanishHandler = null;
        chatHandler = null;
        ticketHandler = null;
        cmdHandler = null;
        modeCoordinator = null;
        infractionCoordinator = null;
        alertCoordinator = null;
        tasks = null;
        plugin = null;

    }

    @Override
    public IOptions getOptions() {
        return options;
    }

    public void reloadFiles() {
        options = new Options();
        languageFile = new LanguageFile();
        messages = new Messages();
    }

    public PermissionHandler getPermissions() {
        return permission;
    }

    ;
}