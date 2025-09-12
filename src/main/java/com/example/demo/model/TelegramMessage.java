package com.example.demo.model;

import lombok.Data;

@Data
public class TelegramMessage {
    private TelegramChat chat;
    private TelegramFrom from;
    private String text;
}
