/**
 * Copyright 2015 yezi.gl. All Rights Reserved.
 */
package com.yueqiu.core.dao;

import org.mongodb.morphia.Datastore;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Repository;

import com.yueqiu.core.entity.Coupon;

/**
 * description here
 *
 * @author yezi
 * @since 2015年6月14日
 */
@Repository
public class CouponDao extends BaseDao<Coupon> {

    @Autowired
    public CouponDao(Datastore datastore) {
        super(datastore);
    }

}
