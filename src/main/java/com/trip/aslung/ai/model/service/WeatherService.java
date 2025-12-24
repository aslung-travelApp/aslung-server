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

            log.info("기상청 API 응답: {}", response);

            // 에러 체크 (기상청은 에러나면 JSON 안에 resultMsg 등을 줍니다)
            if (response == null || !response.contains("NORMAL_SERVICE")) {
                log.error("기상청 API 호출 실패 또는 에러 발생");
                return "Clear"; // 실패 시 기본값
            }

            // 1:비, 2:비/눈, 5:빗방울
            if (response.contains("\"obsrValue\":\"1\"") ||
                    response.contains("\"obsrValue\":\"2\"") ||
                    response.contains("\"obsrValue\":\"5\"")) {
                return "Rainy";
            }

            // 3:눈, 6:빗방울/눈날림, 7:눈날림
            if (response.contains("\"obsrValue\":\"3\"") ||
                    response.contains("\"obsrValue\":\"6\"") ||
                    response.contains("\"obsrValue\":\"7\"")) {
                return "Snowy";
            }

            return "Clear";
        } catch (Exception e) {
            log.error("날씨 API 에러", e);
            return "Clear";
        }
    }
}