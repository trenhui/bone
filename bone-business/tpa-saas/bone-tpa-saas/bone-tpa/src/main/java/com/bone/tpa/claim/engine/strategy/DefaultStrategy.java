package com.bone.tpa.claim.engine.strategy;

import com.bone.core.domain.entity.AbstractEntity;
import com.bone.core.result.PageResult;
import com.bone.core.result.QueryParam;
import com.bone.core.result.SortingField;
import com.bone.metadata.sdk.query.criteria.Criteria;
import com.bone.core.util.PkListUtil;
import com.bone.core.util.SpringContextUtils;
import com.bone.tpa.api.enums.HintMsgType;
import com.bone.tpa.claim.application.enums.BizModelEnum;
import com.bone.tpa.claim.application.request.DeleteRequest;
import com.bone.tpa.claim.application.request.QueryOneRequest;
import com.bone.tpa.claim.application.response.GenericQueryResponse;
import com.bone.tpa.sdk.dao.ClaimHintMsgRespository;
import com.bone.tpa.claim.engine.GenericQueryStrategy;
import com.bone.tpa.sdk.claim.model.ClaimHintMsg;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;

/**
 *
 * 到这里的是未找到特殊实现的模型
 *
 */
@Service
public class DefaultStrategy implements GenericQueryStrategy {
    private final static String tablePrefix = "ss";
    private final static String repositoryPrefix = "com.bone.tpa.claim.infrastructure.persistence.";
    private final static String repositorySuffix = "RepositoryImpl";

    private final static String adjustTablePrefix = "ia";
    private final static String adjustRepositoryPrefix = "com.bone.tpa.intelligent.adjustment.repository.";
    private final static String adjustRepositorySuffix = "Repository";

    @Autowired
    private ClaimHintMsgRespository hintMsgRespository;

    @Autowired
    protected ObjectMapper mapper;

    @Override
    public BizModelEnum getBizModel() {
        return BizModelEnum.DEFAULT;
    }


    @Override
    public List<BizModelEnum> subBizModel() {
        return List.of();
    }


    @Override
    public List<BizModelEnum> childBizModel() {
        return List.of();
    }


    @Override
    public GenericQueryResponse queryOne(QueryOneRequest request) {
        try {
            String mainRepositoryName = fromModelNameToRepository(request.getModelNames().get(0));

            // 使用反射加载 Repository 类
            Class<?> repositoryClass = Class.forName(mainRepositoryName);
            Object repositoryInstance = SpringContextUtils.getBean(repositoryClass);

            // 调用 Repository 类中的方法
            Method method = repositoryClass.getMethod("findById", Object.class);
            Object result = method.invoke(repositoryInstance, request.getId());

            // 第一个查出来的是主模型
            if (result != null) {
                return new GenericQueryResponse(result);
            }
        } catch (ClassNotFoundException | NoSuchMethodException | IllegalAccessException | InvocationTargetException e) {
            e.printStackTrace();
        }

        return new GenericQueryResponse();
    }


    @Override
    public PageResult<?> queryList(String modelName, List<QueryParam> paramList, List<SortingField> sortingFields, String bizIdentityCode, int offset, int pageSize) {
        try {
            String mainRepositoryName = fromModelNameToRepository(modelName);

            // 使用反射加载 Repository 类
            Class<?> repositoryClass = Class.forName(mainRepositoryName);
            Object repositoryInstance = SpringContextUtils.getBean(repositoryClass);

            String tableName = BizModelEnum.getByCode(modelName).getTableName();

            // 调用 Repository 类中的方法
            Method method = repositoryClass.getMethod("queryByCondition", List.class, List.class, Integer.class,
                    Integer.class, String.class, String.class);

            PageResult<Object> result = (PageResult<Object>) method.invoke(repositoryInstance,
                    paramList, sortingFields, offset, pageSize, tableName, bizIdentityCode);

            if (result.getData() == null || result.getData().isEmpty()) {
                result.setData(new ArrayList<>());
            }

            return result;
        } catch (ClassNotFoundException | NoSuchMethodException | IllegalAccessException | InvocationTargetException e) {
            e.printStackTrace();
        }

        return new PageResult<>(new ArrayList<>(), offset/pageSize, pageSize, 0);
    }


    @Override
    public void update(String modelName, Object data) {
        try {
            String mainRepositoryName = fromModelNameToRepository(modelName);

            // 使用反射加载 Repository 类
            Class<?> repositoryClass = Class.forName(mainRepositoryName);
            Object repositoryInstance = SpringContextUtils.getBean(repositoryClass);

            // 调用 Repository 类中的方法
            Method method = repositoryClass.getMethod("save", AbstractEntity.class);
            Class<?> tableClass = BizModelEnum.getByCode(modelName).getBizClass();

            // 将 Map 转为 JSON 字符串，再解析为 DTO
            ObjectMapper mapper = new ObjectMapper();

            method.invoke(repositoryInstance, mapper.convertValue(data, tableClass));

        } catch (ClassNotFoundException | NoSuchMethodException | IllegalAccessException | InvocationTargetException e) {
            e.printStackTrace();
        }

    }


    @Override
    public void delete(DeleteRequest request) {

        String repositoryName = fromModelNameToRepository(request.getModelNames());

        try {
            // 使用反射加载 Repository 类
            Class<?> repositoryClass = Class.forName(repositoryName);
            Object repositoryInstance = SpringContextUtils.getBean(repositoryClass);

            // 调用 Repository 类中的方法
            Method method = repositoryClass.getMethod("deleteByIds", List.class);
            method.invoke(repositoryInstance, request.getIdList());

        } catch (ClassNotFoundException | NoSuchMethodException | IllegalAccessException | InvocationTargetException e) {
            e.printStackTrace();
        }
    }


    /**
     * 将业务模型名转化为表名
     *
     * @param modelName
     * @return
     */
    private String fromModelNameToRepository(String modelName) {

        String tableName = BizModelEnum.getByCode(modelName).getTableName();

        String[] parts = tableName.split("_");

        StringBuilder repositoryName = new StringBuilder();
        //根据表名开头路由到不同的查询那边去
        if (tablePrefix.equals(parts[0])) {
            repositoryName.append(repositoryPrefix);
            for (int i = 1; i< parts.length; i++) {
                repositoryName.append(parts[i].substring(0, 1).toUpperCase());
                repositoryName.append(parts[i].substring(1));
            }
            repositoryName.append(repositorySuffix);
        } else if (adjustTablePrefix.equals(parts[0])) {
            repositoryName.append(adjustRepositoryPrefix);
            for (int i = 1; i< parts.length; i++) {
                repositoryName.append(parts[i].substring(0, 1).toUpperCase());
                repositoryName.append(parts[i].substring(1));
            }
            repositoryName.append(adjustRepositorySuffix);
        }

        return repositoryName.toString();
    }

    /**
     * 查询同步提示信息
     *
     * 不穿后两个参数将会查出全部类型的，传入的话查询特定类型
     */
    protected ClaimHintMsg querySyncHint(Long claimId, HintMsgType type, Long objectId) {
        Criteria<ClaimHintMsg> criteria = new Criteria();
        criteria.eq(ClaimHintMsg::getClaimNumber, claimId);
        if (type != null) {
            criteria.eq(ClaimHintMsg::getRelationId, objectId);
            criteria.eq(ClaimHintMsg::getHintType, type.getCode());
        }

        ClaimHintMsg hintMsg = PkListUtil.first(hintMsgRespository.findByCriteria(criteria));

        return hintMsg;
    }
}
