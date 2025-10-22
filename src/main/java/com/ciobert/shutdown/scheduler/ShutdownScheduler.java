package com.ciobert.shutdown.scheduler;

import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.minecraft.server.MinecraftServer;
import net.minecraft.text.Text;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.time.DayOfWeek;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

import static net.minecraft.server.command.CommandManager.argument;
import static net.minecraft.server.command.CommandManager.literal;

public class ShutdownScheduler implements ModInitializer {

	public static final String MOD_ID = "shutdown-scheduler";
	public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

	private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
	private static final File CONFIG_FILE = new File("config/shutdown_scheduler.json");

	// Usa indici numerici invece di nomi giorni (0=lunedì, 6=domenica)
	private static Map<Integer, List<String>> shutdowns = new HashMap<>();

	private static int warningMinutes = 5;
	private static String language = "en"; // Default: inglese
	private static MinecraftServer server;
	private static ScheduledExecutorService scheduler;
	private static Set<String> triggeredToday = new HashSet<>();
	private static boolean shutdownInProgress = false;
	private static boolean skipNextShutdown = false;

	// Traduzioni
	private static final Map<String, Lang> LANGUAGES = new HashMap<>();

	static {
		// Inglese
		LANGUAGES.put("en", new Lang(
				new String[]{"monday", "tuesday", "wednesday", "thursday", "friday", "saturday", "sunday"},
				"Initializing Shutdown Scheduler...",
				"Configuration loaded: %d scheduled shutdowns",
				"Server started, Shutdown Scheduler active!",
				"§aAdded shutdown for %s at %s",
				"§cRemoved shutdown for %s at %s",
				"§eNo shutdown found for %s at %s",
				"§e=== Scheduled Shutdowns ===\n§7(Total days: %d)\n",
				"§cNo shutdowns scheduled!",
				"§aConfiguration reloaded.",
				"§e=== Debug Info ===\n§fDay: §b%s\n§fTime: §b%s\n§fServer active: §b%s\n§fShutdown in progress: §b%s\n§fTriggered today: §b%d",
				"§aForced shutdown in %d minutes",
				"§c§l⚠ The server will shut down in %d minute%s!",
				"§e§l⚠ The server will shut down in 1 minute!",
				"§4§l⚠ SERVER SHUTTING DOWN...",
				"Configuration file not found, creating new file...",
				"Loading configuration from: %s",
				"Loaded %d days with scheduled shutdowns",
				"No shutdowns found in config",
				"Warning minutes: %d",
				"Config data is null!",
				"Configuration saved: %d shutdowns",
				"Daily triggers reset",
				"=== SHUTDOWN ACTIVATED for %s at %s (in %d minutes) ===",
				"Shutdown scheduled in %d minutes",
				"Warning: 1 minute until shutdown",
				"EXECUTING SHUTDOWN",
				"Stopping server...",
				"§aLanguage changed to English",
				"Available languages: en, it"
		));

		// Italiano
		LANGUAGES.put("it", new Lang(
				new String[]{"lunedi", "martedi", "mercoledi", "giovedi", "venerdi", "sabato", "domenica"},
				"Inizializzazione Shutdown Scheduler...",
				"Configurazione caricata: %d shutdown programmati",
				"Server avviato, Shutdown Scheduler attivo!",
				"§aAggiunto spegnimento per %s alle %s",
				"§cRimosso spegnimento per %s alle %s",
				"§eNon trovato nessuno spegnimento per %s alle %s",
				"§e=== Shutdown Programmati ===\n§7(Totale giorni: %d)\n",
				"§cNessuno shutdown programmato!",
				"§aConfigurazione ricaricata.",
				"§e=== Info Debug ===\n§fGiorno: §b%s\n§fOra: §b%s\n§fServer attivo: §b%s\n§fShutdown in corso: §b%s\n§fTriggered oggi: §b%d",
				"§aShutdown forzato tra %d minuti",
				"§c§l⚠ Il server si spegnerà tra %d minut%s!",
				"§e§l⚠ Il server si spegnerà tra 1 minuto!",
				"§4§l⚠ SPEGNIMENTO DEL SERVER IN CORSO...",
				"File di configurazione non trovato, creazione nuovo file...",
				"Caricamento configurazione da: %s",
				"Caricati %d giorni con shutdown programmati",
				"Nessuno shutdown trovato nel config",
				"Warning minutes: %d",
				"Config data è null!",
				"Configurazione salvata: %d shutdown",
				"Reset trigger giornalieri",
				"=== SHUTDOWN ATTIVATO per %s alle %s (tra %d minuti) ===",
				"Programmazione shutdown tra %d minuti",
				"Avviso: 1 minuto allo shutdown",
				"ESECUZIONE SHUTDOWN",
				"Stop del server...",
				"§aLingua cambiata in Italiano",
				"Lingue disponibili: en, it"
		));
	}

