package com.bone.lowcode.infra.application.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.bone.core.exception.ServiceException;
import com.bone.lowcode.infra.application.dto.groupData.GetBatchDataDTO;
import com.bone.lowcode.infra.application.dto.groupData.GetBatchDataDTOItem;
import com.bone.lowcode.infra.application.vo.PageResult;
import com.bone.lowcode.infra.application.vo.groupData.GetBatchDataVO;
import com.bone.lowcode.infra.application.vo.groupData.SelectDropDataVO;
import com.bone.lowcode.infra.domain.service.OptionSetService;
import com.bone.lowcode.infra.domain.util.MultiTreeNode;
import com.bone.lowcode.infra.domain.util.MultiTreeUtil;
import com.bone.lowcode.infra.domain.valueobject.DataSourceTypeEnum;
import com.bone.lowcode.infra.domain.valueobject.DeletedEnum;
import com.bone.lowcode.infra.domain.valueobject.EnableStatusEnum;
import com.bone.lowcode.infra.domain.valueobject.OptionSetNodeEnum;
import com.bone.lowcode.infra.infrastructure.feign.bean.AddressInfo;
import com.bone.lowcode.infra.infrastructure.feign.bean.EnumEntry;
import com.bone.lowcode.infra.infrastructure.feign.bean.GroupDataParam;
import com.bone.lowcode.infra.infrastructure.feign.util.GroupDataFeignUtil;
import com.bone.lowcode.infra.infrastructure.feign.util.TpaSaasBusinessFeignUtil;
import com.bone.lowcode.infra.infrastructure.persistence.dataobject.OptionSet;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.util.CollectionUtils;
import org.springframework.util.StringUtils;

import java.util.*;
import java.util.stream.Collectors;

@Slf4j
@Component
public class GroupDataApplicationService {

    @Autowired
    private OptionSetService optionSetService;

    @Autowired
    private GroupDataFeignUtil groupDataFeignUtil;

    @Autowired
    private TpaSaasBusinessFeignUtil tpaSaasBusinessFeignUtil;

    private final String provinceCode = "province_code";

    public PageResult<SelectDropDataVO> getDataTypePage(Long pageNum, Long pageSize, Byte type, String name) {
        PageResult<SelectDropDataVO> result = null;
        if (Objects.equals(DataSourceTypeEnum.OPTION_SET.getType(), type)) {
            Page<OptionSet> page = optionSetService.getRootListByName(pageNum, pageSize, name);
            List<OptionSet> optionSetList = page.getRecords();
            List<SelectDropDataVO> voList = optionSetList.stream()
                    .map(i -> new SelectDropDataVO(DataSourceTypeEnum.OPTION_SET.getType(), i.getCode(), i.getName()))
                    .toList();
            result = new PageResult<>(pageNum, pageSize, page.getTotal(), voList);
        } else if (Objects.equals(DataSourceTypeEnum.MASTER_DATA.getType(), type)) {
            long totalSize = 1L + MultiTreeUtil.getRootSize();
            LinkedList<SelectDropDataVO> rootList = MultiTreeUtil.getRootList();
            rootList.forEach(i -> i.setType(type));
            rootList.addFirst(new SelectDropDataVO(type, "province_code", "省市区"));
            List<SelectDropDataVO> pageVOList = rootList.stream().skip((pageNum - 1) * pageSize).limit(pageSize).toList();
            result = new PageResult<>(pageNum, pageSize, totalSize, pageVOList);
        } else if (Objects.equals(DataSourceTypeEnum.ENUM_DATA.getType(), type)) {
            List<EnumEntry> enumList = tpaSaasBusinessFeignUtil.getEnumList();
            result = new PageResult<>(pageNum, pageSize, (long) enumList.size(), null);

            List<EnumEntry> pageRows = enumList.stream().skip((pageNum - 1) * pageSize).limit(pageSize).toList();
            if (!CollectionUtils.isEmpty(pageRows)) {
                List<SelectDropDataVO> voList = pageRows.stream()
                        .map(i -> {
                            SelectDropDataVO vo = new SelectDropDataVO(type, i.getCode(), i.getName());
                            return vo;
                        })
                        .toList();
                result.setRows(voList);
            }
        } else {
            throw new ServiceException(500, "不支持的类型");
        }
        return result;
    }

