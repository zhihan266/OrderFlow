package com.orderflow.orderflowbackend.order.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.orderflow.orderflowbackend.exception.BusinessException;
import com.orderflow.orderflowbackend.inventory.entity.Inventory;
import com.orderflow.orderflowbackend.inventory.mapper.InventoryMapper;
import com.orderflow.orderflowbackend.order.dto.CreateOrderDTO;
import com.orderflow.orderflowbackend.order.dto.CreateOrderItemDTO;
import com.orderflow.orderflowbackend.order.entity.Order;
import com.orderflow.orderflowbackend.order.entity.OrderItem;
import com.orderflow.orderflowbackend.order.entity.idempotency.OrderRequest;
import com.orderflow.orderflowbackend.order.entity.idempotency.OrderRequestMapper;
import com.orderflow.orderflowbackend.order.enums.OrderStatus;
import com.orderflow.orderflowbackend.order.mapper.OrderMapper;
import com.orderflow.orderflowbackend.order.mapper.OrderItemMapper;
import com.orderflow.orderflowbackend.order.mq.outbox.MqOutbox;
import com.orderflow.orderflowbackend.order.mq.outbox.MqOutboxMapper;
import com.orderflow.orderflowbackend.order.vo.CreateOrderVO;
import com.orderflow.orderflowbackend.product.entity.Sku;
import com.orderflow.orderflowbackend.product.mapper.SkuMapper;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import com.orderflow.orderflowbackend.order.mq.OrderTimeoutProducer;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.Comparator;
import java.util.List;

@Service
public class OrderCommandService {

    private final OrderMapper orderMapper;
    private final OrderItemMapper orderItemMapper;
    private final SkuMapper skuMapper;
    private final InventoryMapper inventoryMapper;
    private final OrderTimeoutProducer orderTimeoutProducer;
    private final MqOutboxMapper mqOutboxMapper;
    private final OrderRequestMapper orderRequestMapper;

    @Value("${order.timeout-seconds:1800}")
    private long orderTimeoutSeconds;

    public OrderCommandService(
            OrderMapper orderMapper,
            OrderItemMapper orderItemMapper,
            SkuMapper skuMapper,
            InventoryMapper inventoryMapper,
            OrderTimeoutProducer orderTimeoutProducer,
            MqOutboxMapper mqOutboxMapper,
            OrderRequestMapper orderRequestMapper

    ){
        this.orderMapper = orderMapper;
        this.orderItemMapper = orderItemMapper;
        this.skuMapper = skuMapper;
        this.inventoryMapper = inventoryMapper;
        this.orderTimeoutProducer = orderTimeoutProducer;
        this.mqOutboxMapper = mqOutboxMapper;
        this.orderRequestMapper = orderRequestMapper;
    }

