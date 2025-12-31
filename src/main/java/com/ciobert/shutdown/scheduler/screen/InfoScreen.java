package com.ciobert.shutdown.scheduler.screen;

import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.text.Text;

public class InfoScreen extends Screen {
    private final Screen parent;
    private final String language;

    public InfoScreen(Screen parent, String language) {
        super(Text.literal("it".equals(language) ? "Info Shutdown Scheduler" : "Shutdown Scheduler Info"));
        this.parent = parent;
        this.language = language;
    }

    @Override
    protected void init() {
        String backLabel = "it".equals(language) ? "Indietro" : "Back";
        this.addDrawableChild(ButtonWidget.builder(Text.literal(backLabel), button -> this.client.setScreen(parent))
                .dimensions(this.width / 2 - 50, this.height - 40, 100, 20).build());
    }

    @Override
    public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        super.render(context, mouseX, mouseY, delta);
        boolean it = "it".equals(language);
        
        context.fill(0, 0, this.width, 35, 0xAA000000);
        context.drawCenteredTextWithShadow(this.textRenderer, Text.literal(it ? "Informazioni Mod" : "Shutdown Scheduler Info"), this.width / 2, 12, 0x55FFFF);

        int y = 50;
        context.drawCenteredTextWithShadow(textRenderer, (it ? "Versione: " : "Version: ") + "1.21.1-2.0.0", this.width / 2, y, 0xFFFFFF);
        y += 20;
        context.drawCenteredTextWithShadow(textRenderer, (it ? "Sviluppato da: " : "Developed by: ") + "Ciobert", this.width / 2, y, 0xA0A0A0);
        
        y += 30;
        context.drawCenteredTextWithShadow(textRenderer, Text.literal(it ? "Guida all'uso:" : "How it works:"), this.width / 2, y, 0x55FFFF);
        y += 15;
        context.drawCenteredTextWithShadow(textRenderer, Text.literal(it ? "- Crea gruppi per gestire set diversi di orari." : "- Create groups to manage different sets of times."), this.width / 2, y, 0xAAAAAA);
        y += 12;
        context.drawCenteredTextWithShadow(textRenderer, Text.literal(it ? "- Attiva o disattiva i gruppi con un click." : "- Enable or disable groups with a single click."), this.width / 2, y, 0xAAAAAA);
        y += 12;
        context.drawCenteredTextWithShadow(textRenderer, Text.literal(it ? "- Imposta gli shutdown per ogni giorno della settimana." : "- Set specific shutdown times for each day of the week."), this.width / 2, y, 0xAAAAAA);
        
        y += 20;
        context.drawCenteredTextWithShadow(textRenderer, Text.literal(it ? "Suggerimenti:" : "Tips:"), this.width / 2, y, 0x55FF55);
        y += 15;
        context.drawCenteredTextWithShadow(textRenderer, Text.literal(it ? "Il 'Warn Minutes' avvisa i giocatori prima" : "The 'Warn Minutes' setting warns players"), this.width / 2, y, 0xAAAAAA);
        y += 12;
        context.drawCenteredTextWithShadow(textRenderer, Text.literal(it ? "dello spegnimento automatico del server." : "before the automatic server shutdown occur."), this.width / 2, y, 0xAAAAAA);
        y += 12;
        context.drawCenteredTextWithShadow(textRenderer, Text.literal(it ? "Puoi gestire tutto comodamente via GUI!" : "You can manage everything easily via GUI!"), this.width / 2, y, 0x55FFFF);
    }
}
