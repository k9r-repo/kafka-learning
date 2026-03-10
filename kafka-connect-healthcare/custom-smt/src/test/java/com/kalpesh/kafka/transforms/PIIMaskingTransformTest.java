package com.kalpesh.kafka.transforms;

import org.apache.kafka.connect.data.Schema;
import org.apache.kafka.connect.data.SchemaBuilder;
import org.apache.kafka.connect.data.Struct;
import org.apache.kafka.connect.source.SourceRecord;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.DisplayName;

import java.util.HashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for PIIMaskingTransform
 * Tests PII masking logic for SSN, email, and phone fields
 */
class PIIMaskingTransformTest {

    private PIIMaskingTransform<SourceRecord> transform;
    
    @BeforeEach
    void setUp() {
        transform = new PIIMaskingTransform<>();
    }
    
    @AfterEach
    void tearDown() {
        if (transform != null) {
            transform.close();
        }
    }

    // ==================== SSN Masking Tests ====================
    
    @Test
    @DisplayName("SSN should be masked showing only last 4 digits")
    void testMaskSSN_ShowsLast4Digits() {
        // Given
        Map<String, Object> config = new HashMap<>();
        config.put("fields", "ssn");
        config.put("mask.char", "*");
        config.put("add.audit.fields", "false");
        transform.configure(config);

        Schema schema = SchemaBuilder.struct()
                .field("patient_id", Schema.INT32_SCHEMA)
                .field("ssn", Schema.OPTIONAL_STRING_SCHEMA)
                .build();

        Struct value = new Struct(schema)
                .put("patient_id", 1)
                .put("ssn", "123-45-6789");

        SourceRecord record = createSourceRecord(schema, value);

        // When
        SourceRecord transformedRecord = transform.apply(record);

        // Then
        Struct transformedValue = (Struct) transformedRecord.value();
        assertThat(transformedValue.getString("ssn"))
                .isEqualTo("***-**-6789")
                .as("SSN should show only last 4 digits");
        assertThat(transformedValue.getInt32("patient_id"))
                .isEqualTo(1)
                .as("Non-PII fields should remain unchanged");
    }

    @Test
    @DisplayName("SSN with null value should be handled gracefully")
    void testMaskSSN_NullValue() {
        // Given
        Map<String, Object> config = new HashMap<>();
        config.put("fields", "ssn");
        config.put("add.audit.fields", "false");
        transform.configure(config);

        Schema schema = SchemaBuilder.struct()
                .field("ssn", Schema.OPTIONAL_STRING_SCHEMA)
                .build();

        Struct value = new Struct(schema).put("ssn", null);
        SourceRecord record = createSourceRecord(schema, value);

        // When
        SourceRecord transformedRecord = transform.apply(record);

        // Then
        Struct transformedValue = (Struct) transformedRecord.value();
        assertThat(transformedValue.get("ssn")).isNull();
    }

    @Test
    @DisplayName("Short SSN (3 chars or less) should show first and last char")
    void testMaskSSN_ShortValue() {
        // Given
        Map<String, Object> config = new HashMap<>();
        config.put("fields", "ssn");
        config.put("add.audit.fields", "false");
        transform.configure(config);

        Schema schema = SchemaBuilder.struct()
                .field("ssn", Schema.OPTIONAL_STRING_SCHEMA)
                .build();

        Struct value = new Struct(schema).put("ssn", "123");
        SourceRecord record = createSourceRecord(schema, value);

        // When
        SourceRecord transformedRecord = transform.apply(record);

        // Then
        Struct transformedValue = (Struct) transformedRecord.value();
        assertThat(transformedValue.getString("ssn"))
                .isEqualTo("1*3")
                .as("Short SSN should use generic masking (first and last char visible)");
    }

    // ==================== Email Masking Tests ====================
    
    @Test
    @DisplayName("Email should be masked showing first char and domain")
    void testMaskEmail_ShowsFirstCharAndDomain() {
        // Given
        Map<String, Object> config = new HashMap<>();
        config.put("fields", "email");
        config.put("mask.char", "*");
        config.put("add.audit.fields", "false");
        transform.configure(config);

        Schema schema = SchemaBuilder.struct()
                .field("email", Schema.OPTIONAL_STRING_SCHEMA)
                .build();

        Struct value = new Struct(schema).put("email", "john.doe@email.com");
        SourceRecord record = createSourceRecord(schema, value);

        // When
        SourceRecord transformedRecord = transform.apply(record);

        // Then
        Struct transformedValue = (Struct) transformedRecord.value();
        assertThat(transformedValue.getString("email"))
                .isEqualTo("j******e@email.com")
                .as("Email should show first and last char of local part plus domain");
    }

