package org.stepan1411.dialogAuth.config;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import org.stepan1411.dialogAuth.DialogAuth;

import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

public class ConfigManager {
    
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final Path CONFIG_DIR = Path.of("config", "dialogauth");
    private static final Path DIALOGS_DIR = CONFIG_DIR.resolve("dialogs");
    
    private static Config config;
    private static LangConfig lang;
    private static int minPasswordLength = 4;
    private static long configLastModified = 0;
    private static long langLastModified = 0;
    private static long dialogsLastModified = 0;
    
    public static void initialize() {
        try {
            // Создаем папку конфига если её нет
            Files.createDirectories(CONFIG_DIR);
            Files.createDirectories(DIALOGS_DIR);
            
            // Создаем config.json
            createConfigFile();
            
            // Создаем lang.json
            createLangFile();
            
            // Создаем файлы диалогов
            createDialogFiles();
            
            // Загружаем конфиг
            loadConfig();
            
            // Загружаем языковые файлы
            loadLang();
            
            // Сохраняем время модификации диалогов
            updateDialogsModificationTime();
            
        } catch (IOException e) {
            DialogAuth.LOGGER.error("Failed to create configuration files", e);
        }
    }
    
    public static ReloadResult reload() {
        boolean configChanged = false;
        boolean langChanged = false;
        boolean dialogsChanged = false;
        String error = null;
        
        try {
            // Проверяем изменения в config.json
            File configFile = CONFIG_DIR.resolve("config.json").toFile();
            if (configFile.exists() && configFile.lastModified() != configLastModified) {
                loadConfig();
                configChanged = true;
            }
            
            // Проверяем изменения в lang.json
            File langFile = CONFIG_DIR.resolve("lang.json").toFile();
            if (langFile.exists() && langFile.lastModified() != langLastModified) {
                loadLang();
                langChanged = true;
            }
            
            // Проверяем изменения в файлах диалогов
            long currentDialogsTime = getDialogsModificationTime();
            if (currentDialogsTime != dialogsLastModified) {
                dialogsChanged = true;
                dialogsLastModified = currentDialogsTime;
            }
            
        } catch (Exception e) {
            error = e.getMessage();
            DialogAuth.LOGGER.error("Failed to reload configuration", e);
        }
        
        return new ReloadResult(configChanged || langChanged, dialogsChanged, error);
    }
    
    private static void updateDialogsModificationTime() {
        dialogsLastModified = getDialogsModificationTime();
    }
    
    private static long getDialogsModificationTime() {
        try {
            if (!Files.exists(DIALOGS_DIR)) {
                return 0;
            }
            
            long maxTime = 0;
            try (var stream = Files.walk(DIALOGS_DIR)) {
                maxTime = stream
                    .filter(Files::isRegularFile)
                    .filter(p -> p.toString().endsWith(".json"))
                    .mapToLong(p -> {
                        try {
                            return Files.getLastModifiedTime(p).toMillis();
                        } catch (IOException e) {
                            return 0;
                        }
                    })
                    .max()
                    .orElse(0);
            }
            return maxTime;
        } catch (Exception e) {
            return 0;
        }
    }
    
    private static void loadConfig() {
        File configFile = CONFIG_DIR.resolve("config.json").toFile();
        
        if (!configFile.exists()) {
            DialogAuth.LOGGER.warn("Config file not found, using defaults");
            return;
        }
        
        try (FileReader reader = new FileReader(configFile)) {
            config = GSON.fromJson(reader, Config.class);
            if (config != null && config.authentication != null) {
                minPasswordLength = config.authentication.min_password_length;
            }
            configLastModified = configFile.lastModified();
        } catch (Exception e) {
            DialogAuth.LOGGER.error("Failed to load config file", e);
            throw new RuntimeException("Failed to load config.json: " + e.getMessage());
        }
    }
    
    private static void loadLang() {
        File langFile = CONFIG_DIR.resolve("lang.json").toFile();
        
        if (!langFile.exists()) {
            DialogAuth.LOGGER.warn("Lang file not found, using defaults");
            return;
        }
        
        try (FileReader reader = new FileReader(langFile)) {
            lang = GSON.fromJson(reader, LangConfig.class);
            langLastModified = langFile.lastModified();
        } catch (Exception e) {
            DialogAuth.LOGGER.error("Failed to load lang file", e);
            throw new RuntimeException("Failed to load lang.json: " + e.getMessage());
        }
    }
    
