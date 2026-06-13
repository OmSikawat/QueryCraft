package com.qc.enterprise.model;

import lombok.Data;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;
import org.springframework.data.mongodb.core.mapping.Field;

@Data
@Document(collection = "system_logs")
public class SystemLog {

    @Id
    private String id; // Mongo auto-generates this

    @Field("event_id")
    private String eventId;

    @Field("user_email")
    private String userEmail;

    private String action;
    private String status;
}