    public PageResult<SelectDropDataVO> getDataPage(Integer pageNum, Integer pageSize, Byte type, String parentCode, String name) {
        PageResult<SelectDropDataVO> result = null;
        if (Objects.equals(DataSourceTypeEnum.OPTION_SET.getType(), type)) {
            result = getSelectDropDataVOPageResultFromOptionSet(pageNum, pageSize, type, parentCode, name);
        } else if (Objects.equals(DataSourceTypeEnum.MASTER_DATA.getType(), type)) {
            result = getSelectDropDataVOPageResultFromMasterData(pageNum, pageSize, type, parentCode);
        } else if (Objects.equals(DataSourceTypeEnum.ENUM_DATA.getType(), type)) {
            result = getSelectDropDataVOPageResultFromEnumData(pageNum, pageSize, type, parentCode, name);
        } else {
            throw new ServiceException(500, "不支持的类型");
        }
        return result;
    }

    private PageResult<SelectDropDataVO> getSelectDropDataVOPageResultFromOptionSet(Integer pageNum, Integer pageSize, Byte type, String parentCode, String name) {
        PageResult<SelectDropDataVO> result;
        OptionSet optionSet = optionSetService.getRootSetByCode(parentCode);
        if (optionSet == null)
            throw new ServiceException(500, "根据code未查询到选项集");

        LambdaQueryWrapper<OptionSet> wrapper = new LambdaQueryWrapper<OptionSet>()
                .eq(OptionSet::getParentId, optionSet.getId())
                .eq(OptionSet::getStatus, EnableStatusEnum.ENABLE.getCode())
                .like(StringUtils.hasText(name), OptionSet::getName, name)
                .eq(OptionSet::getDeleted, DeletedEnum.UNDELETED.getCode())
                .orderByAsc(OptionSet::getCreateTime);
        Page<OptionSet> page = optionSetService.optionValuePage(pageNum, pageSize, wrapper);

        long total = page.getTotal();
        result = new PageResult<>(pageNum.longValue(), pageSize.longValue(), total, new ArrayList<>());
        List<OptionSet> optionSetList = page.getRecords();
        if (!CollectionUtils.isEmpty(optionSetList)) {
            List<SelectDropDataVO> voList = optionSetList.stream()
                    .map(i -> {
                        SelectDropDataVO vo = new SelectDropDataVO(type, i.getCode(), i.getName());
                        if (!CollectionUtils.isEmpty(i.getExtraPropertyKeyValue())) {
                            vo.setExtraProperty(i.getExtraPropertyKeyValue());
                        }
                        return vo;
                    })
                    .toList();
            result.setRows(voList);
        }
        return result;
    }

    private PageResult<SelectDropDataVO> getSelectDropDataVOPageResultFromMasterData(Integer pageNum, Integer pageSize, Byte type, String parentCode) {
        if (MultiTreeUtil.containCode(parentCode)) {
            return getClaimConclusionPageResult(pageNum, pageSize, type, parentCode);
        }

        GroupDataParam param = new GroupDataParam();
        param.setPageNumber(pageNum);
        param.setPageSize(pageSize);
        if (provinceCode.equals(parentCode)) {
            param.setType(parentCode);
        } else {
            param.setParentCode(parentCode);
        }
        com.bone.lowcode.infra.infrastructure.feign.bean.PageResult<AddressInfo> pageResult = groupDataFeignUtil.getAddressList(param);
        List<AddressInfo> data = pageResult.getData();
        List<SelectDropDataVO> voList = data.stream().map(addressInfo -> new SelectDropDataVO(type, addressInfo.getCode(), addressInfo.getName())).toList();
        PageResult<SelectDropDataVO> result = new PageResult<>(pageResult.getCurrPage().longValue(), pageResult.getPageSize().longValue(),
                pageResult.getTotalCount().longValue(), voList);
        return result;
    }

    private PageResult<SelectDropDataVO> getClaimConclusionPageResult(Integer pageNum, Integer pageSize, Byte type, String parentCode) {
        List<MultiTreeNode> children = MultiTreeUtil.getChildrenByParentCode(parentCode);
        List<MultiTreeNode> currentPage = children.stream().skip((long) (pageNum - 1) * pageSize).limit(pageSize).toList();
        List<SelectDropDataVO> voList = currentPage.stream().map(node -> new SelectDropDataVO(type, node.getCode(), node.getName())).toList();
        return new PageResult<>((long) pageNum, (long) pageSize, (long) children.size(), voList);
    }

