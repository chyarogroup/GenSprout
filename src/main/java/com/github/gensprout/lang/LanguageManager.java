package com.github.gensprout.lang;

import com.github.gensprout.GenSprout;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import net.kyori.adventure.text.minimessage.tag.resolver.TagResolver;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.Bukkit;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;

import java.io.File;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Central translation layer.
 *
 * <p>Every user-facing string in GenSprout resolves through this class. Two placeholder styles are
 * accepted interchangeably in every language file so translators never have to remember which one a
 * given key supports: MiniMessage tag style ({@code <level>}) and brace style ({@code {level}}).
 * Both are fed from the same value map by {@link #getComponent(String, CommandSender, Map)}.
 *
 * <p>Bundled language files are version stamped with {@code file-version}. When the JAR ships a
 * newer stamp than the copy already on disk, the on-disk copy is renamed to {@code .old} and
 * replaced, so translation updates in a plugin update are never permanently shadowed by a stale
 * file left behind by an earlier install.
 */
public class LanguageManager {

    private static final List<String> BUNDLED_LANGUAGES = List.of("en", "es", "de", "fr", "zh");

    private final GenSprout plugin;
    private final Map<String, FileConfiguration> langConfigs = new HashMap<>();
    private final Map<UUID, String> localeCache = new ConcurrentHashMap<>();
    private String defaultLanguage = "en";

    public LanguageManager(GenSprout plugin) {
        this.plugin = plugin;
        loadLanguages();
    }

    public void loadLanguages() {
        langConfigs.clear();
        localeCache.clear();

        File langFolder = new File(plugin.getDataFolder(), "lang");
        if (!langFolder.exists()) {
            langFolder.mkdirs();
        }

        for (String lang : BUNDLED_LANGUAGES) {
            String resourcePath = "lang/messages_" + lang + ".yml";
            File file = new File(langFolder, "messages_" + lang + ".yml");

            if (!file.exists()) {
                plugin.saveResource(resourcePath, false);
            }

            FileConfiguration config = YamlConfiguration.loadConfiguration(file);
            applyJarDefaults(config, resourcePath);
            langConfigs.put(lang, config);
        }

        // Load any additional operator-supplied language files in plugins/GenSprout/lang/
        File[] customFiles = langFolder.listFiles((dir, name) -> name.startsWith("messages_") && name.endsWith(".yml"));
        if (customFiles != null) {
            for (File file : customFiles) {
                String name = file.getName();
                String code = name.substring("messages_".length(), name.length() - ".yml".length()).toLowerCase(Locale.ROOT);
                if (!langConfigs.containsKey(code)) {
                    FileConfiguration config = YamlConfiguration.loadConfiguration(file);
                    applyJarDefaults(config, "lang/messages_en.yml");
                    langConfigs.put(code, config);
                }
            }
        }

        this.defaultLanguage = plugin.getConfig().getString("language", "en").toLowerCase(Locale.ROOT).trim();
        if (!langConfigs.containsKey(defaultLanguage)) {
            plugin.getLogger().warning("config.yml language '" + defaultLanguage + "' has no matching lang/messages_"
                    + defaultLanguage + ".yml. Falling back to 'en'.");
            this.defaultLanguage = "en";
        }

        plugin.getLogger().info("Loaded " + langConfigs.size() + " language bundle(s) "
                + langConfigs.keySet() + ". Default language: " + defaultLanguage);
    }

    private void applyJarDefaults(FileConfiguration config, String resourcePath) {
        InputStream defaultStream = plugin.getResource(resourcePath);
        if (defaultStream == null) {
            return;
        }
        try (InputStreamReader reader = new InputStreamReader(defaultStream, StandardCharsets.UTF_8)) {
            config.setDefaults(YamlConfiguration.loadConfiguration(reader));
            config.options().copyDefaults(false);
        } catch (java.io.IOException ignored) {
            // A closed stream is not worth failing startup over; the on-disk values still load.
        }
    }

    // ------------------------------------------------------------------
    // Locale resolution
    // ------------------------------------------------------------------

    public String resolveLocale(CommandSender sender) {
        if (!(sender instanceof Player player)) {
            return resolveGlobalConfiguredLanguage();
        }
        String cached = localeCache.get(player.getUniqueId());
        if (cached != null) {
            return cached;
        }
        String resolved = computeLocale(player);
        localeCache.put(player.getUniqueId(), resolved);
        return resolved;
    }

    /**
     * Two behaviours only, both server-wide. There is deliberately no per-player language setting.
     *
     * <ul>
     *   <li>{@code userLanguages: true} (or {@code auto}) follows each player's Minecraft client
     *       language, falling back to the configured default when no bundle matches.</li>
     *   <li>{@code userLanguages: false} forces the {@code language} value from config.yml on
     *       everyone. A language code in place of the boolean forces that code instead.</li>
     * </ul>
     */
    private String computeLocale(Player player) {
        Object userLangObj = plugin.getConfig().get("userLanguages");
        if (userLangObj instanceof Boolean boolVal) {
            if (!boolVal) {
                return defaultLanguage;
            }
        } else if (userLangObj instanceof String strVal) {
            String trimmed = strVal.trim().toLowerCase(Locale.ROOT);
            if (trimmed.equals("false")) {
                return defaultLanguage;
            }
            if (!trimmed.equals("true") && !trimmed.equals("auto")) {
                if (langConfigs.containsKey(trimmed)) {
                    return trimmed;
                }
                // An unloadable code here used to silently fall through to client matching, which
                // looks identical to "auto" and hides the typo. Pin to the default instead.
                plugin.getLogger().warning("config.yml userLanguages '" + strVal + "' is not a loaded language code. "
                        + "Using the default language '" + defaultLanguage + "'.");
                return defaultLanguage;
            }
        }

        return getPlayerClientLanguage(player);
    }

    private String getPlayerClientLanguage(Player player) {
        try {
            String rawLocale = null;
            try {
                rawLocale = player.getLocale();
            } catch (Throwable ignored) {
            }

            try {
                Locale loc = player.locale();
                if (loc != null) {
                    if (rawLocale == null || rawLocale.isEmpty()) {
                        rawLocale = loc.toString();
                    }
                }
            } catch (Throwable ignored) {
            }

            if (rawLocale != null && !rawLocale.isEmpty()) {
                String clean = rawLocale.toLowerCase(Locale.ROOT).trim().replace('-', '_');
                // Most specific first: zh_tw must not collapse to zh when a zh_tw bundle exists.
                if (langConfigs.containsKey(clean)) {
                    return clean;
                }
                int underscore = clean.indexOf('_');
                String base = underscore > 0 ? clean.substring(0, underscore) : clean;
                if (langConfigs.containsKey(base)) {
                    return base;
                }
            }
        } catch (Throwable t) {
            plugin.getLogger().warning("Could not resolve client locale for " + player.getName() + ": " + t.getMessage());
        }
        return defaultLanguage;
    }

    private String resolveGlobalConfiguredLanguage() {
        Object userLangObj = plugin.getConfig().get("userLanguages");
        if (userLangObj instanceof String strVal) {
            String trimmed = strVal.trim().toLowerCase(Locale.ROOT);
            if (!trimmed.equals("true") && !trimmed.equals("false") && !trimmed.equals("auto")
                    && langConfigs.containsKey(trimmed)) {
                return trimmed;
            }
        }
        return defaultLanguage;
    }

    /** Drops one player's cached locale. Called on locale change, language change, and quit. */
    public void invalidateLocale(UUID uuid) {
        if (uuid != null) {
            localeCache.remove(uuid);
        }
    }

    public boolean isLanguageLoaded(String code) {
        return code != null && langConfigs.containsKey(code.trim().toLowerCase(Locale.ROOT));
    }

    public String getDefaultLanguage() {
        return defaultLanguage;
    }

    // ------------------------------------------------------------------
    // Server identity helpers
    // ------------------------------------------------------------------

    /**
     * The server name with all formatting stripped. Language files embed {@code {servername}} inside
     * their own gradients, so injecting the styled config value there would nest one gradient inside
     * another and the outer one would have no visible effect.
     */
    public String getServerName() {
        String raw = plugin.getConfig().getString("server.name", "GenSprout");
        try {
            return PlainTextComponentSerializer.plainText().serialize(plugin.getMiniMessage().deserialize(raw));
        } catch (Throwable t) {
            return raw;
        }
    }

    /** The server name exactly as written in config.yml, formatting tags included. */
    public String getServerNameStyled() {
        return plugin.getConfig().getString("server.name", "GenSprout");
    }

    public String getPrefix(String langCode) {
        String prefix = lookupRaw("prefix", langCode);
        return prefix != null ? prefix : "";
    }

    // ------------------------------------------------------------------
    // Message lookup
    // ------------------------------------------------------------------

    /** Raw lookup with the effective language, default language, and English fallback chain. */
    private String lookupRaw(String key, String langCode) {
        String effectiveLang = normalizeLang(langCode);

        FileConfiguration config = langConfigs.get(effectiveLang);
        String message = config != null ? config.getString(key) : null;

        if (message == null && !effectiveLang.equals(defaultLanguage)) {
            FileConfiguration defaultConfig = langConfigs.get(defaultLanguage);
            message = defaultConfig != null ? defaultConfig.getString(key) : null;
        }
        if (message == null && !effectiveLang.equals("en") && !defaultLanguage.equals("en")) {
            FileConfiguration enConfig = langConfigs.get("en");
            message = enConfig != null ? enConfig.getString(key) : null;
        }
        return message;
    }

    private List<String> lookupRawList(String key, String langCode) {
        String effectiveLang = normalizeLang(langCode);

        FileConfiguration config = langConfigs.get(effectiveLang);
        List<String> list = config != null ? config.getStringList(key) : null;

        if ((list == null || list.isEmpty()) && !effectiveLang.equals(defaultLanguage)) {
            FileConfiguration defaultConfig = langConfigs.get(defaultLanguage);
            if (defaultConfig != null) {
                list = defaultConfig.getStringList(key);
            }
        }
        if ((list == null || list.isEmpty()) && !effectiveLang.equals("en") && !defaultLanguage.equals("en")) {
            FileConfiguration enConfig = langConfigs.get("en");
            if (enConfig != null) {
                list = enConfig.getStringList(key);
            }
        }
        return list != null ? list : Collections.emptyList();
    }

    private String normalizeLang(String langCode) {
        if (langCode == null) {
            return defaultLanguage;
        }
        String lower = langCode.toLowerCase(Locale.ROOT);
        return langConfigs.containsKey(lower) ? lower : defaultLanguage;
    }

    public String getRawMessage(String key, String langCode) {
        String message = lookupRaw(key, langCode);
        if (message == null) {
            return "<red>[Missing string key: " + key + "]</red>";
        }
        return expandGlobals(message, normalizeLang(langCode), null);
    }

    public List<String> getRawStringList(String key, String langCode) {
        String effectiveLang = normalizeLang(langCode);
        List<String> raw = lookupRawList(key, langCode);
        List<String> expanded = new ArrayList<>(raw.size());
        for (String line : raw) {
            expanded.add(expandGlobals(line, effectiveLang, null));
        }
        return expanded;
    }

    /**
     * Substitutes the placeholders that every message may use regardless of call site.
     *
     * <p>{@code <prefix>} is expanded first because the prefix value itself contains
     * {@code {servername}}; expanding it last would leave that placeholder literal.
     */
    private String expandGlobals(String message, String langCode, Player player) {
        return expandGlobals(message, langCode, player, null);
    }

    /**
     * @param supplied names the call site provides explicitly; these are left alone so a call site
     *                 meaning {@code <player>} as "the target of this admin command" is not
     *                 overwritten with the recipient's own name
     */
    private String expandGlobals(String message, String langCode, Player player, Map<String, String> supplied) {
        String result = message;

        if (result.contains("<prefix>") || result.contains("{prefix}")) {
            String prefix = getPrefix(langCode);
            result = result.replace("<prefix>", prefix).replace("{prefix}", prefix);
        }

        String serverName = getServerName();
        result = result
                .replace("<servername>", serverName)
                .replace("<server_name>", serverName)
                .replace("{servername}", serverName)
                .replace("{server_name}", serverName)
                .replace("<servername_styled>", getServerNameStyled())
                .replace("{servername_styled}", getServerNameStyled());

        if (result.contains("server_ip")) {
            String serverIp = plugin.getConfig().getString("server.ip", "play.gensprout.net");
            result = result.replace("<server_ip>", serverIp).replace("{server_ip}", serverIp);
        }
        if (result.contains("tagline")) {
            String tagline = plugin.getConfig().getString("server.tagline", "");
            result = result.replace("<tagline>", tagline).replace("{tagline}", tagline);
        }
        if (result.contains("online")) {
            String online = String.valueOf(Bukkit.getOnlinePlayers().size());
            result = result.replace("<online>", online).replace("{online}", online);
        }
        if (result.contains("max_players")) {
            String maxPlayers = String.valueOf(Bukkit.getMaxPlayers());
            result = result.replace("<max_players>", maxPlayers).replace("{max_players}", maxPlayers);
        }

        if (player != null) {
            if (supplied == null || !supplied.containsKey("player")) {
                result = result.replace("<player>", player.getName()).replace("{player}", player.getName());
            }
            if (result.contains("ping")) {
                String ping = String.valueOf(player.getPing());
                result = result.replace("<ping>", ping).replace("{ping}", ping);
            }
            if (result.contains("<lang>") || result.contains("{lang}")) {
                result = result.replace("<lang>", langCode).replace("{lang}", langCode);
            }
        }

        return result;
    }

    // ------------------------------------------------------------------
    // Public rendering API
    // ------------------------------------------------------------------

    /**
     * Renders a key for a recipient, accepting both placeholder styles.
     *
     * <p>Every entry in {@code values} is applied twice: as a literal {@code {name}} replacement and
     * as a MiniMessage {@code <name>} tag resolver. Translators may use either form and the message
     * renders identically, which removes the whole class of "placeholder shows up as literal text"
     * bugs that came from the two styles being handled by different code paths.
     */
    public Component getComponent(String key, CommandSender target, Map<String, String> values) {
        String langCode = resolveLocale(target);
        String raw = lookupRaw(key, langCode);
        if (raw == null) {
            plugin.getLogger().warning("Missing language key '" + key + "' (language: " + langCode + ")");
            return plugin.getMiniMessage().deserialize("<red>[Missing string key: " + key + "]</red>");
        }
        return render(raw, langCode, target, values);
    }

    public Component getComponent(String key, CommandSender target) {
        return getComponent(key, target, null);
    }

    public boolean hasKey(String key, CommandSender target) {
        String langCode = resolveLocale(target);
        return lookupRaw(key, langCode) != null;
    }

    /**
     * Renders a raw MiniMessage string, not a language key, through the same placeholder pipeline.
     * Used for operator-authored strings that still live in config.yml.
     */
    public Component renderRaw(String raw, CommandSender target, Map<String, String> values) {
        if (raw == null) {
            return Component.empty();
        }
        return render(raw, resolveLocale(target), target, values);
    }

    /**
     * Renders a key whose value is a list, applying the same dual-style placeholder handling to
     * every line.
     */
    public List<Component> getComponentList(String key, CommandSender target, Map<String, String> values) {
        String langCode = resolveLocale(target);
        List<String> raw = lookupRawList(key, langCode);
        List<Component> out = new ArrayList<>(raw.size());
        for (String line : raw) {
            out.add(render(line, langCode, target, values));
        }
        return out;
    }

    public List<Component> getComponentList(String key, CommandSender target) {
        return getComponentList(key, target, null);
    }

    /**
     * Placeholder names whose values are operator-authored and may legitimately contain MiniMessage
     * markup, such as the {@code <sprite:...>} item names in the supplies shop config. These are
     * inserted parsed so that markup renders; everything else is inserted literally so no
     * player-supplied text can inject tags.
     */
    private static final Set<String> PARSED_VALUE_NAMES = Set.of("item_name", "item", "category", "name", "tagline");

    private Component render(String raw, String langCode, CommandSender target, Map<String, String> values) {
        String expanded = expandGlobals(raw, langCode, target instanceof Player p ? p : null, values);
        if (values == null || values.isEmpty()) {
            return plugin.getMiniMessage().deserialize(expanded);
        }

        List<TagResolver> resolvers = new ArrayList<>(values.size());
        for (Map.Entry<String, String> entry : values.entrySet()) {
            String name = entry.getKey();
            String value = entry.getValue() == null ? "" : entry.getValue();
            // Brace style is substituted before parsing, so it always renders markup in the value.
            expanded = expanded.replace("{" + name + "}", value);
            resolvers.add(PARSED_VALUE_NAMES.contains(name)
                    ? Placeholder.parsed(name, value)
                    : Placeholder.unparsed(name, value));
        }
        return plugin.getMiniMessage().deserialize(expanded, TagResolver.resolver(resolvers));
    }

    /**
     * Renders a key and strips all formatting. Used when a translated fragment has to be embedded
     * into another message as a plain value, such as the word MAX inside a cost line.
     */
    public String getPlainText(String key, CommandSender target, Map<String, String> values) {
        return PlainTextComponentSerializer.plainText().serialize(getComponent(key, target, values));
    }

    public String getPlainText(String key, CommandSender target) {
        return getPlainText(key, target, null);
    }

    /** Renders and sends a message in one step. */
    public void send(CommandSender target, String key, Map<String, String> values) {
        if (target == null) {
            return;
        }
        target.sendMessage(getComponent(key, target, values));
    }

    public void send(CommandSender target, String key) {
        send(target, key, null);
    }

    /** Renders and sends a message to the action bar. */
    public void sendActionBar(Player player, String key, Map<String, String> values) {
        if (player == null || !player.isOnline()) {
            return;
        }
        player.sendActionBar(getComponent(key, player, values));
    }

    public void sendActionBar(Player player, String key) {
        sendActionBar(player, key, null);
    }

    public String getMessageString(String key, CommandSender target) {
        String langCode = resolveLocale(target);
        String raw = lookupRaw(key, langCode);
        if (raw == null) {
            return null;
        }
        return expandGlobals(raw, langCode, target instanceof Player p ? p : null);
    }

    public List<String> getMessageStringList(String key, CommandSender target) {
        String langCode = resolveLocale(target);
        List<String> raw = lookupRawList(key, langCode);
        List<String> out = new ArrayList<>(raw.size());
        for (String line : raw) {
            out.add(expandGlobals(line, langCode, target instanceof Player p ? p : null));
        }
        return out;
    }

    /** Convenience builder so call sites can write {@code values("cost", "$10")} inline. */
    public static Map<String, String> values(String... keyValuePairs) {
        Map<String, String> map = new LinkedHashMap<>();
        for (int i = 0; i + 1 < keyValuePairs.length; i += 2) {
            map.put(keyValuePairs[i], keyValuePairs[i + 1]);
        }
        return map;
    }

    public Map<String, FileConfiguration> getLangConfigs() {
        return langConfigs;
    }
}
