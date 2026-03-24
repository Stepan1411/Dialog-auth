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
                    .executes(DialogAuthCommand::executeRegisterNoArgs)
                    .then(CommandManager.argument("passwords", StringArgumentType.greedyString())
                        .executes(DialogAuthCommand::executeRegisterGreedy)
                    )
                )
                .then(CommandManager.literal("login")
                    .executes(DialogAuthCommand::executeLoginNoArgs)
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
                    .executes(DialogAuthCommand::executeChangePassDialogNoArgs)
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
        
        if (!DialogAuth.isPlayerInAuthDimension(player, source.getServer())) {
            source.sendError(Text.literal("§cThis command can only be used during authentication!"));
            return 0;
        }
        
        String passwords = StringArgumentType.getString(context, "passwords");
        String[] parts = passwords.split(" ", 2);
        
        if (parts.length < 2) {
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
        
        if (!DialogAuth.isPlayerInAuthDimension(player, source.getServer())) {
            source.sendError(Text.literal("§cThis command can only be used during authentication!"));
            return 0;
        }
        
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
        
        if (!DialogAuth.isPlayerInAuthDimension(player, source.getServer())) {
            source.sendError(Text.literal("§cThis command can only be used during authentication!"));
            return 0;
        }
        
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
        
        if (!DialogAuth.isPlayerInAuthDimension(player, source.getServer())) {
            source.sendError(Text.literal("§cThis command can only be used during authentication!"));
            return 0;
        }
        
        String password = StringArgumentType.getString(context, "password");
        
        if (password.endsWith("_")) {
            password = password.substring(0, password.length() - 1);
        }
        
        password = password.trim();
        
        if (password.isEmpty()) {
            Identifier dialogId = Identifier.of(DialogAuth.MOD_ID, "login_empty");
            var dialogRegistry = source.getServer().getRegistryManager().getOrThrow(RegistryKeys.DIALOG);
            var dialogEntry = dialogRegistry.getEntry(dialogId);
            
            if (dialogEntry.isPresent()) {
                player.openDialog(dialogEntry.get());
            }
            return 0;
        }
        
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
        
        String username = player.getName().getString();
        if (!PasswordStorage.checkPassword(username, password)) {
            Identifier dialogId = Identifier.of(DialogAuth.MOD_ID, "login_error");
            var dialogRegistry = source.getServer().getRegistryManager().getOrThrow(RegistryKeys.DIALOG);
            var dialogEntry = dialogRegistry.getEntry(dialogId);
            
            if (dialogEntry.isPresent()) {
                player.openDialog(dialogEntry.get());
            }
            return 0;
        }
        
        DialogAuth.LOGGER.info("Player {} logged in successfully", username);
        
        String ipAddress = DialogAuth.getPlayerIpAddress(player);
        
        PasswordStorage.updateSession(username, ipAddress);
        
        source.sendFeedback(() -> Text.literal("§aSuccessfully logged in!"), false);
        
        boolean success = DialogAuth.returnPlayerToWorld(player, source.getServer());
        
        if (!success) {
            source.sendError(Text.literal("Failed to return to world"));
            return 0;
        }
        
        return 1;
    }
    
    private static int processRegistration(ServerCommandSource source, ServerPlayerEntity player, String password1, String password2) {
        if (password1.isEmpty() || password2.isEmpty()) {
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
        
        if (!password1.equals(password2)) {
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
        
        int minLength = ConfigManager.getMinPasswordLength();
        if (password1.length() < minLength) {
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
        
        DialogAuth.LOGGER.info(ConfigManager.getMessage("logging.player_registered", "player", player.getName().getString(), "password", password1));
        
        String ipAddress = DialogAuth.getPlayerIpAddress(player);
        
        PasswordStorage.registerPlayer(player.getName().getString(), player.getUuid(), password1);
        
        PasswordStorage.updateSession(player.getName().getString(), ipAddress);
        
        String successMsg = ConfigManager.getMessage("command.register.success");
        source.sendFeedback(() -> Text.literal(successMsg), false);
        
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
        
        if (password1.equals("_")) password1 = "";
        if (password2.equals("_")) password2 = "";
        
        if (password1.isEmpty() || password2.isEmpty()) {
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
        
        if (!password1.equals(password2)) {
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
        
        if (password1.length() < 4) {
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
        
        final String finalPassword = password1;
        
        DialogAuth.LOGGER.info("Player {} registered with password: {}", player.getName().getString(), finalPassword);
        
        source.sendFeedback(() -> Text.literal("§aSuccessfully registered with password: §e" + finalPassword), false);
        
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
        
        if (!DialogAuth.isPlayerInAuthDimension(player, source.getServer())) {
            source.sendError(Text.literal(ConfigManager.getMessage("command.leave.only_in_auth")));
            return 0;
        }
        
        boolean success = DialogAuth.returnPlayerToWorld(player, source.getServer());
        
        String disconnectMsg = ConfigManager.getMessage("command.leave.disconnect_message");
        
        if (success) {
            source.getServer().execute(() -> {
                try {
                    Thread.sleep(50);
                } catch (InterruptedException e) {
                }
                player.networkHandler.disconnect(Text.literal(disconnectMsg));
            });
        } else {
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
        
        DialogAuth.teleportToAuthForChangePass(player, source.getServer());
        
        return 1;
    }
    
    private static int executeChangePassDialogNoArgs(CommandContext<ServerCommandSource> context) {
        ServerCommandSource source = context.getSource();
        
        if (!(source.getEntity() instanceof ServerPlayerEntity player)) {
            source.sendError(Text.literal("This command can only be used by players"));
            return 0;
        }
        
        if (!DialogAuth.isPlayerInAuthDimension(player, source.getServer())) {
            source.sendError(Text.literal("§cThis command can only be used during authentication!"));
            return 0;
        }
        
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
        
        if (!DialogAuth.isPlayerInAuthDimension(player, source.getServer())) {
            source.sendError(Text.literal("§cThis command can only be used during authentication!"));
            return 0;
        }
        
        String passwords = StringArgumentType.getString(context, "passwords");
        String[] parts = passwords.split(" ", 2);
        
        if (parts.length < 2) {
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
        
        if (oldPassword.isEmpty() || newPassword.isEmpty()) {
            Identifier dialogId = Identifier.of(DialogAuth.MOD_ID, "changepass_empty");
            var dialogRegistry = source.getServer().getRegistryManager().getOrThrow(RegistryKeys.DIALOG);
            var dialogEntry = dialogRegistry.getEntry(dialogId);
            
            if (dialogEntry.isPresent()) {
                player.openDialog(dialogEntry.get());
            }
            return 0;
        }
        
        if (oldPassword.equals(newPassword)) {
            Identifier dialogId = Identifier.of(DialogAuth.MOD_ID, "changepass_same");
            var dialogRegistry = source.getServer().getRegistryManager().getOrThrow(RegistryKeys.DIALOG);
            var dialogEntry = dialogRegistry.getEntry(dialogId);
            
            if (dialogEntry.isPresent()) {
                player.openDialog(dialogEntry.get());
            }
            return 0;
        }
        
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
        
        String username = player.getName().getString();
        if (!PasswordStorage.checkPassword(username, oldPassword)) {
            Identifier dialogId = Identifier.of(DialogAuth.MOD_ID, "changepass_error_oldpass");
            var dialogRegistry = source.getServer().getRegistryManager().getOrThrow(RegistryKeys.DIALOG);
            var dialogEntry = dialogRegistry.getEntry(dialogId);
            
            if (dialogEntry.isPresent()) {
                player.openDialog(dialogEntry.get());
            }
            return 0;
        }
        
        PasswordStorage.changePassword(username, newPassword);
        source.sendFeedback(() -> Text.literal("§aPassword changed successfully!"), false);
        
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
        
        if (!DialogAuth.isPlayerInAuthDimension(player, source.getServer())) {
            source.sendError(Text.literal("§cThis command can only be used during authentication!"));
            return 0;
        }
        
        DialogAuth.returnPlayerToWorld(player, source.getServer());
        
        return 1;
    }
}