    private PageResult<SelectDropDataVO> getSelectDropDataVOPageResultFromEnumData(Integer pageNum, Integer pageSize, Byte type, String parentCode, String name) {
        PageResult<SelectDropDataVO> result;
        List<EnumEntry> enumDetail = tpaSaasBusinessFeignUtil.getEnumDetail(parentCode);
        if (StringUtils.hasText(name)) {
            enumDetail = enumDetail.stream().filter(i -> StringUtils.hasText(i.getName()) && i.getName().contains(name)).collect(Collectors.toList());
        }

        int total = enumDetail.size();
        result = new PageResult<>(pageNum.longValue(), pageSize.longValue(), (long) total, new ArrayList<>());

        List<EnumEntry> pageRows = enumDetail.stream().skip((long) (pageNum - 1) * pageSize).limit(pageSize).toList();
        if (!CollectionUtils.isEmpty(pageRows)) {
            List<SelectDropDataVO> voList = pageRows.stream().map(i -> new SelectDropDataVO(type, i.getCode(), i.getName())).toList();
            result.setRows(voList);
        }
        return result;
    }

    public List<GetBatchDataVO> getBatchData(GetBatchDataDTO param) {
        List<GetBatchDataDTOItem> paramItemList = param.getParamList();
        Integer pageNum = param.getPageNum();
        Integer pageSize = param.getPageSize();
        List<GetBatchDataVO> res = new ArrayList<>();

        for (GetBatchDataDTOItem paramItem : paramItemList) {
            Byte type = paramItem.getType();
            String parentCode = paramItem.getParentCode();
            GetBatchDataVO data = new GetBatchDataVO(type, parentCode, pageNum, pageSize);
            res.add(data);

            if (Objects.equals(DataSourceTypeEnum.OPTION_SET.getType(), type)) {
                OptionSet optionSet = optionSetService.getRootSetByCode(parentCode);
                if (optionSet == null) {
                    throw new ServiceException(500, "根据code未查询到选项集");
                }

                LambdaQueryWrapper<OptionSet> wrapper = new LambdaQueryWrapper<OptionSet>()
                        .eq(OptionSet::getParentId, optionSet.getId())
                        .eq(OptionSet::getStatus, EnableStatusEnum.ENABLE.getCode())
                        .eq(OptionSet::getDeleted, DeletedEnum.UNDELETED.getCode())
                        .orderByAsc(OptionSet::getCreateTime);
                Page<OptionSet> page = optionSetService.optionValuePage(pageNum, pageSize, wrapper);
                data.setTotalSize((int) page.getTotal());
                List<OptionSet> optionSetList = page.getRecords();

                if (!CollectionUtils.isEmpty(optionSetList)) {
                    List<SelectDropDataVO> voList = optionSetList.stream()
                            .map(i -> {
                                SelectDropDataVO vo = new SelectDropDataVO(type, i.getCode(), i.getName());
                                if (!CollectionUtils.isEmpty(i.getExtraPropertyKeyValue())) {
                                    vo.setExtraProperty(i.getExtraPropertyKeyValue());
                                }
                                return vo;
                            })
                            .toList();
                    data.setData(voList);
                }
            } else if (Objects.equals(DataSourceTypeEnum.MASTER_DATA.getType(), type)) {
                if (MultiTreeUtil.containCode(parentCode)) {
                    List<MultiTreeNode> children = MultiTreeUtil.getChildrenByParentCode(parentCode);
                    List<MultiTreeNode> currentPage = children.stream().skip((long) (pageNum - 1) * pageSize).limit(pageSize).toList();
                    List<SelectDropDataVO> voList = currentPage.stream().map(node -> new SelectDropDataVO(type, node.getCode(), node.getName())).toList();
                    data.setTotalSize(children.size());
                    data.setData(voList);
                    continue;
                }

                GroupDataParam pageParam = new GroupDataParam();
                pageParam.setPageNumber(pageNum);
                pageParam.setPageSize(pageSize);
                if (provinceCode.equals(parentCode)) {
                    pageParam.setType(parentCode);
                } else {
                    pageParam.setParentCode(parentCode);
                }
                com.bone.lowcode.infra.infrastructure.feign.bean.PageResult<AddressInfo> pageResult = groupDataFeignUtil.getAddressList(pageParam);
                List<AddressInfo> addressInfos = pageResult.getData();
                List<SelectDropDataVO> voList = addressInfos.stream().map(addressInfo -> new SelectDropDataVO(type, addressInfo.getCode(), addressInfo.getName())).toList();
                data.setTotalSize(pageResult.getTotalCount());
                data.setData(voList);
            } else if (Objects.equals(DataSourceTypeEnum.ENUM_DATA.getType(), type)) {
                List<EnumEntry> enumDetail = tpaSaasBusinessFeignUtil.getEnumDetail(parentCode);
                data.setTotalSize(enumDetail.size());
                if (!CollectionUtils.isEmpty(enumDetail)) {
                    enumDetail = enumDetail.stream().skip((long) (pageNum - 1) * pageSize).limit(pageSize).toList();
                    List<SelectDropDataVO> voList = enumDetail.stream().map(i -> new SelectDropDataVO(type, i.getCode(), i.getName())).toList();
                    data.setData(voList);
                }
            } else {
                throw new ServiceException(500, "不支持的类型");
            }
        }
        return res;
    }

