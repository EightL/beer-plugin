package com.beer.pack;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import net.kyori.adventure.resource.ResourcePackInfo;
import net.kyori.adventure.resource.ResourcePackRequest;
import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.Bukkit;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.logging.Logger;

/**
 * Serves the resource pack bundled in the jar over HTTP and sends it to players on join. It is sent as an extra pack
 * (not replacing others), so it stacks with other plugins' packs. A copy is written to the data folder for servers
 * that merge it into their own pack instead.
 */
public final class PackHost implements Listener {

    private static final String RESOURCE = "beer-pack.zip";
    private static final UUID PACK_ID = UUID.nameUUIDFromBytes("beer-pack".getBytes(StandardCharsets.UTF_8));

    private final JavaPlugin plugin;
    private final Logger log;
    private byte[] bytes;
    private String sha1;
    private HttpServer server;
    private ExecutorService httpExecutor;

    public PackHost(JavaPlugin plugin) {
        this.plugin = plugin;
        this.log = plugin.getLogger();
    }

    public void start() {
        try (InputStream in = plugin.getResource(RESOURCE)) {
            if (in == null) {
                log.warning("The jar has no " + RESOURCE + "; crops and items will look like paper.");
                return;
            }
            bytes = in.readAllBytes();
            sha1 = HexFormat.of().formatHex(MessageDigest.getInstance("SHA-1").digest(bytes));
            Path copy = plugin.getDataFolder().toPath().resolve(RESOURCE);
            Files.createDirectories(copy.getParent());
            Files.write(copy, bytes);
        } catch (IOException | NoSuchAlgorithmException e) {
            log.warning("Could not read the bundled resource pack: " + e.getMessage());
            return;
        }
        if (!cfg().getBoolean("host-enabled", true)) {
            return;
        }
        int port = cfg().getInt("port", 8165);
        try {
            server = HttpServer.create(new InetSocketAddress(port), 0);
        } catch (IOException e) {
            log.warning("Could not serve the resource pack on port " + port + ": " + e.getMessage());
            return;
        }
        httpExecutor = Executors.newFixedThreadPool(2, runnable -> {
            Thread thread = new Thread(runnable, "beer-pack-http");
            thread.setDaemon(true);
            return thread;
        });
        server.setExecutor(httpExecutor);
        server.createContext("/", this::handle);
        server.start();
        plugin.getServer().getPluginManager().registerEvents(this, plugin);
        log.info("Serving resource pack on :" + port + " (sha1 " + sha1 + ")");
    }

    public void stop() {
        if (server != null) {
            server.stop(0);
            server = null;
        }
        if (httpExecutor != null) {
            httpExecutor.shutdownNow();
            httpExecutor = null;
        }
    }

    private ConfigurationSection cfg() {
        ConfigurationSection section = plugin.getConfig().getConfigurationSection("resource-pack");
        return section != null ? section : plugin.getConfig().createSection("resource-pack");
    }

    private void handle(HttpExchange exchange) throws IOException {
        try (exchange) {
            boolean ok = "GET".equals(exchange.getRequestMethod())
                    && exchange.getRequestURI().getPath().equals("/pack/" + sha1 + ".zip");
            if (!ok) {
                exchange.sendResponseHeaders(404, -1);
                return;
            }
            exchange.getResponseHeaders().set("Content-Type", "application/zip");
            exchange.sendResponseHeaders(200, bytes.length);
            try (OutputStream out = exchange.getResponseBody()) {
                out.write(bytes);
            }
        }
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();
        // a little after other plugins' packs, which may replace everything sent before them
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            if (player.isOnline()) {
                send(player);
            }
        }, 40);
    }

    public void send(Player player) {
        if (server == null) {
            return;
        }
        String configured = cfg().getString("public-url", "");
        String base;
        if (!configured.isBlank()) {
            base = configured.endsWith("/") ? configured.substring(0, configured.length() - 1) : configured;
        } else {
            InetSocketAddress virtualHost = player.getVirtualHost();
            String host = virtualHost != null ? virtualHost.getHostString() : Bukkit.getIp();
            if (host == null || host.isBlank()) {
                host = "127.0.0.1";
            }
            base = "http://" + host + ":" + cfg().getInt("port", 8165);
        }
        ResourcePackInfo info = ResourcePackInfo.resourcePackInfo(PACK_ID, URI.create(base + "/pack/" + sha1 + ".zip"), sha1);
        player.sendResourcePacks(ResourcePackRequest.resourcePackRequest()
                .packs(info)
                .replace(false)
                .required(cfg().getBoolean("required", false))
                .prompt(MiniMessage.miniMessage().deserialize(cfg().getString("prompt", "")))
                .build());
    }
}
