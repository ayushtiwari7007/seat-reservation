package com.seatreservation.repository;

import com.seatreservation.entity.CreateShowEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface ShowRepository extends JpaRepository<CreateShowEntity, UUID> {
}
