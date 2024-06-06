package com.power.MRPUSA.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.opencsv.CSVWriter;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

@Service
@Slf4j
public class OpensourceCollector implements ICollector {


    /**
     * The Endpoint Triggers the refresh method when accessed.
     */
    @Override
    public void refresh() {
        opensSourceJsonToScv();
        log.info("PHYSICAL_SERVER POWERMETRIC DATA collected :" + Instant.now());
    }



    /**
     * main()- for opensource requirement, we save power-metric data in JSON format(src/main/resources/input/powermetricData.json) which convert into CSV response offline(src/main/resources/output/physical_server_powermetric.csv)
     */
    @SneakyThrows
    public void opensSourceJsonToScv() {
        List<String[]> exctractFilds = new ArrayList<>();
        JsonNode jsonTree = new ObjectMapper().readTree(new File("src/main/resources/input/powermetricData.json"));
        for (JsonNode powerTree : jsonTree) {
            String ServerName = powerTree.get("ServerName").asText();
            log.info("ServerName :" + ServerName);
            String MinConsumedWatts = powerTree.get("MinConsumedWatts").asText();
            log.info("MinConsumedWatts :" + MinConsumedWatts);
            String AverageConsumedWatts = powerTree.get("AverageConsumedWatts").asText();
            log.info("AverageConsumedWatts :" + AverageConsumedWatts);
            String MaxConsumedWatts = powerTree.get("MaxConsumedWatts").asText();
            log.info("MaxConsumedWatts :" + MaxConsumedWatts);
            String IntervalInMin = powerTree.get("IntervalInMin").asText();
            log.info("IntervalInMin :" + IntervalInMin);
            String ts = powerTree.get("ts").asText();
            log.info("ts :" + ts);
            exctractFilds.add(new String[]{ts, ServerName, MinConsumedWatts, AverageConsumedWatts, MaxConsumedWatts, IntervalInMin});
            log.info("exctractFilds" + exctractFilds);
            String outputfile = "src/main/resources/output/physical_server_powermetric.csv";
            try (FileWriter fileWriter = new FileWriter(outputfile);
                 CSVWriter csvWriter = new CSVWriter(fileWriter)) {
                String[] header = {"ts", "ServerName", "MinConsumedWatts", "AverageConsumedWatts", "MaxConsumedWatts", "IntervalInMin"};
                csvWriter.writeNext(header);
                csvWriter.writeAll(exctractFilds);
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        }
    }
}
