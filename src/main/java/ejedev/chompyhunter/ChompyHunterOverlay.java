package ejedev.chompyhunter;
import net.runelite.api.*;
import net.runelite.api.Point;
import net.runelite.client.ui.overlay.Overlay;
import net.runelite.client.ui.overlay.OverlayLayer;
import net.runelite.client.ui.overlay.OverlayPosition;
import net.runelite.client.ui.overlay.OverlayUtil;
import net.runelite.client.util.Text;
import javax.inject.Inject;
import java.awt.*;
import java.time.Duration;
import java.time.Instant;

public class ChompyHunterOverlay extends Overlay {

    private final ChompyHunterPlugin plugin;
    private final Client client;

    @Inject
    public ChompyHunterOverlay(ChompyHunterPlugin plugin, Client client) {
        this.plugin = plugin;
        this.client = client;
        setPosition(OverlayPosition.DYNAMIC);
        setLayer(OverlayLayer.ABOVE_SCENE);
    }

    @Override
    public Dimension render(Graphics2D graphics)
    {
       renderChompy(graphics);
       return null;
    }

    private void renderChompy(Graphics2D graphics)
    {
        for (Chompy chompy : plugin.getChompies().values())
        {
            NPC npc = chompy.getNpc();
            // player walks away, npc despawns, no longer need to draw chompy until respawned
            if (npc == null) {
                continue;
            }
            Shape objectClickbox = npc.getConvexHull();
            long timeLeft = Duration.between(Instant.now(), chompy.getSpawnTime()).getSeconds();
            String timeLeftFormatted = timeLeft + "";
            Color color = Color.GREEN;
            if(timeLeft <= 30 && timeLeft > 15) {
                color = Color.ORANGE;
            }
            else if(timeLeft <= 15 && timeLeft >= 0) {
                color = Color.RED;
            } else if (timeLeft < 0) {
                plugin.getChompies().remove(npc.getIndex());
                continue;
            }
            if (npc.getId() == ChompyHunterPlugin.LIVE_CHOMPY_ID)
            {
                renderPoly(graphics, color, objectClickbox);
                String npcName = Text.removeTags(npc.getName());
                Point textLocation = npc.getCanvasTextLocation(graphics, npcName, npc.getLogicalHeight() + 40);
                if (textLocation != null)
                {
                    OverlayUtil.renderTextLocation(graphics, textLocation, timeLeftFormatted, color);
                }
            }
        }
    }

    private void renderPoly(Graphics2D graphics, Color color, Shape polygon)
    {
        if (polygon != null)
        {
            graphics.setColor(color);
            graphics.setStroke(new BasicStroke(2));
            graphics.draw(polygon);
            graphics.setColor(new Color(color.getRed(), color.getGreen(), color.getBlue(), 20));
            graphics.fill(polygon);
        }
    }
}


