package dev.msdrop;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import dev.msdrop.data.DataManager;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URI;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Locale;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;
import java.util.regex.Pattern;

public class UpdateChecker {

    private static final Pattern REPOSITORY = Pattern.compile("[\\w.-]+/[\\w.-]+");
    private static final Pattern SHA256 = Pattern.compile("sha256:([a-fA-F0-9]{64})");
    private static final Pattern PLUGIN_NAME = Pattern.compile("(?m)^name:\\s*['\"]?mSDrop['\"]?\\s*$");
    private static final Pattern PLUGIN_MAIN = Pattern.compile("(?m)^main:\\s*['\"]?dev\\.msdrop\\.mSDrop['\"]?\\s*$");

    private static final int MAX_REDIRECTS = 5;
    private static final long MAX_JAR_BYTES = 32L * 1024 * 1024;
    private static final int MAX_API_BYTES = 2 * 1024 * 1024;

    private final mSDrop plugin;
    private final Object lock = new Object();

    private volatile String latestVersion;
    private volatile boolean downloaded;
    private String downloadUrl;
    private String digest;

    public UpdateChecker(mSDrop plugin) {
        this.plugin = plugin;
    }

    public boolean isConfigured() {
        return isValidRepository(repository());
    }

    static boolean isValidRepository(String repository) {
        if (!REPOSITORY.matcher(repository).matches())
            return false;
        for (String part : repository.split("/"))
            if (part.replace(".", "").isEmpty())
                return false;
        return true;
    }

    public boolean check() throws IOException {
        synchronized (lock) {
            String body = new String(read(
                    "https://api.github.com/repos/" + repository() + "/releases/latest",
                    "application/vnd.github+json", MAX_API_BYTES), StandardCharsets.UTF_8);

            Release release = parse(body, repository());

            if (!release.version.equals(latestVersion))
                downloaded = false;

            latestVersion = release.version;
            downloadUrl = release.url;
            digest = release.digest;

            return isUpdateAvailable();
        }
    }

    /**
     * Reads the release json from the GitHub API. Package-private so it can be tested without network.
     */
    static Release parse(String body, String repository) throws IOException {
        JsonObject root;
        try {
            JsonElement element = JsonParser.parseString(body);
            if (!element.isJsonObject())
                throw new IOException("Unexpected answer from GitHub");
            root = element.getAsJsonObject();
        } catch (RuntimeException e) {
            throw new IOException("Unexpected answer from GitHub", e);
        }

        if (isTrue(root, "draft") || isTrue(root, "prerelease"))
            throw new IOException("Latest release is a draft or a pre-release");

        String tag = string(root, "tag_name");
        if (tag == null)
            throw new IOException("Release without a version");

        String version = tag.replaceFirst("^[vV]", "");
        if (!version.matches("[0-9A-Za-z.+-]{1,64}"))
            throw new IOException("Release with an invalid version");

        JsonElement assets = root.get("assets");
        if (assets == null || !assets.isJsonArray())
            throw new IOException("Release without a jar file");

        JsonArray list = assets.getAsJsonArray();
        for (JsonElement item : list) {
            if (!item.isJsonObject())
                continue;

            JsonObject asset = item.getAsJsonObject();
            String name = string(asset, "name");
            String url = string(asset, "browser_download_url");
            if (name == null || url == null || !name.equals("mSDrop-" + version + ".jar"))
                continue;
            if (!isTrustedAssetUrl(url, repository, name))
                continue;

            String hash = null;
            String raw = string(asset, "digest");
            if (raw != null) {
                java.util.regex.Matcher matcher = SHA256.matcher(raw);
                if (matcher.matches())
                    hash = matcher.group(1).toLowerCase(Locale.ROOT);
            }
            return new Release(version, url, hash);
        }
        throw new IOException("Release without a mSDrop jar file");
    }

    private static boolean isTrustedAssetUrl(String address, String repository, String name) {
        try {
            URL url = URI.create(address).toURL();
            return url.getProtocol().equals("https")
                    && url.getHost().equals("github.com")
                    && url.getUserInfo() == null
                    && (url.getPort() == -1 || url.getPort() == 443)
                    && url.getPath().startsWith("/" + repository + "/releases/download/")
                    && url.getPath().endsWith("/" + name);
        } catch (IOException | IllegalArgumentException e) {
            return false;
        }
    }

