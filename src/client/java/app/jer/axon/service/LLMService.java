package app.jer.axon.service;

import io.github.sashirestela.openai.SimpleOpenAIGeminiGoogle;
import net.fabricmc.loader.api.FabricLoader;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Properties;

public class LLMService {
    private static final Path CONFIG_PATH = FabricLoader.getInstance()
            .getGameDir()
            .resolve("axon/llm.properties");
    private static final String KEY = "apiKey";
    private static @Nullable SimpleOpenAIGeminiGoogle api = null;

    public static @Nullable SimpleOpenAIGeminiGoogle getApi() {
        return api;
    }

    public static void setKey(String key) {
        api = getAPI(key);
        storeKey(key);
    }

    public static void initialize() {
        api = getAPI(loadKey());
    }

    private static @Nullable SimpleOpenAIGeminiGoogle getAPI(@Nullable String key) {
        if (key == null || key.isEmpty()) {
            api = null;
            return null;
        }
        return SimpleOpenAIGeminiGoogle.builder()
                .apiKey(key)
                .build();
    }

    private static void storeKey(@Nullable String apiKey) {
        Properties props = new Properties();

        // If a file already exists, load existing properties
        if (Files.exists(CONFIG_PATH)) {
            try (Reader r = Files.newBufferedReader(CONFIG_PATH)) {
                props.load(r);
            } catch (IOException ignored) {
            }
        }

        if (apiKey != null) {
            props.setProperty(KEY, apiKey);
        } else {
            props.remove(KEY);
        }

        // Ensure parent directories exist before writing
        try {
            Files.createDirectories(CONFIG_PATH.getParent());
            try (Writer w = Files.newBufferedWriter(CONFIG_PATH)) {
                props.store(w, "LLM API Key");
            }
        } catch (IOException ignored) {
        }
    }

    @Nullable
    private static String loadKey() {
        if (Files.notExists(CONFIG_PATH)) {
            return null;
        }

        Properties props = new Properties();
        try (Reader r = Files.newBufferedReader(CONFIG_PATH)) {
            props.load(r);
        } catch (IOException e) {
            return null;
        }

        return props.getProperty(KEY);
    }
}
