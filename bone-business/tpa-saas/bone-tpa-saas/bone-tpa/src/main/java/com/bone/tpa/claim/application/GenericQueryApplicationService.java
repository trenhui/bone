package com.bone.tpa.claim.application;

import com.bone.core.domain.entity.AbstractEntity;
import com.bone.core.domain.extension.ExtensibleObject;
import com.bone.core.enums.QueryTypeEnum;
import com.bone.core.result.PageResult;
import com.bone.core.result.QueryParam;
import com.bone.core.tenant.TenantAbstractEntity;
import com.bone.tpa.claim.application.enums.BizModelEnum;
import com.bone.tpa.claim.application.request.DeleteRequest;
import com.bone.tpa.claim.application.request.QueryListRequest;
import com.bone.tpa.claim.application.request.QueryOneRequest;
import com.bone.tpa.claim.application.request.UpdateRequest;
import com.bone.tpa.claim.application.response.GenericQueryResponse;
import com.bone.tpa.claim.engine.GenericQueryStrategyFactory;
import com.bone.tpa.claim.infrastructure.log.SimpleLog;
import com.bone.tpa.intelligent.adjustment.dto.DtoBase;
import com.bone.tpa.sdk.claim.enums.BizErrorCode;
import com.bone.tpa.sdk.claim.exception.TpaBizException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;


/**
 * 签收应用层服务
 */
@Service
@Transactional
public class GenericQueryApplicationService {
    @Autowired
    private GenericQueryStrategyFactory strategyFactory;

    @SimpleLog
    @Transactional(rollbackFor = Throwable.class)
    public GenericQueryResponse queryOne(QueryOneRequest request) {
        // 该接口的子模型需要是1:1的情况，不然不应该调过来
        if (checkMultiRelationship(request.getModelNames()) != 0) {
            throw new TpaBizException(BizErrorCode.BIZ_MODEL_ERROR, "Should not have 1:n sub model");
        }

        GenericQueryResponse queryOneResult = strategyFactory.getBizModelStrategy(request.getModelNames().get(0)).queryOne(request);

        if (queryOneResult.getMain() != null && request.getModelNames().size() > 1) {
            // 取得主模型的id，用于查询副模型
            Long relatedId = ((AbstractEntity<?, Long>) queryOneResult.getMain()).getId();
            // 调用的是泛用的查询方法，然后要确认获得的结果只有一条
            List<QueryParam> paramList = new ArrayList<>();
            paramList.add(new QueryParam("relatedId", relatedId));

            String bizIdentityCode = null;
            if (queryOneResult.getMain() instanceof ExtensibleObject) {
                bizIdentityCode = ((ExtensibleObject<?, ?>) queryOneResult.getMain()).getBizIdentityCode();
            }

            if (request.getModelNames().size() > 1) {
                queryOneResult.setSub1(strategyFactory.getBizModelStrategy(request.getModelNames().get(1)).
                        queryList(request.getModelNames().get(1), paramList, new ArrayList<>(), bizIdentityCode, -1, -1).getData().get(0));
            }

            if (request.getModelNames().size() > 2) {
                queryOneResult.setSub2(strategyFactory.getBizModelStrategy(request.getModelNames().get(2)).
                        queryList(request.getModelNames().get(2), paramList, new ArrayList<>(), bizIdentityCode, -1, -1).getData().get(0));
            }
        }


        return queryOneResult;
    }