    public static String getMessage(String key, Object... args) {
        if (lang == null) return key;
        
        String message = getMessageByPath(key);
        if (message == null) return key;
        
        // Заменяем плейсхолдеры
        for (int i = 0; i < args.length; i += 2) {
            if (i + 1 < args.length) {
                String placeholder = "{" + args[i] + "}";
                String value = String.valueOf(args[i + 1]);
                message = message.replace(placeholder, value);
            }
        }
        
        return message;
    }
    
    private static String getMessageByPath(String path) {
        String[] parts = path.split("\\.");
        
        if (parts.length < 2) return null;
        
        try {
            if (parts[0].equals("command")) {
                if (parts[1].equals("register") && parts.length == 3) {
                    return switch (parts[2]) {
                        case "success" -> lang.command.register.success;
                        case "only_in_auth" -> lang.command.register.only_in_auth;
                        case "player_only" -> lang.command.register.player_only;
                        default -> null;
                    };
                } else if (parts[1].equals("leave") && parts.length == 3) {
                    return switch (parts[2]) {
                        case "disconnect_message" -> lang.command.leave.disconnect_message;
                        case "only_in_auth" -> lang.command.leave.only_in_auth;
                        case "player_only" -> lang.command.leave.player_only;
                        default -> null;
                    };
                } else if (parts[1].equals("reload") && parts.length == 3) {
                    return switch (parts[2]) {
                        case "success" -> lang.command.reload.success;
                        case "note" -> lang.command.reload.note;
                        case "failed" -> lang.command.reload.failed;
                        default -> null;
                    };
                }
            } else if (parts[0].equals("logging") && parts.length == 2) {
                return switch (parts[1]) {
                    case "player_registered" -> lang.logging.player_registered;
                    default -> null;
                };
            }
        } catch (Exception e) {
            return null;
        }
        
        return null;
    }
    
    public static int getMinPasswordLength() {
        return minPasswordLength;
    }
    
    public static Config getConfig() {
        return config;
    }
    
    private static void createConfigFile() throws IOException {
        File configFile = CONFIG_DIR.resolve("config.json").toFile();
        
        if (configFile.exists()) {
            return;
        }
        
        String configJson = """
{
  "authentication": {
    "min_password_length": 4,
    "max_password_length": 32,
    "enable_registration": true,
    "enable_login": true,
    "session_duration_hours": 12,
    "check_ip_address": true
  },
  "dimension": {
    "dimension_id": "dialog_auth:auth",
    "spawn_position": {
      "x": 0.5,
      "y": 65.0,
      "z": 0.5
    },
    "gamemode": "spectator",
    "prevent_mob_spawning": true
  },
  "dialogs": {
    "register_dialog": "dialog_auth:register",
    "error_dialog_mismatch": "dialog_auth:register_error",
    "error_dialog_short": "dialog_auth:register_short",
    "can_close_with_escape": false
  },
  "messages": {
    "registration_success": "§aSuccessfully registered!",
    "password_mismatch": "§cPasswords do not match!§r",
    "password_too_short": "§cPassword must be at least {min_length} characters long!§r",
    "returning_to_world": "Returning to world..."
  },
  "logging": {
    "log_registrations": true,
    "log_passwords": true,
    "log_mob_prevention": false,
    "log_sessions": true
  },
  "advanced": {
    "save_player_location": true,
    "restore_gamemode": true,
    "teleport_delay_ticks": 0
  }
}
""";
        
        try (FileWriter writer = new FileWriter(configFile)) {
            writer.write(configJson);
        }
    }
    
