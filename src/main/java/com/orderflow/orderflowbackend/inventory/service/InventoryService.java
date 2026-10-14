package com.orderflow.orderflowbackend.inventory.service;


import com.orderflow.orderflowbackend.exception.BusinessException;
import com.orderflow.orderflowbackend.inventory.mapper.InventoryMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class InventoryService {

    private final InventoryMapper inventoryMapper;

    public InventoryService(
            InventoryMapper inventoryMapper
    ){

        this.inventoryMapper = inventoryMapper;
    }

    @Transactional
    public void replenish(
            Long skuId,
            Integer quantity
    ){
        int affect = inventoryMapper.replenishStock(
                skuId,
                quantity
        );

        if(affect == 0){

            throw new BusinessException(
                    40420,
                    "库存记录不存在"
            );
        }


    }
}
