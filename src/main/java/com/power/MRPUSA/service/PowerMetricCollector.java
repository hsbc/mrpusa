package com.power.MRPUSA.service;

import java.io.FileWriter;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.Map;
import javax.annotation.PreDestroy;

import autovalue.shaded.com.google.common.base.Strings;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.opencsv.CSVWriter;
import com.power.MRPUSA.util.Utils;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import org.json.JSONException;
import org.json.JSONObject;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Lazy;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler;
import org.springframework.stereotype.Service;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestTemplate;


/**
 * The class PowerMetricCollector is designed to collect power-metric data of hostnames for every 20 min
 */


@Slf4j
@Service
public class PowerMetricCollector {
    PhysicalServerCollector serverCollector;

    /**
     * SERVER_AUTH_USR,SERVER_AUTH_PWD will get the @value from application.yaml
     */

    @Autowired
    @Lazy
    @Qualifier("metric-task-scheduler")
    ThreadPoolTaskScheduler scheduler;
    @Autowired
    @Qualifier("restTemplateByPassSSL")
    RestTemplate restTemplate;

    @Value("<server_name>")
    String SERVER_AUTH_USR;
    @Value("<server_password>")
    String SERVER_AUTH_PWD;


    /**
     * apiMap stores the vendor api information
     */

    static final Map<String, String> apiMap = Map.of(
            "DELL", "/redfish/v1/Chassis/System.Embedded.1/Power",
            "DELL EMC", "/redfish/v1/Chassis/System.Embedded.1/Power",
            "DELLEMC", "/redfish/v1/Chassis/System.Embedded.1/Power",
            "HEWLETT PACKARD", "/redfish/v1/chassis/1/power",
            "HEWLETT-PACKARD", "/redfish/v1/chassis/1/power",
            "LENOVO", "/redfish/v1/Chassis/1/Power"
    );

    void setServerCollector(PhysicalServerCollector physicalServerCollector) {
        this.serverCollector = physicalServerCollector;
    }


    /**
     * Once the serverCollector is ready, method checks if the scheduler executor is shutdown; if not it destroys the existing scheduler and logs this actions
     * The method processes each entry in the server collectors cache in parallel, filtering out entries with empty JSON object and start task for every 20 min.
     */
    public void start() {
        if (serverCollector.isReady.get()) {
            if (!scheduler.getScheduledExecutor().isShutdown()) {
                scheduler.destroy();
                log.info("powerMetricTaskScheduler has been destroyed.");
            }
            /** the scheduler is reinitialize with another log message confirming this */
            scheduler.initialize();
            log.info("powerMetricTaskScheduler has been initialized again.");

            serverCollector.cache.entrySet().parallelStream()
                    .filter(e -> !((JSONObject) e.getValue().get(1)).isEmpty())
                    .forEach(entry -> {
                        try {
                            JSONObject device = (JSONObject) entry.getValue().get(1);
                            PowerMetricTask task = new PowerMetricTask(entry.getKey(),
                                    device.optString("vendor"), device.optString("consoleFqdn"));
                            scheduler.scheduleAtFixedRate(task, Duration.of(20, ChronoUnit.MINUTES));
                        } catch (JSONException e) {
                            log.warn(entry.getKey() + ": " + e.getMessage(), e);
                        }
                    });
        }
    }


    /**
     * the following code ensure Destroying metric-task-scheduler executing tasks
     */

    @PreDestroy
    public void close() {
        log.info("Destroying metric-task-scheduler...");
        scheduler.destroy();
    }


    /**
     * The Endpoint Triggers the refresh method when accessed.*
     * run()- will fetch live power metric data for every 20 min.
     */
    class PowerMetricTask implements Runnable {
        private final String hostname;
        private final String vendor;
        private final String consoleFqdn;

        public PowerMetricTask(String hostname, String vendor, String consoleFqdn) {
            this.hostname = hostname;
            this.vendor = vendor;
            this.consoleFqdn = consoleFqdn;
        }


        /**
         * run()-will fetch live power metric data for every 20 min. and saved response in CSV file
         * /*getRedfishInfo() retrieve redfish information for given hostname and vendor and mapped to vendors devices through consoleFqdn and save response to JSON object named out
         * /** extractPowerMetrics() is collecting power metric fields for that particular hostnames else will give missing power status for hostname
         */
        @SneakyThrows
        @Override
        public void run() {
            log.info("Collecting power metric from {}: vendor = {}, consoleFqdn = {}", hostname, vendor, consoleFqdn);
            JSONObject redfish = getRedfishInfo(hostname, vendor, consoleFqdn);
            JSONObject powerMetric = extractPowerMetrics(redfish, hostname);
            log.info("powerMetric" + powerMetric);
            List<String[]> exctractFilds = new ArrayList<>();
            JsonNode powerTree = new ObjectMapper().readTree(String.valueOf(powerMetric));
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
            String ts = getUTCTimestamp();
            log.info("ts :" + ts);
            exctractFilds.add(new String[]{ts, ServerName, MinConsumedWatts, AverageConsumedWatts, MaxConsumedWatts, IntervalInMin});
            log.info("exctractFilds" + exctractFilds);
            String outputfile = "src/main/resources/output/physical_server_powermetric.csv";
            try (FileWriter fileWriter = new FileWriter(outputfile, true);
                 CSVWriter csvWriter = new CSVWriter(fileWriter)) {
                String[] header = {"ts", "ServerName", "MinConsumedWatts", "AverageConsumedWatts", "MaxConsumedWatts", "IntervalInMin"};
                csvWriter.writeNext(header);
                csvWriter.writeAll(exctractFilds);

            } catch (Exception e) {
                e.printStackTrace();
            }
        }
    }


