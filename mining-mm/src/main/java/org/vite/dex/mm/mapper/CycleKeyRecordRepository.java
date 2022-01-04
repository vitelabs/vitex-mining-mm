package org.vite.dex.mm.mapper;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import org.vite.dex.mm.entity.CycleKeyRecord;

import java.util.List;

@Repository
public interface CycleKeyRecordRepository extends JpaRepository<CycleKeyRecord, Long> {
    List<CycleKeyRecord> findByCycleKey(int cycleKey);
}
