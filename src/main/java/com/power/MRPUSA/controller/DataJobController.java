package com.power.MRPUSA.controller;

import javax.validation.Valid;

import com.power.MRPUSA.service.PhysicalServerCollector;
import com.power.MRPUSA.service.ICollector;
import com.power.MRPUSA.service.OpensourceCollector;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RestController;


/**
 * DataJobController() runs data collection job which is designed to be triggered by POST request
 * specifying the data to collect jobName(PHYSICAL_SERVER,OPENSOURCE_GENERIC)
 */


@Tag(name = "Data Job Controller")
@RestController
@Slf4j
public class DataJobController {

    @Autowired
    private PhysicalServerCollector physicalServerCollector;
    @Autowired
    private OpensourceCollector opensourceCollector;

    @Operation(summary = "Oneshot data job trigger", operationId = "oneshotCollect",
            description = "Manually trigger backend job",
            responses = {
                    @ApiResponse(responseCode = "202", description = "Accepted the request."),
                    @ApiResponse(responseCode = "400", description = "Bad Request. Your request cannot be processed. Please refer to the return message."),
                    @ApiResponse(responseCode = "401", description = "Unauthorized access"),
                    @ApiResponse(responseCode = "403", description = "Forbidden due to processing error. Please refer to the error code"),
                    @ApiResponse(responseCode = "500", description = "Error processing the API. Please check the individual error code and contact support")})

    @RequestMapping(value = "/v1/job/oneshot",
            produces = {"application/json"},
            consumes = {"application/json"},
            method = RequestMethod.POST)
    ResponseEntity<String> oneshotCollect(@Parameter @Valid @RequestBody String jobName) {

        ICollector target = null;

        switch (jobName) {

            case "PHYSICAL_SERVER":
                target = physicalServerCollector;
                break;
            case "OPENSOURCE_GENERIC":
                target = opensourceCollector;
                break;
            default:
                log.warn("CANNOT find the corresponding backend job - {}", jobName);
                return new ResponseEntity<>("CANNOT find the corresponding backend job -" + jobName, HttpStatus.ACCEPTED);
        }


        if (target != null) {
            new Thread(target::refresh).start();
        }
        return new ResponseEntity<String>(target + " will be executed onetime soon!", HttpStatus.ACCEPTED);
    }

}