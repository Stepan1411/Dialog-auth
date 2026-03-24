package org.stepan1411.dialogAuth.storage;

import at.favre.lib.crypto.bcrypt.BCrypt;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.reflect.TypeToken;
import org.stepan1411.dialogAuth.DialogAuth;

import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public class PasswordStorage {
    
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final Path STORAGE_DIR = Path.of("config", "dialogauth");
    private static final Path PASSWORDS_FILE = STORAGE_DIR.resolve("passwords.json");
    private static final int BCRYPT_COST = 12; // Стоимость хеширования (чем выше, тем безопаснее, но медленнее)
    
    private static Map<String, PlayerData> passwords = new HashMap<>();
    
    public static void initialize() {
        try {
            Files.createDirectories(STORAGE_DIR);
            loadPasswords();
        } catch (IOException e) {
            DialogAuth.LOGGER.error("Failed to initialize password storage", e);
        }
    }
    
    private static void loadPasswords() {
        File file = PASSWORDS_FILE.toFile();
        
        if (!file.exists()) {
            passwords = new HashMap<>();
            savePasswords();
            return;
        }
        
        try (FileReader reader = new FileReader(file)) {
            TypeToken<Map<String, PlayerData>> typeToken = new TypeToken<>() {};
            Map<String, PlayerData> loaded = GSON.fromJson(reader, typeToken.getType());
            passwords = loaded != null ? loaded : new HashMap<>();
        } catch (Exception e) {
            DialogAuth.LOGGER.error("Failed to load passwords", e);
            passwords = new HashMap<>();
        }
    }
    
    private static void savePasswords() {
        try (FileWriter writer = new FileWriter(PASSWORDS_FILE.toFile())) {
            GSON.toJson(passwords, writer);
        } catch (IOException e) {
            DialogAuth.LOGGER.error("Failed to save passwords", e);
        }
    }
    
    public static void registerPlayer(String username, UUID uuid, String password) {
        // Хешируем пароль с помощью BCrypt
        String hashedPassword = BCrypt.withDefaults().hashToString(BCRYPT_COST, password.toCharArray());
        
        PlayerData data = new PlayerData(uuid.toString(), hashedPassword);
        passwords.put(username.toLowerCase(), data);
        savePasswords();
        DialogAuth.LOGGER.info("Player {} registered", username);
    }
    
    public static boolean isPlayerRegistered(String username) {
        return passwords.containsKey(username.toLowerCase());
    }
    
    public static boolean checkPassword(String username, String password) {
        PlayerData data = passwords.get(username.toLowerCase());
        if (data == null) {
            return false;
        }
        
        // Проверяем пароль с помощью BCrypt
        BCrypt.Result result = BCrypt.verifyer().verify(password.toCharArray(), data.passwordHash);
        return result.verified;
    }
    
    public static void changePassword(String username, String newPassword) {
        PlayerData data = passwords.get(username.toLowerCase());
        if (data != null) {
            // Хешируем новый пароль
            String hashedPassword = BCrypt.withDefaults().hashToString(BCRYPT_COST, newPassword.toCharArray());
            data.passwordHash = hashedPassword;
            savePasswords();
            DialogAuth.LOGGER.info("Player {} changed password", username);
        }
    }
    
    public static class PlayerData {
        public String uuid;
        public String passwordHash; // Теперь храним хеш, а не пароль
        
        public PlayerData(String uuid, String passwordHash) {
            this.uuid = uuid;
            this.passwordHash = passwordHash;
        }
    }
}
