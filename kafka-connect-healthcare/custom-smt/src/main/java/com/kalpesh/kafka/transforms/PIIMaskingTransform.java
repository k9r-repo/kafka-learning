package com.kalpesh.kafka.transforms;

import org.apache.kafka.common.config.ConfigDef;
import org.apache.kafka.connect.connector.ConnectRecord;
import org.apache.kafka.connect.data.Field;
import org.apache.kafka.connect.data.Schema;
import org.apache.kafka.connect.data.SchemaBuilder;
import org.apache.kafka.connect.data.Struct;
import org.apache.kafka.connect.transforms.Transformation;
import org.apache.kafka.connect.transforms.util.SimpleConfig;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

/**
 * Custom Single Message Transform (SMT) for Healthcare Data Pipeline
 * 
 * Purpose: Masks Personally Identifiable Information (PII) in patient records
 * to ensure HIPAA compliance when streaming healthcare data through Kafka.
 * 
 * Features:
 * - Masks SSN (Social Security Numbers)
 * - Masks email addresses
 * - Masks phone numbers
 * - Adds audit fields (transformed_at, transformer_version)
 * - Configurable masking character
 * 
 * Usage in Connector Configuration:
 * "transforms": "maskPII",
 * "transforms.maskPII.type": "com.kalpesh.kafka.transforms.PIIMaskingTransform",
 * "transforms.maskPII.fields": "ssn,email,phone"
 * 
 * @author Kalpesh Rawal
 * @version 1.0.0
 */
public class PIIMaskingTransform<R extends ConnectRecord<R>> implements Transformation<R> {

    private static final Logger log = LoggerFactory.getLogger(PIIMaskingTransform.class);

    // Configuration keys
    public static final String FIELDS_CONFIG = "fields";
    public static final String MASK_CHAR_CONFIG = "mask.char";
    public static final String ADD_AUDIT_FIELDS_CONFIG = "add.audit.fields";

    // Default values
    private static final String DEFAULT_MASK_CHAR = "*";
    private static final boolean DEFAULT_ADD_AUDIT_FIELDS = true;

    // Configuration definition
    public static final ConfigDef CONFIG_DEF = new ConfigDef()
            .define(
                    FIELDS_CONFIG,
                    ConfigDef.Type.LIST,
                    ConfigDef.NO_DEFAULT_VALUE,
                    ConfigDef.Importance.HIGH,
                    "Comma-separated list of fields to mask (e.g., ssn,email,phone)"
            )
            .define(
                    MASK_CHAR_CONFIG,
                    ConfigDef.Type.STRING,
                    DEFAULT_MASK_CHAR,
                    ConfigDef.Importance.MEDIUM,
                    "Character to use for masking (default: *)"
            )
            .define(
                    ADD_AUDIT_FIELDS_CONFIG,
                    ConfigDef.Type.BOOLEAN,
                    DEFAULT_ADD_AUDIT_FIELDS,
                    ConfigDef.Importance.LOW,
                    "Whether to add audit fields (transformed_at, transformer_version)"
            );

    private List<String> fieldsToMask;
    private String maskChar;
    private boolean addAuditFields;

    // Regex patterns for PII detection
    private static final Pattern SSN_PATTERN = Pattern.compile("\\d{3}-\\d{2}-\\d{4}");
    private static final Pattern EMAIL_PATTERN = Pattern.compile("[a-zA-Z0-9._%+-]+@[a-zA-Z0-9.-]+\\.[a-zA-Z]{2,}");
    private static final Pattern PHONE_PATTERN = Pattern.compile("\\d{3}-\\d{4}|\\(\\d{3}\\)\\s*\\d{3}-\\d{4}");

    @Override
    public void configure(Map<String, ?> configs) {
        final SimpleConfig config = new SimpleConfig(CONFIG_DEF, configs);
        fieldsToMask = config.getList(FIELDS_CONFIG);
        maskChar = config.getString(MASK_CHAR_CONFIG);
        addAuditFields = config.getBoolean(ADD_AUDIT_FIELDS_CONFIG);

        log.info("PIIMaskingTransform configured - Fields to mask: {}, Mask char: {}, Add audit: {}",
                fieldsToMask, maskChar, addAuditFields);
    }

    @Override
    public R apply(R record) {
        if (record.value() == null) {
            log.debug("Skipping null record");
            return record;
        }

        // Handle Struct (schema-based) records
        if (record.value() instanceof Struct) {
            return applyWithSchema(record);
        }

        // Handle Map (schemaless) records
        if (record.value() instanceof Map) {
            return applySchemaless(record);
        }

        log.warn("Unsupported record value type: {}", record.value().getClass());
        return record;
    }

