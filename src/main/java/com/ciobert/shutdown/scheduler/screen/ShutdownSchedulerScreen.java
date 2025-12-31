package com.ciobert.shutdown.scheduler.screen;

import com.ciobert.shutdown.scheduler.Networking;
import com.ciobert.shutdown.scheduler.ShutdownScheduler;
import com.google.gson.Gson;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.gui.widget.ElementListWidget;
import net.minecraft.text.Text;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import net.minecraft.client.gui.widget.TextFieldWidget;
import net.minecraft.client.gui.screen.narration.NarrationMessageBuilder;

public class ShutdownSchedulerScreen extends Screen {
    private ShutdownScheduler.ConfigData config;
    private GroupListWidget list;
    private TextFieldWidget groupNameField;

    public ShutdownSchedulerScreen(String json) {
        super(Text.literal("Shutdown Scheduler Management"));
        this.config = ShutdownScheduler.GSON.fromJson(json, ShutdownScheduler.ConfigData.class);
        if (this.config != null) this.config.fix();
    }

    public void refreshConfig(String json) {
        this.config = ShutdownScheduler.GSON.fromJson(json, ShutdownScheduler.ConfigData.class);
        if (this.config != null) this.config.fix();
        this.clearAndInit();
    }

    public void updateConfig(ShutdownScheduler.ConfigData newConfig) {
        this.config = newConfig;
        this.clearAndInit();
    }

    @Override
    protected void init() {
        // Layout:
        // 0-35: Header
        // 40-55: Section Label
        // 55-75: New Group Section
        // 80-95: List Header/Spacing
        // 100-(height-50): List
        // (height-45)-end: Settings

        // New Group Section (TOP)
        int topY = 55;
        this.groupNameField = new TextFieldWidget(this.textRenderer, this.width / 2 - 155, topY, 150, 20, getLabel("group_placeholder", "Group Name"));
        this.addDrawableChild(groupNameField);

        this.addDrawableChild(ButtonWidget.builder(getLabel("add_group", "Add Group"), button -> {
            String name = groupNameField.getText();
            if (!name.isEmpty()) {
                System.out.println("[ShutdownScheduler] Sending CREATE_GROUP: " + name);
                sendAction("CREATE_GROUP", name);
                groupNameField.setText("");
            } else {
                net.minecraft.client.toast.SystemToast.add(client.getToastManager(), net.minecraft.client.toast.SystemToast.Type.WORLD_ACCESS_FAILURE, getLabel("error_title", "Error"), getLabel("error_empty", "Empty Name!"));
            }
        }).dimensions(this.width / 2 + 5, topY, 150, 20).build());

        // List Section (MIDDLE)
        // Top=85, Bottom=height-50 => Height = height - 135
        this.list = new GroupListWidget(this.client, this.width, this.height - 135, 85, 25);
        this.addSelectableChild(this.list);

        if (config != null && config.groups != null) {
            config.groups.forEach((name, group) -> {
                this.list.addEntry(new GroupEntry(name, group));
            });
        }

        // Settings Section (BOTTOM)
        int settingsY = this.height - 30;
        String langLabel = ("it".equals(config.language) ? "Lingua: " : "Lang: ") + config.language.toUpperCase();
        this.addDrawableChild(ButtonWidget.builder(Text.literal(langLabel), button -> {
            String nextLang = config.language.equals("en") ? "it" : "en";
            sendAction("SET_LANG", nextLang);
        }).dimensions(this.width / 2 - 155, settingsY, 75, 20).build());

        String warnLabel = ("it".equals(config.language) ? "Avviso: " : "Warn: ") + config.warning_minutes + "m";
        this.addDrawableChild(ButtonWidget.builder(Text.literal(warnLabel), button -> {
            int nextWarn = (config.warning_minutes % 10) + 1;
            sendAction("SET_WARNING", String.valueOf(nextWarn));
        }).dimensions(this.width / 2 - 75, settingsY, 75, 20).build());

        this.addDrawableChild(ButtonWidget.builder(getLabel("info", "Info"), button -> this.client.setScreen(new InfoScreen(this, config.language)))
                .dimensions(this.width / 2 + 5, settingsY, 70, 20).build());

        this.addDrawableChild(ButtonWidget.builder(getLabel("close", "Close"), button -> this.close())
                .dimensions(this.width / 2 + 80, settingsY, 75, 20).build());
    }


