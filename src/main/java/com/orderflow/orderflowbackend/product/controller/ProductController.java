package com.orderflow.orderflowbackend.product.controller;

import com.orderflow.orderflowbackend.common.result.Result;
import com.orderflow.orderflowbackend.product.dto.CreateProductDTO;
import com.orderflow.orderflowbackend.product.dto.CreateSkuDTO;
import com.orderflow.orderflowbackend.product.dto.UpdateProductDTO;
import com.orderflow.orderflowbackend.product.entity.Product;
import com.orderflow.orderflowbackend.product.service.ProductService;
import com.orderflow.orderflowbackend.product.service.SkuCommandService;
import com.orderflow.orderflowbackend.product.vo.ProductDetailVo;
import org.springframework.web.bind.annotation.*;
import jakarta.validation.Valid;

import java.util.List;

@RestController
@RequestMapping("/api/products")
public class ProductController {

    private final ProductService productService;
    private final SkuCommandService skuCommandService;

    public ProductController(
            ProductService productService,
            SkuCommandService skuCommandService
            ){
        this.productService= productService;
        this.skuCommandService = skuCommandService;
    }

    @GetMapping
    public Result<List<Product>> listProducts(){

        return Result.success(
                productService.listProducts()
        );
    }

    @GetMapping("/{id}")
    public Result<ProductDetailVo> getProductDetail(
            @PathVariable long id
    ){
        return Result.success(
                productService.getProductDetail(id)
        );
    }

    @PostMapping
    public Result<Long> createProduct(
            @Valid
            @RequestBody CreateProductDTO dto
            ){
      Long productId = productService.createProduct(dto);

      return Result.success(productId);
    };

    @PutMapping("/{productId}")
    public Result<Void> updateProduct(
            @PathVariable Long productId,
            @Valid @RequestBody UpdateProductDTO dto
            ){

        productService.updateProduct(
                productId,
                dto
        );
        return Result.success(null);
    }

    @PostMapping("/{productId}/skus")
    public Result<Long> createSku(
            @PathVariable Long productId,
            @Valid @RequestBody CreateSkuDTO dto
    ){

        Long skuId = skuCommandService.createSku(
                productId,
                dto
        );

        return Result.success(skuId);
    }
}
