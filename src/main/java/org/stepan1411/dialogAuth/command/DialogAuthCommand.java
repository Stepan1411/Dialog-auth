package org.stepan1411.dialogAuth.command;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.server.command.CommandManager;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;
import org.stepan1411.dialogAuth.DialogAuth;
import org.stepan1411.dialogAuth.config.ConfigManager;
import org.stepan1411.dialogAuth.storage.PasswordStorage;

public class DialogAuthCommand {
    
    public static void register(CommandDispatcher<ServerCommandSource> dispatcher) {
        dispatcher.register(
            CommandManager.literal("dialogauth")
                .then(CommandManager.literal("register")
                    .executes(DialogAuthCommand::executeRegisterNoArgs) // Без аргументов - открыть диалог
                    .then(CommandManager.argument("passwords", StringArgumentType.greedyString())
                        .executes(DialogAuthCommand::executeRegisterGreedy)
                    )
                )
                .then(CommandManager.literal("login")
                    .executes(DialogAuthCommand::executeLoginNoArgs) // Без аргументов - открыть диалог
                    .then(CommandManager.argument("password", StringArgumentType.greedyString())
                        .executes(DialogAuthCommand::executeLogin)
                    )
                )
                .then(CommandManager.literal("leave")
                    .executes(DialogAuthCommand::executeLeave)
                )
                .then(CommandManager.literal("changepass")
                    .executes(DialogAuthCommand::executeChangePass)
                )
                .then(CommandManager.literal("changepassdialog")
                    .executes(DialogAuthCommand::executeChangePassDialogNoArgs) // Без аргументов - открыть диалог
                    .then(CommandManager.argument("passwords", StringArgumentType.greedyString())
                        .executes(DialogAuthCommand::executeChangePassDialog)
                    )
                )
                .then(CommandManager.literal("leavechangepass")
                    .executes(DialogAuthCommand::executeLeaveChangePass)
                )
                .then(CommandManager.literal("reload")
                    .executes(DialogAuthCommand::executeReload)
                )
        );
    }
    
    private static int executeRegisterGreedy(CommandContext<ServerCommandSource> context) {
        ServerCommandSource source = context.getSource();
        
        if (!(source.getEntity() instanceof ServerPlayerEntity player)) {
            source.sendError(Text.literal("This command can only be used by players"));
            return 0;
        }
        
        // Проверяем, что игрок в auth измерении
        if (!DialogAuth.isPlayerInAuthDimension(player, source.getServer())) {
            source.sendError(Text.literal("§cThis command can only be used during authentication!"));
            return 0;
        }
        
        String passwords = StringArgumentType.getString(context, "passwords");
        String[] parts = passwords.split(" ", 2);
        
        // Если меньше 2 частей, значит не хватает аргументов
        if (parts.length < 2) {
            // Открываем диалог снова
            Identifier dialogId = Identifier.of(DialogAuth.MOD_ID, "register");
            var dialogRegistry = source.getServer().getRegistryManager().getOrThrow(RegistryKeys.DIALOG);
            var dialogEntry = dialogRegistry.getEntry(dialogId);
            
            if (dialogEntry.isPresent()) {
                player.openDialog(dialogEntry.get());
            }
            return 0;
        }
        
        String password1 = parts[0].trim();
        String password2 = parts[1].trim();
        
        return processRegistration(source, player, password1, password2);
    }
    
    private static int executeRegisterNoArgs(CommandContext<ServerCommandSource> context) {
        ServerCommandSource source = context.getSource();
        
        if (!(source.getEntity() instanceof ServerPlayerEntity player)) {
            source.sendError(Text.literal("This command can only be used by players"));
            return 0;
        }
        
        // Проверяем, что игрок в auth измерении
        if (!DialogAuth.isPlayerInAuthDimension(player, source.getServer())) {
            source.sendError(Text.literal("§cThis command can only be used during authentication!"));
            return 0;
        }
        
        // Открываем диалог регистрации снова
        Identifier dialogId = Identifier.of(DialogAuth.MOD_ID, "register");
        var dialogRegistry = source.getServer().getRegistryManager().getOrThrow(RegistryKeys.DIALOG);
        var dialogEntry = dialogRegistry.getEntry(dialogId);
        
        if (dialogEntry.isPresent()) {
            player.openDialog(dialogEntry.get());
        } else {
            source.sendError(Text.literal("Registration dialog not found!"));
        }
        
        return 1;
    }
    
