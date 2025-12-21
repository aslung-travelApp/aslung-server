package com.trip.aslung.ai.model.service;

import com.trip.aslung.util.GeoConverter;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.net.URI;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

@Service
@RequiredArgsConstructor
@Slf4j
public class WeatherService {

    @Value("${weather.api.key}")
    private String serviceKey;

    private final RestTemplate restTemplate;

    public String getCurrentWeather(String latStr, String lngStr) {
        try {
            double lat = Double.parseDouble(latStr);
            double lng = Double.parseDouble(lngStr);
            GeoConverter.Point point = GeoConverter.toGrid(lat, lng);

            LocalDateTime now = LocalDateTime.now();
            if (now.getMinute() < 40) now = now.minusHours(1);

            String baseDate = now.format(DateTimeFormatter.ofPattern("yyyyMMdd"));
            String baseTime = now.format(DateTimeFormatter.ofPattern("HH00"));

            // 기상청 EndPoint는 여기 고정입니다 (수정 X)
            String url = String.format(
                    "http://apis.data.go.kr/1360000/VilageFcstInfoService_2.0/getUltraSrtNcst" +
                            "?serviceKey=%s&pageNo=1&numOfRows=10&dataType=JSON" +
                            "&base_date=%s&base_time=%s&nx=%d&ny=%d",
                    serviceKey, baseDate, baseTime, point.x, point.y
            );

            URI uri = new URI(url);
            String response = restTemplate.getForObject(uri, String.class);

            if (response.contains("\"category\":\"PTY\",\"obsrValue\":\"1\"")) return "Rainy";
            if (response.contains("\"category\":\"PTY\",\"obsrValue\":\"3\"")) return "Snowy";
            return "Clear";
        } catch (Exception e) {
            log.error("날씨 API 에러", e);
            return "Clear";
        }
    }
}