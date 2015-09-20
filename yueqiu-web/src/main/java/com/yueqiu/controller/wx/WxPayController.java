/**
 * Copyright 2015 yezi.gl. All Rights Reserved.
 */
package com.yueqiu.controller.wx;

import org.apache.commons.lang3.time.DateFormatUtils;
import org.apache.commons.lang3.time.DateUtils;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseBody;

import com.orion.core.utils.HttpUtils;
import com.orion.core.utils.Utils;
import com.thoughtworks.xstream.XStream;
import com.thoughtworks.xstream.io.naming.NoNameCoder;
import com.thoughtworks.xstream.io.xml.XppDriver;
import com.yueqiu.controller.AbstractController;
import com.yueqiu.entity.Order;
import com.yueqiu.entity.PayLog;
import com.yueqiu.model.PayType;
import com.yueqiu.res.PayRes;
import com.yueqiu.res.Representation;
import com.yueqiu.res.Status;
import com.yueqiu.utils.Constants;

/**
 * description here
 *
 * @author yezi
 * @since 2015年9月20日
 */
@Controller
@RequestMapping("/v1/weixin")
public class WxPayController extends AbstractController {

    String ORDER_TIME_FORMAT = "yyyyMMddHHmmss";

    static XStream xstream = new XStream(new XppDriver(new NoNameCoder()));

    static {
        xstream.processAnnotations(new Class<?>[] { UnifiedOrder.class, PrePay.class });
    }

    @RequestMapping(value = "/unifiedorder", method = RequestMethod.POST)
    @ResponseBody
    public Representation unifiedorder(@RequestParam(value = "orderId") String orderId,
            @RequestHeader(value = "X-Forwarded-For", required = false) String forwardIp,
            @RequestHeader(value = "X-Real-IP", required = false) String realIp) {

        Representation rep = new Representation();
        Order order = orderService.get(orderId);
        if (order == null) {
            rep.setError(Status.ERROR_400, "订单不存在");
            return rep;
        }
        orderService.update(order);

        UnifiedOrder unifiedOrder = new UnifiedOrder(Weixin.APP_ID, Weixin.MCH_ID);
        unifiedOrder.setBody(order.getActivity().getTitle());
        unifiedOrder.setOutTradeNo(order.getId().toString());
        unifiedOrder.setFeeType(Weixin.FEE_TYPE);
        unifiedOrder.setTotalFee((int) (order.getAmount() * 100));
        unifiedOrder.setSpbillCreateIp(Utils.getClientIP(forwardIp, realIp));
        unifiedOrder.setTimeStart(DateFormatUtils.format(order.getCreateTime(), ORDER_TIME_FORMAT));
        unifiedOrder.setTimeExpire(DateFormatUtils.format(
                DateUtils.addMilliseconds(order.getUpdateTime(), Constants.ORDER_EXPIRE_TIME), ORDER_TIME_FORMAT));
        unifiedOrder.setNotifyUrl(Weixin.NOTIFY_URL);
        unifiedOrder.setTradeType(Weixin.TRADE_TYPE);

        unifiedOrder.sign();

        // 转为xml，并发送
        String xml = xstream.toXML(unifiedOrder);
        String ret = HttpUtils.post(Weixin.UNIFIED_ORDER_URL, xml);
        // 返回结果转为pojo
        PrePay prePay = (PrePay) xstream.fromXML(ret);
        if (!prePay.checkSign()) {
            rep.setError(Status.ERROR_400, "签名错误");
            return rep;
        }

        PrePayRes res = new PrePayRes();
        res.setAppId(Weixin.APP_ID);
        res.setPartnerId(Weixin.MCH_ID);
        res.setPrepayId(prePay.prepay_id);
        res.setNonceStr(prePay.nonce_str);
        res.setTimestamp(System.currentTimeMillis() / 1000);
        res.setPackageValue(Weixin.PACKAGE);
        res.setSign(prePay.sign(res.getTimestamp()));
        
        rep.setData(res);

        return rep;
    }
    
    @RequestMapping(value = "/pay/callback")
    public String payCallback(@RequestBody String xml) {
        
        PayNotifyRes res = new PayNotifyRes();
        res.return_code = Weixin.RETURN_SUCCESS;
        
        PayNotify payNotify = (PayNotify) xstream.fromXML(xml);
        
        if (!payNotify.checkSign()) {
            res.return_code = Weixin.RETURN_FAIL;
            res.return_msg = "签名错误";
        }
        
        if (Weixin.RETURN_SUCCESS.equals(payNotify.return_code)) {
            PayLog payLog = new PayLog();
            Order order = orderService.get(payNotify.out_trade_no);
            if (order == null) {
                res.return_code = Weixin.RETURN_FAIL;
                res.return_msg = "找不到订单";
            } else {
                payLog.setDetail(xml);
                payLog.setOrder(order);
                payLog.setActivity(order.getActivity());
                payLog.setUser(order.getUser());
                payLog.setPayType(PayType.WEIXIN);
                payLogService.create(payLog);
            }
        } else {
            res.return_code = Weixin.RETURN_FAIL;
            res.return_msg = payNotify.return_msg;
        }
        
        return xstream.toXML(res);
    }
    
    @RequestMapping(value = "/unifiedorder", method = RequestMethod.GET)
    @ResponseBody
    public Representation checkOrder(@RequestParam(value = "orderId") String orderId) {

        Representation rep = new Representation();
        Order order = orderService.get(orderId);
        if (order == null) {
            rep.setError(Status.ERROR_400, "订单不存在");
            return rep;
        }

        PayLog payLog = payLogService.getByOrder(order);
        PayNotify payNotify = (PayNotify) xstream.fromXML(payLog.getDetail());

        PayRes res = new PayRes();
        res.setResult(payNotify.result_code);
        res.setMessage(payNotify.err_code_des);
        
        rep.setData(res);

        return rep;
    }
}