	private static Lang getLang() {
		return LANGUAGES.getOrDefault(language, LANGUAGES.get("en"));
	}

	private static String[] getDays() {
		return getLang().days;
	}

	// Converte nome giorno in indice (0-6)
	private static int getDayIndex(String dayName) {
		String[] days = getDays();
		for (int i = 0; i < days.length; i++) {
			if (days[i].equalsIgnoreCase(dayName)) {
				return i;
			}
		}
		return -1;
	}

	// Ottiene il nome del giorno dall'indice
	private static String getDayName(int index) {
		return getDays()[index];
	}

	@Override
	public void onInitialize() {
		LOGGER.info(getLang().initMessage);
		loadConfig();

		LOGGER.info(String.format(getLang().configLoaded, shutdowns.values().stream().mapToInt(List::size).sum()));

		ServerLifecycleEvents.SERVER_STARTED.register(s -> {
			server = s;
			LOGGER.info(getLang().serverStarted);
		});

		CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) -> {
			dispatcher.register(literal("shutdownscheduler")
					.requires(src -> src.hasPermissionLevel(4))

					// Add
					.then(literal("add")
							.then(argument("day", StringArgumentType.word())
									.suggests((ctx, builder) -> {
										for(String d : getDays()) builder.suggest(d);
										return builder.buildFuture();
									})
									.then(argument("hour", IntegerArgumentType.integer(0,23))
											.then(argument("minute", IntegerArgumentType.integer(0,59))
													.executes(ctx -> {
														String dayName = StringArgumentType.getString(ctx, "day").toLowerCase();
														int hour = IntegerArgumentType.getInteger(ctx, "hour");
														int minute = IntegerArgumentType.getInteger(ctx, "minute");
														String time = String.format("%02d:%02d", hour, minute);

														int dayIndex = getDayIndex(dayName);
														if (dayIndex == -1) {
															ctx.getSource().sendFeedback(() -> Text.literal("§cGiorno non valido!"), false);
															return 0;
														}

														LOGGER.info("=== COMANDO ADD ESEGUITO ===");
														LOGGER.info("Giorno: {} ({}), Ora: {}", dayName, dayIndex, time);
														LOGGER.info("Map prima: {}", shutdowns);

														shutdowns.computeIfAbsent(dayIndex, k -> new ArrayList<>()).add(time);

														LOGGER.info("Map dopo: {}", shutdowns);

														saveConfig();
														String msg = String.format(getLang().addedShutdown, dayName, time);
														ctx.getSource().sendFeedback(() -> Text.literal(msg), false);
														return 1;
													})
											)
									)
							)
					)

					// Remove
					.then(literal("remove")
							.then(argument("day", StringArgumentType.word())
									.suggests((ctx, builder) -> {
										for(String d : getDays()) builder.suggest(d);
										return builder.buildFuture();
									})
									.then(argument("hour", IntegerArgumentType.integer(0,23))
											.then(argument("minute", IntegerArgumentType.integer(0,59))
													.executes(ctx -> {
														String dayName = StringArgumentType.getString(ctx, "day").toLowerCase();
														int hour = IntegerArgumentType.getInteger(ctx, "hour");
														int minute = IntegerArgumentType.getInteger(ctx, "minute");
														String time = String.format("%02d:%02d", hour, minute);

														int dayIndex = getDayIndex(dayName);
														if (dayIndex == -1) {
															ctx.getSource().sendFeedback(() -> Text.literal("§cGiorno non valido!"), false);
															return 0;
														}

														List<String> times = shutdowns.get(dayIndex);
														String msg;
														if(times != null && times.remove(time)) {
															saveConfig();
															msg = String.format(getLang().removedShutdown, dayName, time);
														} else {
															msg = String.format(getLang().notFoundShutdown, dayName, time);
														}
														String finalMsg = msg;
														ctx.getSource().sendFeedback(() -> Text.literal(finalMsg), false);
														return 1;
													})
											)
									)
							)
					)

					// List
					.then(literal("list").executes(ctx -> {
						LOGGER.info("=== COMANDO LIST ESEGUITO ===");
						LOGGER.info("Shutdowns map size: {}", shutdowns.size());
						LOGGER.info("Shutdowns map: {}", shutdowns);

						StringBuilder sb = new StringBuilder(String.format(getLang().listHeader, shutdowns.size()));

						if (shutdowns.isEmpty()) {
							sb.append(getLang().noShutdowns);
							LOGGER.warn("Map degli shutdown è VUOTA!");
						} else {
							LOGGER.info("Iterazione sui giorni...");
							for (int i = 0; i < 7; i++) {
								List<String> times = shutdowns.get(i);
								String dayName = getDayName(i);
								LOGGER.info("Giorno {} ({}): {}", i, dayName, times);
								if (times != null && !times.isEmpty()) {
									sb.append("§b").append(dayName).append("§f: ");
									sb.append(String.join(", ", times)).append("\n");
								}
							}
						}
						String message = sb.toString();
						LOGGER.info("Messaggio da inviare: {}", message);
						ctx.getSource().sendFeedback(() -> Text.literal(message), false);
						return 1;
					}))

					// Reload
					.then(literal("reload").executes(ctx -> {
						loadConfig();
						triggeredToday.clear();
						shutdownInProgress = false;
						LOGGER.info("Configurazione ricaricata");
						ctx.getSource().sendFeedback(() -> Text.literal(getLang().configReloaded), false);
						return 1;
					}))

					// Skip next shutdown
					.then(literal("skipnext").executes(ctx -> {
						skipNextShutdown = !skipNextShutdown;
						String msg = skipNextShutdown
								? (language.equals("it") ? "§eIl prossimo shutdown è stato temporaneamente disattivato." : "§eNext scheduled shutdown temporarily disabled.")
								: (language.equals("it") ? "§aIl prossimo shutdown è stato riattivato." : "§aNext scheduled shutdown re-enabled.");
						ctx.getSource().sendFeedback(() -> Text.literal(msg), false);
						return 1;
					}))


					// Language
					.then(literal("language")
							.then(argument("lang", StringArgumentType.word())
									.suggests((ctx, builder) -> {
										builder.suggest("en");
										builder.suggest("it");
										return builder.buildFuture();
									})
									.executes(ctx -> {
										String newLang = StringArgumentType.getString(ctx, "lang").toLowerCase();
										if (!LANGUAGES.containsKey(newLang)) {
											ctx.getSource().sendFeedback(() -> Text.literal(getLang().availableLanguages), false);
											return 0;
										}

										language = newLang;
										saveConfig();
										String msg = getLang().languageChanged;
										ctx.getSource().sendFeedback(() -> Text.literal(msg), false);
										return 1;
									})
							)
					)

					// Test (per debug)
					.then(literal("test").executes(ctx -> {
						LocalDateTime now = LocalDateTime.now();
						int dayIndex = now.getDayOfWeek().getValue() - 1;
						String dayName = getDayName(dayIndex);
						String time = now.format(DateTimeFormatter.ofPattern("HH:mm"));

						String msg = String.format(getLang().debugInfo,
								dayName, time, (server != null), shutdownInProgress, triggeredToday.size());
						ctx.getSource().sendFeedback(() -> Text.literal(msg), false);
						return 1;
					}))

					// Force shutdown (per test)
					.then(literal("force")
							.then(argument("minutes", IntegerArgumentType.integer(0,10))
									.executes(ctx -> {
										int minutes = IntegerArgumentType.getInteger(ctx, "minutes");
										scheduleShutdown(minutes);
										String msg = String.format(getLang().forceShutdown, minutes);
										ctx.getSource().sendFeedback(() -> Text.literal(msg), false);
										return 1;
									})
							)
					)
			);
		});

		// Scheduler principale
		scheduler = Executors.newSingleThreadScheduledExecutor();
		scheduler.scheduleAtFixedRate(() -> {
			try {
				checkScheduledShutdowns();
			} catch (Exception e) {
				LOGGER.error("Errore nel controllo degli shutdown programmati", e);
			}
		}, 0, 30, TimeUnit.SECONDS);

		LOGGER.info("=== Shutdown Scheduler caricato con successo! ===");
	}

	private static void checkScheduledShutdowns() {
		if (server == null || shutdownInProgress) return;

		LocalDateTime now = LocalDateTime.now();
		DayOfWeek today = now.getDayOfWeek();
		int dayIndex = today.getValue() - 1;
		String dayName = getDayName(dayIndex);
		LocalTime currentTime = now.toLocalTime();

		// Reset del set dei trigger e del flag di skip a mezzanotte
		if (currentTime.getHour() == 0 && currentTime.getMinute() == 0) {
			triggeredToday.clear();
			skipNextShutdown = false; // reset automatico ogni giorno
			LOGGER.info(getLang().resetTriggers);
		}

		List<String> times = shutdowns.getOrDefault(dayIndex, Collections.emptyList());
		if (times.isEmpty()) return;

		for (String scheduledTime : times) {
			String triggerKey = dayIndex + "_" + scheduledTime;

			if (triggeredToday.contains(triggerKey)) continue;

			try {
				LocalTime scheduled = LocalTime.parse(scheduledTime, DateTimeFormatter.ofPattern("HH:mm"));
				long minutesUntil = java.time.Duration.between(currentTime, scheduled).toMinutes();

				LOGGER.debug("Check: {} - Ora: {} - Programmato: {} - Minuti: {}",
						dayName, currentTime.format(DateTimeFormatter.ofPattern("HH:mm")), scheduledTime, minutesUntil);

				// Trigger se siamo nell'intervallo giusto
				if (minutesUntil >= 0 && minutesUntil <= warningMinutes) {

					// Se è attivo lo skip, consumalo e marca il trigger come fatto
					if (skipNextShutdown) {
						skipNextShutdown = false; // consumato: vale solo per il prossimo shutdown
						triggeredToday.add(triggerKey); // evita che venga riprovato nello stesso intervallo
						LOGGER.info(String.format("[ShutdownScheduler] Prossimo shutdown per %s alle %s saltato (skip).", dayName, scheduledTime));

						// Notifica ai giocatori che lo shutdown è stato saltato (opzionale)
						server.execute(() -> {
							String msg = language.equals("it")
									? "§eIl prossimo shutdown programmato è stato saltato."
									: "§eNext scheduled shutdown has been skipped.";
							server.getPlayerManager().broadcast(Text.literal(msg), false);
						});

						// non impostare shutdownInProgress, non chiamare scheduleShutdown
						continue;
					}

					// Altrimenti procedi normalmente
					triggeredToday.add(triggerKey);
					shutdownInProgress = true;
					LOGGER.info(String.format(getLang().shutdownActivated, dayName, scheduledTime, minutesUntil));
					scheduleShutdown(minutesUntil);
					break;
				}
			} catch (Exception e) {
				LOGGER.error("Errore nel parsing del tempo: " + scheduledTime, e);
			}
		}
	}



	private static void scheduleShutdown(long minutesUntil) {
		LOGGER.info(String.format(getLang().schedulingShutdown, minutesUntil));

		server.execute(() -> {
			String plural = language.equals("en") ? (minutesUntil == 1 ? "" : "s") : (minutesUntil == 1 ? "o" : "i");
			String msg = String.format(getLang().shutdownWarning, minutesUntil, plural);
			server.getPlayerManager().broadcast(Text.literal(msg), false);
		});

		// Avviso a 1 minuto se necessario
		if (minutesUntil > 1) {
			scheduler.schedule(() -> {
				LOGGER.info(getLang().oneMinuteWarningLog);
				server.execute(() -> {
					server.getPlayerManager().broadcast(Text.literal(getLang().oneMinuteWarning), false);
				});
			}, minutesUntil - 1, TimeUnit.MINUTES);
		}

		// Shutdown finale
		scheduler.schedule(() -> {
			LOGGER.info(getLang().executingShutdown);
			server.execute(() -> {
				server.getPlayerManager().broadcast(Text.literal(getLang().shuttingDown), false);

				// Aspetta 3 secondi
				scheduler.schedule(() -> {
					LOGGER.info(getLang().stoppingServer);
					server.execute(() -> {
						try {
							server.stop(false);
						} catch (Exception e) {
							LOGGER.error("Errore durante lo stop del server", e);
							System.exit(0);
						}
					});
				}, 3, TimeUnit.SECONDS);
			});
		}, minutesUntil, TimeUnit.MINUTES);
	}

	private static void loadConfig() {
		try {
			if (!CONFIG_FILE.exists()) {
				LOGGER.info(getLang().configNotFound);
				CONFIG_FILE.getParentFile().mkdirs();
				shutdowns = new HashMap<>();
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
				if (data.shutdowns != null) {
					shutdowns = data.shutdowns;
					LOGGER.info(String.format(getLang().loadedDays, shutdowns.size()));
					shutdowns.forEach((dayIdx, times) ->
							LOGGER.info("  - Day {}: {}", dayIdx, times));
				} else {
					shutdowns = new HashMap<>();
					LOGGER.warn(getLang().noShutdownsInConfig);
				}

				warningMinutes = data.warning_minutes;
				language = data.language != null ? data.language : "en";
				LOGGER.info(String.format(getLang().warningMinutes, warningMinutes));
				LOGGER.info("Language: {}", language);
			} else {
				LOGGER.warn(getLang().configNull);
				shutdowns = new HashMap<>();
			}
		} catch (Exception e) {
			LOGGER.error("Errore nel caricamento della configurazione!", e);
			e.printStackTrace();
		}
	}

	private static void saveConfig() {
		try {
			CONFIG_FILE.getParentFile().mkdirs();
			FileWriter w = new FileWriter(CONFIG_FILE);
			ConfigData data = new ConfigData();
			data.shutdowns = shutdowns;
			data.warning_minutes = warningMinutes;
			data.language = language;
			GSON.toJson(data, w);
			w.close();
			LOGGER.info(String.format(getLang().configSaved,
					shutdowns.values().stream().mapToInt(List::size).sum()));
		} catch (Exception e) {
			LOGGER.error("Errore nel salvataggio della configurazione!", e);
			e.printStackTrace();
		}
	}

	// Classe per serializzazione JSON
	private static class ConfigData {
		public Map<Integer, List<String>> shutdowns = new HashMap<>();
		public int warning_minutes = 5;
		public String language = "en";
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

		public Lang(String[] days, String initMessage, String configLoaded, String serverStarted,
					String addedShutdown, String removedShutdown, String notFoundShutdown,
					String listHeader, String noShutdowns, String configReloaded, String debugInfo,
					String forceShutdown, String shutdownWarning, String oneMinuteWarning,
					String shuttingDown, String configNotFound, String loadingConfig,
					String loadedDays, String noShutdownsInConfig, String warningMinutes,
					String configNull, String configSaved, String resetTriggers,
					String shutdownActivated, String schedulingShutdown, String oneMinuteWarningLog,
					String executingShutdown, String stoppingServer, String languageChanged,
					String availableLanguages) {
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
		}
	}
}