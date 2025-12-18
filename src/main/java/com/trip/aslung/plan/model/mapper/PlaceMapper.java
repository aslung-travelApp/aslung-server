package com.trip.aslung.plan.model.mapper;

import com.trip.aslung.plan.model.dto.Place;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

@Mapper
public interface PlaceMapper {
    Place findByKakaoMapId(String kakaoMapId);
    Place findByNameAndLocation(@Param("name") String name,
                                @Param("lat") double lat,
                                @Param("lng") double lng);
    void updateKakaoMapId(@Param("placeId") Long placeId,
                          @Param("kakaoMapId") String kakaoMapId);
    void savePlace(Place placeDto);
}
