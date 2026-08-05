package uk.gov.hmcts.reform.hmc.data;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

@ExtendWith(MockitoExtension.class)
class JsonDataConverterTest {

    private static final ObjectMapper mapper = new ObjectMapper();

    static {
        mapper.setSerializationInclusion(JsonInclude.Include.NON_NULL);
    }

    @InjectMocks
    private JsonDataConverter jsonbConverter;

    @BeforeEach
    void setup() {
        jsonbConverter = new JsonDataConverter();
    }

    @Test
    void convertToDatabaseColumn() throws Exception {
        final String jsonString = "{\"key\":\"value\"}";
        assertEquals(jsonString, jsonbConverter.convertToDatabaseColumn(mapper.readTree(jsonString)));
    }

    @Test
    void convertToDatabaseColumn_shouldReturnNull() {
        assertNull(jsonbConverter.convertToDatabaseColumn(null));
    }

    @Test
    void convertToEntityAttribute() {
        final JsonNode converted = jsonbConverter.convertToEntityAttribute("{\"key\":\"value\"}");
        assertEquals("value", converted.get("key").asText());
    }

    @Test
    void convertToEntityAttribute_shouldReturnNull() {
        assertNull(jsonbConverter.convertToEntityAttribute(null));
    }

    @Test
    void convertToEntityAttribute_shouldThrowRuntimeException() {
        RuntimeException exception =
            assertThrows(RuntimeException.class,
                         () -> jsonbConverter.convertToEntityAttribute("hjkdash\""),
                         "Expected failure due to incorrect JSON");
        assertNotNull(exception);
        assertEquals("Unable to deserialize to json field", exception.getMessage());
    }
}
