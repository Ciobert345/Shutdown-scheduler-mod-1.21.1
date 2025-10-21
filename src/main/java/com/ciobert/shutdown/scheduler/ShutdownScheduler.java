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
	private static Map<String, List<String>> shutdowns = new HashMap<>();
	private static int warningMinutes = 5;
	private static MinecraftServer server;
	private static ScheduledExecutorService scheduler;
	private static Set<String> triggeredToday = new HashSet<>();
	private static boolean shutdownInProgress = false;

	private static final String[] DAYS = {"lunedi","martedi","mercoledi","giovedi","venerdi","sabato","domenica"};

	@Override
	public void onInitialize() {
		LOGGER.info("=== Inizializzazione Shutdown Scheduler ===");
		loadConfig();

		LOGGER.info("Configurazione caricata: {} shutdown programmati",
				shutdowns.values().stream().mapToInt(List::size).sum());

		ServerLifecycleEvents.SERVER_STARTED.register(s -> {
			server = s;
			LOGGER.info("Server avviato, Shutdown Scheduler attivo!");
		});

		CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) -> {
			dispatcher.register(literal("shutdownscheduler")
					.requires(src -> src.hasPermissionLevel(4))

					// Add
					.then(literal("add")
							.then(argument("day", StringArgumentType.word())
									.suggests((ctx, builder) -> {
										for(String d : DAYS) builder.suggest(d);
										return builder.buildFuture();
									})
									.then(argument("hour", IntegerArgumentType.integer(0,23))
											.then(argument("minute", IntegerArgumentType.integer(0,59))
													.executes(ctx -> {
														String day = StringArgumentType.getString(ctx, "day").toLowerCase();
														int hour = IntegerArgumentType.getInteger(ctx, "hour");
														int minute = IntegerArgumentType.getInteger(ctx, "minute");
														String time = String.format("%02d:%02d", hour, minute);

														LOGGER.info("=== COMANDO ADD ESEGUITO ===");
														LOGGER.info("Giorno: {}, Ora: {}", day, time);
														LOGGER.info("Map prima: {}", shutdowns);

														shutdowns.computeIfAbsent(day, k -> new ArrayList<>()).add(time);

														LOGGER.info("Map dopo: {}", shutdowns);

														saveConfig();
														LOGGER.info("Aggiunto shutdown: {} alle {}", day, time);
														ctx.getSource().sendFeedback(() -> Text.literal("§aAggiunto spegnimento per " + day + " alle " + time), false);
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
										for(String d : DAYS) builder.suggest(d);
										return builder.buildFuture();
									})
									.then(argument("hour", IntegerArgumentType.integer(0,23))
											.then(argument("minute", IntegerArgumentType.integer(0,59))
													.executes(ctx -> {
														String day = StringArgumentType.getString(ctx, "day").toLowerCase();
														int hour = IntegerArgumentType.getInteger(ctx, "hour");
														int minute = IntegerArgumentType.getInteger(ctx, "minute");
														String time = String.format("%02d:%02d", hour, minute);

														List<String> times = shutdowns.get(day);
														if(times != null && times.remove(time)) {
															saveConfig();
															LOGGER.info("Rimosso shutdown: {} alle {}", day, time);
															ctx.getSource().sendFeedback(() -> Text.literal("§cRimosso spegnimento per " + day + " alle " + time), false);
														} else {
															ctx.getSource().sendFeedback(() -> Text.literal("§eNon trovato nessuno spegnimento per " + day + " alle " + time), false);
														}
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

						StringBuilder sb = new StringBuilder("§e=== Shutdown Programmati ===\n");
						sb.append("§7(Totale giorni: ").append(shutdowns.size()).append(")\n");

						if (shutdowns.isEmpty()) {
							sb.append("§cNessuno shutdown programmato!");
							LOGGER.warn("Map degli shutdown è VUOTA!");
						} else {
							LOGGER.info("Iterazione sui giorni...");
							for (String d : DAYS) {
								List<String> times = shutdowns.get(d);
								LOGGER.info("Giorno {}: {}", d, times);
								if (times != null && !times.isEmpty()) {
									sb.append("§b").append(d).append("§f: ");
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
						ctx.getSource().sendFeedback(() -> Text.literal("§aConfigurazione ricaricata."), false);
						return 1;
					}))

					// Test (per debug)
					.then(literal("test").executes(ctx -> {
						LocalDateTime now = LocalDateTime.now();
						String day = DAYS[now.getDayOfWeek().getValue()-1];
						String time = now.format(DateTimeFormatter.ofPattern("HH:mm"));

						ctx.getSource().sendFeedback(() -> Text.literal(
								"§e=== Debug Info ===\n" +
										"§fGiorno: §b" + day + "\n" +
										"§fOra: §b" + time + "\n" +
										"§fServer attivo: §b" + (server != null) + "\n" +
										"§fShutdown in corso: §b" + shutdownInProgress + "\n" +
										"§fTriggered oggi: §b" + triggeredToday.size()
						), false);
						return 1;
					}))

					// Force shutdown (per test)
					.then(literal("force")
							.then(argument("minutes", IntegerArgumentType.integer(0,10))
									.executes(ctx -> {
										int minutes = IntegerArgumentType.getInteger(ctx, "minutes");
										scheduleShutdown(minutes);
										ctx.getSource().sendFeedback(() -> Text.literal("§aShutdown forzato tra " + minutes + " minuti"), false);
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
		String day = DAYS[today.getValue()-1];
		LocalTime currentTime = now.toLocalTime();

		// Reset del set dei trigger a mezzanotte
		if (currentTime.getHour() == 0 && currentTime.getMinute() == 0) {
			triggeredToday.clear();
			LOGGER.info("Reset trigger giornalieri");
		}

		List<String> times = shutdowns.getOrDefault(day, Collections.emptyList());
		if (times.isEmpty()) return;

		for (String scheduledTime : times) {
			String triggerKey = day + "_" + scheduledTime;

			if (triggeredToday.contains(triggerKey)) continue;

			try {
				LocalTime scheduled = LocalTime.parse(scheduledTime, DateTimeFormatter.ofPattern("HH:mm"));
				long minutesUntil = java.time.Duration.between(currentTime, scheduled).toMinutes();

				LOGGER.debug("Check: {} - Ora: {} - Programmato: {} - Minuti: {}",
						day, currentTime.format(DateTimeFormatter.ofPattern("HH:mm")), scheduledTime, minutesUntil);

				// Trigger se siamo nell'intervallo giusto
				if (minutesUntil >= 0 && minutesUntil <= warningMinutes) {
					triggeredToday.add(triggerKey);
					shutdownInProgress = true;
					LOGGER.info("=== SHUTDOWN ATTIVATO per {} alle {} (tra {} minuti) ===", day, scheduledTime, minutesUntil);
					scheduleShutdown(minutesUntil);
					break;
				}
			} catch (Exception e) {
				LOGGER.error("Errore nel parsing del tempo: " + scheduledTime, e);
			}
		}
	}

	private static void scheduleShutdown(long minutesUntil) {
		LOGGER.info("Programmazione shutdown tra {} minuti", minutesUntil);

		server.execute(() -> {
			server.getPlayerManager().broadcast(
					Text.literal("§c§l⚠ Il server si spegnerà tra " + minutesUntil + " minut" + (minutesUntil == 1 ? "o" : "i") + "!"),
					false
			);
		});

		// Avviso a 1 minuto se necessario
		if (minutesUntil > 1) {
			scheduler.schedule(() -> {
				LOGGER.info("Avviso: 1 minuto allo shutdown");
				server.execute(() -> {
					server.getPlayerManager().broadcast(
							Text.literal("§e§l⚠ Il server si spegnerà tra 1 minuto!"),
							false
					);
				});
			}, minutesUntil - 1, TimeUnit.MINUTES);
		}

		// Shutdown finale
		scheduler.schedule(() -> {
			LOGGER.info("ESECUZIONE SHUTDOWN");
			server.execute(() -> {
				server.getPlayerManager().broadcast(
						Text.literal("§4§l⚠ SPEGNIMENTO DEL SERVER IN CORSO..."),
						false
				);

				// Aspetta 3 secondi
				scheduler.schedule(() -> {
					LOGGER.info("Stop del server...");
					server.execute(() -> {
						try {
							server.stop(false);
						} catch (Exception e) {
							LOGGER.error("Errore durante lo stop del server", e);
							// Fallback
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
				LOGGER.info("File di configurazione non trovato, creazione nuovo file...");
				CONFIG_FILE.getParentFile().mkdirs();
				shutdowns = new HashMap<>();
				warningMinutes = 5;
				saveConfig();
				return;
			}

			LOGGER.info("Caricamento configurazione da: {}", CONFIG_FILE.getAbsolutePath());
			FileReader reader = new FileReader(CONFIG_FILE);
			ConfigData data = GSON.fromJson(reader, ConfigData.class);
			reader.close();

			if (data != null) {
				if (data.shutdowns != null) {
					shutdowns = data.shutdowns;
					LOGGER.info("Caricati {} giorni con shutdown programmati", shutdowns.size());
					shutdowns.forEach((day, times) ->
							LOGGER.info("  - {}: {}", day, times));
				} else {
					shutdowns = new HashMap<>();
					LOGGER.warn("Nessuno shutdown trovato nel config");
				}

				warningMinutes = data.warning_minutes;
				LOGGER.info("Warning minutes: {}", warningMinutes);
			} else {
				LOGGER.warn("Config data è null!");
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
			GSON.toJson(data, w);
			w.close();
			LOGGER.info("Configurazione salvata: {} shutdown",
					shutdowns.values().stream().mapToInt(List::size).sum());
		} catch (Exception e) {
			LOGGER.error("Errore nel salvataggio della configurazione!", e);
			e.printStackTrace();
		}
	}

	// Classe per serializzazione JSON
	private static class ConfigData {
		public Map<String, List<String>> shutdowns = new HashMap<>();
		public int warning_minutes = 5;
	}
}