    @Transactional
    public CreateOrderVO createOrder(
            CreateOrderDTO dto,
            String idempotencyKey,
            String requestHash
    ){
        //提前进入order_request表中检查是不是之前处理过的订单
        int inserted = orderRequestMapper.tryCreate(
                dto.getUserId(),
                idempotencyKey,
                requestHash
        );
        //如果有同样的幂等key
        if(inserted == 0){


            //先拿到原来存在的订单信息
            OrderRequest existingRequest =
                    orderRequestMapper.selectOne(
                            new LambdaQueryWrapper<OrderRequest>()
                                    .eq(
                                            OrderRequest::getUserId,
                                            dto.getUserId()
                                    )
                                    .eq(
                                            OrderRequest::getIdempotencyKey,
                                            idempotencyKey
                                    )
                    );

            if (existingRequest == null){

                throw new BusinessException(
                        50030,
                        "订单幂等记录异常"
                );
            }

            //如果是同一个key，但是内容不同
            if(!existingRequest.getRequestHash().equals(requestHash)){

                throw new BusinessException(
                        40901,
                        "Idempotency-key 已经被其他的请求使用"
                );
            }

            //如果完全就是相同的请求，不重新创建，直接返回原来的订单结果

            //若发现订单完成
            if("SUCCESS".equals(existingRequest.getStatus())){

                return getExistingOrderResult(existingRequest.getOrderId());
            }

            //若发现订单正在处理，返回不要重复执行就行
            if ("PROCESSING".equals(existingRequest.getStatus())) {

                throw new BusinessException(
                        40902,
                        "订单请求正在处理中"
                );
            }


            throw new BusinessException(
                    50033,
                    "未知幂等状态"
            );
        }



        //1、防止同一个SKU在请求中重复出现
        Set<Long> skuIdSet = new HashSet<>();

        for(CreateOrderItemDTO item : dto.getItems()){

            if(!skuIdSet.add(item.getSkuId())){
                throw new BusinessException(
                        40010,
                        "订单中存在重复的SKU"
                );
            }
        }

        List<Long> skuIds =new ArrayList<>(skuIdSet);

        //2、一次查询所有SKU
        List<Sku> skuList = skuMapper.selectList(new LambdaQueryWrapper<Sku>()
                .in(Sku::getId,skuIds));

        if (skuList.size()!=skuIdSet.size()){
            throw new BusinessException(
                    400411,
                    "部分的商品不存在"
            );
        }

        for (Sku sku : skuList) {

            if (sku.getStatus() == null
                    || sku.getStatus() != 1) {

                throw new BusinessException(
                        40014,
                        "商品SKU当前不可购买"
                );
            }
        }
        Map<Long, Sku> skuMap =
                skuList.stream()
                        .collect(
                                Collectors.toMap(
                                        Sku::getId,
                                        Function.identity()
                                )
                        );

        //3、批量查询库存

        List<Inventory> inventoryList =inventoryMapper
                .selectList(new LambdaQueryWrapper<Inventory>()
                        .in(
                                Inventory::getSkuId,
                                skuIds
                        ));

        Map<Long,Inventory> inventoryMap =inventoryList.stream()
                .collect(
                        Collectors.toMap(
                                Inventory::getSkuId,
                                Function.identity()
                        )
                );

        //4、检查库存+计算总金额

        BigDecimal totalAmount =BigDecimal.ZERO;

        for (CreateOrderItemDTO item : dto.getItems()){

            Sku sku =skuMap.get(item.getSkuId());

            Inventory inventory = inventoryMap.get(item.getSkuId());

            if(inventory == null){

                throw new BusinessException(
                        40012,
                        "商品记录不存在"
                );
            }

            if (inventory.getAvailableStock() < item.getQuantity()){

                throw new BusinessException(
                        40013,
                        "商品库存不足"
                );
            }

            BigDecimal lineAmount = sku.getPrice()
                    .multiply(BigDecimal.valueOf(item.getQuantity()));

            totalAmount = totalAmount.add(lineAmount);
        }

        //5、创建订单主表

        Order order =new Order();

        order.setOrderNo(generateOrderNo());
        order.setUserId(dto.getUserId());
        order.setTotalAmount(totalAmount);

        order.setStatus(OrderStatus.PENDING_PAYMENT.name());

        order.setExpireAt(
                LocalDateTime.now()
                        .plusSeconds(orderTimeoutSeconds)
        );

        orderMapper.insert(order);

        //6、预占内存+创建订单明细,先进行排序防止发生死锁

        List<CreateOrderItemDTO> sortedItems =
                dto.getItems()
                        .stream()
                        .sorted(
                                Comparator.comparing(
                                        CreateOrderItemDTO::getSkuId
                                )
                        )
                        .toList();

        for(CreateOrderItemDTO item : sortedItems){

            Sku  sku=skuMap.get(item.getSkuId());

            int affectedRows = inventoryMapper.reserveStock(
                    item.getSkuId(),
                    item.getQuantity()
            );

            if(affectedRows == 0){

                throw new BusinessException(
                        40013,
                        "商品库存不足"
                );
            }

            OrderItem orderItem = new OrderItem();

            orderItem.setOrderId(order.getId());
            orderItem.setSkuId(sku.getId());
            orderItem.setSkuCode(sku.getSkuCode());
            orderItem.setSkuSpec(sku.getSpec());
            orderItem.setUnitPrice(sku.getPrice());
            orderItem.setQuantity(item.getQuantity());
            orderItem.setLineAmount(sku.getPrice().multiply(BigDecimal.valueOf(item.getQuantity())));

            orderItemMapper.insert(orderItem);

        }

        //7、插入MqOutbox表

        MqOutbox outbox = new MqOutbox();
        outbox.setMessageId(
                UUID.randomUUID().toString()
        );
        outbox.setOrderId(order.getId());
        outbox.setEventType("ORDER_TIMEOUT");
        outbox.setStatus("PENDING");
        outbox.setRetryCount(0);
        mqOutboxMapper.insert(outbox);

        //8、下单请求已经处理成功，更新order_request表中status为success
        int rows = orderRequestMapper.markSuccess(
                dto.getUserId(),
                idempotencyKey,
                order.getId()
        );

        if (rows != 1) {

            throw new BusinessException(
                    50034,
                    "更新订单幂等状态失败"
            );
        }


        //9、返回VO

        CreateOrderVO result = buildCreateOrderVO(order);
        return result;

    }