    private static int executeLoginNoArgs(CommandContext<ServerCommandSource> context) {
        ServerCommandSource source = context.getSource();
        
        if (!(source.getEntity() instanceof ServerPlayerEntity player)) {
            source.sendError(Text.literal("This command can only be used by players"));
            return 0;
        }
        
        // Проверяем, что игрок в auth измерении
        if (!DialogAuth.isPlayerInAuthDimension(player, source.getServer())) {
            source.sendError(Text.literal("§cThis command can only be used during authentication!"));
            return 0;
        }
        
        // Открываем диалог логина снова
        Identifier dialogId = Identifier.of(DialogAuth.MOD_ID, "login");
        var dialogRegistry = source.getServer().getRegistryManager().getOrThrow(RegistryKeys.DIALOG);
        var dialogEntry = dialogRegistry.getEntry(dialogId);
        
        if (dialogEntry.isPresent()) {
            player.openDialog(dialogEntry.get());
        } else {
            source.sendError(Text.literal("Login dialog not found!"));
        }
        
        return 1;
    }
    
    private static int executeLogin(CommandContext<ServerCommandSource> context) {
        ServerCommandSource source = context.getSource();
        
        if (!(source.getEntity() instanceof ServerPlayerEntity player)) {
            source.sendError(Text.literal("This command can only be used by players"));
            return 0;
        }
        
        // Проверяем, что игрок в auth измерении
        if (!DialogAuth.isPlayerInAuthDimension(player, source.getServer())) {
            source.sendError(Text.literal("§cThis command can only be used during authentication!"));
            return 0;
        }
        
        String password = StringArgumentType.getString(context, "password");
        
        // Убираем _ в конце если он есть (placeholder из диалога)
        if (password.endsWith("_")) {
            password = password.substring(0, password.length() - 1);
        }
        
        password = password.trim();
        
        // Если пароль пустой после trim, показываем диалог с ошибкой
        if (password.isEmpty()) {
            Identifier dialogId = Identifier.of(DialogAuth.MOD_ID, "login_empty");
            var dialogRegistry = source.getServer().getRegistryManager().getOrThrow(RegistryKeys.DIALOG);
            var dialogEntry = dialogRegistry.getEntry(dialogId);
            
            if (dialogEntry.isPresent()) {
                player.openDialog(dialogEntry.get());
            }
            return 0;
        }
        
        // Проверяем длину пароля
        int minLength = ConfigManager.getMinPasswordLength();
        if (password.length() < minLength) {
            Identifier dialogId = Identifier.of(DialogAuth.MOD_ID, "login_short");
            var dialogRegistry = source.getServer().getRegistryManager().getOrThrow(RegistryKeys.DIALOG);
            var dialogEntry = dialogRegistry.getEntry(dialogId);
            
            if (dialogEntry.isPresent()) {
                player.openDialog(dialogEntry.get());
            }
            return 0;
        }
        
        // Проверяем пароль
        String username = player.getName().getString();
        if (!PasswordStorage.checkPassword(username, password)) {
            // Неправильный пароль
            Identifier dialogId = Identifier.of(DialogAuth.MOD_ID, "login_error");
            var dialogRegistry = source.getServer().getRegistryManager().getOrThrow(RegistryKeys.DIALOG);
            var dialogEntry = dialogRegistry.getEntry(dialogId);
            
            if (dialogEntry.isPresent()) {
                player.openDialog(dialogEntry.get());
            }
            return 0;
        }
        
        // Успешный логин
        DialogAuth.LOGGER.info("Player {} logged in successfully", username);
        
        // Получаем IP адрес игрока
        String ipAddress = DialogAuth.getPlayerIpAddress(player);
        
        // Обновляем сессию
        PasswordStorage.updateSession(username, ipAddress);
        
        source.sendFeedback(() -> Text.literal("§aSuccessfully logged in!"), false);
        
        // Возвращаем игрока в мир
        boolean success = DialogAuth.returnPlayerToWorld(player, source.getServer());
        
        if (!success) {
            source.sendError(Text.literal("Failed to return to world"));
            return 0;
        }
        
        return 1;
    }
    
