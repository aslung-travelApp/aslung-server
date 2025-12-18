package com.trip.aslung.plan.model.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class SocketEventDTO {
    private String type;     // "ADD", "UPDATE", "DELETE"
    private Long planId;     // 어느 여행 계획인지
    private Object data;     // 실제 일정 객체나 삭제된 ID
}