    //产生订单号

    private String generateOrderNo(){

        return "ORD" +UUID.randomUUID().toString().replace("-","").toUpperCase();
    }


    @Transactional
    public void cancelOrderId(Long orderId){

        Order order = orderMapper.selectById(orderId);

        if(order == null){
            throw new BusinessException(
                    40410,
                    "订单不存在"
            );
        }

        int affectedRows = orderMapper.cancelPendingOrder(orderId);

        if (affectedRows == 0){
            throw new BusinessException(
                    40020,
                    "当前订单状态不允许取消"
            );
        }

        List<OrderItem> items =orderItemMapper.selectList(
                new LambdaQueryWrapper<OrderItem>()
                        .eq(
                                OrderItem::getOrderId,
                                orderId
                        )
        );

        List<OrderItem> sortedOrderItems =
                items.stream()
                        .sorted(
                                Comparator.comparing(
                                        OrderItem::getSkuId
                                )
                        )
                        .toList();

        for (OrderItem item :sortedOrderItems){

            int released = inventoryMapper.releaseStock(
                    item.getSkuId(),
                    item.getQuantity()
            );

            if(released == 0){

                throw new BusinessException(
                        50010,
                        "释放库存失败"
                );
            }
        }
    }

    @Transactional
    public void payOrder(Long orderId){

        //1、查询订单是否存在
        Order order = orderMapper.selectById(orderId);

        if (order == null){

            throw new BusinessException(
                    40410,
                    "订单不存在"
            );
        }

        //2、竞争订单状态
        int affectedRows = orderMapper.markPaidIfPending(orderId);

        if(affectedRows == 0){
            throw new BusinessException(
                    40021,
                    "当前订单状态不允许支付"
            );
        }

        //3、查询订单明细
        List<OrderItem> items= orderItemMapper.selectList(
                new LambdaQueryWrapper<OrderItem>()
                        .eq(
                                OrderItem::getOrderId,
                                orderId
                        )
        );

        //4、确认消费预占内存
        List<OrderItem> sortedItems =
                sortOrderItemsBySkuId(items);


        for(OrderItem item : sortedItems){

            int confirmed =inventoryMapper.confirmStock(
                    item.getSkuId(),
                    item.getQuantity()
            );

            if (confirmed == 0){
                throw new BusinessException(
                        50011,
                        "确认库存失败"
                );
            }
        }
    }



