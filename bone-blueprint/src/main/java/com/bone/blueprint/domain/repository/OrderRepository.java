
package com.bone.blueprint.domain.repository;

import com.bone.blueprint.domain.model.order.Order;
import com.bone.blueprint.domain.model.order.vo.OrderId;
import com.bone.metadata.sdk.Repository;

public interface OrderRepository extends Repository&lt;Order, OrderId&gt; {
    // 只使用基类提供的方法，不添加额外查询方法
}

