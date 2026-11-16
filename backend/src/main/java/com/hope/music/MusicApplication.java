package com.hope.music;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/** Independent backend entry point; the legacy WAR remains a separate build. */
@SpringBootApplication
public class MusicApplication {
    public static void main(String[] args) {
        SpringApplication.run(MusicApplication.class, args);
    }
}
