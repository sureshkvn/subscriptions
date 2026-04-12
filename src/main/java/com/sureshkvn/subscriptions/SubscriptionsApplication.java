package com.sureshkvn.subscriptions;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * Main entry point for the Subscriptions API.
 *
 * <p>This application provides flexible subscription management supporting recurring
 * billing intervals from hourly to monthly and beyond.
 *
 * <p>Virtual threads are enabled via {@code spring.threads.virtual.enabled=true} in
 * application.yml, leveraging Java 21's Project Loom for improved concurrency.
 */
@SpringBootApplication
public class SubscriptionsApplication {

    public static void main(String[] args) {
        SpringApplication.run(SubscriptionsApplication.class, args);
    }
}
