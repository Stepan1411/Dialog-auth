package org.stepan1411.dialogAuth;

import com.mojang.brigadier.CommandDispatcher;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.minecraft.registry.RegistryKey;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.GameMode;
import net.minecraft.world.TeleportTarget;
import net.minecraft.world.World;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.stepan1411.dialogAuth.command.DialogAuthCommand;
import org.stepan1411.dialogAuth.config.ConfigManager;
import org.stepan1411.dialogAuth.storage.PasswordStorage;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public class DialogAuth implements ModInitializer {

    public static final String MOD_ID = "dialog_auth";
    public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);
    
    // Хранилище для сохранения позиций игроков
    private static final Map<UUID, PlayerLocation> savedLocations = new HashMap<>();
    
    // Игроки в процессе аутентификации
    private static final Map<UUID, Boolean> authenticatingPlayers = new HashMap<>();
    
    // Ключ измерения auth
    public static final RegistryKey<World> AUTH_DIMENSION = RegistryKey.of(
        RegistryKeys.WORLD,
        Identifier.of(MOD_ID, "auth")
    );

    @Override
    public void onInitialize() {
        LOGGER.info("DialogAuth mod initialized!");
        
        // Создаем конфигурационные файлы
        ConfigManager.initialize();
        
        // Инициализируем хранилище паролей
        PasswordStorage.initialize();
        
        // Регистрируем команды
        CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) -> {
            DialogAuthCommand.register(dispatcher);
        });
        
        // Проверяем игроков каждый тик
        ServerTickEvents.END_SERVER_TICK.register(this::onServerTick);
        
        // Отменяем спавн мобов в измерении auth
        net.fabricmc.fabric.api.event.lifecycle.v1.ServerEntityEvents.ENTITY_LOAD.register((entity, world) -> {
            // Если это моб в измерении auth - удаляем его
            if (world.getRegistryKey().equals(AUTH_DIMENSION) && entity instanceof net.minecraft.entity.mob.MobEntity) {
                entity.discard();
                LOGGER.debug("Prevented mob spawn in auth dimension: {}", entity.getType().getName().getString());
            }
        });
        
        // Показываем диалог при входе игрока на сервер
        ServerPlayConnectionEvents.JOIN.register((handler, sender, server) -> {
            ServerPlayerEntity player = handler.getPlayer();
            
            server.execute(() -> {
                // Получаем IP адрес игрока
                String ipAddress = getPlayerIpAddress(player);
                
                // Проверяем, зарегистрирован ли игрок
                boolean isRegistered = PasswordStorage.isPlayerRegistered(player.getName().getString());
                
                // Проверяем, нужна ли аутентификация
                boolean needsAuth = !isRegistered || PasswordStorage.needsLogin(player.getName().getString(), ipAddress);
                
                if (needsAuth) {
                    // Нужна аутентификация - сохраняем позицию и телепортируем в auth
                    
                    // Сохраняем позицию только если её ещё нет
                    if (!savedLocations.containsKey(player.getUuid())) {
                        // Игрок всегда заходит в оверворлд при первом подключении
                        ServerWorld world = server.getOverworld();
                        Vec3d pos = new Vec3d(player.getX(), player.getY(), player.getZ());
                        float yaw = player.getYaw();
                        float pitch = player.getPitch();
                        GameMode gameMode = player.interactionManager.getGameMode();
                        
                        PlayerLocation location = new PlayerLocation(
                            world.getRegistryKey(),
                            pos,
                            yaw,
                            pitch,
                            gameMode
                        );
                        savedLocations.put(player.getUuid(), location);
                    }
                    
                    // Помечаем игрока как аутентифицирующегося
                    authenticatingPlayers.put(player.getUuid(), true);
                    
                    // Телепортируем в измерение auth
                    teleportToAuth(player, server);
                    
                    // Показываем диалог
                    showAuthDialog(player, server, isRegistered);
                } else {
                    // Сессия активна - пропускаем аутентификацию
                    LOGGER.info("Player {} has valid session, skipping authentication", player.getName().getString());
                }
            });
        });
        
        // Убираем игрока из списков при выходе
        ServerPlayConnectionEvents.DISCONNECT.register((handler, server) -> {
            UUID uuid = handler.getPlayer().getUuid();
            authenticatingPlayers.remove(uuid);
            savedLocations.remove(uuid);
        });
    }
    
    private void onServerTick(MinecraftServer server) {
        // Можно добавить дополнительную логику здесь при необходимости
    }
    
    private void teleportToAuth(ServerPlayerEntity player, MinecraftServer server) {
        ServerWorld authWorld = server.getWorld(AUTH_DIMENSION);
        
        if (authWorld == null) {
            LOGGER.error("Auth dimension not found!");
            return;
        }
        
        // Устанавливаем режим наблюдателя
        player.changeGameMode(GameMode.SPECTATOR);
        
        // Телепортируем в центр измерения auth
        Vec3d targetPos = new Vec3d(0.5, 65, 0.5);
        TeleportTarget target = new TeleportTarget(authWorld, targetPos, Vec3d.ZERO, 0, 0, TeleportTarget.NO_OP);
        
        player.teleportTo(target);
    }
    
    private void showAuthDialog(ServerPlayerEntity player, MinecraftServer server, boolean isRegistered) {
        if (!isRegistered) {
            // Не зарегистрирован - показываем регистрацию
            Identifier dialogId = Identifier.of(MOD_ID, "register");
            var dialogRegistry = server.getRegistryManager().getOrThrow(RegistryKeys.DIALOG);
            var dialogEntry = dialogRegistry.getEntry(dialogId);
            
            if (dialogEntry.isPresent()) {
                player.openDialog(dialogEntry.get());
            } else {
                LOGGER.error("Dialog {} not found in registry!", dialogId);
            }
        } else {
            // Зарегистрирован - показываем логин
            Identifier dialogId = Identifier.of(MOD_ID, "login");
            var dialogRegistry = server.getRegistryManager().getOrThrow(RegistryKeys.DIALOG);
            var dialogEntry = dialogRegistry.getEntry(dialogId);
            
            if (dialogEntry.isPresent()) {
                player.openDialog(dialogEntry.get());
            } else {
                LOGGER.error("Dialog {} not found in registry!", dialogId);
            }
        }
    }
    
    public static boolean returnPlayerToWorld(ServerPlayerEntity player, MinecraftServer server) {
        PlayerLocation location = savedLocations.remove(player.getUuid());
        
        if (location == null) {
            return false;
        }
        
        ServerWorld targetWorld = server.getWorld(location.dimension);
        
        if (targetWorld == null) {
            return false;
        }
        
        // Телепортируем обратно
        TeleportTarget target = new TeleportTarget(
            targetWorld,
            location.position,
            Vec3d.ZERO,
            location.yaw,
            location.pitch,
            TeleportTarget.NO_OP
        );
        
        player.teleportTo(target);
        
        // Восстанавливаем режим игры
        player.changeGameMode(location.gameMode);
        
        // Убираем из списка аутентифицирующихся
        authenticatingPlayers.remove(player.getUuid());
        
        return true;
    }
    
    public static boolean isPlayerInAuthDimension(ServerPlayerEntity player, MinecraftServer server) {
        // Получаем auth мир
        ServerWorld authWorld = server.getWorld(AUTH_DIMENSION);
        if (authWorld == null) return false;
        
        // Сравниваем через поле world (которое protected в Entity)
        // Используем рефлексию или альтернативный способ
        try {
            var worldField = net.minecraft.entity.Entity.class.getDeclaredField("world");
            worldField.setAccessible(true);
            Object playerWorld = worldField.get(player);
            return playerWorld == authWorld;
        } catch (Exception e) {
            return false;
        }
    }
    
    public static String getPlayerIpAddress(ServerPlayerEntity player) {
        try {
            var connectionField = net.minecraft.server.network.ServerCommonNetworkHandler.class.getDeclaredField("connection");
            connectionField.setAccessible(true);
            Object connection = connectionField.get(player.networkHandler);
            if (connection instanceof net.minecraft.network.ClientConnection) {
                String fullAddress = ((net.minecraft.network.ClientConnection) connection).getAddress().toString();
                // Убираем порт из адреса (например "/127.0.0.1:12345" -> "127.0.0.1")
                if (fullAddress.startsWith("/")) {
                    fullAddress = fullAddress.substring(1);
                }
                int colonIndex = fullAddress.indexOf(':');
                if (colonIndex > 0) {
                    return fullAddress.substring(0, colonIndex);
                }
                return fullAddress;
            }
        } catch (Exception e) {
            LOGGER.error("Failed to get player IP address", e);
        }
        return "unknown";
    }
    
    public static void teleportToAuthForChangePass(ServerPlayerEntity player, MinecraftServer server) {
        // Проверяем, что игрок НЕ в auth измерении
        if (isPlayerInAuthDimension(player, server)) {
            LOGGER.warn("Player {} is already in auth dimension, not saving location", player.getName().getString());
            // Просто показываем диалог без сохранения позиции
            Identifier dialogId = Identifier.of(MOD_ID, "changepass");
            var dialogRegistry = server.getRegistryManager().getOrThrow(RegistryKeys.DIALOG);
            var dialogEntry = dialogRegistry.getEntry(dialogId);
            
            if (dialogEntry.isPresent()) {
                player.openDialog(dialogEntry.get());
            } else {
                LOGGER.error("Dialog {} not found in registry!", dialogId);
            }
            return;
        }
        
        // Получаем текущий мир игрока через рефлексию
        ServerWorld currentWorld = null;
        try {
            var worldField = net.minecraft.entity.Entity.class.getDeclaredField("world");
            worldField.setAccessible(true);
            Object playerWorld = worldField.get(player);
            if (playerWorld instanceof ServerWorld) {
                currentWorld = (ServerWorld) playerWorld;
            }
        } catch (Exception e) {
            LOGGER.error("Failed to get player world", e);
            currentWorld = server.getOverworld(); // Fallback to overworld
        }
        
        // Сохраняем текущую позицию игрока
        Vec3d pos = new Vec3d(player.getX(), player.getY(), player.getZ());
        float yaw = player.getYaw();
        float pitch = player.getPitch();
        GameMode gameMode = player.interactionManager.getGameMode();
        
        PlayerLocation location = new PlayerLocation(
            currentWorld.getRegistryKey(),
            pos,
            yaw,
            pitch,
            gameMode
        );
        savedLocations.put(player.getUuid(), location);
        
        // Телепортируем в auth измерение
        ServerWorld authWorld = server.getWorld(AUTH_DIMENSION);
        
        if (authWorld == null) {
            LOGGER.error("Auth dimension not found!");
            return;
        }
        
        // Устанавливаем режим наблюдателя
        player.changeGameMode(GameMode.SPECTATOR);
        
        // Телепортируем в центр измерения auth
        Vec3d targetPos = new Vec3d(0.5, 65, 0.5);
        TeleportTarget target = new TeleportTarget(authWorld, targetPos, Vec3d.ZERO, 0, 0, TeleportTarget.NO_OP);
        
        player.teleportTo(target);
        
        // Показываем диалог смены пароля
        Identifier dialogId = Identifier.of(MOD_ID, "changepass");
        var dialogRegistry = server.getRegistryManager().getOrThrow(RegistryKeys.DIALOG);
        var dialogEntry = dialogRegistry.getEntry(dialogId);
        
        if (dialogEntry.isPresent()) {
            player.openDialog(dialogEntry.get());
        } else {
            LOGGER.error("Dialog {} not found in registry!", dialogId);
        }
    }
    
    private record PlayerLocation(
        RegistryKey<World> dimension,
        Vec3d position,
        float yaw,
        float pitch,
        GameMode gameMode
    ) {}
}
