package com.orderflow.orderflowbackend.inventory.controller;

import com.orderflow.orderflowbackend.common.result.Result;
import com.orderflow.orderflowbackend.inventory.dto.ReplenishStockDTO;
import com.orderflow.orderflowbackend.inventory.service.InventoryService;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.*;


@RestController
@RequestMapping("api/inventory")
public class InventoryController {

    private final InventoryService inventoryService;

    public InventoryController (
            InventoryService inventoryService
    ){

        this.inventoryService = inventoryService;
    }

    @PostMapping("{skuId}/replenish")
    public Result<Void> replenish(
            @PathVariable Long skuId,
            @Valid @RequestBody ReplenishStockDTO dto
            ){

        inventoryService.replenish(
                skuId,
                dto.getQuantity()
        );

        return Result.success(null);
    }


}
