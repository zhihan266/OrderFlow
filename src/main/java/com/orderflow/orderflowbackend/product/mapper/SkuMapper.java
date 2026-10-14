package com.orderflow.orderflowbackend.product.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.orderflow.orderflowbackend.product.entity.Sku;
import org.apache.ibatis.annotations.Mapper;

@Mapper

public interface SkuMapper
          extends BaseMapper<Sku>{
}