    @SimpleLog
    @Transactional(rollbackFor = Throwable.class)
    public PageResult<GenericQueryResponse> queryList(QueryListRequest request) {
        // 该接口的子模型无论是1:1还是1:n都可以接受，但是必须有关联
        int childModelFlag = checkMultiRelationship(request.getModelNames());

        Integer offset = -1;
        if (request.getPageNo() > 0 && request.getPageSize() > 0) {
            offset = (request.getPageNo() - 1) * request.getPageSize();
        }

        PageResult<GenericQueryResponse> pageResult = new PageResult<>();

        if (request.getId() != null) {
            request.getQueryParams().add(new QueryParam("relatedId", Long.parseLong(request.getId())));
        }
        if (request.getTenantId() != null) {
            request.getQueryParams().add(new QueryParam("tenantId", Long.parseLong(request.getTenantId())));
        }

        try {
            if(childModelFlag == 0) {
                // 如果没有1:n的模型，这种情况下分页直接在主模型上做，然后查对应子模型便可
                PageResult<?> mainModelResult = strategyFactory.getBizModelStrategy(request.getModelNames().get(0)).
                        queryList(request.getModelNames().get(0), request.getQueryParams(), request.getSortingFields(),
                                request.getBizIdentityCode(), offset, request.getPageSize());

                //构造新结果
                pageResult.setCurrPage((int) mainModelResult.getCurrPage());
                pageResult.setPageSize((int) mainModelResult.getPageSize());
                pageResult.setTotalPage((int) mainModelResult.getTotalPage());
                pageResult.setTotalCount(mainModelResult.getTotalCount());

                if (mainModelResult.getData() == null || mainModelResult.getData().isEmpty()) {
                    pageResult.setData(new ArrayList<>());
                    return pageResult;
                }

                //提取业务场景码
                String bizIdentityCode = null;
                if (mainModelResult.getData().get(0) instanceof ExtensibleObject) {
                    bizIdentityCode = ((ExtensibleObject<?, ?>) mainModelResult.getData().get(0)).getBizIdentityCode();
                }

                //复合结果，先用map装起来
                //id
                Map<Long, GenericQueryResponse> responseMap = new HashMap<>();

                //idList是用来保存查询出来的顺序的，以免map出现顺序错位
                List<GenericQueryResponse> responseList = new ArrayList<>();
                List<Long> idList = new ArrayList<>();

                Boolean singleFlag = false;
                for (Object entity: mainModelResult.getData()) {
                    GenericQueryResponse response = new GenericQueryResponse();
                    response.setMain(entity);
                    if (entity instanceof ExtensibleObject) {
                        response.setBizIdentityCode(((ExtensibleObject<?, ?>) entity).getBizIdentityCode());
                    }
                    if (entity instanceof TenantAbstractEntity) {
                        TenantAbstractEntity<?, Long> tenantEntity = (TenantAbstractEntity<?, Long>) entity;

                        response.setTenantId(String.valueOf(tenantEntity.getTenantId()));
                        responseMap.put(tenantEntity.getId(), response);
                        idList.add(tenantEntity.getId());
                    } else if (entity instanceof DtoBase tenantEntity) {
                        response.setTenantId(String.valueOf(tenantEntity.getTenantId()));
                        responseMap.put(tenantEntity.getId(), response);
                        idList.add(tenantEntity.getId());
                    } else {
                        //到这里绝不可能有子模型，装一下就返回
                        singleFlag = true;

                        response.setTenantId(request.getTenantId());
                        response.setBizIdentityCode(request.getBizIdentityCode());
                        response.setMain(entity);

                        responseList.add(response);
                    }
                }

                //确定没有子模型直接返回
                if (singleFlag) {
                    pageResult.setData(responseList);
                    return pageResult;
                }


                //查询副模型
                List<QueryParam> paramList = new ArrayList<>();
                paramList.add(new QueryParam("relatedId", idList, QueryTypeEnum.IN.getCode()));

                if (request.getModelNames().size() > 1) {
                    List<?> subModelList = strategyFactory.getBizModelStrategy(request.getModelNames().get(1)).
                            queryList(request.getModelNames().get(1), paramList, new ArrayList<>(), bizIdentityCode, -1, -1).getData();

                    for (Object subModel: subModelList) {
                        Class<?> subClazz = subModel.getClass();
                        Method subMethod = subClazz.getMethod("getRelatedId");
                        Long relatedId = (Long)subMethod.invoke(subModel);

                        responseMap.get(relatedId).setSub1(subModel);
                    }
                }

                if (request.getModelNames().size() > 2) {
                    List<?> subModelList = strategyFactory.getBizModelStrategy(request.getModelNames().get(1)).
                            queryList(request.getModelNames().get(2), paramList, new ArrayList<>(), bizIdentityCode, -1, -1).getData();

                    for (Object subModel: subModelList) {
                        Class<?> subClazz = subModel.getClass();
                        Method subMethod = subClazz.getMethod("getRelatedId");
                        Long relatedId = (Long)subMethod.invoke(subModel);

                        responseMap.get(relatedId).setSub2(subModel);
                    }
                }

                //将其排序回原来的顺序
                for (Long id : idList) {
                    responseList.add(responseMap.get(id));
                }

                pageResult.setData(responseList);
            } else {
//                //如果存在1:n的模型，那么首先进行第一次查询，第一次查询不进行分页。
//                PageResult<TenantAbstractEntity<?, Long>> result = (PageResult<TenantAbstractEntity<?, Long>>) method.invoke(repositoryInstance,
//                        request.getQueryParams(), request.getSortingFields(), 0, 0, tableName, request.getBizIdentityCode());
//
//                if (result.getData() == null || result.getData().isEmpty()) {
//                    return new PageResult<>(new ArrayList<>(), request.getPageNo(), request.getPageSize(), 0);
//                }
//
//                //提取业务场景码
//                String bizIdentityCode = null;
//                if (result.getData().get(0) instanceof ExtensibleObject) {
//                    bizIdentityCode = ((ExtensibleObject<?, ?>) result.getData().get(0)).getBizIdentityCode();
//                }
//
//                // 主模型map
//                Map<Long, TenantAbstractEntity<?, Long>> mainModelMap = result.getData().stream().collect(Collectors.toMap(TenantAbstractEntity::getId, t -> t));
//
//                //提取出查出的所有id，利用这些id进行带left join的二次查询，此次要带分页
//                List<Long> relatedIdList = result.getData().stream().map(TenantAbstractEntity::getId).toList();
//
//                // 调用 Repository 类中的方法
//                Method customMethod = repositoryClass.getMethod("queryByCustomerSql", String.class, Map.class);
//
//                // 构造参数列表
//                Map<String, Object> param = new HashMap<>();
//                if (offset >= 0) {
//                    param.put("offset", offset);
//                    param.put("pageSize", request.getPageSize());
//                }
//                param.put("idList", relatedIdList);
//
//                // 这里要获取那个1:n的模型的表名，并且将可能出现的子搜索条件填入
//                String subModel = request.getModelNames().get(childModelFlag);
//                BizModelEnum subModelEnum = BizModelEnum.getByCode(subModel);
//
//                // 这里存在一个小问题，queryParam直接使用的时候可以有查询方式，但是通过这个方式查询的话查询方式是跟着sql走的
//                // 需要自己保证一致
//                if (subModelEnum.getDefaultParams() != null) {
//                    for (QueryParam queryParam : subModelEnum.getDefaultParams()) {
//                        param.put(queryParam.getType(), queryParam.getValue());
//                    }
//                }
//
//                // 调用已经写好的二次查询sql，获得子表的id，此时这些id是满足了所有条件的
//                QueryResult customResult = (QueryResult) customMethod.invoke(repositoryInstance, subModelEnum.getTableName(), param);
//                List<Long> childIdList = new ArrayList<>();
//
//                // 主模型id表
//                List<Long> mainIdList = new ArrayList<>();
//                for (Map<String, Object> map : customResult.getData()) {
//                    mainIdList.add((Long) map.get("id"));
//                    childIdList.add((Long) map.get("childId"));
//                }
//
//                List<Object> childResultList = new ArrayList<>();
//
//                if (!childIdList.stream().allMatch(Objects::isNull)) {
//                    // 去获取子表的字段
//                    List<QueryParam> paramList = new ArrayList<>();
//                    paramList.add(new QueryParam("id", childIdList, QueryTypeEnum.IN.getCode()));
//                    String childModelName = request.getModelNames().get(childModelFlag);
//
//                    childResultList = querySub(childModelName, paramList, bizIdentityCode);
//                }
//
//                Map<Long, Object> childResultMap = new HashMap<>();
//                // 查询1:n的模型并置入map中，待后续使用
//                for (Object entity : childResultList) {
//                    Class<?> subClazz = entity.getClass();
//                    Method subMethod = subClazz.getMethod("getRelatedId");
//                    Long relatedId = (Long)subMethod.invoke(entity);
//
//                    childResultMap.put(relatedId, entity);
//                }
//
//                // 这个时候再去Count一下结果
//                param.remove("offset");
//                param.remove("pageSize");
//                QueryResult countResult = (QueryResult) customMethod.invoke(repositoryInstance, subModelEnum.getTableName() + "_Count", param);
//
//                List<GenericQueryResponse> responseList = new ArrayList<>();
//                // 将分页参数填入
//                pageResult = new PageResult<>(responseList, request.getPageNo(), request.getPageSize(), (int) countResult.getData().get(0).get("Count(*)"));
//
//                // 如果有三个模型则这时候去查第三个模型
//                // relatedId, entity
//                Map<Long, Object> subResultMap = new HashMap<>();
//                if (request.getModelNames().size() > 2) {
//                    String subModelName = request.getModelNames().get(3-childModelFlag);
//
//                    List<QueryParam> subParamList = new ArrayList<>();
//                    subParamList.add(new QueryParam("relatedId", mainIdList, QueryTypeEnum.IN.getCode()));
//                    List<Object> subResultList = querySub(subModelName, subParamList, bizIdentityCode);
//
//                    // 查询1:1的模型并置入map中，待后续使用
//                    for (Object entity : subResultList) {
//                        Class<?> subClazz = entity.getClass();
//                        Method subMethod = subClazz.getMethod("getRelatedId");
//                        Long relatedId = (Long)subMethod.invoke(entity);
//
//                        subResultMap.put(relatedId, entity);
//                    }
//                }
//
//                // 二次查询的结果只包含子表的数据，将其处理并反向找出主表中应该取得哪些数据
//                for (Long mainId : mainIdList) {
//                    GenericQueryResponse genericQueryResponse = new GenericQueryResponse();
//                    genericQueryResponse.setMain(mainModelMap.get(mainId));
//                    genericQueryResponse.setTenantId(String.valueOf(mainModelMap.get(mainId).getTenantId()));
//                    genericQueryResponse.setBizIdentityCode(bizIdentityCode);
//
//                    if (childModelFlag == 1) {
//                        genericQueryResponse.setSub1(childResultMap.get(mainId));
//                        if (request.getModelNames().size() > 2) {
//                            genericQueryResponse.setSub2(subResultMap.get(mainId));
//                        }
//                    } else if (childModelFlag == 2) {
//                        genericQueryResponse.setSub2(childResultMap.get(mainId));
//                        genericQueryResponse.setSub1(subResultMap.get(mainId));
//                    }
//
//                    responseList.add(genericQueryResponse);
//                }
//
//                pageResult.setData(responseList);
            }

            return pageResult;
        } catch (NoSuchMethodException | IllegalAccessException | InvocationTargetException e) {
            e.printStackTrace();
            return null;
        }
    }

