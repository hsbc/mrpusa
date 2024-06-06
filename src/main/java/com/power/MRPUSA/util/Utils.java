package com.power.MRPUSA.util;

import java.util.Base64;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import javax.annotation.PostConstruct;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.stereotype.Component;


/**
 * Utils class is designed for required Credentials ,URL, HTTP Headers
 */

@Slf4j
@Component
public class Utils {

    private static final Pattern ENC_PATTERN = Pattern.compile("^ENC\\((.*)\\)$");

    @Value("<username>")
    String X_API_USER;
    @Value("<key>")
    String X_API_KEY;

    public static HttpEntity<String> deviceApiHeaders;

    @PostConstruct
    private void init() {
        HttpHeaders headers = new HttpHeaders();
        headers.set("x-api-user", X_API_USER);
        headers.set("x-api-key", Utils.decrypt(X_API_KEY));
        deviceApiHeaders = new HttpEntity<>("", headers);
    }

    public static String decrypt(String encrypted) {
        Matcher matcher = ENC_PATTERN.matcher(encrypted);
        if (matcher.find()) {
            return new String(Base64.getDecoder().decode(matcher.group(1)));
        }
        return encrypted;
    }
}