    @Test
    @DisplayName("Email without @ symbol should use generic masking (first and last char)")
    void testMaskEmail_NoAtSymbol() {
        // Given
        Map<String, Object> config = new HashMap<>();
        config.put("fields", "email");
        config.put("add.audit.fields", "false");
        transform.configure(config);

        Schema schema = SchemaBuilder.struct()
                .field("email", Schema.OPTIONAL_STRING_SCHEMA)
                .build();

        Struct value = new Struct(schema).put("email", "invalidemail");
        SourceRecord record = createSourceRecord(schema, value);

        // When
        SourceRecord transformedRecord = transform.apply(record);

        // Then
        Struct transformedValue = (Struct) transformedRecord.value();
        assertThat(transformedValue.getString("email"))
                .isEqualTo("i**********l")
                .as("Invalid email should use generic masking showing first and last char");
    }

    @Test
    @DisplayName("Email with null value should be handled gracefully")
    void testMaskEmail_NullValue() {
        // Given
        Map<String, Object> config = new HashMap<>();
        config.put("fields", "email");
        config.put("add.audit.fields", "false");
        transform.configure(config);

        Schema schema = SchemaBuilder.struct()
                .field("email", Schema.OPTIONAL_STRING_SCHEMA)
                .build();

        Struct value = new Struct(schema).put("email", null);
        SourceRecord record = createSourceRecord(schema, value);

        // When
        SourceRecord transformedRecord = transform.apply(record);

        // Then
        Struct transformedValue = (Struct) transformedRecord.value();
        assertThat(transformedValue.get("email")).isNull();
    }

    // ==================== Phone Masking Tests ====================
    
    @Test
    @DisplayName("Phone should be masked showing only last 4 digits")
    void testMaskPhone_ShowsLast4Digits() {
        // Given
        Map<String, Object> config = new HashMap<>();
        config.put("fields", "phone");
        config.put("mask.char", "*");
        config.put("add.audit.fields", "false");
        transform.configure(config);

        Schema schema = SchemaBuilder.struct()
                .field("phone", Schema.OPTIONAL_STRING_SCHEMA)
                .build();

        Struct value = new Struct(schema).put("phone", "555-0101");
        SourceRecord record = createSourceRecord(schema, value);

        // When
        SourceRecord transformedRecord = transform.apply(record);

        // Then
        Struct transformedValue = (Struct) transformedRecord.value();
        assertThat(transformedValue.getString("phone"))
                .isEqualTo("***-0101")
                .as("Phone should show only last 4 digits");
    }

    @Test
    @DisplayName("Phone with null value should be handled gracefully")
    void testMaskPhone_NullValue() {
        // Given
        Map<String, Object> config = new HashMap<>();
        config.put("fields", "phone");
        config.put("add.audit.fields", "false");
        transform.configure(config);

        Schema schema = SchemaBuilder.struct()
                .field("phone", Schema.OPTIONAL_STRING_SCHEMA)
                .build();

        Struct value = new Struct(schema).put("phone", null);
        SourceRecord record = createSourceRecord(schema, value);

        // When
        SourceRecord transformedRecord = transform.apply(record);

        // Then
        Struct transformedValue = (Struct) transformedRecord.value();
        assertThat(transformedValue.get("phone")).isNull();
    }

    // ==================== Multiple Fields Tests ====================
    
