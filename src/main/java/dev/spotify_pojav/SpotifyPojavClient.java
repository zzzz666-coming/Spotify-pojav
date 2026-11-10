package dev.spotify_pojav;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.fabricmc.fabric.api.client.rendering.v1.HudRenderCallback;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.option.KeyBinding;
import net.minecraft.client.util.InputUtil;
import net.minecraft.text.Text;
import org.lwjgl.glfw.GLFW;

import java.net.HttpURLConnection;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

public final class SpotifyPojavClient implements ClientModInitializer {
    private static final ScheduledExecutorService POLLER = Executors.newSingleThreadScheduledExecutor(r -> {
        Thread t = new Thread(r, "Spotify-Pojav-Bridge");
        t.setDaemon(true);
        return t;
    });

    private static volatile TrackInfo track = TrackInfo.disconnected();
    private static boolean visible = true;
    private static KeyBinding toggleKey;

    @Override
    public void onInitializeClient() {
        toggleKey = KeyBindingHelper.registerKeyBinding(new KeyBinding(
                "key.spotifypojav.toggle_hud",
                InputUtil.Type.KEYSYM,
                GLFW.GLFW_KEY_RIGHT_SHIFT,
                KeyBinding.Category.MISC
        ));

        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            while (toggleKey.wasPressed()) visible = !visible;
        });

        HudRenderCallback.EVENT.register((drawContext, tickCounter) -> {
            if (visible) renderHud(MinecraftClient.getInstance(), drawContext);
        });

        POLLER.scheduleWithFixedDelay(SpotifyPojavClient::pollBridge, 0, 2, TimeUnit.SECONDS);
    }

    private static void pollBridge() {
        HttpURLConnection connection = null;
        try {
            connection = (HttpURLConnection) URI.create("http://127.0.0.1:8765/now").toURL().openConnection();
            connection.setRequestMethod("GET");
            connection.setConnectTimeout(700);
            connection.setReadTimeout(700);
            connection.setRequestProperty("Accept", "application/json");
            if (connection.getResponseCode() != 200) throw new IllegalStateException("Bridge HTTP " + connection.getResponseCode());
            byte[] bytes = connection.getInputStream().readAllBytes();
            JsonObject json = JsonParser.parseString(new String(bytes, StandardCharsets.UTF_8)).getAsJsonObject();
            track = new TrackInfo(
                    stringOr(json, "title", "Unknown track"),
                    stringOr(json, "artist", "Unknown artist"),
                    stringOr(json, "lyric", ""),
                    json.has("playing") && json.get("playing").getAsBoolean(),
                    true
            );
        } catch (Exception ignored) {
            track = TrackInfo.disconnected();
        } finally {
            if (connection != null) connection.disconnect();
        }
    }

    private static String stringOr(JsonObject json, String key, String fallback) {
        return json.has(key) && !json.get(key).isJsonNull() ? json.get(key).getAsString() : fallback;
    }

    private static void renderHud(MinecraftClient client, DrawContext ctx) {
        if (client.player == null || client.options.hudHidden) return;
        TrackInfo data = track;
        int x = 12;
        int y = 12;
        int width = 190;
        int height = data.lyric.isBlank() ? 62 : 78;

        ctx.fill(x, y, x + width, y + height, 0xD9141422);
        ctx.fill(x, y, x + width, y + 2, 0xFFB56CFF);
        ctx.fill(x, y + 2, x + 3, y + height, 0xFF55DDE0);

        ctx.drawTextWithShadow(client.textRenderer, Text.literal("♫  SPOTIFY POJAV"), x + 10, y + 8, 0xFFDCC5FF);
        String status = data.connected ? (data.playing ? "NOW PLAYING" : "PAUSED") : "BRIDGE OFFLINE";
        int statusColor = data.connected ? (data.playing ? 0xFF77E6C2 : 0xFFFFD479) : 0xFFFF8B9B;
        ctx.drawTextWithShadow(client.textRenderer, Text.literal(status), x + 10, y + 21, statusColor);

        ctx.drawTextWithShadow(client.textRenderer, Text.literal(trim(data.title, 25)), x + 10, y + 34, 0xFFFFFFFF);
        ctx.drawTextWithShadow(client.textRenderer, Text.literal(trim(data.artist, 27)), x + 10, y + 46, 0xFFB8B8CC);
        if (!data.lyric.isBlank()) {
            ctx.drawTextWithShadow(client.textRenderer, Text.literal(trim(data.lyric, 29)), x + 10, y + 61, 0xFF79E3E0);
        }
    }

    private static String trim(String value, int max) {
        if (value == null) return "";
        return value.length() <= max ? value : value.substring(0, max - 1) + "…";
    }

    private static final class TrackInfo {
        final String title;
        final String artist;
        final String lyric;
        final boolean playing;
        final boolean connected;

        TrackInfo(String title, String artist, String lyric, boolean playing, boolean connected) {
            this.title = title;
            this.artist = artist;
            this.lyric = lyric;
            this.playing = playing;
            this.connected = connected;
        }

        static TrackInfo disconnected() {
            return new TrackInfo("Waiting for Android bridge", "Start the companion app", "", false, false);
        }
    }
}
