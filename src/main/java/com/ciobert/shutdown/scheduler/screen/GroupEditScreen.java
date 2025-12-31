package com.ciobert.shutdown.scheduler.screen;

import com.ciobert.shutdown.scheduler.Networking;
import com.ciobert.shutdown.scheduler.ShutdownScheduler;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.gui.widget.ElementListWidget;
import net.minecraft.client.gui.widget.TextFieldWidget;
import net.minecraft.client.gui.screen.narration.NarrationMessageBuilder;
import net.minecraft.text.Text;

import java.util.ArrayList;
import java.util.List;

public class GroupEditScreen extends Screen {
    private final Screen parent;
    private final String groupName;
    private ShutdownScheduler.ConfigData config;
    private ScheduleListWidget list;
    
    private int currentDay = 0;
    private boolean showAll = false;
    private TextFieldWidget hourField;
    private TextFieldWidget minuteField;
    @SuppressWarnings("unused")
    private ButtonWidget dayBtn;

    private static final String[] DAYS = {"Monday", "Tuesday", "Wednesday", "Thursday", "Friday", "Saturday", "Sunday"};

    public GroupEditScreen(Screen parent, String groupName, ShutdownScheduler.ConfigData config) {
        super(Text.literal("Editing Group: " + groupName));
        this.parent = parent;
        this.groupName = groupName;
        this.config = config;
        if (this.config != null) this.config.fix();
    }

    public void refreshConfig(String json) {
        System.out.println("[ShutdownScheduler] Received config sync, updating UI...");
        this.config = ShutdownScheduler.GSON.fromJson(json, ShutdownScheduler.ConfigData.class);
        if (this.config != null) this.config.fix();
        
        // Update parent to avoid stale data when going back
        if (parent instanceof ShutdownSchedulerScreen p) {
            p.updateConfig(this.config);
        }
        
        this.clearAndInit();
    }

