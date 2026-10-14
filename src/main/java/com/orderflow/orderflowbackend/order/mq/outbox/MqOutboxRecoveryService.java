package com.orderflow.orderflowbackend.order.mq.outbox;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.orderflow.orderflowbackend.exception.BusinessException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.ObjectMapper;

import java.util.List;

@Service
public class MqOutboxRecoveryService {
    private final MqOutboxMapper outboxMapper;

    public MqOutboxRecoveryService(
            MqOutboxMapper outboxMapper
    ) {
        this.outboxMapper = outboxMapper;
    }

    @Transactional
    public void replayFailedMessage(
            Long outboxId
    ){

        MqOutbox outbox = outboxMapper.selectById(outboxId);

        if (outbox == null) {

            throw new BusinessException(
                    40430,
                    "Outbox消息不存在"
            );
        }

        //只有failed才可以重试
        if (!"FAILED".equals(
                outbox.getStatus()
        )) {

            throw new BusinessException(
                    40070,
                    "只有FAILED消息可以重新投递"
            );
        }

        int affected =
                outboxMapper
                        .resetFailedForReplay(
                                outboxId
                        );


        if (affected == 0) {

            /*
             * 防止：
             *
             * 查的时候还是FAILED
             * 但并发情况下已经被其他操作修改
             */
            throw new BusinessException(
                    40910,
                    "Outbox状态已发生变化"
            );
        }

    }
    public List<MqOutbox> listFailedMessages(){
        return outboxMapper.selectList(
                new LambdaQueryWrapper<MqOutbox>()
                        .eq(
                                MqOutbox::getStatus,
                                "FAILED"
                        )
                        .orderByDesc(
                                MqOutbox::getUpdatedAt
                        )
                        .last("LIMIT 100")
        );
    }
}