    /**
     * Apply transformation to records with schema (Struct)
     */
    private R applyWithSchema(R record) {
        final Struct value = (Struct) record.value();
        final Schema schema = value.schema();

        // Build new schema with audit fields if needed
        final Schema updatedSchema = addAuditFields ? 
                makeUpdatedSchema(schema) : schema;

        // Create new Struct with masked values
        final Struct updatedValue = new Struct(updatedSchema);

        // Copy all fields, masking specified ones
        for (Field field : schema.fields()) {
            String fieldName = field.name();
            Object fieldValue = value.get(fieldName);

            if (fieldsToMask.contains(fieldName) && fieldValue != null) {
                // Mask the field
                String maskedValue = maskField(fieldName, fieldValue.toString());
                updatedValue.put(fieldName, maskedValue);
                log.debug("Masked field: {} in record", fieldName);
            } else {
                // Copy as-is
                updatedValue.put(fieldName, fieldValue);
            }
        }

        // Add audit fields if configured
        if (addAuditFields) {
            updatedValue.put("transformed_at", System.currentTimeMillis());
            updatedValue.put("transformer_version", "1.0.0");
        }

        return record.newRecord(
                record.topic(),
                record.kafkaPartition(),
                record.keySchema(),
                record.key(),
                updatedSchema,
                updatedValue,
                record.timestamp()
        );
    }

    /**
     * Apply transformation to schemaless records (Map)
     */
    @SuppressWarnings("unchecked")
    private R applySchemaless(R record) {
        final Map<String, Object> value = (Map<String, Object>) record.value();
        final Map<String, Object> updatedValue = new HashMap<>(value);

        // Mask specified fields
        for (String fieldName : fieldsToMask) {
            if (updatedValue.containsKey(fieldName)) {
                Object fieldValue = updatedValue.get(fieldName);
                if (fieldValue != null) {
                    String maskedValue = maskField(fieldName, fieldValue.toString());
                    updatedValue.put(fieldName, maskedValue);
                    log.debug("Masked field: {} in schemaless record", fieldName);
                }
            }
        }

        // Add audit fields if configured
        if (addAuditFields) {
            updatedValue.put("transformed_at", System.currentTimeMillis());
            updatedValue.put("transformer_version", "1.0.0");
        }

        return record.newRecord(
                record.topic(),
                record.kafkaPartition(),
                record.keySchema(),
                record.key(),
                null, // schemaless
                updatedValue,
                record.timestamp()
        );
    }

    /**
     * Mask a field value based on field name and pattern
     */
    private String maskField(String fieldName, String value) {
        if (value == null || value.isEmpty()) {
            return value;
        }

        switch (fieldName.toLowerCase()) {
            case "ssn":
                return maskSSN(value);
            case "email":
                return maskEmail(value);
            case "phone":
                return maskPhone(value);
            default:
                // Generic masking - show first and last char only
                return maskGeneric(value);
        }
    }

    /**
     * Mask SSN: 123-45-6789 -> ***-**-6789 (show last 4 digits)
     */
    private String maskSSN(String ssn) {
        if (SSN_PATTERN.matcher(ssn).matches()) {
            return maskChar.repeat(3) + "-" + maskChar.repeat(2) + "-" + ssn.substring(7);
        }
        return maskGeneric(ssn);
    }

    /**
     * Mask Email: john.doe@example.com -> j***e@example.com
     */
    private String maskEmail(String email) {
        if (EMAIL_PATTERN.matcher(email).matches()) {
            int atIndex = email.indexOf('@');
            if (atIndex > 2) {
                String localPart = email.substring(0, atIndex);
                String domain = email.substring(atIndex);
                return localPart.charAt(0) + maskChar.repeat(localPart.length() - 2) +
                        localPart.charAt(localPart.length() - 1) + domain;
            }
        }
        return maskGeneric(email);
    }

    /**
     * Mask Phone: 555-0101 -> ***-0101 or (555) 123-4567 -> (***) ***-4567
     */
    private String maskPhone(String phone) {
        if (phone.contains("(")) {
            // Format: (555) 123-4567 -> (***) ***-4567
            return "(" + maskChar.repeat(3) + ") " + maskChar.repeat(3) + phone.substring(phone.lastIndexOf('-'));
        } else if (phone.contains("-")) {
            // Format: 555-0101 -> ***-0101
            return maskChar.repeat(3) + phone.substring(phone.indexOf('-'));
        }
        return maskGeneric(phone);
    }

    /**
     * Generic masking: Show first and last character, mask the rest
     */
    private String maskGeneric(String value) {
        if (value.length() <= 2) {
            return maskChar.repeat(value.length());
        }
        return value.charAt(0) + maskChar.repeat(value.length() - 2) + value.charAt(value.length() - 1);
    }

    /**
     * Create updated schema with audit fields
     */
    private Schema makeUpdatedSchema(Schema schema) {
        final SchemaBuilder builder = SchemaBuilder.struct();

        // Copy existing fields
        for (Field field : schema.fields()) {
            builder.field(field.name(), field.schema());
        }

        // Add audit fields
        builder.field("transformed_at", Schema.INT64_SCHEMA);
        builder.field("transformer_version", Schema.STRING_SCHEMA);

        return builder.build();
    }

    @Override
    public ConfigDef config() {
        return CONFIG_DEF;
    }

    @Override
    public void close() {
        log.info("PIIMaskingTransform closed");
    }
}
