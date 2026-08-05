package me.elordenador.clonetube.services;

import lombok.RequiredArgsConstructor;
import me.elordenador.clonetube.dtos.PointsViewDTO;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

@Service
@RequiredArgsConstructor
public class PointsService {

    public PointsViewDTO getPoints() {
        throw new ResponseStatusException(HttpStatus.NOT_IMPLEMENTED);
    }
}