    private static void createLangFile() throws IOException {
        File langFile = CONFIG_DIR.resolve("lang.json").toFile();
        
        if (langFile.exists()) {
            return;
        }
        
        String langJson = """
{
  "command": {
    "register": {
      "success": "§aSuccessfully registered!",
      "only_in_auth": "§cThis command can only be used during authentication!",
      "player_only": "This command can only be used by players"
    },
    "leave": {
      "disconnect_message": "Disconnected",
      "only_in_auth": "§cThis command can only be used during authentication!",
      "player_only": "This command can only be used by players"
    },
    "reload": {
      "success": "§aConfiguration reloaded successfully!",
      "note": "§eNote: Dialog changes require server restart",
      "failed": "§cFailed to reload configuration: {error}"
    }
  },
  "logging": {
    "player_registered": "Registration successful"
  }
}
""";
        
        try (FileWriter writer = new FileWriter(langFile)) {
            writer.write(langJson);
        }
    }
    
    private static void createDialogFiles() throws IOException {
        // Создаем папку register
        Path registerDir = DIALOGS_DIR.resolve("register");
        Files.createDirectories(registerDir);
        
        // Создаем папку login
        Path loginDir = DIALOGS_DIR.resolve("login");
        Files.createDirectories(loginDir);
        
        // Создаем папку changepass
        Path changepassDir = DIALOGS_DIR.resolve("changepass");
        Files.createDirectories(changepassDir);
        
        // register/register.json
        createDialogFile(registerDir, "register.json", """
{
  "type": "minecraft:multi_action",
  "title": "Register",
  "body": {
    "type": "minecraft:plain_message",
    "contents": "Create your password"
  },
  "can_close_with_escape": false,
  "inputs": [
    {
      "type": "minecraft:text",
      "key": "pass1",
      "label": "Enter password"
    },
    {
      "type": "minecraft:text",
      "key": "pass2",
      "label": "Repeat password"
    }
  ],
  "exit_action": {
    "label": "Leave",
    "action": {
      "type": "minecraft:run_command",
      "command": "dialogauth leave"
    }
  },
  "actions": [
    {
      "label": "Register",
      "action": {
        "type": "dynamic/run_command",
        "template": "dialogauth register $(pass1) $(pass2)"
      }
    }
  ],
  "pause": false
}
""");
        
        // register/error.json
        createDialogFile(registerDir, "error.json", """
{
  "type": "minecraft:multi_action",
  "title": "Register",
  "body": {
    "type": "minecraft:plain_message",
    "contents": "§cPasswords do not match!§r\\n\\nPlease try again"
  },
  "can_close_with_escape": false,
  "inputs": [
    {
      "type": "minecraft:text",
      "key": "pass1",
      "label": "Enter password"
    },
    {
      "type": "minecraft:text",
      "key": "pass2",
      "label": "Repeat password"
    }
  ],
  "exit_action": {
    "label": "Leave",
    "action": {
      "type": "minecraft:run_command",
      "command": "dialogauth leave"
    }
  },
  "actions": [
    {
      "label": "Register",
      "action": {
        "type": "dynamic/run_command",
        "template": "dialogauth register $(pass1) $(pass2)"
      }
    }
  ],
  "pause": false
}
""");
        
        // register/short.json
        createDialogFile(registerDir, "short.json", """
{
  "type": "minecraft:multi_action",
  "title": "Register",
  "body": {
    "type": "minecraft:plain_message",
    "contents": "§cPassword must be at least 4 characters long!§r\\n\\nPlease try again"
  },
  "can_close_with_escape": false,
  "inputs": [
    {
      "type": "minecraft:text",
      "key": "pass1",
      "label": "Enter password"
    },
    {
      "type": "minecraft:text",
      "key": "pass2",
      "label": "Repeat password"
    }
  ],
  "exit_action": {
    "label": "Leave",
    "action": {
      "type": "minecraft:run_command",
      "command": "dialogauth leave"
    }
  },
  "actions": [
    {
      "label": "Register",
      "action": {
        "type": "dynamic/run_command",
        "template": "dialogauth register $(pass1) $(pass2)"
      }
    }
  ],
  "pause": false
}
""");
        
        // register/empty.json
        createDialogFile(registerDir, "empty.json", """
{
  "type": "minecraft:multi_action",
  "title": "Register",
  "body": {
    "type": "minecraft:plain_message",
    "contents": "§cPassword cannot be empty!§r\\n\\nPlease try again"
  },
  "can_close_with_escape": false,
  "inputs": [
    {
      "type": "minecraft:text",
      "key": "pass1",
      "label": "Enter password"
    },
    {
      "type": "minecraft:text",
      "key": "pass2",
      "label": "Repeat password"
    }
  ],
  "exit_action": {
    "label": "Leave",
    "action": {
      "type": "minecraft:run_command",
      "command": "dialogauth leave"
    }
  },
  "actions": [
    {
      "label": "Register",
      "action": {
        "type": "dynamic/run_command",
        "template": "dialogauth register $(pass1) $(pass2)"
      }
    }
  ],
  "pause": false
}
""");
        
        // login/login.json
        createDialogFile(loginDir, "login.json", """
{
  "type": "minecraft:multi_action",
  "title": "Login",
  "body": {
    "type": "minecraft:plain_message",
    "contents": "Enter your password"
  },
  "can_close_with_escape": false,
  "inputs": [
    {
      "type": "minecraft:text",
      "key": "pass",
      "label": "Enter password"
    }
  ],
  "exit_action": {
    "label": "Leave",
    "action": {
      "type": "minecraft:run_command",
      "command": "dialogauth leave"
    }
  },
  "actions": [
    {
      "label": "Login",
      "action": {
        "type": "dynamic/run_command",
        "template": "dialogauth login $(pass)"
      }
    }
  ],
  "pause": false
}
""");
        
        // login/error.json
        createDialogFile(loginDir, "error.json", """
{
  "type": "minecraft:multi_action",
  "title": "Login",
  "body": {
    "type": "minecraft:plain_message",
    "contents": "§cIncorrect password!§r\\n\\nPlease try again"
  },
  "can_close_with_escape": false,
  "inputs": [
    {
      "type": "minecraft:text",
      "key": "pass",
      "label": "Enter password"
    }
  ],
  "exit_action": {
    "label": "Leave",
    "action": {
      "type": "minecraft:run_command",
      "command": "dialogauth leave"
    }
  },
  "actions": [
    {
      "label": "Login",
      "action": {
        "type": "dynamic/run_command",
        "template": "dialogauth login $(pass)"
      }
    }
  ],
  "pause": false
}
""");
        
        // login/empty.json
        createDialogFile(loginDir, "empty.json", """
{
  "type": "minecraft:multi_action",
  "title": "Login",
  "body": {
    "type": "minecraft:plain_message",
    "contents": "§cPassword cannot be empty!§r\\n\\nPlease try again"
  },
  "can_close_with_escape": false,
  "inputs": [
    {
      "type": "minecraft:text",
      "key": "pass",
      "label": "Enter password"
    }
  ],
  "exit_action": {
    "label": "Leave",
    "action": {
      "type": "minecraft:run_command",
      "command": "dialogauth leave"
    }
  },
  "actions": [
    {
      "label": "Login",
      "action": {
        "type": "dynamic/run_command",
        "template": "dialogauth login $(pass)"
      }
    }
  ],
  "pause": false
}
""");
        
        // login/short.json
        createDialogFile(loginDir, "short.json", """
{
  "type": "minecraft:multi_action",
  "title": "Login",
  "body": {
    "type": "minecraft:plain_message",
    "contents": "§cPassword must be at least 4 characters long!§r\\n\\nPlease try again"
  },
  "can_close_with_escape": false,
  "inputs": [
    {
      "type": "minecraft:text",
      "key": "pass",
      "label": "Enter password"
    }
  ],
  "exit_action": {
    "label": "Leave",
    "action": {
      "type": "minecraft:run_command",
      "command": "dialogauth leave"
    }
  },
  "actions": [
    {
      "label": "Login",
      "action": {
        "type": "dynamic/run_command",
        "template": "dialogauth login $(pass)"
      }
    }
  ],
  "pause": false
}
""");
        
        // changepass/changepass.json
        createDialogFile(changepassDir, "changepass.json", """
{
  "type": "minecraft:multi_action",
  "title": "Change password",
  "body": {
    "type": "minecraft:plain_message",
    "contents": "Enter your old and new password"
  },
  "can_close_with_escape": false,
  "inputs": [
    {
      "type": "minecraft:text",
      "key": "oldpass",
      "label": "Enter old password"
    },
    {
      "type": "minecraft:text",
      "key": "newpass",
      "label": "Enter new password"
    }
  ],
  "exit_action": {
    "label": "Cancel",
    "action": {
      "type": "minecraft:run_command",
      "command": "dialogauth leavechangepass"
    }
  },
  "actions": [
    {
      "label": "Change",
      "action": {
        "type": "dynamic/run_command",
        "template": "dialogauth changepassdialog $(oldpass) $(newpass)"
      }
    }
  ],
  "pause": false
}
""");
        
        // changepass/error_oldpass.json
        createDialogFile(changepassDir, "error_oldpass.json", """
{
  "type": "minecraft:multi_action",
  "title": "Change password",
  "body": {
    "type": "minecraft:plain_message",
    "contents": "§cIncorrect old password!§r\\n\\nPlease try again"
  },
  "can_close_with_escape": false,
  "inputs": [
    {
      "type": "minecraft:text",
      "key": "oldpass",
      "label": "Enter old password"
    },
    {
      "type": "minecraft:text",
      "key": "newpass",
      "label": "Enter new password"
    }
  ],
  "exit_action": {
    "label": "Cancel",
    "action": {
      "type": "minecraft:run_command",
      "command": "dialogauth leavechangepass"
    }
  },
  "actions": [
    {
      "label": "Change",
      "action": {
        "type": "dynamic/run_command",
        "template": "dialogauth changepassdialog $(oldpass) $(newpass)"
      }
    }
  ],
  "pause": false
}
""");
        
        // changepass/short.json
        createDialogFile(changepassDir, "short.json", """
{
  "type": "minecraft:multi_action",
  "title": "Change password",
  "body": {
    "type": "minecraft:plain_message",
    "contents": "§cNew password must be at least 4 characters long!§r\\n\\nPlease try again"
  },
  "can_close_with_escape": false,
  "inputs": [
    {
      "type": "minecraft:text",
      "key": "oldpass",
      "label": "Enter old password"
    },
    {
      "type": "minecraft:text",
      "key": "newpass",
      "label": "Enter new password"
    }
  ],
  "exit_action": {
    "label": "Cancel",
    "action": {
      "type": "minecraft:run_command",
      "command": "dialogauth leavechangepass"
    }
  },
  "actions": [
    {
      "label": "Change",
      "action": {
        "type": "dynamic/run_command",
        "template": "dialogauth changepassdialog $(oldpass) $(newpass)"
      }
    }
  ],
  "pause": false
}
""");
        
        // changepass/empty.json
        createDialogFile(changepassDir, "empty.json", """
{
  "type": "minecraft:multi_action",
  "title": "Change password",
  "body": {
    "type": "minecraft:plain_message",
    "contents": "§cPasswords cannot be empty!§r\\n\\nPlease try again"
  },
  "can_close_with_escape": false,
  "inputs": [
    {
      "type": "minecraft:text",
      "key": "oldpass",
      "label": "Enter old password"
    },
    {
      "type": "minecraft:text",
      "key": "newpass",
      "label": "Enter new password"
    }
  ],
  "exit_action": {
    "label": "Cancel",
    "action": {
      "type": "minecraft:run_command",
      "command": "dialogauth leavechangepass"
    }
  },
  "actions": [
    {
      "label": "Change",
      "action": {
        "type": "dynamic/run_command",
        "template": "dialogauth changepassdialog $(oldpass) $(newpass)"
      }
    }
  ],
  "pause": false
}
""");
        
        // changepass/same.json
        createDialogFile(changepassDir, "same.json", """
{
  "type": "minecraft:multi_action",
  "title": "Change password",
  "body": {
    "type": "minecraft:plain_message",
    "contents": "§cNew password must be different from old password!§r\\n\\nPlease try again"
  },
  "can_close_with_escape": false,
  "inputs": [
    {
      "type": "minecraft:text",
      "key": "oldpass",
      "label": "Enter old password"
    },
    {
      "type": "minecraft:text",
      "key": "newpass",
      "label": "Enter new password"
    }
  ],
  "exit_action": {
    "label": "Cancel",
    "action": {
      "type": "minecraft:run_command",
      "command": "dialogauth leavechangepass"
    }
  },
  "actions": [
    {
      "label": "Change",
      "action": {
        "type": "dynamic/run_command",
        "template": "dialogauth changepassdialog $(oldpass) $(newpass)"
      }
    }
  ],
  "pause": false
}
""");
    }
    