    private Text getLabel(String key, String defaultText) {
        boolean it = "it".equals(config.language);
        return switch (key) {
            case "add_group" -> Text.literal(it ? "Aggiungi Gruppo" : "Add Group");
            case "info" -> Text.literal(it ? "Informazioni" : "Info");
            case "close" -> Text.literal(it ? "Chiudi" : "Close");
            case "new_group_label" -> Text.literal(it ? "Nome Nuovo Gruppo:" : "New Group Name:");
            case "global_settings" -> Text.literal(it ? "Impostazioni Globali:" : "Global Settings:");
            case "title" -> Text.literal(it ? "Gestione Shutdown Scheduler" : "Shutdown Scheduler Management");
            case "group_placeholder" -> Text.literal(it ? "Nome Gruppo" : "Group Name");
            case "edit" -> Text.literal(it ? "Modifica" : "Edit");
            case "error_empty" -> Text.literal(it ? "Nome Vuoto!" : "Empty Name!");
            case "error_title" -> Text.literal(it ? "Errore" : "Error");
            case "next_shutdown" -> Text.literal(it ? "Prossimo Shutdown: " : "Next Shutdown: ");
            case "none" -> Text.literal(it ? "Nessuno" : "None");
            default -> Text.literal(defaultText);
        };
    }

    private String getNextShutdownTime() {
        if (config == null || config.groups == null) return getLabel("none", "").getString();

        // Calculate offset between server and client time if available
        long offset = config.server_time > 0 ? (config.server_time - System.currentTimeMillis()) : 0;
        LocalDateTime now = LocalDateTime.now().plus(java.time.Duration.ofMillis(offset));
        LocalDateTime soonest = null;

        for (ShutdownScheduler.ShutdownGroup group : config.groups.values()) {
            if (!group.enabled) continue;
            for (Map.Entry<String, List<String>> entry : group.shutdowns.entrySet()) {
                try {
                    int dayIndex = Integer.parseInt(entry.getKey()); // 0=Mon, ..., 6=Sun
                    for (String timeStr : entry.getValue()) {
                        try {
                            String[] parts = timeStr.split(":");
                            int h = Integer.parseInt(parts[0]);
                            int m = Integer.parseInt(parts[1]);
                            
                            // Initialize with "today" at target time
                            LocalDateTime scheduled = now.withHour(h).withMinute(m).withSecond(0).withNano(0);
                            
                            // Adjust to correct day of week
                            int currentDayIndex = now.getDayOfWeek().getValue() - 1; // 1-7 -> 0-6
                            int daysToAdd = (dayIndex - currentDayIndex + 7) % 7;
                            scheduled = scheduled.plusDays(daysToAdd);
                            
                            // If it already passed today, jump to next week
                            if (scheduled.isBefore(now)) {
                                scheduled = scheduled.plusWeeks(1);
                            }
                            
                            if (soonest == null || scheduled.isBefore(soonest)) {
                                soonest = scheduled;
                            }
                        } catch (Exception inner) {}
                    }
                } catch (Exception outer) {}
            }
        }

        if (soonest == null) return getLabel("none", "").getString();
        
        boolean it = "it".equals(config.language);
        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("EEEE HH:mm", it ? java.util.Locale.ITALY : java.util.Locale.US);
        String formatted = soonest.format(formatter);
        return formatted.substring(0, 1).toUpperCase() + formatted.substring(1);
    }

    @Override
    public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        super.render(context, mouseX, mouseY, delta);
        this.list.render(context, mouseX, mouseY, delta);
        
        // Header shadow/decoration
        context.fill(0, 0, this.width, 35, 0xAA000000);
        context.drawCenteredTextWithShadow(this.textRenderer, getLabel("title", ""), this.width / 2, 12, 0x55FFFF);
        
        context.drawTextWithShadow(textRenderer, getLabel("new_group_label", ""), this.width / 2 - 155, 42, 0xA0A0A0);
        
        // Next Shutdown Info (Bottom)
        String next = getLabel("next_shutdown", "").getString() + getNextShutdownTime();
        context.drawCenteredTextWithShadow(textRenderer, next, this.width / 2, this.height - 65, 0xFFAA00);

