package ejedev.chompyhunter;
import com.google.inject.Provides;
import lombok.AccessLevel;
import lombok.Getter;
import net.runelite.api.*;
import net.runelite.api.coords.WorldPoint;
import net.runelite.api.events.*;
import net.runelite.client.config.ConfigManager;
import net.runelite.client.events.ConfigChanged;
import net.runelite.client.plugins.Plugin;
import net.runelite.client.plugins.PluginDescriptor;
import javax.inject.Inject;
import net.runelite.client.eventbus.Subscribe;
import net.runelite.client.ui.overlay.OverlayManager;

import java.time.Duration;
import java.time.Instant;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

import net.runelite.client.Notifier;

@PluginDescriptor(
        name = "Chompy Hunter",
        description = "A plugin to overlay chompy birds with a timer and colour based on remaining time till despawn. This is an updated version originally by ejedev.",
        tags = {"chompy", "bird", "hunt", "hunting", "chompies", "track", "count", "western"}
)

public class ChompyHunterPlugin extends Plugin{

    @Inject
    private OverlayManager overlayManager;

    @Inject
    private ChompyHunterConfig config;

    static final int LIVE_CHOMPY_ID = NpcID.CHOMPY_BIRD;
    static final int DEAD_CHOMPY_ID = NpcID.CHOMPY_BIRD_1476;
    static final String AUTO_HIDE_KEY = "autoHide";
    private static final Pattern Chompy_KC_REGEX = Pattern.compile("You've scratched up a total of.*");

    private static final List<Integer> CW_Feldhip_REGION_IDS = Arrays.asList(
            9263,
            9519,
            9775,
            10051,
            9774,
            10030,
            10286,
            10542
    );

    private int lastRegionId = -1;
    private boolean panelEnabled = false;

    @Provides
    ChompyHunterConfig provideConfig(ConfigManager configManager)
    {
        return configManager.getConfig(ChompyHunterConfig.class);
    }

    @Override
    protected void startUp()
    {
        overlayManager.add(overlay);
        overlayManager.add(overlayInfo);
        chompies.clear();
        ChompyKills = 0;
        ChompyTotalKills = 0;
        StartTime = null;
        PluginTimeout = null;
        panelEnabled = false;
    }

    @Override
    protected void shutDown()
    {
        overlayManager.remove(overlay);
        overlayManager.remove(overlayInfo);
        chompies.clear();
        ChompyKills = 0;
        ChompyTotalKills = 0;
        StartTime = null;
        PluginTimeout = null;
        panelEnabled = false;
    }

    @Getter(AccessLevel.PACKAGE)
    private final Map<Integer, Chompy> chompies = new HashMap<>();

    @Getter(AccessLevel.PACKAGE)
    private int ChompyKills;
    @Getter(AccessLevel.PACKAGE)
    private int ChompyTotalKills;

    @Getter(AccessLevel.PACKAGE)
    private Instant StartTime;
    @Getter(AccessLevel.PACKAGE)
    private Instant PluginTimeout;

    @Inject
    private Client client;

    @Inject
    private ChompyHunterOverlay overlay;

    @Inject
    private ChompyHunterInfoOverlay overlayInfo;

    @Inject
    private Notifier notifier;

    public boolean getPanelEnabled()
    {
        return panelEnabled;
    }

    @Subscribe
    public void onChatMessage(ChatMessage chatMessage) {
        if (chatMessage.getMessage().equals("You scratch a notch on your bow for the chompy bird kill.") && chatMessage.getType() == ChatMessageType.SPAM) {
            if (StartTime == null) {
                StartTime = Instant.now();
                PluginTimeout = null;
            }
            panelEnabled = true;
            ChompyKills++;
            ChompyTotalKills++;
        }
        if (Chompy_KC_REGEX.matcher(chatMessage.getMessage()).matches() && chatMessage.getType() == ChatMessageType.GAMEMESSAGE) {
            ChompyTotalKills = Integer.parseInt(chatMessage.getMessage().replaceAll("[^0-9]", ""));
            if (StartTime == null) {
                StartTime = Instant.now();
                PluginTimeout = null;
            }
            panelEnabled = true;
        }
    }

    @Subscribe
   private void onNpcSpawned(NpcSpawned event)
    {
        NPC npc = event.getNpc();

        if (npc == null)
        {
            return;
        }

        int npcId = event.getNpc().getId();
        if (npcId == LIVE_CHOMPY_ID) {
            if (!chompies.containsKey(npc.getIndex())) {
                // new chompy
                chompies.put(npc.getIndex(), new Chompy(npc));
                if (config.notifyChompySpawn()) {
                    notifier.notify("A chompy has spawned!");
                }
            } else {
                // replace old chompy with new npc since you walked away and despawned
                Chompy chompy = chompies.get(npc.getIndex());
                Instant oldStartTime = chompy.getSpawnTime();
                Chompy newChompy = new Chompy(npc);
                newChompy.setSpawnTime(oldStartTime);
                chompies.put(npc.getIndex(), newChompy);
            }
        }
    }

    @Subscribe
    private void onNpcChanged(NpcChanged event)
    {
        NPC npc = event.getNpc();
        int npcId = event.getNpc().getId();
        if (npcId == DEAD_CHOMPY_ID && chompies.containsKey(npc.getIndex())) {
            // remove chompy when killed
            chompies.remove(event.getNpc().getIndex());
        }
    }

   @Subscribe
    private void onNpcDespawned(NpcDespawned event)
    {
        // chompy ran of out time or player walked away
        NPC npc = event.getNpc();
        int npcId = event.getNpc().getId();
        if (npcId == LIVE_CHOMPY_ID && chompies.containsKey(npc.getIndex())) {
            Chompy chompy = chompies.get(npc.getIndex());
            long timeLeft = Duration.between(Instant.now(), chompy.getSpawnTime()).getSeconds();
            if (timeLeft < 0) {
                chompies.remove(npc.getIndex());
            }
        }
    }

    @Subscribe
    public void onConfigChanged(ConfigChanged event) {
        if (event.getKey().equals(AUTO_HIDE_KEY)) {
            boolean nearChompy = CW_Feldhip_REGION_IDS.contains(getRegionId());
            panelEnabled = nearChompy || !config.autoHide();
            if (panelEnabled && StartTime == null) {
                StartTime = Instant.now();
                PluginTimeout = null;
            }
        }
    }

    @Subscribe
    private void onGameTick(GameTick tick) {
        if(panelEnabled && config.autoHide()) {
            checkRegion();
        }
    }

    private void checkRegion() {
        final int regionId = getRegionId();

        if (!CW_Feldhip_REGION_IDS.contains(regionId)) {
            if (PluginTimeout == null) {
                PluginTimeout = Instant.now().plusSeconds(config.autoHideTimeout());
            } else if (PluginTimeout.isBefore(Instant.now())) {
                StartTime = null;
                PluginTimeout = null;
                panelEnabled = false;
                chompies.clear();
            }

            lastRegionId = regionId;
            return;
        }
    }

    private int getRegionId() {
        Player player = client.getLocalPlayer();
        if (player == null) {
            return -1;
        }

        return WorldPoint.fromLocalInstance(client, player.getLocalLocation()).getRegionID();
    }
}