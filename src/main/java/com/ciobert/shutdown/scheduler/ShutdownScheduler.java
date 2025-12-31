package com.ciobert.shutdown.scheduler;

import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.server.MinecraftServer;
import static net.minecraft.server.command.CommandManager.literal;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;

public class ShutdownScheduler implements ModInitializer {

	public static final String MOD_ID = "shutdown-scheduler";
	public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

	public static final Gson GSON = new GsonBuilder().create();
	private static final File CONFIG_FILE = new File("config/shutdown_scheduler.json");

	private static Map<String, ShutdownGroup> groups = java.util.Collections.synchronizedMap(new HashMap<>());

	private static int warningMinutes = 5;
	private static String language = "en";
	private static MinecraftServer server;
	private static ScheduledExecutorService scheduler;
	private static Set<String> triggeredToday = java.util.concurrent.ConcurrentHashMap.newKeySet();
	private static boolean shutdownInProgress = false;
	private static boolean skipNextShutdown = false;
	private static final List<java.util.concurrent.ScheduledFuture<?>> currentShutdownTasks = Collections.synchronizedList(new ArrayList<>());
	private static String currentTriggerKey = null;

	// Traduzioni
	private static final Map<String, Lang> LANGUAGES = new HashMap<>();

	static {
		// Inglese
		LANGUAGES.put("en", new Lang(
				new String[]{"monday", "tuesday", "wednesday", "thursday", "friday", "saturday", "sunday"},
				"Initializing Shutdown Scheduler v2.0.0...",
				"Configuration loaded: %d groups",
				"Server started, Shutdown Scheduler active!",
				"§aAdded shutdown to group %s for %s at %s",
				"§cRemoved shutdown from group %s for %s at %s",
				"§eNo shutdown found in group %s for %s at %s",
				"§e=== Scheduled Shutdowns (Group: %s, Enabled: %b) ===\n",
				"§cNo shutdowns scheduled in this group!",
				"§aConfiguration reloaded.",
				"§e=== Debug Info ===\n§fDay: §b%s\n§fTime: §b%s\n§fServer active: §b%s\n§fShutdown in progress: §b%s\n§fTriggered today: §b%d",
				"§aForced shutdown in %d minutes",
				"§c§l⚠ The server will shut down in %d minute%s!",
				"§e§l⚠ The server will shut down in 1 minute!",
				"§4§l⚠ SERVER SHUTTING DOWN...",
				"Configuration file not found, creating new file...",
				"Loading configuration from: %s",
				"Loaded %d groups",
				"No groups found in config",
				"Warning minutes: %d",
				"Config data is null!",
				"Configuration saved: %d groups",
				"Daily triggers reset",
				"=== SHUTDOWN ACTIVATED for %s at %s (in %d minutes) ===",
				"Shutdown scheduled in %d minutes",
				"Warning: 1 minute until shutdown",
				"EXECUTING SHUTDOWN",
				"Stopping server...",
				"§aLanguage changed to English",
				"Available languages: en, it",
				"§aGroup §b%s §acreated.",
				"§cGroup §b%s §calready exists.",
				"§aGroup §b%s §atoggled to: §b%b",
				"§cGroup §b%s §cnot found.",
				"§aGroup §b%s §adeleted.",
				"§e=== Groups List ===\n",
				"§cGiorno non valido!"
		));

		// Italiano
		LANGUAGES.put("it", new Lang(
				new String[]{"lunedi", "martedi", "mercoledi", "giovedi", "venerdi", "sabato", "domenica"},
				"Inizializzazione Shutdown Scheduler v2.0.0...",
				"Configurazione caricata: %d gruppi",
				"Server avviato, Shutdown Scheduler attivo!",
				"§aAggiunto spegnimento al gruppo %s per %s alle %s",
				"§cRimosso spegnimento dal gruppo %s per %s alle %s",
				"§eNon trovato nessuno spegnimento nel gruppo %s per %s alle %s",
				"§e=== Shutdown Programmati (Gruppo: %s, Attivo: %b) ===\n",
				"§cNessuno shutdown programmato in questo gruppo!",
				"§aConfigurazione ricaricata.",
				"§e=== Info Debug ===\n§fGiorno: §b%s\n§fOra: §b%s\n§fServer attivo: §b%s\n§fShutdown in corso: §b%s\n§fTriggered oggi: §b%d",
				"§aShutdown forzato tra %d minuti",
				"§c§l⚠ Il server si spegnerà tra %d minut%s!",
				"§e§l⚠ Il server si spegnerà tra 1 minuto!",
				"§4§l⚠ SPEGNIMENTO DEL SERVER IN CORSO...",
				"File di configurazione non trovato, creazione nuovo file...",
				"Caricamento configurazione da: %s",
				"Caricati %d gruppi",
				"Nessun gruppo trovato nel config",
				"Warning minutes: %d",
				"Config data è null!",
				"Configurazione salvata: %d gruppi",
				"Reset trigger giornalieri",
				"=== SHUTDOWN ATTIVATO per %s alle %s (tra %d minuti) ===",
				"Programmazione shutdown tra %d minuti",
				"Avviso: 1 minuto allo shutdown",
				"ESECUZIONE SHUTDOWN",
				"Stop del server...",
				"§aLingua cambiata in Italiano",
				"Lingue disponibili: en, it",
				"§aGruppo §b%s §acreato.",
				"§cIl gruppo §b%s §cesiste già.",
				"§aGruppo §b%s §acambiato in: §b%b",
				"§cGruppo §b%s §cnon trovato.",
				"§aGruppo §b%s §aeliminato.",
				"§e=== Lista Gruppi ===\n",
				"§cGiorno non valido!"
		));
	}