    private static void createDialogFile(Path dir, String filename, String content) throws IOException {
        File dialogFile = dir.resolve(filename).toFile();
        
        if (dialogFile.exists()) {
            return;
        }
        
        try (FileWriter writer = new FileWriter(dialogFile)) {
            writer.write(content);
        }
    }
    
    // Классы для десериализации конфига
    public static class Config {
        public Authentication authentication;
        public Dimension dimension;
        public Dialogs dialogs;
        public Messages messages;
        public Logging logging;
        public Advanced advanced;
    }
    
    public static class Authentication {
        public int min_password_length = 4;
        public int max_password_length = 32;
        public boolean enable_registration = true;
        public boolean enable_login = true;
        public int session_duration_hours = 12;
        public boolean check_ip_address = true;
    }
    
    public static class Dimension {
        public String dimension_id = "dialog_auth:auth";
        public SpawnPosition spawn_position = new SpawnPosition();
        public String gamemode = "spectator";
        public boolean prevent_mob_spawning = true;
    }
    
    public static class SpawnPosition {
        public double x = 0.5;
        public double y = 65.0;
        public double z = 0.5;
    }
    
    public static class Dialogs {
        public String register_dialog = "dialog_auth:register";
        public String error_dialog_mismatch = "dialog_auth:register_error";
        public String error_dialog_short = "dialog_auth:register_short";
        public boolean can_close_with_escape = false;
    }
    
