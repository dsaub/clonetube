package me.elordenador.clonetube.controller;

import lombok.RequiredArgsConstructor;
import me.elordenador.clonetube.decorators.RequireAuth;
import me.elordenador.clonetube.dtos.PointsViewDTO;
import me.elordenador.clonetube.services.PointsService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/points")
@RequiredArgsConstructor
public class PointsController {

    private final PointsService pointsService;

    @GetMapping
    @RequireAuth
    public PointsViewDTO getPoints() {
        return pointsService.getPoints();
    }
}
