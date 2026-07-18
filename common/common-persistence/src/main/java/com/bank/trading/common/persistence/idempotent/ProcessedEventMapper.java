package com.bank.trading.common.persistence.idempotent;

import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.time.LocalDateTime;

@Mapper
public interface ProcessedEventMapper {

    @Insert("INSERT OR IGNORE INTO processed_events (event_id, processed_at) VALUES (#{eventId}, #{processedAt})")
    int insert(@Param("eventId") String eventId, @Param("processedAt") LocalDateTime processedAt);

    @Select("SELECT COUNT(*) FROM processed_events WHERE event_id = #{eventId}")
    int exists(@Param("eventId") String eventId);
}
