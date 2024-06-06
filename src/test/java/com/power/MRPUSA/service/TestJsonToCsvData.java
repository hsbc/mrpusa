package com.power.MRPUSA.service;

import static com.jayway.jsonpath.internal.path.PathCompiler.fail;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import java.io.File;
import java.io.IOException;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.powermock.core.classloader.annotations.PowerMockIgnore;
import org.powermock.core.classloader.annotations.PrepareForTest;
import org.powermock.modules.junit4.PowerMockRunner;

/**
 * TestJsonToCsvData test class is designed to ensure JSON file should not be null while converting
 * input JSON to output CSV file
 */
@RunWith(PowerMockRunner.class)
@PrepareForTest(TestJsonToCsvData.class)
@PowerMockIgnore({"com.sun.org.apache.xerces.*", "javax.xml.*", "org.xml.*", "javax.management.*"})
public class TestJsonToCsvData {

    @Test
    public void readJsonFileTest() {

        String inputJsonPath = "src/main/resources/input/powermetricData.json";
        ObjectMapper objectMapper = new ObjectMapper();

        try {
            JsonNode jsonNode = objectMapper.readTree(new File(inputJsonPath));
            assertNotNull(jsonNode, "JSON node should not be null");
        } catch (IOException e) {
            fail("IO Exception occured: " + e.getMessage());
        }

    }


}






