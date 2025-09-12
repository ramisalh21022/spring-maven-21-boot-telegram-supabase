package com.example.demo.controller;

import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.telegram.telegrambots.meta.api.objects.Update;
import com.example.demo.service.TelegramService;

@RestController
@RequestMapping("/webhook")
@RequiredArgsConstructor
public class TelegramWebhookController {

    private final TelegramService telegramService;

    @PostMapping("/{token}")
    public ResponseEntity<Void> onUpdateReceived(@PathVariable String token, @RequestBody Update update) {
        if (!token.equals(System.getenv("TELEGRAM_TOKEN"))) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
        }
        telegramService.onWebhookUpdateReceived(update);
        return ResponseEntity.ok().build();
    }
}
