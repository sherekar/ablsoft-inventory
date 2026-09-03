package com.ablsoft.inventory.config;

import java.time.Clock;
import java.time.ZoneId;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class TimeConfiguration {
    @Bean
    Clock clock(@Value("${inventory.timezone}") String timezone) {
        return Clock.system(ZoneId.of(timezone));
    }
}
