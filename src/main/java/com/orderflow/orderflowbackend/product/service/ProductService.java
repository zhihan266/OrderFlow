package com.orderflow.orderflowbackend.product.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.orderflow.orderflowbackend.common.result.Result;
import com.orderflow.orderflowbackend.exception.BusinessException;
import com.orderflow.orderflowbackend.product.cache.ProductDetailCacheData;
import com.orderflow.orderflowbackend.product.cache.SkuCacheData;
import com.orderflow.orderflowbackend.product.entity.Product;
import com.orderflow.orderflowbackend.product.entity.Sku;
import com.orderflow.orderflowbackend.inventory.entity.Inventory;
import com.orderflow.orderflowbackend.inventory.mapper.InventoryMapper;
import com.orderflow.orderflowbackend.product.mapper.ProductMapper;
import com.orderflow.orderflowbackend.product.mapper.SkuMapper;
import com.orderflow.orderflowbackend.product.vo.ProductDetailVo;
import com.orderflow.orderflowbackend.product.vo.SkuDetailVo;
import org.springframework.stereotype.Service;
import com.orderflow.orderflowbackend.product.dto.CreateProductDTO;
import org.springframework.transaction.annotation.Transactional;
import com.orderflow.orderflowbackend.product.dto.UpdateProductDTO;

import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
public class ProductService {

    private final ProductMapper productMapper;
    private final SkuMapper skuMapper;
    private final InventoryMapper inventoryMapper;
    private final ProductCacheService productCacheService;

    public ProductService(
            ProductMapper productMapper,
            SkuMapper skuMapper,
            InventoryMapper inventoryMapper,
            ProductCacheService productCacheService
    ){
        this.productMapper =productMapper;
        this.skuMapper =skuMapper;
        this.inventoryMapper =inventoryMapper;
        this.productCacheService =productCacheService;
    }

    public List<Product> listProducts(){
        return productMapper.selectList(null);
    }

    public ProductDetailVo getProductDetail(Long productId){

        ProductDetailCacheData baseData =getProductBaseData(productId);

        //查询库存并且组装最终的VO
        return buildProductDetailWithInventory(
                baseData
        );

    }


    //查询数据库的商品信息，并且写入redis
    private ProductDetailCacheData loadProductBaseFromDatabase(Long productId){
        System.out.println(
                "CACHE_DB_REBUILD productId=" + productId
        );
        //查询商品
        Product product =productMapper.selectById(productId);

        if (product == null){

            //给redis加上缓存空值,防止缓存穿透
            productCacheService.setNull(productId);

            throw new BusinessException(
                    40401,
                    "商品不存在");
        }

        //查询这个商品下的所有sku
        List<Sku> skuList =skuMapper.selectList(new LambdaQueryWrapper<Sku>()
                .eq(
                        Sku::getProductId,
                        productId
                )
        );


        //将数据库数据转化成缓存数据 1、product 转化成CacheData
        ProductDetailCacheData result =new ProductDetailCacheData();

        result.setId(product.getId());
        result.setName(product.getName());
        result.setDescription(product.getDescription());
        result.setStatus(product.getStatus());

        //2、将SKU 转化成 SkuCacheData

        List<SkuCacheData> skuCacheDataList =new ArrayList<>();

        for(Sku sku : skuList ){
            SkuCacheData cacheData = new SkuCacheData();

            cacheData.setSkuId(sku.getId());
            cacheData.setSkuCode(sku.getSkuCode());
            cacheData.setSpec(sku.getSpec());
            cacheData.setPrice(sku.getPrice());

            skuCacheDataList.add(cacheData);

        }
        result.setSkus(skuCacheDataList);

        return result;

    }


