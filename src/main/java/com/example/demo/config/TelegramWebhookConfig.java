package com.example.demo.config;
import org.telegram.telegrambots.meta.api.methods.updates.SetWebhook;

import com.example.demo.service.TelegramService;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class TelegramWebhookConfig {

    private final TelegramService telegramService;
    private final AppConfig appConfig;

    @PostConstruct
    public void setWebhook() {
        try {
            String webhookUrl = System.getenv("RENDER_EXTERNAL_URL") + "/webhook/" + appConfig.getTelegramToken();
            SetWebhook setWebhook = SetWebhook.builder().url(webhookUrl).build();
            telegramService.setWebhook(setWebhook); // ✅ الآن صحيح
            System.out.println("✅ Telegram webhook set to: " + webhookUrl);
        } catch (Exception e) {
            e.printStackTrace();
            System.err.println("❌ Error setting Telegram webhook: " + e.getMessage());
        }
    }
}
