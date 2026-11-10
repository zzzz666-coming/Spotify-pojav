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
                "key.spotifypojav.toggle_hud", InputUtil.Type.KEYSYM,
                GLFW.GLFW_KEY_RIGHT_SHIFT, KeyBinding.Category.MISC));
        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            while (toggleKey.wasPressed()) visible = !visible;
        });
        HudRenderCallback.EVENT.register((drawContext, tickCounter) -> {
            if (visible) renderHud(MinecraftClient.getInstance(), drawContext);
        });
        POLLER.scheduleWithFixedDelay(SpotifyPojavClient::pollBridge, 0, 1, TimeUnit.SECONDS);
    }

    private static void pollBridge() {
        HttpURLConnection connection = null;
        try {
            connection = (HttpURLConnection) URI.create("http://127.0.0.1:8765/now").toURL().openConnection();
            connection.setRequestMethod("GET");
            connection.setConnectTimeout(700);
            connection.setReadTimeout(700);
            connection.setRequestProperty("Accept", "application/json");
            if (connection.getResponseCode() != 200) throw new IllegalStateException("Bridge offline");
            JsonObject json = JsonParser.parseString(new String(connection.getInputStream().readAllBytes(), StandardCharsets.UTF_8)).getAsJsonObject();
            track = new TrackInfo(stringOr(json, "title", "Unknown track"),
                    stringOr(json, "artist", "Unknown artist"), stringOr(json, "lyric", ""),
                    stringOr(json, "nextLyric", ""), json.has("playing") && json.get("playing").getAsBoolean(),
                    json.has("positionMs") ? json.get("positionMs").getAsLong() : 0,
                    json.has("durationMs") ? json.get("durationMs").getAsLong() : 0,
                    json.has("lyricsLoaded") && json.get("lyricsLoaded").getAsBoolean(), true);
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
        TrackInfo d = track;
        int x = 12, y = 12, width = 218;
        int height = d.lyricsLoaded ? 112 : 88;
        ctx.fill(x, y, x + width, y + height, 0xE9161726);
        ctx.fill(x, y, x + width, y + 3, 0xFFB66CFF);
        ctx.fill(x, y + 3, x + 3, y + height, 0xFF55DDE0);
        ctx.fill(x + 10, y + 10, x + 29, y + 29, 0xFF35264D);
        ctx.drawTextWithShadow(client.textRenderer, Text.literal("♫"), x + 16, y + 15, 0xFFDAB8FF);
        ctx.drawTextWithShadow(client.textRenderer, Text.literal("SPOTIFY POJAV"), x + 36, y + 11, 0xFFDCC5FF);
        String state = d.connected ? (d.playing ? "NOW PLAYING" : "PAUSED") : "BRIDGE OFFLINE";
        int color = d.connected ? (d.playing ? 0xFF77E6C2 : 0xFFFFD479) : 0xFFFF8B9B;
        ctx.drawTextWithShadow(client.textRenderer, Text.literal(state), x + 36, y + 23, color);
        ctx.drawTextWithShadow(client.textRenderer, Text.literal(trim(d.title, 30)), x + 10, y + 42, 0xFFFFFFFF);
        ctx.drawTextWithShadow(client.textRenderer, Text.literal(trim(d.artist, 32)), x + 10, y + 55, 0xFFB8B8CC);
        if (d.durationMs > 0) {
            int bx = x + 10, by = y + 70, bw = width - 20;
            ctx.fill(bx, by, bx + bw, by + 3, 0xFF39394B);
            int fill = (int) (bw * Math.min(1.0, (double) d.positionMs / d.durationMs));
            if (fill > 0) ctx.fill(bx, by, bx + fill, by + 3, 0xFFB66CFF);
        }
        if (d.lyricsLoaded) {
            String line = d.lyric.isBlank() ? "♪  Waiting for lyrics..." : d.lyric;
            ctx.drawTextWithShadow(client.textRenderer, Text.literal(trim(line, 34)), x + 10, y + 82, 0xFF8CF4E8);
            if (!d.nextLyric.isBlank() && !d.nextLyric.equals(d.lyric))
                ctx.drawTextWithShadow(client.textRenderer, Text.literal(trim(d.nextLyric, 34)), x + 10, y + 96, 0xFF88899F);
        } else {
            ctx.drawTextWithShadow(client.textRenderer, Text.literal("Load a .LRC file in companion app"), x + 10, y + 82, 0xFF9293A8);
        }
    }

    private static String trim(String value, int max) {
        if (value == null) return "";
        return value.length() <= max ? value : value.substring(0, max - 1) + "…";
    }

    private static final class TrackInfo {
        final String title, artist, lyric, nextLyric;
        final boolean playing, lyricsLoaded, connected;
        final long positionMs, durationMs;
        TrackInfo(String title, String artist, String lyric, String nextLyric, boolean playing,
                  long positionMs, long durationMs, boolean lyricsLoaded, boolean connected) {
            this.title = title; this.artist = artist; this.lyric = lyric; this.nextLyric = nextLyric;
            this.playing = playing; this.positionMs = positionMs; this.durationMs = durationMs;
            this.lyricsLoaded = lyricsLoaded; this.connected = connected;
        }
        static TrackInfo disconnected() {
            return new TrackInfo("Waiting for Android bridge", "Start the companion app", "",
                    "", false, 0, 0, false, false);
        }
    }
}
