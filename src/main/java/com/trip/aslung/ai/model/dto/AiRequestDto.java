package com.trip.aslung.ai.model.dto;

import lombok.Data;
import java.util.List;

@Data
public class AiRequestDto {
    private String x;            // 경도 (lng)
    private String y;            // 위도 (lat)
    private String companion;    // 누구와 (연인, 친구...)
    private List<String> styles; // 스타일 (힐링, 핫플...)
    private String type;         // "PLACE" (장소 하나) or "COURSE" (코스)
    private String keyword;
}