package uk.gov.hmcts.reform.hmc.data;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.InjectMocks;
import org.mockito.MockitoAnnotations;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.fail;

class JsonDataConverterTest {

    private static ObjectMapper mapper = new ObjectMapper();

    static {
        mapper.setSerializationInclusion(JsonInclude.Include.NON_NULL);
    }

    @InjectMocks
    private JsonDataConverter jsonbConverter;

    @BeforeEach
    public void setup() {
        MockitoAnnotations.openMocks(this);
        jsonbConverter = new JsonDataConverter();
    }

    @Test
    void convertToDatabaseColumn() throws Exception {
        assertNull(jsonbConverter.convertToDatabaseColumn(null));

        final String jsonString = "{\"key\":\"value\"}";
        assertEquals(jsonString, jsonbConverter.convertToDatabaseColumn(mapper.readTree(jsonString)));
    }

    @Test
    void convertToEntityAttribute() {
        // Testing null
        assertNull(jsonbConverter.convertToEntityAttribute(null));

        // Teasing valid non null
        final JsonNode converted = jsonbConverter.convertToEntityAttribute("{\"key\":\"value\"}");
        assertEquals("value", converted.get("key").asText());

        try {
            jsonbConverter.convertToEntityAttribute("hjkdash\"");
            fail("Expected failure due to incorrect JSON");
        } catch (Exception e) {
            assertNotNull(e);
        }
    }
}