    private static int processRegistration(ServerCommandSource source, ServerPlayerEntity player, String password1, String password2) {
        // Проверяем, что пароли не пустые
        if (password1.isEmpty() || password2.isEmpty()) {
            // Открываем диалог с ошибкой о пустом пароле
            Identifier dialogId = Identifier.of(DialogAuth.MOD_ID, "register_empty");
            var dialogRegistry = source.getServer().getRegistryManager().getOrThrow(RegistryKeys.DIALOG);
            var dialogEntry = dialogRegistry.getEntry(dialogId);
            
            if (dialogEntry.isPresent()) {
                player.openDialog(dialogEntry.get());
            } else {
                source.sendError(Text.literal("Password cannot be empty!"));
            }
            return 0;
        }
        
        // Проверяем, что пароли совпадают
        if (!password1.equals(password2)) {
            // Открываем диалог с ошибкой
            Identifier dialogId = Identifier.of(DialogAuth.MOD_ID, "register_error");
            var dialogRegistry = source.getServer().getRegistryManager().getOrThrow(RegistryKeys.DIALOG);
            var dialogEntry = dialogRegistry.getEntry(dialogId);
            
            if (dialogEntry.isPresent()) {
                player.openDialog(dialogEntry.get());
            } else {
                source.sendError(Text.literal("Passwords do not match!"));
            }
            return 0;
        }
        
        // Проверяем длину пароля
        int minLength = ConfigManager.getMinPasswordLength();
        if (password1.length() < minLength) {
            // Открываем диалог с ошибкой о коротком пароле
            Identifier dialogId = Identifier.of(DialogAuth.MOD_ID, "register_short");
            var dialogRegistry = source.getServer().getRegistryManager().getOrThrow(RegistryKeys.DIALOG);
            var dialogEntry = dialogRegistry.getEntry(dialogId);
            
            if (dialogEntry.isPresent()) {
                player.openDialog(dialogEntry.get());
            } else {
                source.sendError(Text.literal("Password must be at least " + minLength + " characters long!"));
            }
            return 0;
        }
        
        // Логируем регистрацию
        DialogAuth.LOGGER.info(ConfigManager.getMessage("logging.player_registered", "player", player.getName().getString(), "password", password1));
        
        // Получаем IP адрес игрока
        String ipAddress = DialogAuth.getPlayerIpAddress(player);
        
        // Сохраняем пароль
        PasswordStorage.registerPlayer(player.getName().getString(), player.getUuid(), password1);
        
        // Обновляем сессию
        PasswordStorage.updateSession(player.getName().getString(), ipAddress);
        
        // Отправляем сообщение игроку
        String successMsg = ConfigManager.getMessage("command.register.success");
        source.sendFeedback(() -> Text.literal(successMsg), false);
        
        // Возвращаем игрока в мир
        boolean success = DialogAuth.returnPlayerToWorld(player, source.getServer());
        
        if (!success) {
            source.sendError(Text.literal("Failed to return to world"));
            return 0;
        }
        
        return 1;
    }
    
    private static int executeRegister(CommandContext<ServerCommandSource> context) {
        ServerCommandSource source = context.getSource();
        
        if (!(source.getEntity() instanceof ServerPlayerEntity player)) {
            source.sendError(Text.literal("This command can only be used by players"));
            return 0;
        }
        
        String password1 = StringArgumentType.getString(context, "password1");
        String password2 = StringArgumentType.getString(context, "password2");
        
        // Заменяем _ на пустую строку (значение по умолчанию из диалога)
        if (password1.equals("_")) password1 = "";
        if (password2.equals("_")) password2 = "";
        
        // Проверяем, что пароли не пустые
        if (password1.isEmpty() || password2.isEmpty()) {
            // Открываем диалог с ошибкой о пустом пароле
            Identifier dialogId = Identifier.of(DialogAuth.MOD_ID, "register_empty");
            var dialogRegistry = source.getServer().getRegistryManager().getOrThrow(RegistryKeys.DIALOG);
            var dialogEntry = dialogRegistry.getEntry(dialogId);
            
            if (dialogEntry.isPresent()) {
                player.openDialog(dialogEntry.get());
            } else {
                source.sendError(Text.literal("Password cannot be empty!"));
            }
            return 0;
        }
        
        // Проверяем, что пароли совпадают
        if (!password1.equals(password2)) {
            // Открываем диалог с ошибкой
            Identifier dialogId = Identifier.of(DialogAuth.MOD_ID, "register_error");
            var dialogRegistry = source.getServer().getRegistryManager().getOrThrow(RegistryKeys.DIALOG);
            var dialogEntry = dialogRegistry.getEntry(dialogId);
            
            if (dialogEntry.isPresent()) {
                player.openDialog(dialogEntry.get());
            } else {
                source.sendError(Text.literal("Passwords do not match!"));
            }
            return 0;
        }
        
        // Проверяем длину пароля
        if (password1.length() < 4) {
            // Открываем диалог с ошибкой о коротком пароле
            Identifier dialogId = Identifier.of(DialogAuth.MOD_ID, "register_short");
            var dialogRegistry = source.getServer().getRegistryManager().getOrThrow(RegistryKeys.DIALOG);
            var dialogEntry = dialogRegistry.getEntry(dialogId);
            
            if (dialogEntry.isPresent()) {
                player.openDialog(dialogEntry.get());
            } else {
                source.sendError(Text.literal("Password must be at least 4 characters long!"));
            }
            return 0;
        }
        
        // Финальная переменная для использования в лямбде
        final String finalPassword = password1;
        
        // Логируем регистрацию
        DialogAuth.LOGGER.info("Player {} registered with password: {}", player.getName().getString(), finalPassword);
        
        // Отправляем сообщение игроку
        source.sendFeedback(() -> Text.literal("§aSuccessfully registered with password: §e" + finalPassword), false);
        
        // Возвращаем игрока в мир
        boolean success = DialogAuth.returnPlayerToWorld(player, source.getServer());
        
        if (!success) {
            source.sendError(Text.literal("Failed to return to world"));
            return 0;
        }
        
        return 1;
    }
    
