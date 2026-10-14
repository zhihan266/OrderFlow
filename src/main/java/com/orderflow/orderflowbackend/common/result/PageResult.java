package com.orderflow.orderflowbackend.common.result;


import lombok.AllArgsConstructor;
import lombok.Data;

import java.util.List;

@Data
@AllArgsConstructor
public class PageResult<T> {

    private List<T> records;

    private long total;

    private long page;

    private long pageSize;

    private long pages;

}
