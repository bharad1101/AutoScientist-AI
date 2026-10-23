package com.autoscientist;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication
public class AutoScientistApplication {

    static {
        // Ensure local persistent directories exist before DataSource initialization
        try {
            java.nio.file.Files.createDirectories(java.nio.file.Path.of("data", "h2"));
            java.nio.file.Files.createDirectories(java.nio.file.Path.of("uploads", "datasets"));
        } catch (Exception ignored) {
        }
    }

    public static void main(String[] args) {
        SpringApplication.run(AutoScientistApplication.class, args);
    }
}
