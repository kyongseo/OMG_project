package com.example.omg_project.domain.trip.controller;

import com.example.omg_project.domain.trip.dto.CityDTO;
import com.example.omg_project.domain.trip.entity.City;
import com.example.omg_project.domain.trip.service.CityService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/cities")
@RequiredArgsConstructor

/**
 * 도시 이름으로 도시 ID 조회
 *
 * @param cityName 조회할 도시의 이름
 * @return 도시 ID
 */
public class CityApiController {
    private final CityService cityService;

    @GetMapping("/name/{cityName}")
    public ResponseEntity<CityDTO> getCityByName(@PathVariable String cityName) {
        City city = cityService.getCityByName(cityName)
                .orElseThrow(() -> new RuntimeException("City not found"));

        CityDTO cityDTO = new CityDTO();
        cityDTO.setId(city.getId());
        cityDTO.setName(city.getName());

        return ResponseEntity.ok(cityDTO);
    }
}