    //查询库存，并且返回最终的VO
    private ProductDetailVo buildProductDetailWithInventory(
            ProductDetailCacheData baseDate
            ){

        //获取所有的skuId
        List<Long> skuIds = baseDate.getSkus()
                .stream().map(
                        SkuCacheData::getSkuId
                ).toList();


        //批量查询库存
        Map<Long,Inventory> inventoryMap =new HashMap<>();

        if(!skuIds.isEmpty()){
            List<Inventory> inventoryList =inventoryMapper.selectList(
                    new LambdaQueryWrapper<Inventory>()
                            .in(
                                    Inventory::getSkuId,
                                    skuIds
                            )
                    );

            inventoryMap =inventoryList.stream()
                    .collect(
                            Collectors.toMap(
                                    Inventory::getSkuId,
                                    Function.identity()
                            )
                    );
        }
        // 转化成 SkuDetailVo
        List<SkuDetailVo> skuVoList =new ArrayList<>();

        for (SkuCacheData sku :baseDate.getSkus()){
            SkuDetailVo skuVo =new SkuDetailVo();

            skuVo.setSkuId(sku.getSkuId());
            skuVo.setSkuCode(sku.getSkuCode());
            skuVo.setSpec(sku.getSpec());
            skuVo.setPrice(sku.getPrice());

            Inventory inventory =inventoryMap.get(sku.getSkuId());

            if(inventory != null){

                skuVo.setAvailableStock(
                        inventory.getAvailableStock()
                );

                skuVo.setReservedStock(
                        inventory.getReservedStock()
                );
            }

            skuVoList.add(skuVo);
        }

        // 转化成 ProductDetailVo
        ProductDetailVo result =new ProductDetailVo();

        result.setId(baseDate.getId());
        result.setName(baseDate.getName());
        result.setDescription(baseDate.getDescription());
        result.setStatus(baseDate.getStatus());
        result.setSkus(skuVoList);

        return result;
    }

    //获取缓存方法
    private ProductDetailCacheData getProductBaseData(Long productId){

        //1、第一次查redis
        ProductDetailCacheData cache = productCacheService.get(productId);

        //命中缓存直接返回
        if (cache != null){
            return cache;
        }

        //2、没有命中redis，准备抢锁
        String lockValue = UUID.randomUUID().toString();

        boolean locked = productCacheService.tryLock(
                productId,
                lockValue
        );

        //3、如果抢到redis锁
        if(locked){

            //二次检查有没有命中缓存
            try{
                cache = productCacheService.get(productId);

                if (cache !=null){
                    return cache;
                }

                //二次检查通过，开始访问数据库，并且重建redis缓存
                ProductDetailCacheData baseData =
                        loadProductBaseFromDatabase(productId);

                productCacheService.set(
                        productId,
                        baseData
                );
                return baseData;
            }finally {

                //创建错误时及时释放锁，防止锁被一直占用
                productCacheService.unlock(
                        productId,
                        lockValue
                );

            }

        }

        //4、没有抢到锁，等待
        return waitForCache(productId);
    }

    private ProductDetailCacheData waitForCache(Long productId){

        //最多等待1秒
        for(int i = 0;i<20;i++){

            try {
                Thread.sleep(50);
            }catch (InterruptedException e){

                Thread.currentThread().interrupt();

                throw new RuntimeException(
                        "等待商品缓存时进程被中断",
                        e
                );
            }
            //重新开始获取缓存
            ProductDetailCacheData cached = productCacheService.get(productId);

            if(cached != null){
                return cached;
            }
        }

        throw new BusinessException(
                50010,
                "商品查询繁忙，请稍后再试"
        );
    }


    public Long createProduct(
            CreateProductDTO dto
    ){
        Product product =new Product();

        product.setName(dto.getName());
        product.setDescription(dto.getDescription());

        //新的商品默认上架
        product.setStatus(1);

        productMapper.insert(product);

        //防止ID之前存在null的redis
        productCacheService.delete(product.getId());

        return product.getId();
    }

    @Transactional
    public void updateProduct(
            Long productId,
            UpdateProductDTO dto
    ){

        //1、确认商品的存在
        Product product = productMapper.selectById(productId);

        if (product ==null){
            throw new BusinessException(
                    40401,
                    "商品不存在"
            );
        }

        //2、修改数据库
        product.setName(dto.getName());
        product.setDescription(dto.getDescription());
        product.setStatus(dto.getStatus());

        productMapper.updateById(product);

        //3、删除缓存
        productCacheService.deleteAfterCommit(productId);
    }
}
