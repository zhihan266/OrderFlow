package com.orderflow.orderflowbackend.order.mq.outbox;


import com.orderflow.orderflowbackend.common.result.Result;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/admin/oubox")
public class OutboxAdminController {

    private final MqOutboxRecoveryService
            recoveryService;

    public OutboxAdminController(
            MqOutboxRecoveryService recoveryService
    ) {
        this.recoveryService =
                recoveryService;
    }

    @PostMapping("/{id}/replay")
    public Result<Void> replay(
            @PathVariable Long id
    ){

        recoveryService.replayFailedMessage(id);

        return Result.success(null);
    }

    @GetMapping("/failed")
    public Result<List<MqOutbox>> listFailedMessages(){

        return Result.success(
                recoveryService.listFailedMessages()
        );
    }
}