    @SimpleLog
    @Transactional(rollbackFor = Throwable.class)
    public Boolean delete(DeleteRequest request) {
        strategyFactory.getBizModelStrategy(request.getModelNames()).delete(request);

        return true;
    }


    @SimpleLog
    @Transactional(rollbackFor = Throwable.class)
    public boolean update(UpdateRequest request) {
        // 该接口的子模型不管怎么说也得有关系
        checkMultiRelationship(request.getModelNames());

        // 主模型更新
        strategyFactory.getBizModelStrategy(request.getModelNames().get(0)).update(request.getModelNames().get(0), request.getUpdateData().getMain());

        // 子模型更新
        if (request.getModelNames().size() > 1) {
            strategyFactory.getBizModelStrategy(request.getModelNames().get(1)).update(request.getModelNames().get(1), request.getUpdateData().getSub1());
        }

        if (request.getModelNames().size() > 2) {
            strategyFactory.getBizModelStrategy(request.getModelNames().get(2)).update(request.getModelNames().get(2), request.getUpdateData().getSub2());
        }

        return true;
    }



    /**
     * 判断业务模型之间的关系
     * 其他情况报错
     */
    private int checkMultiRelationship(List<String> modelNames) {
        if (modelNames == null || modelNames.isEmpty()) {
            throw new TpaBizException(BizErrorCode.BIZ_MODEL_ERROR, "No biz model!");
        }

        //第一个模型是主模型
        BizModelEnum mainModel = BizModelEnum.getByCode(modelNames.get(0));
        if (mainModel == null) {
            throw new TpaBizException(BizErrorCode.BIZ_MODEL_ERROR, modelNames.get(0) + " not exist!");
        }

        // 如果有其他模型就是子模型
        if (modelNames.size() > 1) {
            List<BizModelEnum> subModelList = strategyFactory.getBizModelStrategy(modelNames.get(0)).subBizModel();
            List<BizModelEnum> childModelList = strategyFactory.getBizModelStrategy(modelNames.get(0)).childBizModel();

            //1:n的记录flag
            int childModelFlag = 0;
            for (int i = 1; i < modelNames.size(); i++) {
                BizModelEnum subModel = BizModelEnum.getByCode(modelNames.get(i));

                if (childModelList.contains(subModel)) {
                    if (childModelFlag != 0) {
                        throw new TpaBizException(BizErrorCode.BIZ_MODEL_ERROR, "Two child models are illegal!");
                    }
                    childModelFlag = i;
                } else if (subModelList.contains(subModel)) {

                } else {
                    throw new TpaBizException(BizErrorCode.BIZ_MODEL_ERROR, modelNames.get(i) + " is not a sub model of " + modelNames.get(0));
                }
            }

            // 返回是否有1:n模型，用于判断
            return childModelFlag;
        }

        // 没有子模型的情况相当于没有1:n模型
        return 0;
    }
}