    public static class Messages {
        public String registration_success = "§aSuccessfully registered with password: §e{password}";
        public String password_mismatch = "§cPasswords do not match!§r";
        public String password_too_short = "§cPassword must be at least {min_length} characters long!§r";
        public String returning_to_world = "Returning to world...";
    }
    
    public static class Logging {
        public boolean log_registrations = true;
        public boolean log_passwords = true;
        public boolean log_mob_prevention = false;
        public boolean log_sessions = true;
    }
    
    public static class Advanced {
        public boolean save_player_location = true;
        public boolean restore_gamemode = true;
        public int teleport_delay_ticks = 0;
    }
    
    // Классы для lang.json
    public static class LangConfig {
        public CommandLang command = new CommandLang();
        public LoggingLang logging = new LoggingLang();
    }
    
    public static class CommandLang {
        public RegisterCommand register = new RegisterCommand();
        public LeaveCommand leave = new LeaveCommand();
        public ReloadCommand reload = new ReloadCommand();
    }
    
    public static class RegisterCommand {
        public String success = "§aSuccessfully registered!";
        public String only_in_auth = "§cThis command can only be used during authentication!";
        public String player_only = "This command can only be used by players";
    }
    
    public static class LeaveCommand {
        public String disconnect_message = "You chose to leave";
        public String only_in_auth = "§cThis command can only be used during authentication!";
        public String player_only = "This command can only be used by players";
    }
    
    public static class ReloadCommand {
        public String success = "§aConfiguration reloaded successfully!";
        public String note = "§eNote: Dialog changes require server restart";
        public String failed = "§cFailed to reload configuration: {error}";
    }
    
    public static class LoggingLang {
        public String player_registered = "Player {player} registered with password: {password}";
    }
    
    // Результат перезагрузки
    public static class ReloadResult {
        public final boolean configChanged;
        public final boolean dialogsChanged;
        public final String error;
        
        public ReloadResult(boolean configChanged, boolean dialogsChanged, String error) {
            this.configChanged = configChanged;
            this.dialogsChanged = dialogsChanged;
            this.error = error;
        }
        
        public boolean hasError() {
            return error != null;
        }
        
        public boolean hasChanges() {
            return configChanged || dialogsChanged;
        }
    }
}
