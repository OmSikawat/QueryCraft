package com.qc.enterprise.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.qc.enterprise.dto.ExecutionPlan;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class AiQueryService {

    private final ChatClient chatClient;
    private final ObjectMapper objectMapper = new ObjectMapper();

    public AiQueryService(ChatClient.Builder chatClientBuilder) {
        this.chatClient = chatClientBuilder.build();
    }

    public List<ExecutionPlan> generateExecutionPlans(String userQuestion, String schemaContext) {

        // 1. The Dynamic RAG Prompt
        String systemPromptTemplate = """
            You are an elite Enterprise Data Router.
            Your job is to translate the user's question into exact database queries.
            
            CRITICAL RULE: You may ONLY query the databases and targets listed in the Context below. Do not guess or hallucinate table names.
            
            ====================
            AVAILABLE SCHEMA CONTEXT:
            %s
            ====================
            
            RULES:
            1. Return ONLY a valid JSON array.
            2. Valid databaseType values are: POSTGRES, MONGO, MINIO_CSV.
            3. You MUST provide a 'targetName' for every plan based exactly on the context above.
            4. POSTGRES queries must be standard SQL SELECT statements.
            5. MONGO queries MUST be a stringified basic MongoDB filter document (e.g., "{}" or "{\\"level\\": \\"INFO\\"}").
            6. MINIO_CSV queries must use 'SELECT * FROM s3object'.
            """;

        // 2. Inject the dynamic context into the prompt!
        String finalSystemPrompt = String.format(systemPromptTemplate, schemaContext);
        System.out.println("DEBUG: Sending dynamic prompt to Gemini...\n" + finalSystemPrompt);

        // 3. Call Gemini
        String aiResponse = chatClient.prompt()
                .system(finalSystemPrompt)
                .user(userQuestion)
                .call()
                .content();

        System.out.println("Raw AI Response: \n" + aiResponse);

        // 4. Parse the AI's string response into our List<ExecutionPlan> objects
        // 4. Parse the AI's string response into our List<ExecutionPlan> objects
        try {
            // 🛡️ THE SUPER SCRUBBER
            int arrayStart = aiResponse.indexOf("[");
            int arrayEnd = aiResponse.lastIndexOf("]");

            int objStart = aiResponse.indexOf("{");
            int objEnd = aiResponse.lastIndexOf("}");

            String cleanJson;

            if (arrayStart != -1 && arrayEnd != -1) {
                // The AI correctly returned an array
                cleanJson = aiResponse.substring(arrayStart, arrayEnd + 1);
            } else if (objStart != -1 && objEnd != -1) {
                // The AI lazily returned a single object! Wrap it in an array for Jackson.
                cleanJson = "[" + aiResponse.substring(objStart, objEnd + 1) + "]";
            } else {
                throw new RuntimeException("AI did not return any recognizable JSON.");
            }

            // Convert clean JSON array to Java List
            return objectMapper.readValue(cleanJson, new TypeReference<List<ExecutionPlan>>() {});

        } catch (Exception e) {
            System.err.println("Failed to parse AI response: " + aiResponse);
            throw new RuntimeException("AI generated invalid JSON. Please try asking the question differently.", e);
        }
    }
}