    private static String string(JsonObject object, String key) {
        JsonElement element = object.get(key);
        if (element == null || !element.isJsonPrimitive() || !element.getAsJsonPrimitive().isString())
            return null;
        return element.getAsString();
    }

    private static boolean isTrue(JsonObject object, String key) {
        JsonElement element = object.get(key);
        return element != null && element.isJsonPrimitive() && element.getAsJsonPrimitive().isBoolean() && element.getAsBoolean();
    }

    public void download() throws IOException {
        synchronized (lock) {
            if (!isUpdateAvailable() || downloaded)
                return;

            if (digest == null)
                throw new IOException("The release has no SHA-256 digest, refusing to install it");

            File folder = plugin.getDataFolder();
            folder.mkdirs();
            File temp = new File(folder, "update.tmp");

            try {
                fetch(downloadUrl, temp);
                verify(temp);
                backup();

                File updateFolder = plugin.getServer().getUpdateFolderFile();
                updateFolder.mkdirs();
                Files.move(temp.toPath(), new File(updateFolder, plugin.getJarName()).toPath(), StandardCopyOption.REPLACE_EXISTING);
            } finally {
                Files.deleteIfExists(temp.toPath());
            }
            downloaded = true;
        }
    }

    public boolean isUpdateAvailable() {
        String latest = latestVersion;
        return latest != null && isNewer(latest, plugin.getDescription().getVersion());
    }

    public boolean isDownloaded() {
        return downloaded;
    }

    public String getLatestVersion() {
        return latestVersion;
    }

    private String repository() {
        String repository = DataManager.getInstance().getConfig().getString("update.repository");
        return repository == null ? "" : repository.trim();
    }

    private byte[] read(String address, String accept, int limit) throws IOException {
        HttpURLConnection connection = open(URI.create(address).toURL(), accept, 10000);
        try {
            if (connection.getResponseCode() != HttpURLConnection.HTTP_OK)
                throw new IOException("GitHub answered with HTTP " + connection.getResponseCode());
        } catch (IOException e) {
            connection.disconnect();
            throw e;
        }
        try (InputStream in = connection.getInputStream()) {
            byte[] data = in.readNBytes(limit + 1);
            if (data.length > limit)
                throw new IOException("Answer from GitHub is too large");
            return data;
        } finally {
            connection.disconnect();
        }
    }

    /**
     * Downloads a file following redirects by hand, so every hop is checked to stay on GitHub over https.
     */
    private void fetch(String address, File target) throws IOException {
        URL url = URI.create(address).toURL();

        for (int hop = 0; hop <= MAX_REDIRECTS; hop++) {
            if (!isGithubHost(url))
                throw new IOException("Untrusted download url: " + url.getHost());

            HttpURLConnection connection = open(url, "application/octet-stream", 20000);
            int code = connection.getResponseCode();

            if (code == HttpURLConnection.HTTP_MOVED_PERM || code == HttpURLConnection.HTTP_MOVED_TEMP
                    || code == HttpURLConnection.HTTP_SEE_OTHER || code == 307 || code == 308) {
                String location = connection.getHeaderField("Location");
                connection.disconnect();
                if (location == null)
                    throw new IOException("Redirect without a location");
                try {
                    url = URI.create(url.toString()).resolve(location).toURL();
                } catch (IllegalArgumentException e) {
                    throw new IOException("Invalid redirect location", e);
                }
                continue;
            }

            if (code != HttpURLConnection.HTTP_OK) {
                connection.disconnect();
                throw new IOException("GitHub answered with HTTP " + code);
            }

            try (InputStream in = connection.getInputStream();
                 OutputStream out = Files.newOutputStream(target.toPath())) {
                byte[] buffer = new byte[8192];
                long total = 0;
                int read;
                while ((read = in.read(buffer)) != -1) {
                    total += read;
                    if (total > MAX_JAR_BYTES)
                        throw new IOException("Download is too large");
                    out.write(buffer, 0, read);
                }
            } finally {
                connection.disconnect();
            }
            return;
        }
        throw new IOException("Too many redirects");
    }

