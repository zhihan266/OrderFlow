package com.orderflow.orderflowbackend.product.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.orderflow.orderflowbackend.product.entity.Product;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface ProductMapper
        extends BaseMapper<Product>{

}
