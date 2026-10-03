package com.seatreservation.service;

import com.seatreservation.exception.BadRequestException;
import com.seatreservation.dto.request.CreateShowRequest;
import com.seatreservation.dto.response.CreateShowResponse;
import com.seatreservation.entity.CreateShowEntity;
import com.seatreservation.entity.SeatEntity;
import com.seatreservation.entity.SeatId;
import com.seatreservation.repository.SeatRepository;
import com.seatreservation.repository.ShowRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import com.seatreservation.observability.ReservationMetrics;

@Service
@RequiredArgsConstructor
@Slf4j
public class CreateShowService {

    static final int DEFAULT_PER_USER_LIMIT = 4;

    private final ShowRepository showRepository;
    private final SeatRepository seatRepository;
    private final ReservationMetrics metrics;


    @Transactional
    public CreateShowResponse create(CreateShowRequest req) {
        log.info("service.create_show.started seatCount={}", req.getSeats() == null ? 0 : req.getSeats().size());
        List<String> labels = req.getSeats().stream().map(String::trim).toList();

        Set<String> seen = new HashSet<>();
        for (String label : labels) {
            if (!seen.add(label)) {
                throw new BadRequestException("duplicate seat label: " + label);
            }
        }

        String name = req.getName().trim();
        int limit = req.getPerUserLimit() == null ? DEFAULT_PER_USER_LIMIT : req.getPerUserLimit();
        CreateShowEntity savedShow = showRepository.saveAndFlush(
                CreateShowEntity.builder()
                        .name(name)
                        .pricePaise(req.getPricePaise())
                        .perUserLimit(limit)
                        .totalSeats(labels.size())
                        .build());

        UUID showId = savedShow.getId();

        List<SeatEntity> seats = labels.stream()
                .map(label -> SeatEntity.builder()
                        .id(SeatId.builder().showId(showId).seatLabel(label).build())
                        .status(SeatEntity.AVAILABLE)
                        .build())
                .toList();

        seatRepository.saveAll(seats);
        metrics.registerShow(showId);

        log.info("show.created showId={} name={} seatCount={} pricePaise={} perUserLimit={}",
                showId, name, labels.size(), req.getPricePaise(), limit);

        return CreateShowResponse.created(showId, name, req.getPricePaise(), limit, labels);
    }
}
