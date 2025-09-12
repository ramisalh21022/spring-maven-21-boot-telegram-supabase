package com.example.demo.config;

import lombok.Getter;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Component
@Getter
public class AppConfig {

    @Value("${supabase.url}")
    private String supabaseUrl;

    @Value("${supabase.key}")
    private String supabaseKey;

    @Value("${supabase.bucket:food-stor}")
    private String supabaseBucket;

    @Value("${telegram.token}")
    private String telegramToken;

    @Value("${telegram.username:rami21022_bot}")
    private String telegramUsername;

    public String getBucketUrl() {
        return supabaseUrl + "/storage/v1/object/public/" + supabaseBucket + "/";
    }
}