    public List<SelectDropDataVO> getValueListByCodeList(Byte type, String rootCode, List<String> codeList) {
        List<SelectDropDataVO> result = null;

        if (Objects.equals(DataSourceTypeEnum.OPTION_SET.getType(), type)) {
            OptionSet optionSet = optionSetService.getRootSetByCode(rootCode);
            if (optionSet == null) {
                throw new ServiceException(500, "根据code未查询到选项集");
            }
            List<OptionSet> list = optionSetService.getListBySetIdAndCodeList(optionSet.getId(), codeList);

            result = list.stream().map(i -> {
                SelectDropDataVO vo = new SelectDropDataVO(type, i.getCode(), i.getName());
                if (!CollectionUtils.isEmpty(i.getExtraPropertyKeyValue())) {
                    vo.setExtraProperty(i.getExtraPropertyKeyValue());
                }
                return vo;
            }).toList();
        } else if (Objects.equals(DataSourceTypeEnum.MASTER_DATA.getType(), type)) {
            throw new ServiceException(500, "组数据服务待接入");
            //todo
        } else if (Objects.equals(DataSourceTypeEnum.ENUM_DATA.getType(), type)) {
            List<EnumEntry> enumDetail = tpaSaasBusinessFeignUtil.getEnumDetail(rootCode);
            List<EnumEntry> valueList = enumDetail.stream().filter(i -> codeList.contains(i.getCode())).toList();
            result = valueList.stream().map(i -> new SelectDropDataVO(type, i.getCode(), i.getName())).toList();
        } else {
            throw new ServiceException(500, "不支持的类型");
        }
        return result;
    }

    public SelectDropDataVO getDataType(Byte type, String code) {
        SelectDropDataVO result = null;
        if (Objects.equals(DataSourceTypeEnum.OPTION_SET.getType(), type)) {
            OptionSet releasedOptionSet = optionSetService.getRootSetByCode(code);
            if (releasedOptionSet == null || releasedOptionSet.getNodeType() != OptionSetNodeEnum.ROOT_NODE.getCode()) {
                throw new ServiceException(500, "目标选项集不存在");
            }

            result = new SelectDropDataVO(type, releasedOptionSet.getCode(), releasedOptionSet.getName());
        } else if (Objects.equals(DataSourceTypeEnum.MASTER_DATA.getType(), type)) {
            throw new ServiceException(500, "主数据服务待接入");
        } else if (Objects.equals(DataSourceTypeEnum.ENUM_DATA.getType(), type)) {
            List<EnumEntry> enumList = tpaSaasBusinessFeignUtil.getEnumList();
            Optional<EnumEntry> first = enumList.stream().filter(i -> i.getCode().equals(code)).findFirst();
            if (first.isEmpty()) {
                throw new ServiceException(500, "未找到符合条件的枚举");
            }
            EnumEntry enumEntry = first.get();
            result = new SelectDropDataVO(type, enumEntry.getCode(), enumEntry.getName());
        } else {
            throw new ServiceException(500, "不支持的类型");
        }
        return result;
    }
}
