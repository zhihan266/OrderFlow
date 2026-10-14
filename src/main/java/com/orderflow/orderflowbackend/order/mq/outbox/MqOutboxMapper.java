package com.orderflow.orderflowbackend.order.mq.outbox;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Update;

import java.time.LocalDateTime;


@Mapper
public interface MqOutboxMapper
        extends BaseMapper<MqOutbox> {

    @Update("""
    UPDATE mq_outbox
    SET status = 'SENT',
        sent_at = NOW(),
        last_error = NULL
    WHERE id = #{id}
       AND status = 'PENDING'
    """)
    int markSent(@Param("id")  Long id);


    @Update("""
    UPDATE mq_outbox
    SET retry_count = retry_count + 1,
        next_retry_at = #{nextRetryAt},
        last_error = #{lastError}
    WHERE id = #{id}
      AND status = 'PENDING'
    """)
    int markRetry(
            @Param("id") Long id,
            @Param("nextRetryAt")
            LocalDateTime nextRetryAt,
            @Param("lastError")
            String lastError
    );


    @Update("""
            UPDATE mq_outbox
            SET status = 'FAILED',
                retry_count = retry_count + 1,
                next_retry_at = NULL,
                last_error = #{lastError}
            WHERE id = #{id}
              AND status = 'PENDING'
            """)
    int markFailed(
            @Param("id") Long id,
            @Param("lastError")
            String lastError
    );

    @Update("""
            UPDATE mq_outbox
            SET status = 'PENDING',
                retry_count = 0,
                next_retry_at = NULL,
                last_error = NULL
            WHERE id = #{id}
               AND status = 'FAILED'
            """)
    int resetFailedForReplay(
            @Param("id") Long id
    );

}
