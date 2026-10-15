package com.cim.rms.server.signoff;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

/** 签核事件时间线仓储。 */
public interface RmsSignoffEventRepository extends JpaRepository<RmsSignoffEvent, String> {

    /** 某签核单的全部事件，按发生时间正序（时间线用）。 */
    List<RmsSignoffEvent> findBySignoffIdAndDeletedFalseOrderByOccurredAtAsc(String signoffId);
}
