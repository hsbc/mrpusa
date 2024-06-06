package com.power.MRPUSA.service;

import static com.power.MRPUSA.util.Utils.deviceApiHeaders;

import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.StreamSupport;
import javax.annotation.PostConstruct;
import javax.annotation.PreDestroy;

import autovalue.shaded.com.google.common.base.Strings;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import org.json.JSONArray;
import org.json.JSONObject;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Lazy;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.stereotype.Service;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestTemplate;


/**
 * PhysicalServerCollector class is designed to collect server list and extract required fields
 */
@Service
@Slf4j
public class PhysicalServerCollector implements ICollector {

    @Autowired
    @Lazy
    @Qualifier("server-info-executor")
    ThreadPoolTaskExecutor executor;
    @Autowired
    @Qualifier("restTemplateByPassSSL")
    RestTemplate restTemplate;
    @Autowired
    @Lazy
    PowerMetricCollector powerMetric;

    @Value("<your_server_list_endpoint>")
    String SERVER_LIST_URL;
    @Value("<your_devices_list_endpoint>")
    String SERVER_DEVICE_URL;
    @Value("<your_hostname>")
    List<String> hostnames;

    MultiValueMap<String, Object> cache = new LinkedMultiValueMap();
    AtomicBoolean isReady = new AtomicBoolean(false);
    AtomicInteger counter = new AtomicInteger(0);
    String reportDate = DateTimeFormatter.ofPattern("yyyy-MM-dd").withZone(ZoneId.systemDefault()).format(Instant.now());


    @PostConstruct
    public void init() {

        powerMetric.setServerCollector(this);
    }


    /**
     * The Endpoint Triggers the refresh method when accessed.
     */
    @SneakyThrows
    @Override
    public void refresh() {
        log.info("Refreshing at " + Instant.now());
        //reset();
        collect();
        log.info("Cache key size is {}.", cache.keySet().size());
        startDeviceTasks();

    }

    /**
     * This collect() method start collecting server list and extract required fields.
     * This method iterates-through a list of hostnames and processes each server response to extract necessary fields and stored in json object.
     * The code retrieves the servers in pages of 5000 servers each. The loop continues until all pages of server has been retrieved.
     * The code extracts the servers from the response and add them to @serverlist.
     */

    private JSONArray collect() {
        ResponseEntity<String> responseEntity;

        int pageIndex = 0;
        int pageSize = 5000;
        boolean allPagesDone = false;
        List<String> allServers = new ArrayList<>();
        String pagedUrl;
        JSONArray results;

        while (!allPagesDone) {
            for (String hname : hostnames) {
                String apiURL = SERVER_LIST_URL + hname;
                pagedUrl = apiURL + "&pageIndex=" + pageIndex + "&pageSize=" + pageSize;
                log.info("SERVER_LIST_URL" + apiURL);

                responseEntity = restTemplate.exchange(pagedUrl, HttpMethod.GET, deviceApiHeaders, String.class);
                results = new JSONArray(Objects.requireNonNull(responseEntity.getBody()));

                log.info("Here I am , got servers " + results.length() + " from " + pagedUrl + " so far " + allServers.toArray().length);
                if (results.length() < 1) {
                    allPagesDone = true;
                }

                List<JSONObject> vmServices = new ArrayList<>();
                StreamSupport.stream(results.spliterator(), false).map(val -> (JSONObject) val).filter(val -> val.optJSONObject("type") != null).filter(val -> val.optJSONObject("lifeCycleState") != null)
//                    .filter(val -> val.optJSONObject("itOrganisation") != null)
                        .filter(val -> !Objects.equals(val.optJSONObject("lifeCycleState").optString("name"), "Demised")).forEach(val -> {
                            String hostname = val.getString("hostname");

                            //based on Physical and Virtual they have sorted out

                            if ("Physical".equals(val.optJSONObject("type").optString("name"))) {
                                allServers.add(hostname);
                                cache.add(hostname, val);
                            } else if ("Virtual".equals(val.optJSONObject("type").optString("name"))) {
                                JSONObject tmp = new JSONObject();
                                tmp.put("import_date", reportDate);
                                tmp.put("name", hostname);
                                tmp.put("services", val.optJSONArray("services").toString());
                                vmServices.add(tmp);
                            } else {
                                log.warn("Cannot determine its server type: {}", hostname);
                            }
                        });
            }

            pageIndex = pageIndex + 1;
        }

        return new JSONArray(allServers);
    }

    /**
     * getServerDevice()method is retrieve information about a server device from server device API.
     * This method takes hostname as input and return wrapped JSON object contacting the server device information.
     * if the response contain data, the method parses the JSON to extract (consoleFqdn,vendor,model) fields. if consoleFqdn missing it uses consoleIpAddress
     * The extracted information added to as JSON object named wrapped
     */


    public JSONObject getServerDevice(String hostname) {
        JSONObject wrapped = new JSONObject();
        try {
            ResponseEntity<String> responseEntity = restTemplate.exchange(SERVER_DEVICE_URL, HttpMethod.GET, deviceApiHeaders, String.class, hostname);
            String deviceResponse = responseEntity.getBody();

            log.info("{} - devices: {}", hostname, deviceResponse.length());

            if (!deviceResponse.equalsIgnoreCase("[]")) {
                JSONObject device = new JSONArray(deviceResponse).getJSONObject(0).getJSONObject("device");

                if (Strings.isNullOrEmpty(device.optString("consoleFqdn"))) {
                    if (!Strings.isNullOrEmpty(device.optString("consoleIpAddress"))) {
                        wrapped.put("consoleFqdn", device.optString("consoleIpAddress"));
                    }
                } else {
                    wrapped.put("consoleFqdn", device.optString("consoleFqdn"));
                }

                wrapped.put("vendor", device.optJSONObject("model").optJSONObject("manufacturer").optString("name"));
                wrapped.put("model", device.optJSONObject("model").optString("name"));
            }
        } catch (Exception e) {
            log.warn(hostname + ": " + e.getMessage(), e);
        }

        return wrapped;
    }


    /**
     * The startDeviceTasks method initialize task to collect server data in parallel from cache of hostnames.
     * It retrieves the total number of hostnames and processes them concurrently using a parallel stream. For each hostnames it submits  a
     * task to an executor service which calls the getServerDevice() to collect data from hostname.
     */

    private void startDeviceTasks() {
        int total = cache.keySet().size();

        cache.keySet().parallelStream().forEach(hostname -> executor.submit(() -> {
            JSONObject device = getServerDevice(hostname);
            cache.add(hostname, device);


            if (counter.incrementAndGet() == total) {
                isReady.compareAndExchange(false, true);
                powerMetric.start();
                log.info("Server cache is ready, start to collect power metrics.");
            }
        }));
    }


    /**
     * the following code ensure the executor service is properly shutdown after executing tasks
     */
    @PreDestroy
    public void close() {
        if (!executor.getThreadPoolExecutor().isShutdown()) {
            log.info("Destroying server-info-executor...");
            executor.destroy();
        }
    }


    private void reset() {
        counter = new AtomicInteger(0);
        isReady.compareAndExchange(true, false);
        cache.clear();

        reportDate = DateTimeFormatter.ofPattern("yyyy-MM-dd").withZone(ZoneId.systemDefault()).format(Instant.now());
    }
}
