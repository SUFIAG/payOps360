package com.payops.payops360.common.config;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Contact;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.info.License;
import io.swagger.v3.oas.models.servers.Server;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.List;

/**
 * OpenAPI/Swagger configuration for API documentation.
 */
@Configuration
public class OpenAPIConfig {

    @Bean
    public OpenAPI payOps360OpenAPI() {
        return new OpenAPI()
                .info(new Info()
                        .title("PayOps360 API")
                        .description("""
                                AI-Powered Payment Operations Intelligence Platform
                                
                                Features:
                                - Real-time payment lifecycle tracking
                                - Provider health monitoring
                                - Intelligent failure classification
                                - Incident correlation
                                - Retry recommendations
                                - AI-powered root cause analysis
                                """)
                        .version("1.0.0")
                        .contact(new Contact()
                                .name("PayOps360 Team")
                                .email("support@payops360.com")
                                .url("https://payops360.com"))
                        .license(new License()
                                .name("MIT License")
                                .url("https://opensource.org/licenses/MIT")))
                .servers(List.of(
                        new Server()
                                .url("http://localhost:8080")
                                .description("Local Development Server"),
                        new Server()
                                .url("https://api.payops360.com")
                                .description("Production Server")
                ));
    }
}

