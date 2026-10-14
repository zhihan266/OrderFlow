package com.orderflow.orderflowbackend.inventory.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.orderflow.orderflowbackend.inventory.entity.Inventory;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Update;

@Mapper
public interface InventoryMapper
        extends BaseMapper<Inventory>{

    @Update("""
            UPDATE inventory
            SET available_stock = available_stock - #{quantity},
                reserved_stock = reserved_stock + #{quantity}
            WHERE sku_id = #{skuId}
                AND available_stock >= #{quantity}
            """)
    int reserveStock(
            @Param("skuId") Long skuId,
            @Param("quantity") Integer quantity
    );

    @Update("""
            UPDATE inventory
            SET available_stock = available_stock + #{quantity},
                reserved_stock = reserved_stock - #{quantity}
            WHERE sku_id = #{skuId}
               AND reserved_stock >= #{quantity}
            """)
    int releaseStock(
            @Param("skuId") Long skuId,
            @Param("quantity") Integer quantity
    );

    @Update("""
             UPDATE inventory
             SET reserved_stock = reserved_stock - #{quantity}
             WHERE sku_id = #{skuId}
               AND reserved_stock >= #{quantity}
             """)
    int confirmStock(
            @Param("skuId") Long skuId,
            @Param("quantity") Integer quantity
    );

    @Update(""" 
            UPDATE inventory
            SET available_stock = 
                available_stock + #{quantity}
            WHERE sku_id = #{skuId}
            """)
    int replenishStock(
            @Param("skuId") Long skuId,
            @Param("quantity") Integer quantity
    );

}
