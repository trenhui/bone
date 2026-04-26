package com.bone.tpa.claim.engine;

import com.bone.core.result.PageResult;
import com.bone.core.result.QueryParam;
import com.bone.core.result.SortingField;
import com.bone.tpa.claim.application.enums.BizModelEnum;
import com.bone.tpa.claim.application.request.DeleteRequest;
import com.bone.tpa.claim.application.request.QueryOneRequest;
import com.bone.tpa.claim.application.response.GenericQueryResponse;

import java.util.List;

public interface GenericQueryStrategy {

    /**
     * 该策略所对应的业务模型
     */
    BizModelEnum getBizModel();


    /**
     * 获取该业务模型的所有1:1的子模型
     */
    List<BizModelEnum> subBizModel();


    /**
     * 获取该业务模型的所有1:n的子模型
     */
    List<BizModelEnum> childBizModel();


    /**
     * 查询单条数据
     * 基本上是直接用id去查
     */
    GenericQueryResponse queryOne(QueryOneRequest request);


    /**
     * 查询列表数据
     * 要复杂很多
     */
    PageResult<?> queryList(String modelName, List<QueryParam> paramList, List<SortingField> sortingFields, String bizIdentityCode, int offset, int pageSize);


    /**
     * 单条数据的更新
     */
    void update(String modelName, Object data);


    /**
     * 单条数据的删除
     */
    void delete(DeleteRequest request);
}