    @Transactional
    public boolean cancelOrderIfPending(
            Long orderId
    ){

        Order order = orderMapper.selectById(orderId);

        if (order == null){
            return false;
        }

        int affected = orderMapper.cancelPendingOrder(orderId);

        if (affected == 0){

            return false;
        }

        List<OrderItem> items = orderItemMapper.selectList(
                new LambdaQueryWrapper<OrderItem>()
                        .eq(
                                OrderItem::getOrderId,
                                orderId
                        )
        );

        List<OrderItem> sortedItems =
                sortOrderItemsBySkuId(items);

        for(OrderItem item :sortedItems){

            int rows = inventoryMapper.releaseStock(
                    item.getSkuId(),
                    item.getQuantity()
            );

            if (rows == 0){

                throw new BusinessException(
                        50020,
                        "释放库存失败"
                );
        }


        }

        return true;
    }

    @Transactional
    public boolean cancelOrderIfExpired(
            Long orderId
    ){

        Order order = orderMapper.selectById(orderId);

        if (order == null){
            return false;
        }

        int affected =
                orderMapper.cancelExpiredOrder(
                        orderId
                );

        if (affected == 0) {

            /*
             * 可能：
             * 1. 已支付
             * 2. 已取消
             * 3. 还没真正过期
             */
            return false;
        }

        List<OrderItem> items =
                orderItemMapper.selectList(
                        new LambdaQueryWrapper<OrderItem>()
                                .eq(
                                        OrderItem::getOrderId,
                                        orderId
                                )
                );
        List<OrderItem> sortedItems =
                sortOrderItemsBySkuId(items);

        for (OrderItem item : sortedItems) {

            int rows =
                    inventoryMapper.releaseStock(
                            item.getSkuId(),
                            item.getQuantity()
                    );

            if (rows == 0) {
                throw new BusinessException(
                        50020,
                        "释放库存失败"
                );
            }
        }

        return true;
    }



    private CreateOrderVO buildCreateOrderVO(
            Order order
    ) {

        CreateOrderVO result =
                new CreateOrderVO();

        result.setOrderId(
                order.getId()
        );

        result.setOrderNo(
                order.getOrderNo()
        );

        result.setTotalAmount(
                order.getTotalAmount()
        );

        result.setStatus(
                order.getStatus()
        );

        return result;
    }

    public CreateOrderVO getExistingOrderResult(
            Long orderId
    ) {

        Order existingOrder =
                orderMapper.selectById(orderId);

        if (existingOrder == null) {
            throw new BusinessException(
                    50032,
                    "幂等记录对应订单不存在"
            );
        }

        return buildCreateOrderVO(
                existingOrder
        );
    }

    public CreateOrderVO findCommittedIdempotentResult(
            Long userId,
            String idempotencyKey,
            String requestHash
    ) {

        OrderRequest request =
                orderRequestMapper.selectOne(
                        new LambdaQueryWrapper<OrderRequest>()
                                .eq(
                                        OrderRequest::getUserId,
                                        userId
                                )
                                .eq(
                                        OrderRequest::getIdempotencyKey,
                                        idempotencyKey
                                )
                );


        /*
         * 数据库还没有任何已提交记录。
         *
         * 有可能原请求仍然在事务中，
         * 所以交给上层返回 PROCESSING。
         */
        if (request == null) {
            return null;
        }


        /*
         * 同一个key却对应不同业务请求。
         */
        if (!requestHash.equals(
                request.getRequestHash()
        )) {

            throw new BusinessException(
                    40901,
                    "幂等键已被不同请求使用"
            );
        }


        /*
         * 只有数据库明确SUCCESS，
         * 才能认为订单已经成功。
         */
        if (!"SUCCESS".equals(
                request.getStatus()
        )) {

            return null;
        }


        if (request.getOrderId() == null) {

            throw new BusinessException(
                    50036,
                    "幂等记录状态异常"
            );
        }


        return getExistingOrderResult(
                request.getOrderId()
        );
    }

    private List<OrderItem> sortOrderItemsBySkuId(
            List<OrderItem> items
    ) {

        return items.stream()
                .sorted(
                        Comparator.comparing(
                                OrderItem::getSkuId
                        )
                )
                .toList();
    }

}