    private static boolean isGithubHost(URL url) {
        String host = url.getHost().toLowerCase(Locale.ROOT);
        return url.getProtocol().equals("https")
                && url.getUserInfo() == null
                && (url.getPort() == -1 || url.getPort() == 443)
                && (host.equals("github.com") || host.endsWith(".githubusercontent.com"));
    }

    private HttpURLConnection open(URL url, String accept, int timeout) throws IOException {
        HttpURLConnection connection = (HttpURLConnection) url.openConnection();
        connection.setInstanceFollowRedirects(false);
        connection.setRequestProperty("User-Agent", "mSDrop/" + plugin.getDescription().getVersion());
        connection.setRequestProperty("Accept", accept);
        connection.setConnectTimeout(5000);
        connection.setReadTimeout(timeout);

        int code = connection.getResponseCode();
        if (code == HttpURLConnection.HTTP_NOT_FOUND) {
            connection.disconnect();
            throw new IOException("Not found, the repository must be public and have a release");
        }
        if (code != HttpURLConnection.HTTP_OK && !isRedirect(code)) {
            connection.disconnect();
            throw new IOException("GitHub answered with HTTP " + code);
        }
        return connection;
    }

    private static boolean isRedirect(int code) {
        return code == HttpURLConnection.HTTP_MOVED_PERM || code == HttpURLConnection.HTTP_MOVED_TEMP
                || code == HttpURLConnection.HTTP_SEE_OTHER || code == 307 || code == 308;
    }

    private void backup() throws IOException {
        File folder = new File(plugin.getDataFolder(), "backups");
        folder.mkdirs();

        File target = new File(folder, "mSDrop-" + plugin.getDescription().getVersion() + ".jar");
        if (!target.exists())
            Files.copy(plugin.getJarFile().toPath(), target.toPath());
    }

    private void verify(File file) throws IOException {
        if (!digest.equalsIgnoreCase(sha256(file)))
            throw new IOException("Checksum does not match the release");

        try (JarFile jar = new JarFile(file)) {
            JarEntry entry = jar.getJarEntry("plugin.yml");
            if (entry == null)
                throw new IOException("Downloaded file has no plugin.yml");
            if (entry.getSize() > 64 * 1024)
                throw new IOException("Downloaded plugin.yml is too large");

            try (InputStream in = jar.getInputStream(entry)) {
                String yml = new String(in.readNBytes(64 * 1024), StandardCharsets.UTF_8);
                if (!PLUGIN_NAME.matcher(yml).find() || !PLUGIN_MAIN.matcher(yml).find())
                    throw new IOException("Downloaded file is not mSDrop");
            }
        }
    }

    private String sha256(File file) throws IOException {
        try {
            MessageDigest sha = MessageDigest.getInstance("SHA-256");
            try (InputStream in = Files.newInputStream(file.toPath())) {
                byte[] buffer = new byte[8192];
                int read;
                while ((read = in.read(buffer)) != -1)
                    sha.update(buffer, 0, read);
            }
            StringBuilder hex = new StringBuilder();
            for (byte b : sha.digest())
                hex.append(String.format("%02x", b));
            return hex.toString();
        } catch (NoSuchAlgorithmException e) {
            throw new IOException(e);
        }
    }

    static boolean isNewer(String latest, String current) {
        String[] a = numbers(latest);
        String[] b = numbers(current);

        for (int i = 0; i < Math.max(a.length, b.length); i++) {
            int x = number(a, i);
            int y = number(b, i);
            if (x != y)
                return x > y;
        }
        return current.contains("-") && !latest.contains("-");
    }

    private static String[] numbers(String version) {
        return version.replaceFirst("^[vV]", "").split("[^0-9.]", 2)[0].split("\\.");
    }

    private static int number(String[] parts, int index) {
        if (index >= parts.length || parts[index].isEmpty())
            return 0;
        try {
            return Integer.parseInt(parts[index]);
        } catch (NumberFormatException e) {
            return 0;
        }
    }

    static final class Release {
        final String version;
        final String url;
        final String digest;

        Release(String version, String url, String digest) {
            this.version = version;
            this.url = url;
            this.digest = digest;
        }
    }

}