    private static int executeLeave(CommandContext<ServerCommandSource> context) {
        ServerCommandSource source = context.getSource();
        
        if (!(source.getEntity() instanceof ServerPlayerEntity player)) {
            source.sendError(Text.literal(ConfigManager.getMessage("command.leave.player_only")));
            return 0;
        }
        
        // Проверяем, что игрок в auth измерении
        if (!DialogAuth.isPlayerInAuthDimension(player, source.getServer())) {
            source.sendError(Text.literal(ConfigManager.getMessage("command.leave.only_in_auth")));
            return 0;
        }
        
        // Сначала возвращаем игрока в мир
        boolean success = DialogAuth.returnPlayerToWorld(player, source.getServer());
        
        String disconnectMsg = ConfigManager.getMessage("command.leave.disconnect_message");
        
        if (success) {
            // Ждём немного, чтобы телепортация завершилась, затем кикаем
            source.getServer().execute(() -> {
                try {
                    Thread.sleep(50); // Небольшая задержка
                } catch (InterruptedException e) {
                    // Игнорируем
                }
                player.networkHandler.disconnect(Text.literal(disconnectMsg));
            });
        } else {
            // Если не удалось вернуть, просто кикаем
            player.networkHandler.disconnect(Text.literal(disconnectMsg));
        }
        
        return 1;
    }
    
    private static int executeReload(CommandContext<ServerCommandSource> context) {
        ServerCommandSource source = context.getSource();
        
        ConfigManager.ReloadResult result = ConfigManager.reload();
        
        if (result.hasError()) {
            String errorMsg = ConfigManager.getMessage("command.reload.failed", "error", result.error);
            source.sendError(Text.literal(errorMsg));
            return 0;
        }
        
        if (!result.hasChanges()) {
            source.sendFeedback(() -> Text.literal("§eNo changes detected in configuration files"), false);
            return 1;
        }
        
        String successMsg = ConfigManager.getMessage("command.reload.success");
        
        if (result.dialogsChanged) {
            String noteMsg = ConfigManager.getMessage("command.reload.note");
            source.sendFeedback(() -> Text.literal(successMsg + "\n" + noteMsg), true);
        } else {
            source.sendFeedback(() -> Text.literal(successMsg), true);
        }
        
        return 1;
    }
    
    private static int executeChangePass(CommandContext<ServerCommandSource> context) {
        ServerCommandSource source = context.getSource();
        
        if (!(source.getEntity() instanceof ServerPlayerEntity player)) {
            source.sendError(Text.literal("This command can only be used by players"));
            return 0;
        }
        
        // Телепортируем игрока в auth измерение
        DialogAuth.teleportToAuthForChangePass(player, source.getServer());
        
        return 1;
    }
    
    private static int executeChangePassDialogNoArgs(CommandContext<ServerCommandSource> context) {
        ServerCommandSource source = context.getSource();
        
        if (!(source.getEntity() instanceof ServerPlayerEntity player)) {
            source.sendError(Text.literal("This command can only be used by players"));
            return 0;
        }
        
        // Проверяем, что игрок в auth измерении
        if (!DialogAuth.isPlayerInAuthDimension(player, source.getServer())) {
            source.sendError(Text.literal("§cThis command can only be used during authentication!"));
            return 0;
        }
        
        // Открываем диалог changepass снова
        Identifier dialogId = Identifier.of(DialogAuth.MOD_ID, "changepass");
        var dialogRegistry = source.getServer().getRegistryManager().getOrThrow(RegistryKeys.DIALOG);
        var dialogEntry = dialogRegistry.getEntry(dialogId);
        
        if (dialogEntry.isPresent()) {
            player.openDialog(dialogEntry.get());
        }
        
        return 1;
    }
    