    @Test
    @DisplayName("Multiple PII fields should all be masked correctly")
    void testMaskMultipleFields() {
        // Given
        Map<String, Object> config = new HashMap<>();
        config.put("fields", "ssn,email,phone");
        config.put("mask.char", "*");
        config.put("add.audit.fields", "false");
        transform.configure(config);

        Schema schema = SchemaBuilder.struct()
                .field("patient_id", Schema.INT32_SCHEMA)
                .field("first_name", Schema.STRING_SCHEMA)
                .field("ssn", Schema.OPTIONAL_STRING_SCHEMA)
                .field("email", Schema.OPTIONAL_STRING_SCHEMA)
                .field("phone", Schema.OPTIONAL_STRING_SCHEMA)
                .build();

        Struct value = new Struct(schema)
                .put("patient_id", 1)
                .put("first_name", "John")
                .put("ssn", "123-45-6789")
                .put("email", "john.doe@email.com")
                .put("phone", "555-0101");

        SourceRecord record = createSourceRecord(schema, value);

        // When
        SourceRecord transformedRecord = transform.apply(record);

        // Then
        Struct transformedValue = (Struct) transformedRecord.value();
        assertThat(transformedValue.getString("ssn")).isEqualTo("***-**-6789");
        assertThat(transformedValue.getString("email")).isEqualTo("j******e@email.com");
        assertThat(transformedValue.getString("phone")).isEqualTo("***-0101");
        assertThat(transformedValue.getInt32("patient_id")).isEqualTo(1);
        assertThat(transformedValue.getString("first_name")).isEqualTo("John");
    }

    // ==================== Audit Fields Tests ====================
    
    @Test
    @DisplayName("Audit fields should be added when enabled")
    void testAuditFields_AddedWhenEnabled() {
        // Given
        Map<String, Object> config = new HashMap<>();
        config.put("fields", "ssn");
        config.put("add.audit.fields", "true");
        transform.configure(config);

        Schema schema = SchemaBuilder.struct()
                .field("ssn", Schema.OPTIONAL_STRING_SCHEMA)
                .build();

        Struct value = new Struct(schema).put("ssn", "123-45-6789");
        SourceRecord record = createSourceRecord(schema, value);

        // When
        SourceRecord transformedRecord = transform.apply(record);

        // Then
        Struct transformedValue = (Struct) transformedRecord.value();
        assertThat(transformedValue.schema().field("transformed_at"))
                .as("transformed_at field should be added")
                .isNotNull();
        assertThat(transformedValue.schema().field("transformer_version"))
                .as("transformer_version field should be added")
                .isNotNull();
        
        assertThat(transformedValue.getInt64("transformed_at"))
                .as("transformed_at should have a valid timestamp")
                .isGreaterThan(0L);
        assertThat(transformedValue.getString("transformer_version"))
                .as("transformer_version should match SMT version")
                .isEqualTo("1.0.0");
    }

    @Test
    @DisplayName("Audit fields should not be added when disabled")
    void testAuditFields_NotAddedWhenDisabled() {
        // Given
        Map<String, Object> config = new HashMap<>();
        config.put("fields", "ssn");
        config.put("add.audit.fields", "false");
        transform.configure(config);

        Schema schema = SchemaBuilder.struct()
                .field("ssn", Schema.OPTIONAL_STRING_SCHEMA)
                .build();

        Struct value = new Struct(schema).put("ssn", "123-45-6789");
        SourceRecord record = createSourceRecord(schema, value);

        // When
        SourceRecord transformedRecord = transform.apply(record);

        // Then
        Struct transformedValue = (Struct) transformedRecord.value();
        assertThat(transformedValue.schema().field("transformed_at"))
                .as("transformed_at field should not be added")
                .isNull();
        assertThat(transformedValue.schema().field("transformer_version"))
                .as("transformer_version field should not be added")
                .isNull();
    }

    // ==================== Edge Cases Tests ====================
    
    @Test
    @DisplayName("Record with no matching PII fields should pass through unchanged")
    void testRecordWithoutPII_PassesThrough() {
        // Given
        Map<String, Object> config = new HashMap<>();
        config.put("fields", "ssn,email,phone");
        config.put("add.audit.fields", "false");
        transform.configure(config);

        Schema schema = SchemaBuilder.struct()
                .field("patient_id", Schema.INT32_SCHEMA)
                .field("first_name", Schema.STRING_SCHEMA)
                .build();

        Struct value = new Struct(schema)
                .put("patient_id", 1)
                .put("first_name", "John");

        SourceRecord record = createSourceRecord(schema, value);

        // When
        SourceRecord transformedRecord = transform.apply(record);

        // Then
        Struct transformedValue = (Struct) transformedRecord.value();
        assertThat(transformedValue.getInt32("patient_id")).isEqualTo(1);
        assertThat(transformedValue.getString("first_name")).isEqualTo("John");
    }