    @Override
    protected void init() {
        // Layout:
        // 0-35: Header
        // 40-55: Section Label
        // 55-75: Day Navigation + New Schedule
        // 80-95: List Header/Spacing
        // 100-(height-50): List
        // (height-45)-end: Bottom Section

        int topY = 55;
        int startX = this.width / 2 - 130;

        boolean it = "it".equals(config.language);

        // Day Navigation
        ButtonWidget prevBtn = ButtonWidget.builder(Text.literal("<"), button -> {
            currentDay = (currentDay + 6) % 7;
            this.clearAndInit();
        }).dimensions(startX - 30, topY, 20, 20).build();
        prevBtn.active = !showAll;
        this.addDrawableChild(prevBtn);

        this.addDrawableChild(ButtonWidget.builder(Text.literal(getDays()[currentDay]), button -> {})
                .dimensions(startX - 5, topY, 80, 20).build());

        ButtonWidget nextBtn = ButtonWidget.builder(Text.literal(">"), button -> {
            currentDay = (currentDay + 1) % 7;
            this.clearAndInit();
        }).dimensions(startX + 80, topY, 20, 20).build();
        nextBtn.active = !showAll;
        this.addDrawableChild(nextBtn);

        // ALL Toggle Button
        ButtonWidget allBtn = ButtonWidget.builder(Text.literal(it ? "TUTTI" : "ALL"), button -> {
            showAll = !showAll;
            this.clearAndInit();
        }).dimensions(startX + 105, topY, 40, 20).build();
        this.addDrawableChild(allBtn);

        // Time Input (TOP)
        this.hourField = new TextFieldWidget(this.textRenderer, startX + 150, topY, 30, 20, Text.literal("HH"));
        this.minuteField = new TextFieldWidget(this.textRenderer, startX + 185, topY, 30, 20, Text.literal("MM"));
        this.hourField.setEditable(!showAll);
        this.minuteField.setEditable(!showAll);
        this.addDrawableChild(hourField);
        this.addDrawableChild(minuteField);

        ButtonWidget addBtn = ButtonWidget.builder(Text.literal("+"), button -> {
            try {
                String hStr = hourField.getText();
                String mStr = minuteField.getText();
                
                if (hStr.isEmpty() || mStr.isEmpty()) {
                    net.minecraft.client.toast.SystemToast.add(client.getToastManager(), net.minecraft.client.toast.SystemToast.Type.WORLD_ACCESS_FAILURE, getLabel("error_title", "Error"), getLabel("error_empty_time", "Missing values!"));
                    return;
                }

                int h = Integer.parseInt(hStr);
                int m = Integer.parseInt(mStr);
                if (h >= 0 && h < 24 && m >= 0 && m < 60) {
                    String data = groupName + ";" + currentDay + ";" + h + ";" + m;
                    System.out.println("[ShutdownScheduler] Sending ADD_SHUTDOWN: " + data);
                    ClientPlayNetworking.send(new Networking.ActionPayload("ADD_SHUTDOWN", data));
                    net.minecraft.client.toast.SystemToast.add(client.getToastManager(), net.minecraft.client.toast.SystemToast.Type.PERIODIC_NOTIFICATION, Text.literal(it ? "Invio..." : "Sending..."), Text.literal(hStr + ":" + mStr));
                } else {
                    net.minecraft.client.toast.SystemToast.add(client.getToastManager(), net.minecraft.client.toast.SystemToast.Type.WORLD_ACCESS_FAILURE, getLabel("error_title", "Error"), getLabel("error_invalid_range", "0-23 : 0-59"));
                }
            } catch (NumberFormatException e) {
                net.minecraft.client.toast.SystemToast.add(client.getToastManager(), net.minecraft.client.toast.SystemToast.Type.WORLD_ACCESS_FAILURE, getLabel("error_title", "Error"), getLabel("error_NaN", "Numbers only!"));
            }
        }).dimensions(startX + 220, topY, 25, 20).build();

        addBtn.active = !showAll;
        this.addDrawableChild(addBtn);

        // List Section (MIDDLE)
        this.list = new ScheduleListWidget(this.client, this.width, this.height - 135, 85, 25);
        this.addSelectableChild(this.list);

        ShutdownScheduler.ShutdownGroup group = config.groups.get(groupName);
        if (group != null) {
            if (showAll) {
                for (int d = 0; d < 7; d++) {
                    String dayKey = String.valueOf(d);
                    if (group.shutdowns.containsKey(dayKey)) {
                        for (String time : group.shutdowns.get(dayKey)) {
                            this.list.addEntry(new ScheduleEntry(d, time));
                        }
                    }
                }
            } else {
                String dayKey = String.valueOf(currentDay);
                if (group.shutdowns.containsKey(dayKey)) {
                    for (String time : group.shutdowns.get(dayKey)) {
                        this.list.addEntry(new ScheduleEntry(currentDay, time));
                    }
                }
            }
        }

        // Bottom section
        this.addDrawableChild(ButtonWidget.builder(getLabel("back", "Back"), button -> client.setScreen(parent))
                .dimensions(this.width / 2 - 50, this.height - 30, 100, 20).build());
    }

    @Override
    public void tick() {
    }

    private String[] getDays() {
        return "it".equals(config.language) 
            ? new String[]{"Lunedì", "Martedì", "Mercoledì", "Giovedì", "Venerdì", "Sabato", "Domenica"}
            : new String[]{"Monday", "Tuesday", "Wednesday", "Thursday", "Friday", "Saturday", "Sunday"};
    }

    private Text getLabel(String key, String defaultText) {
        boolean it = "it".equals(config.language);
        return switch (key) {
            case "back" -> Text.literal(it ? "Indietro" : "Back");
            case "new_schedule" -> Text.literal(it ? "Nuovo Shutdown:" : "New Schedule:");
            case "title" -> Text.literal(it ? "Modifica Gruppo: " + groupName : "Edit Group: " + groupName);
            case "error_empty_time" -> Text.literal(it ? "Valori mancanti!" : "Missing values!");
            case "error_invalid_range" -> Text.literal(it ? "Ore 0-23, Min 0-59" : "0-23 : 0-59");
            case "error_NaN" -> Text.literal(it ? "Solo numeri!" : "Numbers only!");
            case "error_title" -> Text.literal(it ? "Errore" : "Error");
            default -> Text.literal(defaultText);
        };
    }