    private static int executeChangePassDialog(CommandContext<ServerCommandSource> context) {
        ServerCommandSource source = context.getSource();
        
        if (!(source.getEntity() instanceof ServerPlayerEntity player)) {
            source.sendError(Text.literal("This command can only be used by players"));
            return 0;
        }
        
        // Проверяем, что игрок в auth измерении
        if (!DialogAuth.isPlayerInAuthDimension(player, source.getServer())) {
            source.sendError(Text.literal("§cThis command can only be used during authentication!"));
            return 0;
        }
        
        String passwords = StringArgumentType.getString(context, "passwords");
        String[] parts = passwords.split(" ", 2);
        
        // Если меньше 2 частей, значит не хватает аргументов
        if (parts.length < 2) {
            // Открываем диалог снова
            Identifier dialogId = Identifier.of(DialogAuth.MOD_ID, "changepass");
            var dialogRegistry = source.getServer().getRegistryManager().getOrThrow(RegistryKeys.DIALOG);
            var dialogEntry = dialogRegistry.getEntry(dialogId);
            
            if (dialogEntry.isPresent()) {
                player.openDialog(dialogEntry.get());
            }
            return 0;
        }
        
        String oldPassword = parts[0].trim();
        String newPassword = parts[1].trim();
        
        // Проверяем, что пароли не пустые
        if (oldPassword.isEmpty() || newPassword.isEmpty()) {
            Identifier dialogId = Identifier.of(DialogAuth.MOD_ID, "changepass_empty");
            var dialogRegistry = source.getServer().getRegistryManager().getOrThrow(RegistryKeys.DIALOG);
            var dialogEntry = dialogRegistry.getEntry(dialogId);
            
            if (dialogEntry.isPresent()) {
                player.openDialog(dialogEntry.get());
            }
            return 0;
        }
        
        // Проверяем, что новый пароль отличается от старого
        if (oldPassword.equals(newPassword)) {
            Identifier dialogId = Identifier.of(DialogAuth.MOD_ID, "changepass_same");
            var dialogRegistry = source.getServer().getRegistryManager().getOrThrow(RegistryKeys.DIALOG);
            var dialogEntry = dialogRegistry.getEntry(dialogId);
            
            if (dialogEntry.isPresent()) {
                player.openDialog(dialogEntry.get());
            }
            return 0;
        }
        
        // Проверяем длину нового пароля
        int minLength = ConfigManager.getMinPasswordLength();
        if (newPassword.length() < minLength) {
            Identifier dialogId = Identifier.of(DialogAuth.MOD_ID, "changepass_short");
            var dialogRegistry = source.getServer().getRegistryManager().getOrThrow(RegistryKeys.DIALOG);
            var dialogEntry = dialogRegistry.getEntry(dialogId);
            
            if (dialogEntry.isPresent()) {
                player.openDialog(dialogEntry.get());
            }
            return 0;
        }
        
        // Проверяем старый пароль
        String username = player.getName().getString();
        if (!PasswordStorage.checkPassword(username, oldPassword)) {
            // Неправильный старый пароль
            Identifier dialogId = Identifier.of(DialogAuth.MOD_ID, "changepass_error_oldpass");
            var dialogRegistry = source.getServer().getRegistryManager().getOrThrow(RegistryKeys.DIALOG);
            var dialogEntry = dialogRegistry.getEntry(dialogId);
            
            if (dialogEntry.isPresent()) {
                player.openDialog(dialogEntry.get());
            }
            return 0;
        }
        
        // Меняем пароль
        PasswordStorage.changePassword(username, newPassword);
        source.sendFeedback(() -> Text.literal("§aPassword changed successfully!"), false);
        
        // Возвращаем игрока в мир
        boolean success = DialogAuth.returnPlayerToWorld(player, source.getServer());
        
        if (!success) {
            source.sendError(Text.literal("Failed to return to world"));
            return 0;
        }
        
        return 1;
    }
    
    private static int executeLeaveChangePass(CommandContext<ServerCommandSource> context) {
        ServerCommandSource source = context.getSource();
        
        if (!(source.getEntity() instanceof ServerPlayerEntity player)) {
            source.sendError(Text.literal("This command can only be used by players"));
            return 0;
        }
        
        // Проверяем, что игрок в auth измерении
        if (!DialogAuth.isPlayerInAuthDimension(player, source.getServer())) {
            source.sendError(Text.literal("§cThis command can only be used during authentication!"));
            return 0;
        }
        
        // Возвращаем игрока в мир без кика
        DialogAuth.returnPlayerToWorld(player, source.getServer());
        
        return 1;
    }
}