    @Test
    @DisplayName("Null record should be handled gracefully")
    void testNullRecord() {
        // Given
        Map<String, Object> config = new HashMap<>();
        config.put("fields", "ssn");
        transform.configure(config);

        SourceRecord record = new SourceRecord(
                null, null, "test-topic", 0, null, null, null, null
        );

        // When
        SourceRecord transformedRecord = transform.apply(record);

        // Then
        assertThat(transformedRecord).isEqualTo(record);
    }

    @Test
    @DisplayName("Record with null value should be handled gracefully")
    void testRecordWithNullValue() {
        // Given
        Map<String, Object> config = new HashMap<>();
        config.put("fields", "ssn");
        transform.configure(config);

        Schema schema = SchemaBuilder.struct()
                .field("ssn", Schema.OPTIONAL_STRING_SCHEMA)
                .build();

        SourceRecord record = new SourceRecord(
                null, null, "test-topic", 0, schema, null
        );

        // When
        SourceRecord transformedRecord = transform.apply(record);

        // Then
        assertThat(transformedRecord).isEqualTo(record);
    }

    @Test
    @DisplayName("Custom mask character should be used")
    void testCustomMaskCharacter() {
        // Given
        Map<String, Object> config = new HashMap<>();
        config.put("fields", "ssn");
        config.put("mask.char", "#");
        config.put("add.audit.fields", "false");
        transform.configure(config);

        Schema schema = SchemaBuilder.struct()
                .field("ssn", Schema.OPTIONAL_STRING_SCHEMA)
                .build();

        Struct value = new Struct(schema).put("ssn", "123-45-6789");
        SourceRecord record = createSourceRecord(schema, value);

        // When
        SourceRecord transformedRecord = transform.apply(record);

        // Then
        Struct transformedValue = (Struct) transformedRecord.value();
        assertThat(transformedValue.getString("ssn"))
                .isEqualTo("###-##-6789")
                .as("Custom mask character should be used");
    }

    // ==================== Schemaless (Map) Tests ====================
    
    @Test
    @DisplayName("Schemaless record (Map) should be transformed correctly")
    void testSchemalessRecord_MapsCorrectly() {
        // Given
        Map<String, Object> config = new HashMap<>();
        config.put("fields", "ssn,email,phone");
        config.put("mask.char", "*");
        config.put("add.audit.fields", "true");
        transform.configure(config);

        Map<String, Object> value = new HashMap<>();
        value.put("patient_id", 1);
        value.put("ssn", "123-45-6789");
        value.put("email", "john.doe@email.com");
        value.put("phone", "555-0101");

        SourceRecord record = new SourceRecord(
                null, null, "test-topic", 0,
                Schema.STRING_SCHEMA, "key",
                null, value
        );

        // When
        SourceRecord transformedRecord = transform.apply(record);

        // Then
        @SuppressWarnings("unchecked")
        Map<String, Object> transformedValue = (Map<String, Object>) transformedRecord.value();
        assertThat(transformedValue.get("ssn")).isEqualTo("***-**-6789");
        assertThat(transformedValue.get("email")).isEqualTo("j******e@email.com");
        assertThat(transformedValue.get("phone")).isEqualTo("***-0101");
        assertThat(transformedValue.get("patient_id")).isEqualTo(1);
        assertThat(transformedValue).containsKey("transformed_at");
        assertThat(transformedValue).containsKey("transformer_version");
        assertThat(transformedValue.get("transformer_version")).isEqualTo("1.0.0");
    }

    // ==================== Configuration Tests ====================
    
    @Test
    @DisplayName("Config with default values should work")
    void testDefaultConfiguration() {
        // Given
        Map<String, Object> config = new HashMap<>();
        config.put("fields", "ssn");
        // mask.char defaults to "*"
        // add.audit.fields defaults to true
        
        // When/Then - should not throw exception
        assertDoesNotThrow(() -> transform.configure(config));
    }

    // ==================== Helper Methods ====================
    
    private SourceRecord createSourceRecord(Schema schema, Struct value) {
        return new SourceRecord(
                null,                           // sourcePartition
                null,                           // sourceOffset
                "test-topic",                   // topic
                0,                              // partition
                Schema.STRING_SCHEMA,           // keySchema
                "test-key",                     // key
                schema,                         // valueSchema
                value                           // value
        );
    }
}