    /**
     * getUTCTimestamp() gives the current timestamps
     */

    private String getUTCTimestamp() {

        return Instant.now().atOffset(ZoneOffset.UTC).format(DateTimeFormatter.ISO_INSTANT);
    }


    /**
     * getRedfishInfo() retrieve redfish information for given hostname and
     * vendor and mapped to vendors devices through consoleFqdn and save response to JSON object named out
     */

    private JSONObject getRedfishInfo(String hostname, String vendor, String consoleFqdn) {
        JSONObject out = new JSONObject();

        if (Strings.isNullOrEmpty(vendor)) {
            log.warn("{}: Unable to get a hardware vendor for the device", hostname);
            return out;
        }
        if (Strings.isNullOrEmpty(consoleFqdn)) {
            log.warn("{}: Device name/consoleFqdn could not be found for given hostname. The server should exist in Device collector",
                    hostname);
            return out;
        }
        String redfishUrl = "";
        try {
            HttpEntity<String> httpEntity = redfishApiHeaders();

            String redfishMapped = apiMap.get(vendor);
            if (redfishMapped == null) {
                return out;
            }
            redfishUrl = "https://" + consoleFqdn + redfishMapped;
            ResponseEntity<String> responseEntity = restTemplate.exchange(redfishUrl, HttpMethod.GET, httpEntity, String.class);
            if (responseEntity.getStatusCode().is2xxSuccessful()) {
                String redfishJsonStr = responseEntity.getBody();
                out = new JSONObject(redfishJsonStr);
            }
        } catch (HttpClientErrorException re) {
            saveFailure(hostname, consoleFqdn, redfishUrl, re.getStatusCode().toString(), re.getMessage(), re.getResponseBodyAsString());
        } catch (Exception e) {
            saveFailure(hostname, consoleFqdn, redfishUrl, "XXX", e.getMessage(), null);
        }

        return out;
    }

    /**
     * missing information about power metric data is saved into saveFailure() from getRedfishInfo() method
     */
    private void saveFailure(String hostname, String fqdn, String redfishUrl, String errorCode, String error, String metadata) {
        try {
            JSONObject failure = new JSONObject();
            failure.put("serverName", hostname);
            failure.put("consoleFqdn", fqdn);
            failure.put("redfishUrl", redfishUrl);
            failure.put("errorCode", errorCode);
            failure.put("message", error);

            if (!Strings.isNullOrEmpty(metadata)) {
                failure.put("metadata", metadata);
            }
        } catch (Exception ex) {
            throw new RuntimeException(ex);
        }
    }


    /**
     * extractPowerMetrics() is collecting power metric fields for that particular hostnames else will give missing power status for hostname
     */

    private JSONObject extractPowerMetrics(JSONObject redfishJson, String hostname) {
        JSONObject returnObj = new JSONObject();

        log.info("redfishJson" + redfishJson);

        if (redfishJson.has("PowerControl")) {
            returnObj.put("ServerName", hostname);

            JSONObject metricsJson = redfishJson.optJSONArray("PowerControl").optJSONObject(0)
                    .optJSONObject("PowerMetrics");
            returnObj.put("AverageConsumedWatts", String.valueOf(metricsJson.optInt("AverageConsumedWatts")));
            returnObj.put("MaxConsumedWatts", String.valueOf(metricsJson.optInt("MaxConsumedWatts")));
            returnObj.put("MinConsumedWatts", String.valueOf(metricsJson.optInt("MinConsumedWatts")));
            returnObj.put("IntervalInMin", String.valueOf(metricsJson.optInt("IntervalInMin")));

        } else {
            log.warn("Missing power status in json for: " + hostname);
        }

        return returnObj;
    }

    private String getBasicAuthString() {
        return "Basic " + Base64.getEncoder().encodeToString((SERVER_AUTH_USR + ":" + Utils.decrypt(SERVER_AUTH_PWD)).getBytes());
    }


    private HttpEntity<String> redfishApiHeaders() {
        HttpHeaders headers = new HttpHeaders();
        headers.set("Authorization", getBasicAuthString());
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.setAccept(List.of(MediaType.ALL));
        return new HttpEntity<>("", headers);
    }
}

























