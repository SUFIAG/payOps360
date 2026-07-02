package com.payops.payops360;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.data.jpa.repository.config.EnableJpaAuditing;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * PayOps360 - AI-Powered Payment Operations Intelligence Platform
 *
 * Main application entry point for the payment operations monitoring system.
 *
 * Architecture: Hexagonal (Ports & Adapters) + Domain-Driven Design
 * Performance Target: 2000-5000 TPS, <150ms P95 latency
 *
 * @version 1.0.0
 * @since 2026-06-25
 */
@SpringBootApplication
@EnableJpaAuditing
@EnableAsync
@EnableScheduling
public class PayOps360Application {

	public static void main(String[] args) {
		SpringApplication.run(PayOps360Application.class, args);
	}

}
