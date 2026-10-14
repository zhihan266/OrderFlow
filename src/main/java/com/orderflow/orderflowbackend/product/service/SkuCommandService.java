package com.orderflow.orderflowbackend.product.service;

import com.orderflow.orderflowbackend.exception.BusinessException;
import com.orderflow.orderflowbackend.inventory.entity.Inventory;
import com.orderflow.orderflowbackend.inventory.mapper.InventoryMapper;
import com.orderflow.orderflowbackend.product.dto.CreateSkuDTO;
import com.orderflow.orderflowbackend.product.entity.Product;
import com.orderflow.orderflowbackend.product.entity.Sku;
import com.orderflow.orderflowbackend.product.mapper.ProductMapper;
import com.orderflow.orderflowbackend.product.mapper.SkuMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;


@Service
public class SkuCommandService {

    private final ProductMapper productMapper;
    private final SkuMapper skuMapper;
    private final InventoryMapper inventoryMapper;
    private final ProductCacheService productCacheService;

    public SkuCommandService(
            ProductMapper productMapper,
            SkuMapper skuMapper,
            InventoryMapper inventoryMapper,
            ProductCacheService productCacheService
    ){

        this.productMapper = productMapper;
        this.skuMapper = skuMapper;
        this.inventoryMapper = inventoryMapper;
        this.productCacheService = productCacheService;
    }

    @Transactional
    public Long createSku(
            Long productId,
            CreateSkuDTO dto
    ){

        //1,product 必须存在
        Product product = productMapper.selectById(productId);

        if (product == null){

            throw new BusinessException(
                    40401,
                    "商品不存在"
            );
        }

        //2、创建SKU
        Sku sku = new Sku();

        sku.setProductId(productId);
        sku.setSkuCode(dto.getSkuCode());
        sku.setSpec(dto.getSpec());
        sku.setPrice(dto.getPrice());
        sku.setStatus(dto.getStatus());

        skuMapper.insert(sku);

        //让MySQL来回填skuid
        Long skuId = sku.getId();

        //3、同一事务创建库存
        Inventory inventory = new Inventory();

        inventory.setSkuId(skuId);
        inventory.setAvailableStock(dto.getInitialStock());
        inventory.setReservedStock(0);

        inventoryMapper.insert(inventory);

        productCacheService.deleteAfterCommit(
                productId
        );

        return skuId;

    }
}
