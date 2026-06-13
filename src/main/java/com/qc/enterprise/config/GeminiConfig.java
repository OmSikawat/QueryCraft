//package com.qc.enterprise.config;
//
//import org.springframework.beans.factory.config.BeanFactoryPostProcessor;
//import org.springframework.context.annotation.Bean;
//import org.springframework.context.annotation.Configuration;
//import org.springframework.core.env.Environment;
//
//@Configuration
//public class GeminiConfig {
//
//    // By declaring this as static BeanFactoryPostProcessor, we guarantee
//    // this code runs BEFORE the strict Spring AI Auto-Configuration wakes up!
//    @Bean
//    public static BeanFactoryPostProcessor loadGeminiProperties(Environment env) {
//        return beanFactory -> {
//
//            // 1. Dynamically pull your custom variables from application.yml
//            String apiKey = env.getProperty("custom.gemini.api-key");
//            String projectId = env.getProperty("custom.gemini.project-id", "querycraft-demo");
//            String location = env.getProperty("custom.gemini.location", "us-central1");
//
//            if (apiKey != null && !apiKey.isEmpty()) {
//                // 2. Inject them into the exact system properties the AI Validator demands
//                System.setProperty("spring.ai.google.genai.api-key", apiKey);
//                System.setProperty("spring.ai.google.genai.project-id", projectId);
//                System.setProperty("spring.ai.google.genai.location", location);
//
//                System.out.println("✅ GeminiConfig: Successfully loaded credentials from application.yml!");
//            } else {
//                System.err.println("❌ GeminiConfig: Could not find custom.gemini.api-key in application.yml");
//            }
//        };
//    }
//}