    @Override
    public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        super.render(context, mouseX, mouseY, delta);
        this.list.render(context, mouseX, mouseY, delta);
        
        // Header
        context.fill(0, 0, this.width, 35, 0xAA000000);
        context.drawCenteredTextWithShadow(this.textRenderer, getLabel("title", ""), this.width / 2, 12, 0x55FFFF);
        
        context.drawTextWithShadow(textRenderer, ":", this.width / 2 - 130 + 181, 61, 0xFFFFFF); // Adjust colon position
        context.drawTextWithShadow(textRenderer, getLabel("new_schedule", ""), this.width / 2 - 150, 42, 0xA0A0A0);

        if (showAll) {
            context.fill(this.width / 2 - 130 + 105, 55, this.width / 2 - 130 + 105 + 40, 75, 0x8800AA00);
        }
    }

    private class ScheduleListWidget extends ElementListWidget<ScheduleEntry> {
        public ScheduleListWidget(net.minecraft.client.MinecraftClient client, int width, int height, int top, int itemHeight) {
            super(client, width, height, top, itemHeight);
        }

        @Override
        public int addEntry(ScheduleEntry entry) {
            return super.addEntry(entry);
        }

        @Override
        public int getRowWidth() { return 260; }

        @Override
        protected int getScrollbarX() { return this.width / 2 + 135; }
    }

    private class ScheduleEntry extends ElementListWidget.Entry<ScheduleEntry> implements net.minecraft.client.gui.ParentElement {
        private final int day;
        private final String time;
        private final List<net.minecraft.client.gui.Element> children = new ArrayList<>();
        private final ButtonWidget removeBtn;

        public ScheduleEntry(int day, String time) {
            this.day = day;
            this.time = time;
            this.removeBtn = ButtonWidget.builder(Text.literal("Delete"), button -> {
                String[] parts = time.split(":");
                String data = groupName + ";" + day + ";" + parts[0] + ";" + parts[1];
                System.out.println("[ShutdownScheduler] Sending REMOVE_SHUTDOWN: " + data);
                ClientPlayNetworking.send(new Networking.ActionPayload("REMOVE_SHUTDOWN", data));
            }).dimensions(0, 0, 50, 20).build();
            this.children.add(removeBtn);
        }

        @Override
        public void render(DrawContext context, int index, int y, int x, int entryWidth, int entryHeight, int mouseX, int mouseY, boolean hovered, float delta) {
            if (hovered) {
                context.fill(x - 2, y - 2, x + entryWidth + 2, y + entryHeight + 2, 0x22FFFFFF);
            }

            boolean it = "it".equals(config.language);
            String label = getDays()[day] + (it ? " alle " : " at ") + time;
            context.drawTextWithShadow(textRenderer, label, x + 5, y + 6, 0x55FFFF);

            removeBtn.setMessage(it ? Text.literal("Elimina") : Text.literal("Delete"));
            removeBtn.setX(x + 200);
            removeBtn.setY(y);
            
            // Custom Red Button Rendering
            int bx1 = removeBtn.getX();
            int by1 = removeBtn.getY();
            int bx2 = bx1 + removeBtn.getWidth();
            int by2 = by1 + removeBtn.getHeight();
            
            boolean removeHovered = mouseX >= bx1 && mouseX <= bx2 && mouseY >= by1 && mouseY <= by2;
            int fillColor = removeHovered ? 0xFFFF0000 : 0xFF990000;
            int borderColor = removeHovered ? 0xFFFFFFFF : 0xFF555555;
            
            context.fill(bx1, by1, bx2, by2, borderColor);
            context.fill(bx1 + 1, by1 + 1, bx2 - 1, by2 - 1, fillColor);
            context.drawCenteredTextWithShadow(textRenderer, removeBtn.getMessage(), bx1 + removeBtn.getWidth() / 2, by1 + 6, 0xFFFFFF);
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