	private static Lang getLang() {
		return LANGUAGES.getOrDefault(language, LANGUAGES.get("en"));
	}

	private static String getDayName(int index) {
		return getDays()[index];
	}

	private static String[] getDays() {
		return getLang().days;
	}

	private static int getDayIndex(String dayName) {
		String[] days = getDays();
		for (int i = 0; i < days.length; i++) {
			if (days[i].equalsIgnoreCase(dayName)) {
				return i;
			}
		}
		return -1;
	}

	@Override
	public void onInitialize() {
		LOGGER.info(getLang().initMessage);
		loadConfig();
		Networking.registerPayloads();

		LOGGER.info(String.format(getLang().configLoaded, groups.size()));

		ServerLifecycleEvents.SERVER_STARTED.register(s -> {
			server = s;
			LOGGER.info(getLang().serverStarted);
		});
		ServerPlayNetworking.registerGlobalReceiver(Networking.ActionPayload.ID, (payload, context) -> {
			context.server().execute(() -> handleGuiAction(payload.action(), payload.data(), context.player()));
		});

		ServerPlayConnectionEvents.JOIN.register((handler, sender, server) -> {
			syncConfig(handler.getPlayer(), false);
		});

		CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) -> {
			dispatcher.register(literal("shutdownscheduler")
					.requires(src -> src.hasPermissionLevel(4))
					.executes(context -> {
						syncConfig(context.getSource().getPlayer(), true);
						return 1;
					}));

			dispatcher.register(literal("ss")
					.requires(src -> src.hasPermissionLevel(4))
					.then(literal("cancel")
							.executes(context -> {
								cancelPendingShutdown();
								context.getSource().sendFeedback(() -> Text.literal("§aShutdown cancellato."), true);
								return 1;
							}))
					.then(literal("sync")
							.executes(context -> {
								syncConfig(context.getSource().getPlayer(), true);
								return 1;
							}))
					.executes(context -> {
						syncConfig(context.getSource().getPlayer(), true);
						return 1;
					}));
		});

		ServerLifecycleEvents.SERVER_STOPPING.register(server -> {
			if (scheduler != null && !scheduler.isShutdown()) {
				LOGGER.info("Stopping Shutdown Scheduler...");
				scheduler.shutdown();
				try {
					if (!scheduler.awaitTermination(5, TimeUnit.SECONDS)) {
						scheduler.shutdownNow();
					}
				} catch (InterruptedException e) {
					scheduler.shutdownNow();
				}
			}
		});

		// Scheduler principale con Thread Factory per thread Daemon
		scheduler = Executors.newSingleThreadScheduledExecutor(r -> {
			Thread t = new Thread(r, "ShutdownScheduler-Worker");
			t.setDaemon(true);
			return t;
		});
		scheduler.scheduleAtFixedRate(() -> {
			try {
				checkScheduledShutdowns();
			} catch (Exception e) {
				LOGGER.error("Errore nel controllo degli shutdown programmati", e);
			}
		}, 0, 30, TimeUnit.SECONDS);

		LOGGER.info("=== Shutdown Scheduler caricato con successo! ===");
	}

	private static void syncConfig(ServerPlayerEntity player, boolean showGui) {
		if (player == null) return;
		ConfigData data = buildConfigDataSnapshot();
		String json = GSON.toJson(data);
		ServerPlayNetworking.send(player, new Networking.ConfigSyncPayload(json, showGui));
	}

	private static long lastBroadcastTime = 0;
	private static boolean broadcastPending = false;
	private static String lastBroadcastJson = null;

	private static void broadcastConfig() {
		if (server == null) return;
		
		long now = System.currentTimeMillis();
		// Debounce: broadcast at most once every 500ms
		if (now - lastBroadcastTime < 500) {
			if (!broadcastPending) {
				broadcastPending = true;
				scheduler.schedule(() -> {
					broadcastPending = false;
					broadcastConfig();
				}, 500, TimeUnit.MILLISECONDS);
			}
			return;
		}

		lastBroadcastTime = now;
		ConfigData snapshot = buildConfigDataSnapshot();
		String json = GSON.toJson(snapshot);
		
		// Optimization: don't broadcast if nothing changed since last broadcast
		if (json.equals(lastBroadcastJson)) return;
		lastBroadcastJson = json;

		Networking.ConfigSyncPayload payload = new Networking.ConfigSyncPayload(json, false);
		for (ServerPlayerEntity p : server.getPlayerManager().getPlayerList()) {
			ServerPlayNetworking.send(p, payload);
		}
	}

	private static ConfigData buildConfigDataSnapshot() {
		ConfigData data = new ConfigData();
		data.warning_minutes = warningMinutes;
		data.language = language;
		data.server_time = System.currentTimeMillis();
		data.groups = new HashMap<>();
		synchronized (groups) {
			for (Map.Entry<String, ShutdownGroup> entry : groups.entrySet()) {
				ShutdownGroup original = entry.getValue();
				ShutdownGroup copy = new ShutdownGroup();
				copy.enabled = original.enabled;
				synchronized (original.shutdowns) {
					copy.shutdowns = new HashMap<>();
					for (Map.Entry<String, List<String>> dEntry : original.shutdowns.entrySet()) {
						copy.shutdowns.put(dEntry.getKey(), new ArrayList<>(dEntry.getValue()));
					}
				}
				data.groups.put(entry.getKey(), copy);
			}
		}
		return data;
	}

	private static void handleGuiAction(String action, String data, ServerPlayerEntity player) {
		if (player == null) return;
		if (!player.hasPermissionLevel(4)) {
			LOGGER.warn("Permission denied for player {} attempting action {}", player.getName().getString(), action);
			return;
		}
		LOGGER.info("[ShutdownScheduler] Handling GUI action: {} with data: '{}' from player {}", action, data, player.getName().getString());

		try {
			switch (action) {
				case "CREATE_GROUP" -> createGroup(data);
				case "DELETE_GROUP" -> deleteGroup(data);
				case "TOGGLE_GROUP" -> toggleGroup(data);
				case "ADD_SHUTDOWN" -> {
					String[] parts = data.split(";");
					if (parts.length == 4) {
						addShutdown(parts[0], Integer.parseInt(parts[1]), Integer.parseInt(parts[2]), Integer.parseInt(parts[3]));
					} else {
						LOGGER.error("[ShutdownScheduler] ADD_SHUTDOWN invalid parts: {}", parts.length);
					}
				}
				case "REMOVE_SHUTDOWN" -> {
					String[] parts = data.split(";");
					if (parts.length == 4) {
						removeShutdown(parts[0], Integer.parseInt(parts[1]), Integer.parseInt(parts[2]), Integer.parseInt(parts[3]));
					} else {
						LOGGER.error("[ShutdownScheduler] REMOVE_SHUTDOWN invalid parts: {}", parts.length);
					}
				}
				case "SET_WARNING" -> {
					warningMinutes = Integer.parseInt(data);
					saveConfig();
				}
				case "SET_LANG" -> {
					language = data;
					saveConfig();
				}
				default -> LOGGER.warn("[ShutdownScheduler] Unknown action: {}", action);
			}
			syncConfig(player, false); // Immediate feedback only for the initiator
			broadcastConfig(); // Broadcast sync after action to others (debounced)
			saveConfig(); // Save in background
		} catch (Exception e) {
			LOGGER.error("[ShutdownScheduler] Error handling GUI action: " + action, e);
		}
	}

	// Logic extractions
	private static boolean createGroup(String name) {
		if (groups.containsKey(name)) return false;
		groups.put(name, new ShutdownGroup());
		return true;
	}

	private static boolean deleteGroup(String name) {
		if (groups.remove(name) != null) {
			return true;
		}
		return false;
	}

	private static Boolean toggleGroup(String name) {
		ShutdownGroup group = groups.get(name);
		if (group != null) {
			group.enabled = !group.enabled;
			return group.enabled;
		}
		return null;
	}

	private static boolean addShutdown(String groupName, int dayIndex, int hour, int minute) {
		ShutdownGroup group = groups.get(groupName);
		if (group == null) {
			LOGGER.error("Cannot add shutdown: group {} not found", groupName);
			return false;
		}
		if (dayIndex < 0 || dayIndex > 6) return false;
		String time = String.format("%02d:%02d", hour, minute);
		synchronized (group.shutdowns) {
			group.shutdowns.computeIfAbsent(String.valueOf(dayIndex), k -> new ArrayList<>()).add(time);
		}
		LOGGER.info("Added shutdown to group {}: day {}, time {}", groupName, dayIndex, time);
		return true;
	}

	private static boolean addShutdown(String groupName, String dayName, int hour, int minute) {
		return addShutdown(groupName, getDayIndex(dayName), hour, minute);
	}

	private static String removeShutdown(String groupName, int dayIndex, int hour, int minute) {
		ShutdownGroup group = groups.get(groupName);
		if (group == null) return String.format(getLang().groupNotFound, groupName);
		if (dayIndex < 0 || dayIndex > 6) return getLang().invalidDay;
		String time = String.format("%02d:%02d", hour, minute);
		synchronized (group.shutdowns) {
			List<String> times = group.shutdowns.get(String.valueOf(dayIndex));
			if(times != null && times.remove(time)) {
				// Clear from triggeredToday so it doesn't stay as a "ghost" trigger
				triggeredToday.remove(groupName + "_" + dayIndex + "_" + time);
				return String.format(getLang().removedShutdown, groupName, getDayName(dayIndex), time);
			}
		}
		return String.format(getLang().notFoundShutdown, groupName, getDayName(dayIndex), time);
	}

	private static String removeShutdown(String groupName, String dayName, int hour, int minute) {
		return removeShutdown(groupName, getDayIndex(dayName), hour, minute);
	}

	private static void checkScheduledShutdowns() {
		if (server == null) return;
		
		if (shutdownInProgress) {
			if (currentTriggerKey != null && !isTriggerKeyValid(currentTriggerKey)) {
				LOGGER.info("[ShutdownScheduler] Shutdown invalidato (elemento rimosso o gruppo disabilitato), annullamento...");
				cancelPendingShutdown();
			}
			return;
		}

		LocalDateTime now = LocalDateTime.now();
		int dayIndex = now.getDayOfWeek().getValue() - 1;
		LocalTime currentTime = now.toLocalTime();

		// Reset del set dei trigger e del flag di skip a mezzanotte
		if (currentTime.getHour() == 0 && currentTime.getMinute() == 0) {
			if (!triggeredToday.isEmpty()) {
				triggeredToday.clear();
				skipNextShutdown = false;
				LOGGER.info(getLang().resetTriggers);
			}
		}

		// Cicla su tutti i gruppi
		synchronized (groups) {
			for (Map.Entry<String, ShutdownGroup> entry : groups.entrySet()) {
				ShutdownGroup group = entry.getValue();
				if (!group.enabled) continue;

				// We check ALL days to catch cross-day warnings (e.g. 23:55 warnings for 00:05)
				synchronized (group.shutdowns) {
					for (Map.Entry<String, List<String>> dayEntry : group.shutdowns.entrySet()) {
						int scheduledDayIndex = Integer.parseInt(dayEntry.getKey());
						List<String> times = new ArrayList<>(dayEntry.getValue()); // Copia per evitare CME durante l'iterazione
						for (String scheduledTime : times) {
							String triggerKey = entry.getKey() + "_" + scheduledDayIndex + "_" + scheduledTime;
							if (triggeredToday.contains(triggerKey)) continue;

							try {
								String[] parts = scheduledTime.split(":");
								int h = Integer.parseInt(parts[0]);
								int m = Integer.parseInt(parts[1]);

								// Calcola la PROSSIMA occorrenza di questo orario
								LocalDateTime scheduledDT = now.withHour(h).withMinute(m).withSecond(0).withNano(0);
								int currentDI = now.getDayOfWeek().getValue() - 1;
								int daysToAdd = (scheduledDayIndex - currentDI + 7) % 7;
								scheduledDT = scheduledDT.plusDays(daysToAdd);
								
								if (scheduledDT.isBefore(now)) {
									scheduledDT = scheduledDT.plusWeeks(1);
								}

								long minutesUntil = java.time.Duration.between(now, scheduledDT).toMinutes();

								// Trigger se siamo nell'intervallo di avviso
								if (minutesUntil >= 0 && minutesUntil <= warningMinutes) {
									if (skipNextShutdown) {
										skipNextShutdown = false;
										triggeredToday.add(triggerKey);
										LOGGER.info("[ShutdownScheduler] Shutdown saltato (SKIP) per {} il {} alle {}", entry.getKey(), getDayName(scheduledDayIndex), scheduledTime);
			
										server.execute(() -> {
											String msg = language.equals("it")
													? "§eIl prossimo shutdown programmato è stato saltato."
													: "§eNext scheduled shutdown has been skipped.";
											server.getPlayerManager().broadcast(Text.literal(msg), false);
										});
										continue;
									}

									triggeredToday.add(triggerKey);
									currentTriggerKey = triggerKey;
									shutdownInProgress = true;
									LOGGER.info("=== SHUTDOWN ATTIVATO per {} il {} alle {} (tra {} minuti) ===", entry.getKey(), getDayName(scheduledDayIndex), scheduledTime, minutesUntil);
									scheduleShutdown(minutesUntil);
									return; 
								}
							} catch (Exception e) {
								LOGGER.error("Errore nel calcolo shutdown: " + scheduledTime, e);
							}
						}
					}
				}
			}
		}
	}

	private static void scheduleShutdown(long minutesUntil) {
		LOGGER.info(String.format(getLang().schedulingShutdown, minutesUntil));
		currentShutdownTasks.clear();

		server.execute(() -> {
			String plural = language.equals("en") ? (minutesUntil == 1 ? "" : "s") : (minutesUntil == 1 ? "o" : "i");
			String msg = String.format(getLang().shutdownWarning, minutesUntil, plural);
			server.getPlayerManager().broadcast(Text.literal(msg), false);
		});

		// Avviso a 1 minuto se necessario
		if (minutesUntil > 1) {
			currentShutdownTasks.add(scheduler.schedule(() -> {
				LOGGER.info(getLang().oneMinuteWarningLog);
				server.execute(() -> {
					server.getPlayerManager().broadcast(Text.literal(getLang().oneMinuteWarning), false);
				});
			}, minutesUntil - 1, TimeUnit.MINUTES));
		}

		// Shutdown finale
		currentShutdownTasks.add(scheduler.schedule(() -> {
			LOGGER.info(getLang().executingShutdown);
			server.execute(() -> {
				server.getPlayerManager().broadcast(Text.literal(getLang().shuttingDown), false);

				// Aspetta 3 secondi
				currentShutdownTasks.add(scheduler.schedule(() -> {
					LOGGER.info(getLang().stoppingServer);
					server.execute(() -> {
						try {
							server.stop(false);
						} catch (Exception e) {
							LOGGER.error("Errore durante lo stop del server", e);
							System.exit(0);
						}
					});
				}, 3, TimeUnit.SECONDS));
			});
		}, minutesUntil, TimeUnit.MINUTES));
	}

	private static void cancelPendingShutdown() {
		if (!shutdownInProgress) return;
		
		LOGGER.info("[ShutdownScheduler] Annullamento shutdown in corso...");
		synchronized (currentShutdownTasks) {
			for (java.util.concurrent.ScheduledFuture<?> task : currentShutdownTasks) {
				if (task != null && !task.isDone()) {
					task.cancel(false);
				}
			}
			currentShutdownTasks.clear();
		}
		shutdownInProgress = false;
		currentTriggerKey = null;
		
		if (server != null) {
			server.execute(() -> {
				String msg = language.equals("it") ? "§a§l⚠ SHUTDOWN ANNULLATO!" : "§a§l⚠ SHUTDOWN CANCELLED!";
				server.getPlayerManager().broadcast(Text.literal(msg), false);
			});
		}
	}

	private static boolean isTriggerKeyValid(String key) {
		try {
			// Format: GroupName_DayIndex_Time
			int firstUnderscore = key.indexOf('_');
			int lastUnderscore = key.lastIndexOf('_');
			if (firstUnderscore == -1 || lastUnderscore == -1 || firstUnderscore == lastUnderscore) return false;
			
			String groupName = key.substring(0, firstUnderscore);
			String dayIdxStr = key.substring(firstUnderscore + 1, lastUnderscore);
			String time = key.substring(lastUnderscore + 1);
			
			ShutdownGroup group = groups.get(groupName);
			if (group == null || !group.enabled) return false;
			
			synchronized (group.shutdowns) {
				List<String> times = group.shutdowns.get(dayIdxStr);
				return times != null && times.contains(time);
			}
		} catch (Exception e) {
			return false;
		}
	}

	private static void loadConfig() {
		try {
			if (!CONFIG_FILE.exists()) {
				LOGGER.info(getLang().configNotFound);
				CONFIG_FILE.getParentFile().mkdirs();
				groups = new HashMap<>();
				groups.put("default", new ShutdownGroup());
				warningMinutes = 5;
				language = "en";
				saveConfig();
				return;
			}

			LOGGER.info(String.format(getLang().loadingConfig, CONFIG_FILE.getAbsolutePath()));
			FileReader reader = new FileReader(CONFIG_FILE);
			ConfigData data = GSON.fromJson(reader, ConfigData.class);
			reader.close();

			if (data != null) {
				if (data.groups != null) {
					groups.clear();
					groups.putAll(data.groups);
				}
				
				// Ensure all group shutdowns are initialized
				for (ShutdownGroup g : groups.values()) {
					if (g.shutdowns == null) g.shutdowns = new HashMap<>();
				}

				if (data.shutdowns != null && !data.shutdowns.isEmpty()) {
					ShutdownGroup defaultGroup = groups.computeIfAbsent("default", k -> new ShutdownGroup());
					for (Map.Entry<?, List<String>> entry : data.shutdowns.entrySet()) {
						defaultGroup.shutdowns.put(String.valueOf(entry.getKey()), entry.getValue());
					}
					LOGGER.info("Migrated old top-level shutdowns to 'default' group.");
				}

				if (groups.isEmpty()) {
					groups.put("default", new ShutdownGroup());
				}

				warningMinutes = data.warning_minutes;
				language = data.language != null ? data.language : "en";
				LOGGER.info(String.format(getLang().warningMinutes, warningMinutes));
			} else {
				LOGGER.warn(getLang().configNull);
				groups = new java.util.concurrent.ConcurrentHashMap<>();
                groups.put("default", new ShutdownGroup());
			}
		} catch (Exception e) {
			LOGGER.error("Errore nel caricamento della configurazione!", e);
			e.printStackTrace();
		}
	}

	private static void saveConfig() {
		if (scheduler == null) return;
		ConfigData data = buildConfigDataSnapshot();
		scheduler.submit(() -> {
			try {
				CONFIG_FILE.getParentFile().mkdirs();
				try (FileWriter w = new FileWriter(CONFIG_FILE)) {
					GSON.toJson(data, w);
				}
				LOGGER.info("[ShutdownScheduler] Config saved asynchronously.");
			} catch (Exception e) {
				LOGGER.error("Errore nel salvataggio della configurazione!", e);
			}
		});
	}

	// Semplice classe per il gruppo
	public static class ShutdownGroup {
		public boolean enabled = true;
		public Map<String, List<String>> shutdowns = new HashMap<>();
	}

	// Classe per serializzazione JSON
	public static class ConfigData {
		public Map<String, List<String>> shutdowns; // Per migrazione
		public Map<String, ShutdownGroup> groups = new HashMap<>();
		public int warning_minutes = 5;
		public String language = "en";
		public long server_time = 0; // Epoch millis for client sync

		public void fix() {
			if (groups == null) groups = new HashMap<>();
			for (ShutdownGroup g : groups.values()) {
				if (g.shutdowns == null) g.shutdowns = new HashMap<>();
			}
		}
	}

	// Classe per le traduzioni
	private static class Lang {
		public final String[] days;
		public final String initMessage;
		public final String configLoaded;
		public final String serverStarted;
		public final String addedShutdown;
		public final String removedShutdown;
		public final String notFoundShutdown;
		public final String listHeader;
		public final String noShutdowns;
		public final String configReloaded;
		public final String debugInfo;
		public final String forceShutdown;
		public final String shutdownWarning;
		public final String oneMinuteWarning;
		public final String shuttingDown;
		public final String configNotFound;
		public final String loadingConfig;
		public final String loadedDays;
		public final String noShutdownsInConfig;
		public final String warningMinutes;
		public final String configNull;
		public final String configSaved;
		public final String resetTriggers;
		public final String shutdownActivated;
		public final String schedulingShutdown;
		public final String oneMinuteWarningLog;
		public final String executingShutdown;
		public final String stoppingServer;
		public final String languageChanged;
		public final String availableLanguages;
		public final String groupCreated;
		public final String groupExists;
		public final String groupToggled;
		public final String groupNotFound;
		public final String groupDeleted;
		public final String groupsListHeader;
		public final String invalidDay;

		public Lang(String[] days, String initMessage, String configLoaded, String serverStarted,
					String addedShutdown, String removedShutdown, String notFoundShutdown,
					String listHeader, String noShutdowns, String configReloaded, String debugInfo,
					String forceShutdown, String shutdownWarning, String oneMinuteWarning,
					String shuttingDown, String configNotFound, String loadingConfig,
					String loadedDays, String noShutdownsInConfig, String warningMinutes,
					String configNull, String configSaved, String resetTriggers,
					String shutdownActivated, String schedulingShutdown, String oneMinuteWarningLog,
					String executingShutdown, String stoppingServer, String languageChanged,
					String availableLanguages, String groupCreated, String groupExists,
					String groupToggled, String groupNotFound, String groupDeleted,
					String groupsListHeader, String invalidDay) {
			this.days = days;
			this.initMessage = initMessage;
			this.configLoaded = configLoaded;
			this.serverStarted = serverStarted;
			this.addedShutdown = addedShutdown;
			this.removedShutdown = removedShutdown;
			this.notFoundShutdown = notFoundShutdown;
			this.listHeader = listHeader;
			this.noShutdowns = noShutdowns;
			this.configReloaded = configReloaded;
			this.debugInfo = debugInfo;
			this.forceShutdown = forceShutdown;
			this.shutdownWarning = shutdownWarning;
			this.oneMinuteWarning = oneMinuteWarning;
			this.shuttingDown = shuttingDown;
			this.configNotFound = configNotFound;
			this.loadingConfig = loadingConfig;
			this.loadedDays = loadedDays;
			this.noShutdownsInConfig = noShutdownsInConfig;
			this.warningMinutes = warningMinutes;
			this.configNull = configNull;
			this.configSaved = configSaved;
			this.resetTriggers = resetTriggers;
			this.shutdownActivated = shutdownActivated;
			this.schedulingShutdown = schedulingShutdown;
			this.oneMinuteWarningLog = oneMinuteWarningLog;
			this.executingShutdown = executingShutdown;
			this.stoppingServer = stoppingServer;
			this.languageChanged = languageChanged;
			this.availableLanguages = availableLanguages;
			this.groupCreated = groupCreated;
			this.groupExists = groupExists;
			this.groupToggled = groupToggled;
			this.groupNotFound = groupNotFound;
			this.groupDeleted = groupDeleted;
			this.groupsListHeader = groupsListHeader;
			this.invalidDay = invalidDay;
		}
	}
}