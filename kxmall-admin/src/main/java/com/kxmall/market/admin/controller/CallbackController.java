package com.kxmall.market.admin.controller;

import com.alibaba.fastjson.JSONObject;
import com.baomidou.mybatisplus.mapper.EntityWrapper;
import com.github.binarywang.wxpay.bean.notify.WxPayNotifyResponse;
import com.github.binarywang.wxpay.bean.notify.WxPayOrderNotifyResult;
import com.github.binarywang.wxpay.exception.WxPayException;
import com.github.binarywang.wxpay.service.WxPayService;
import com.kxmall.market.biz.service.notify.AdminNotifyBizService;
import com.kxmall.market.biz.service.order.OrderBizService;
import com.kxmall.market.biz.service.user.UserBizService;
import com.kxmall.market.data.domain.OrderDO;
import com.kxmall.market.data.domain.OrderSkuDO;
import com.kxmall.market.data.dto.order.OrderDTO;
import com.kxmall.market.data.enums.OrderStatusType;
import com.kxmall.market.plugin.core.inter.IPluginPaySuccess;
import com.kxmall.market.plugin.core.manager.PluginsManager;
import com.kxmall.market.data.mapper.OrderMapper;
import com.kxmall.market.data.mapper.OrderSkuMapper;
import com.kxmall.market.data.mapper.SpuMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.CollectionUtils;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Date;
import java.util.List;

/**
 *
 * @author admin
 * @date 2019/7/10
 */
@RestController
@RequestMapping("/cb")
public class CallbackController {

    private static final Logger logger = LoggerFactory.getLogger(CallbackController.class);

    @Autowired
    private OrderBizService orderBizService;

    @Autowired
    private UserBizService userBizService;

    @Autowired
    private SpuMapper spuMapper;

    @Autowired
    private OrderSkuMapper orderSkuMapper;

    @Autowired
    private WxPayService wxPayService;

    @Autowired
    private OrderMapper orderMapper;

    @Autowired
    private PluginsManager pluginsManager;

    @Autowired
    private AdminNotifyBizService adminNotifyBizService;

    @RequestMapping("/wxpay")
    @Transactional(rollbackFor = Exception.class)
    public Object wxpay(@RequestBody String body) throws Exception {
        WxPayOrderNotifyResult result = null;
        try {
            result = wxPayService.parseOrderNotifyResult(body);
        } catch (WxPayException e) {
            logger.error("[????????????????????????] ??????", e);
            return WxPayNotifyResponse.fail(e.getMessage());
        }
        logger.info("???????????????????????????????????????");
        logger.info(JSONObject.toJSONString(result));

        /* ???????????????????????????????????????ID */
        String orderNo = result.getOutTradeNo();
        String payId = result.getTransactionId();

        List<OrderDO> orderDOList = orderMapper.selectList(
                new EntityWrapper<OrderDO>()
                        .eq("order_no", orderNo));

        if (CollectionUtils.isEmpty(orderDOList)) {
            return WxPayNotifyResponse.fail("??????????????? orderNo=" + orderNo);
        }

        OrderDO order = orderDOList.get(0);

        // ???????????????????????????????????????
        if (order.getStatus() != OrderStatusType.UNPAY.getCode()) {
            return WxPayNotifyResponse.success("????????????????????????!");
        }

        Integer totalFee = result.getTotalFee();

        // ????????????????????????
        if (!totalFee.equals(order.getActualPrice())) {
            return WxPayNotifyResponse.fail(order.getOrderNo() + " : ????????????????????? totalFee=" + totalFee);
        }

        //**************** ????????????????????? ??????????????? ?????? ???????????????????????????????????? **********************//

        OrderDO updateOrderDO = new OrderDO();
        updateOrderDO.setPayId(payId);
        updateOrderDO.setPayChannel("WX");
        updateOrderDO.setPayPrice(totalFee);
        updateOrderDO.setGmtPay(new Date());
        updateOrderDO.setGmtUpdate(order.getGmtPay());
        if (order.getGroupShopId() != null) {
            updateOrderDO.setStatus(OrderStatusType.GROUP_SHOP_WAIT.getCode());
        } else {
            updateOrderDO.setStatus(OrderStatusType.WAIT_PREPARE_GOODS.getCode());
        }
        orderBizService.changeOrderStatus(orderNo, OrderStatusType.UNPAY.getCode(), updateOrderDO);
        List<OrderSkuDO> orderSkuDOList = orderSkuMapper.selectList(
                new EntityWrapper<OrderSkuDO>()
                        .eq("order_no", orderNo));
        orderSkuDOList.forEach(item -> {
            //????????????
            spuMapper.incSales(item.getSpuId(), item.getNum());
        });

        OrderDTO orderDTO = new OrderDTO();
        BeanUtils.copyProperties(order, orderDTO);
        orderDTO.setPayChannel(updateOrderDO.getPayChannel());
        orderDTO.setSkuList(orderSkuDOList);

        List<IPluginPaySuccess> plugins = pluginsManager.getPlugins(IPluginPaySuccess.class);
        if (!CollectionUtils.isEmpty(plugins)) {
            String formId = userBizService.getValidFormIdByUserId(orderDTO.getUserId()).getFormId();
            for (IPluginPaySuccess paySuccess : plugins) {
                orderDTO = paySuccess.invoke(orderDTO, formId);
            }
        }
        //?????????????????????
//        OrderDTO finalOrderDTO = orderDTO;
//        GlobalExecutor.execute(() -> {
//            adminNotifyBizService.newOrder(finalOrderDTO);
//        });


        return WxPayNotifyResponse.success("????????????");
    }

}
