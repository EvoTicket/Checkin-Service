package com.capstone.checkinservice.repository;

import com.capstone.checkinservice.entity.CheckerAssignment;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface CheckerAssignmentRepository extends JpaRepository<CheckerAssignment, Long> {
    List<CheckerAssignment> findByCheckerIdAndActiveTrue(Long checkerId);

    List<CheckerAssignment> findByCheckerIdAndEventIdAndShowtimeIdAndActiveTrue(
            Long checkerId,
            Long eventId,
            Long showtimeId
    );
}