        context.drawTextWithShadow(textRenderer, getLabel("global_settings", ""), this.width / 2 - 155, this.height - 42, 0xA0A0A0);
    }

    private void sendAction(String action, String data) {
        ClientPlayNetworking.send(new Networking.ActionPayload(action, data));
    }

    private class GroupListWidget extends ElementListWidget<GroupEntry> {
        public GroupListWidget(net.minecraft.client.MinecraftClient client, int width, int height, int top, int itemHeight) {
            super(client, width, height, top, itemHeight);
        }

        @Override
        public int addEntry(GroupEntry entry) {
            return super.addEntry(entry);
        }

        @Override
        public int getRowWidth() { return 310; }

        @Override
        protected int getScrollbarX() { return this.width / 2 + 160; }
    }

    private class GroupEntry extends ElementListWidget.Entry<GroupEntry> implements net.minecraft.client.gui.ParentElement {
        private final String name;
        private final ShutdownScheduler.ShutdownGroup group;
        private final List<net.minecraft.client.gui.Element> children = new ArrayList<>();
        private final ButtonWidget toggleBtn;
        private final ButtonWidget editBtn;
        private final ButtonWidget deleteBtn;

        public GroupEntry(String name, ShutdownScheduler.ShutdownGroup group) {
            this.name = name;
            this.group = group;

            this.toggleBtn = ButtonWidget.builder(Text.literal(group.enabled ? "On" : "Off"), button -> {
                sendAction("TOGGLE_GROUP", name);
            }).dimensions(0, 0, 40, 20).build();

            this.editBtn = ButtonWidget.builder(getLabel("edit", "Edit"), button -> {
                client.setScreen(new GroupEditScreen(ShutdownSchedulerScreen.this, name, config));
            }).dimensions(0, 0, 50, 20).build();

            this.deleteBtn = ButtonWidget.builder(Text.literal("§lX"), button -> {
                sendAction("DELETE_GROUP", name);
            }).dimensions(0, 0, 25, 20).build();

            this.children.add(toggleBtn);
            this.children.add(editBtn);
            this.children.add(deleteBtn);
        }

        @Override
        public void render(DrawContext context, int index, int y, int x, int entryWidth, int entryHeight, int mouseX, int mouseY, boolean hovered, float delta) {
            if (hovered) {
                context.fill(x - 2, y - 2, x + entryWidth + 2, y + entryHeight + 2, 0x22FFFFFF);
            }

            context.drawTextWithShadow(textRenderer, name, x + 5, y + 6, group.enabled ? 0x55FF55 : 0xFF5555);
            
            toggleBtn.setX(x + 180);
            toggleBtn.setY(y);
            
            // Render colored background for toggle button
            int bgColor = group.enabled ? 0x8800AA00 : 0x88AA0000; // Semi-transparent Green/Red
            context.fill(toggleBtn.getX(), toggleBtn.getY(), toggleBtn.getX() + toggleBtn.getWidth(), toggleBtn.getY() + toggleBtn.getHeight(), bgColor);
            
            toggleBtn.render(context, mouseX, mouseY, delta);

            editBtn.setX(x + 225);
            editBtn.setY(y);
            editBtn.render(context, mouseX, mouseY, delta);

            deleteBtn.setX(x + 280);
            deleteBtn.setY(y);
            
            // Custom Red Button Rendering
            int bx1 = deleteBtn.getX();
            int by1 = deleteBtn.getY();
            int bx2 = bx1 + deleteBtn.getWidth();
            int by2 = by1 + deleteBtn.getHeight();
            
            boolean deleteHovered = mouseX >= bx1 && mouseX <= bx2 && mouseY >= by1 && mouseY <= by2;
            int fillColor = deleteHovered ? 0xFFFF0000 : 0xFF990000;
            int borderColor = deleteHovered ? 0xFFFFFFFF : 0xFF555555;
            
            context.fill(bx1, by1, bx2, by2, borderColor);
            context.fill(bx1 + 1, by1 + 1, bx2 - 1, by2 - 1, fillColor);
            context.drawCenteredTextWithShadow(textRenderer, "§lX", bx1 + deleteBtn.getWidth() / 2, by1 + 6, 0xFFFFFF);
        }

        @Override
        public List<? extends net.minecraft.client.gui.Element> children() { return children; }

        @Override
        public List<? extends net.minecraft.client.gui.Selectable> selectableChildren() { return children.stream().map(e -> (net.minecraft.client.gui.Selectable)e).toList(); }

        @Override
        public void setFocused(boolean focused) {}

        @Override
        public boolean isFocused() { return false; }
    }
}
