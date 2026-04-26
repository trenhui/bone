package com.bone.tpa.facade.feign;

import com.bone.core.result.Result;
import com.bone.tpa.facade.FeignTpaConfig;
import com.bone.tpa.facade.request.CollectionBindQueryRequest;
import com.bone.tpa.facade.request.OptionWithCnQueryRequest;
import com.bone.tpa.facade.request.OptionWithCodeQueryRequest;
import com.bone.tpa.facade.vo.ColletionBindVO;
import com.bone.tpa.facade.vo.OptionSetDTO;
import com.bone.tpa.facade.vo.PageBizIdentityVO;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;

import java.util.List;

/**
 * 页面、业务字段的feign
 * 1 取模块的扩展字段的code
 * 2 根据扩展字段入库数据
 * 3 根据扩展业务字段的code 获取选项集
 *
 */
@FeignClient( value = "pageConfig-server",contextId = "PageModelConfigFeign",configuration = FeignTpaConfig.class)
public interface PageModelConfigFeign {
    /**
     * 获取模块字段绑定选项集
     * 主要用途： 在数据同步匹配字段时候，
     * 如果页面配置了选项集，则需要进行选项集的匹配，因此需要根据这来选择匹配策略
     * 如果页面走了主数据，则通过主数据的帮定策略进行匹配
     * @param request
     * @return
     */
    @PostMapping(value = "/cfg/provide/queryCollectionBind", consumes = "application/json")
    Result<List<ColletionBindVO>> queryCollectionBind(@RequestBody CollectionBindQueryRequest request);


    /**
     * 根据中文匹配精确选项集
     * @param request
     * @return
     */
    @PostMapping(value = "/cfg/provide/queryCollectionByOptionCn", consumes = "application/json")
    Result<OptionSetDTO> queryCollectionByOptionCn(@RequestBody OptionWithCnQueryRequest request);


    @PostMapping(value = "/cfg/provide/queryCollectionByOptionCode", consumes = "application/json")
    Result<OptionSetDTO> queryCollectionByOptionCode(@RequestBody OptionWithCodeQueryRequest request);



    @GetMapping("/cfg/provide/getAllBizIdentity")
    Result<List<PageBizIdentityVO>> getAllBizidentity();
}
