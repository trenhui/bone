package com.bone.tpa.intelligent.adjustment.limit.service;

import com.bone.core.domain.entity.AbstractEntity;
import com.bone.core.result.PageResult;
import com.bone.metadata.sdk.query.criteria.Criteria;
import com.bone.metadata.sdk.query.criteria.SortItem;
import com.bone.metadata.sdk.query.criteria.SqlSortTypeEnums;
import com.bone.metadata.sdk.dto.MetaFieldDTO;
import com.bone.tpa.intelligent.adjustment.enums.LimitControlTypeEnum;
import com.bone.tpa.intelligent.adjustment.enums.QuotaControllerTypeEnum;
import com.bone.tpa.sdk.adjustment.enums.PersonalQuotaCalculateEnum;
import com.bone.tpa.sdk.adjustment.enums.QuotaStatusEnum;
import com.bone.tpa.sdk.adjustment.model.AdjustmentRecord;
import com.bone.tpa.sdk.dao.PersonQuotaChangeBatchRepository;
import com.bone.tpa.sdk.dao.PersonQuotaChangeRepository;
import com.bone.tpa.sdk.dao.PersonQuotaRepository;
import com.bone.tpa.intelligent.adjustment.infrastructure.util.CommonUtil;
import com.bone.tpa.intelligent.adjustment.model.GetPersonalQuotaListReq;
import com.bone.tpa.intelligent.adjustment.model.PersonInfo;
import com.bone.tpa.sdk.adjustment.enums.InsuredStateEnum;
import com.bone.tpa.sdk.adjustment.enums.PersonalQuotaOperationEnum;
import com.bone.tpa.sdk.adjustment.model.PersonalQuota;
import com.bone.tpa.sdk.adjustment.model.PersonalQuotaChange;
import com.bone.tpa.sdk.adjustment.model.PersonalQuotaChangeBatch;
import com.bone.tpa.sdk.claim.exception.TpaBizException;
import lombok.extern.slf4j.Slf4j;
import org.apache.poi.ss.usermodel.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.util.CollectionUtils;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.math.BigDecimal;
import java.text.SimpleDateFormat;
import java.util.*;
import java.util.stream.Collectors;

@Slf4j
@Service
public class PersonalQuotaService {

    @Autowired
    private TransactionTemplate transactionTemplate;
    @Autowired
    private PersonQuotaChangeBatchRepository personQuotaChangeBatchRepository;
    @Autowired
    private PersonQuotaRepository personQuotaRepository;
    @Autowired
    private PersonQuotaChangeRepository personQuotaChangeRepository;

    public boolean uploadPersonQuota(MultipartFile file, String policyNo, Byte operationType) throws IOException {
        Workbook workbook = WorkbookFactory.create(file.getInputStream());
        Sheet sheet = workbook.getSheetAt(0); // 获取第一个工作表
        //1. 首先要获取第一行字段，检查和规则设置的是否一致
        Row titleRow = sheet.getRow(0);

        //获取列名称
        List<String> titleList = new ArrayList<>();
        for (Cell cell : titleRow) {
            titleList.add(CommonUtil.getCellValue(cell));
        }
        HashSet<String> columnSet = new HashSet<>(titleList);
        if (columnSet.size() == 0 || columnSet.size() != titleList.size()) {
            throw new TpaBizException("模板字段不匹配");
        }

        String insuredNameStr = "";//被保险人姓名
        String insuredCertificateTypeStr = "";//被保险人证件类型
        String insuredCertificateNOStr = "";//被保险人证件号
        String quotaChangeStr = "";//额度
        for (String column : titleList) {
            if (column.contains("姓名")) {
                insuredNameStr = column;
            } else if (column.contains("证件类型")) {
                insuredCertificateTypeStr = column;
            } else if (column.contains("证件号")) {
                insuredCertificateNOStr = column;
            } else if (column.contains("额度")) {
                quotaChangeStr = column;
            }
        }
        if (!StringUtils.hasText(insuredNameStr) || !StringUtils.hasText(insuredCertificateTypeStr) ||
                !StringUtils.hasText(insuredCertificateNOStr) || !StringUtils.hasText(quotaChangeStr)) {
            throw new TpaBizException("模板字段不匹配");
        }

        //保存列名和列索引的映射关系
        Map<String, Integer> columnNameIndexMap = new HashMap<>();
        for (int i = 0; i < titleList.size(); i++) {
            columnNameIndexMap.put(titleList.get(i), i);
        }

        String userName = "system"; //用户名
        Date now = new Date();

        BigDecimal zero = new BigDecimal(0);
        List<PersonalQuotaChange> changeList = new ArrayList<>();
        Set<String> rowFlag = new HashSet<>();
        boolean removePeople = Objects.equals(operationType, PersonalQuotaOperationEnum.REMOVE_PEOPLE.getCode());
        boolean initialPeople = Objects.equals(operationType, PersonalQuotaOperationEnum.INITIAL.getCode());
        for (int i = 1; i <= sheet.getLastRowNum(); i++) {
            Row row = sheet.getRow(i);
            String insuredName = null;
            String insuredCertificateType = null;
            String insuredCertificateNO = null;
            BigDecimal quotaChange = null;
            try {
                insuredName = CommonUtil.getCellValue(row.getCell(columnNameIndexMap.get(insuredNameStr)));

                insuredCertificateType = CommonUtil.getCellValue(row.getCell(columnNameIndexMap.get(insuredCertificateTypeStr)));

                insuredCertificateNO = CommonUtil.getCellValue(row.getCell(columnNameIndexMap.get(insuredCertificateNOStr)));
            } catch (Exception e) {
                log.error("获取被保人信息字段值异常", e);
            }
            if (!StringUtils.hasText(insuredName) || insuredCertificateType == null || !StringUtils.hasText(insuredCertificateNO)) {
                throw new TpaBizException("被保人信息字段必须有值");
            }

            if (!removePeople) {
                try {
                    double quota = Double.parseDouble(CommonUtil.getCellValue(row.getCell(columnNameIndexMap.get(quotaChangeStr))));
                    quotaChange = new BigDecimal(quota);
                    if (initialPeople && quotaChange.compareTo(zero) < 0)
                        throw new TpaBizException("初始金额不可为负数");
                } catch (Exception e) {
                    log.error("获取额度字段值异常", e);
                }
                if (quotaChange == null) {
                    throw new TpaBizException("获取额度字段值异常");
                }
            }

            boolean add = rowFlag.add(insuredCertificateType + "_" + insuredCertificateNO);
            if (!add)
                throw new TpaBizException("人员重复,证件号:" + insuredCertificateNO);

            //判断人员在保单中 todo

            PersonalQuotaChange change = new PersonalQuotaChange();
            changeList.add(change);

            change.setOperationType(operationType);
            change.setPolicyNo(policyNo);
            change.setInsuredName(insuredName);
            change.setInsuredCertificateType(insuredCertificateType);
            change.setInsuredCertificateNumber(insuredCertificateNO);
            if (removePeople) {
                change.setQuotaChange(zero);
            } else {
                change.setQuotaChange(quotaChange);
            }
            change.setCreatePeople(userName);
            change.setCreateTime(now);
        }
        rowFlag.clear();

        if (initialPeople) {
            for (PersonalQuotaChange change : changeList) {
                PersonalQuota personalQuota = getPersonalQuota(policyNo, change.getInsuredCertificateType(), change.getInsuredCertificateNumber());
                if (personalQuota != null)
                    throw new TpaBizException("初始化个人额度时当前人员已在数据库中存在,证件号:" + change.getInsuredCertificateNumber());
            }
        }

        String filename = file.getOriginalFilename();
        String batchName = filename + "_" + userName + "_" + new SimpleDateFormat("yyyyMMdd-HH:mm:ss").format(now);
        Boolean re = transactionTemplate.execute(status -> {
            try {
                PersonalQuotaChangeBatch batch = new PersonalQuotaChangeBatch();
                batch.setPolicyNo(policyNo);
                batch.setOperationType(operationType);
                batch.setBatchName(batchName);
                batch.setCreatePeople(userName);
                batch.setCreateTime(now);
                personQuotaChangeBatchRepository.insert(batch);

                List<PersonalQuota> quotaList = new ArrayList<>();
                for (PersonalQuotaChange change : changeList) {
                    change.setBatchId(batch.getId());
                    PersonalQuota personalQuota = null;

                    if (initialPeople) {
                        change.setQuotaBefore(null);
                        change.setQuotaAfter(change.getQuotaChange());

                        personalQuota = new PersonalQuota();
                        personalQuota.setPolicyNo(policyNo);
                        personalQuota.setInsuredName(change.getInsuredName());
                        personalQuota.setInsuredCertificateType(change.getInsuredCertificateType());
                        personalQuota.setInsuredCertificateNumber(change.getInsuredCertificateNumber());
                        personalQuota.setQuotaInitial(change.getQuotaChange());
                        personalQuota.setQuotaClaim(zero);
                        personalQuota.setQuotaRemaining(change.getQuotaChange());
                        personalQuota.setQuotaFrozen(zero);
                        personalQuota.setQuotaAvailable(change.getQuotaChange());
                        personalQuota.setInsuredState(InsuredStateEnum.NORMAL.getCode());
                        personalQuota.setCreatePeople(userName);
                        personalQuota.setCreateTime(now);
                        personalQuota.setCreatePeople(userName);
                        personalQuota.setUpdateTime(now);
                    } else {
                        personalQuota = getPersonalQuota(policyNo, change.getInsuredCertificateType(), change.getInsuredCertificateNumber());
                        if (personalQuota == null) {
                            throw new TpaBizException("目标人员在数据库中不存在,证件号:" + change.getInsuredCertificateNumber());
                        }

                        personalQuota.setUpdatePeople(userName);
                        personalQuota.setUpdateTime(now);

                        BigDecimal quotaRemainingOld = personalQuota.getQuotaRemaining();
                        change.setQuotaBefore(quotaRemainingOld);
                        if (removePeople) {
                            change.setQuotaAfter(quotaRemainingOld);

                            personalQuota.setInsuredState(InsuredStateEnum.REMOVED.getCode());
                        } else {
                            BigDecimal quotaRemaining = quotaRemainingOld.add(change.getQuotaChange());
                            if (quotaRemaining.compareTo(zero) < 0)
                                throw new TpaBizException("剩余个人额度变动后不能小于0,证件号:" + change.getInsuredCertificateNumber());
                            change.setQuotaAfter(quotaRemaining);

                            personalQuota.setQuotaRemaining(quotaRemaining);
                            BigDecimal quotaAvailable = personalQuota.getQuotaAvailable().add(change.getQuotaChange());
                            if (quotaAvailable.compareTo(zero) < 0) {
                                throw new TpaBizException("当前可用额度变动后不能小于0");
                            }
                            personalQuota.setQuotaAvailable(quotaAvailable);
                        }
                    }
                    quotaList.add(personalQuota);
                }

                personQuotaChangeRepository.insertBatch(changeList);
                personQuotaRepository.saveBatch(quotaList);
                return true;
            } catch (Exception e) {
                log.error("维护个人额度发生异常:", e);
                status.setRollbackOnly();
                return false;
            }
        });
        log.info("维护个人额度结束,结果:{}, 保单号:{}, 文件名:{}", re, policyNo, filename);
        return Boolean.TRUE.equals(re);
    }

    public PersonalQuota getPersonalQuota(String policyNo, String insuredCertificateType, String insuredCertificateNO) {
        Criteria<PersonalQuota> criteria = Criteria.create();
        criteria.eq(PersonalQuota::getPolicyNo, policyNo)
                .eq(PersonalQuota::getInsuredCertificateType, insuredCertificateType)
                .eq(PersonalQuota::getInsuredCertificateNumber, insuredCertificateNO)
                .eq(PersonalQuota::getInsuredState, 0)
                .eq(PersonalQuota::getDeleted, 0);
        List<PersonalQuota> personalQuotaList = personQuotaRepository.findByCriteria(criteria);

        if (personalQuotaList.isEmpty()) {
            return null;
        }

        if (personalQuotaList.size() > 1) {
            throw new TpaBizException("目标人员数据在数据库中重复,证件号:" + insuredCertificateNO);
        }
        return personalQuotaList.get(0);
    }

    public void updatePersonQuota(PersonalQuota personalQuota) {
        personQuotaRepository.save(personalQuota);
    }

    /**
     * 进行额度占用或者额度释放
     */
    public void calculatePersonalQuota(PersonalQuota personalQuota, List<AdjustmentRecord> adjustmentRecordList, PersonalQuotaCalculateEnum operation) {
        //先筛选类型是个人额度并且是这个用户的
        if (personalQuota == null) {
            return;
        }

        List<AdjustmentRecord> personRecordList = adjustmentRecordList.stream().filter(t -> (t.getQuotaType().equals(LimitControlTypeEnum.PERSONAL.getCode())
                || t.getQuotaType().equals(LimitControlTypeEnum.LIABILITY_PERSONAL.getCode()))
                && t.getInsuredCertificateNumber().equals(personalQuota.getInsuredCertificateNumber())).collect(Collectors.toList());

        if (personRecordList.isEmpty()) {
            return;
        }

        //计算冻结额度和占用额度

        //如果是冻结。此时入参是全量的
        if (operation.equals(PersonalQuotaCalculateEnum.FREEZE)) {
            BigDecimal quotaClaim = personRecordList.stream().filter(t -> t.getRecordStatus().equals(QuotaStatusEnum.CONFIRMED.getCode())).map(AdjustmentRecord::getPayoutAmount).reduce(BigDecimal.ZERO, BigDecimal::add);

            BigDecimal quotaFrozen = personRecordList.stream().filter(t -> t.getRecordStatus().equals(QuotaStatusEnum.FROZEN.getCode())).map(AdjustmentRecord::getPayoutAmount).reduce(BigDecimal.ZERO, BigDecimal::add);

            personalQuota.setQuotaClaim(quotaClaim);
            personalQuota.setQuotaFrozen(quotaFrozen);
        }

        //如果是解冻。此时入参是需要去掉的
        if (operation.equals(PersonalQuotaCalculateEnum.UNFREEZE)) {
            BigDecimal quotaClaim = personRecordList.stream().filter(t -> t.getRecordStatus().equals(QuotaStatusEnum.CONFIRMED.getCode())).map(AdjustmentRecord::getPayoutAmount).reduce(BigDecimal.ZERO, BigDecimal::add);

            BigDecimal quotaFrozen = personRecordList.stream().filter(t -> t.getRecordStatus().equals(QuotaStatusEnum.FROZEN.getCode())).map(AdjustmentRecord::getPayoutAmount).reduce(BigDecimal.ZERO, BigDecimal::add);

            personalQuota.setQuotaClaim(personalQuota.getQuotaClaim().subtract(quotaClaim));
            personalQuota.setQuotaFrozen(personalQuota.getQuotaFrozen().subtract(quotaFrozen));
        }


        //计算两个剩余额度
        personalQuota.setQuotaRemaining(personalQuota.getQuotaInitial().subtract(personalQuota.getQuotaClaim()));
        personalQuota.setQuotaAvailable(personalQuota.getQuotaInitial().subtract(personalQuota.getQuotaClaim()).subtract(personalQuota.getQuotaFrozen()));

        updatePersonQuota(personalQuota);
    }


    public PageResult<String> getInsuredNameListByPolicyNo(String policyNo, Integer pageNo, Integer pageSize) {
        Criteria<PersonalQuota> criteria = Criteria.create();
        criteria.eq(PersonalQuota::getPolicyNo, policyNo)
                .eq(PersonalQuota::getInsuredState, 0)
                .eq(PersonalQuota::getDeleted, 0);
        SortItem sortItem = new SortItem();
        MetaFieldDTO sortField = new MetaFieldDTO();
        sortField.setFieldName("insured_name");
        sortItem.addItem(sortField);
        criteria.addSort(sortItem);
        criteria.page(pageSize, (pageNo - 1) * pageSize);
        PageResult<PersonalQuota> pageResult = personQuotaRepository.pageByCriteria(criteria);
        List<String> newData = pageResult.getList().stream().map(PersonalQuota::getInsuredName).toList();
        PageResult<String> result = new PageResult<>(newData, pageResult.getCurrPage(), pageResult.getPageSize(), pageResult.getTotalCount());

        return result;
    }

    public PageResult<String> getOperateBatchList(String policyNo, Integer pageNo, Integer pageSize) {
        Criteria<PersonalQuotaChangeBatch> criteria = Criteria.create();
        criteria.eq(PersonalQuotaChangeBatch::getPolicyNo, policyNo)
                .eq(PersonalQuotaChangeBatch::getDeleted, 0);
        SortItem sortItem = new SortItem();
        MetaFieldDTO sortField = new MetaFieldDTO();
        sortField.setFieldName("create_time");
        sortItem.addItem(sortField);
        sortItem.setSortType(SqlSortTypeEnums.DESC);
        criteria.addSort(sortItem);
        criteria.page(pageSize, (pageNo - 1) * pageSize);
        PageResult<PersonalQuotaChangeBatch> pageResult = personQuotaChangeBatchRepository.pageByCriteria(criteria);
        List<String> newData = pageResult.getList().stream().map(PersonalQuotaChangeBatch::getBatchName).toList();

        PageResult<String> result = new PageResult<>(newData, pageResult.getCurrPage(), pageResult.getPageSize(), pageResult.getTotalCount());
        return result;
    }

    public PageResult<PersonalQuota> getPersonalQuotaList(GetPersonalQuotaListReq request) {
        List<String> numberList = null;
        if (StringUtils.hasText(request.getBatchName())) {
            Criteria<PersonalQuotaChangeBatch> criteria = Criteria.create();
            criteria.eq(PersonalQuotaChangeBatch::getBatchName, request.getBatchName())
                    .eq(PersonalQuotaChangeBatch::getDeleted, 0);
            List<PersonalQuotaChangeBatch> batchList = personQuotaChangeBatchRepository.findByCriteria(criteria);
            List<Long> batchIdList = batchList.stream().map(AbstractEntity::getId).toList();
            if (!CollectionUtils.isEmpty(batchIdList)) {
                Criteria<PersonalQuotaChange> changeCriteria = Criteria.create();
                changeCriteria.in(PersonalQuotaChange::getBatchId, batchIdList)
                        .eq(PersonalQuotaChange::getDeleted, 0);
                List<PersonalQuotaChange> changeList = personQuotaChangeRepository.findByCriteria(changeCriteria);
                numberList = changeList.stream().map(PersonalQuotaChange::getInsuredCertificateNumber).toList();
            }
        }

        Criteria<PersonalQuota> criteria = Criteria.create();
        criteria.eq(PersonalQuota::getPolicyNo, request.getPolicyNo())
                .eq(PersonalQuota::getInsuredState, 0)
                .eq(PersonalQuota::getDeleted, 0);
        if (StringUtils.hasText(request.getInsuredName())) {
            criteria.eq(PersonalQuota::getInsuredName, request.getInsuredName());
        }
        if (request.getInsuredCertificateType() != null && !request.getInsuredCertificateType().isBlank()) {
            criteria.eq(PersonalQuota::getInsuredCertificateType, request.getInsuredCertificateType());
        }
        if (StringUtils.hasText(request.getInsuredCertificateNumber())) {
            criteria.eq(PersonalQuota::getInsuredCertificateNumber, request.getInsuredCertificateNumber());
        }
        if (!CollectionUtils.isEmpty(numberList)) {
            criteria.in(PersonalQuota::getInsuredCertificateNumber, numberList);
        }

        criteria.addSort(Criteria.getDefaultIdSort());
        criteria.page(request.getPageSize(), (request.getPageNo() - 1) * request.getPageSize());
        PageResult<PersonalQuota> pageResult = personQuotaRepository.pageByCriteria(criteria);
        return pageResult;
    }

    public PageResult<PersonalQuotaChange> getPersonalQuotaChangeList(PersonInfo personInfo) {
        Criteria<PersonalQuotaChange> criteria = Criteria.create();
        criteria.eq(PersonalQuotaChange::getPolicyNo, personInfo.getPolicyNo())
                .eq(PersonalQuotaChange::getInsuredName, personInfo.getInsuredName())
                .eq(PersonalQuotaChange::getInsuredCertificateType, personInfo.getInsuredCertificateType())
                .eq(PersonalQuotaChange::getInsuredCertificateNumber, personInfo.getInsuredCertificateNumber())
                .eq(PersonalQuotaChange::getDeleted, 0);
        SortItem sortItem = new SortItem();
        MetaFieldDTO sortField = new MetaFieldDTO();
        sortField.setFieldName("create_time");
        sortItem.addItem(sortField);
        sortItem.setSortType(SqlSortTypeEnums.DESC);
        criteria.addSort(sortItem);

        criteria.page(personInfo.getPageSize(), (personInfo.getPageNo() - 1) * personInfo.getPageSize());
        PageResult<PersonalQuotaChange> changePageResult = personQuotaChangeRepository.pageByCriteria(criteria);

        return changePageResult;
    }

    public Map<Long, String> getBatchRecord(List<Long> batchIdList) {
        Map<Long, String> batchMap = new HashMap<>();

        if (!CollectionUtils.isEmpty(batchIdList)) {
            Criteria<PersonalQuotaChangeBatch> batchCriteria = Criteria.create();
            batchCriteria.in(PersonalQuotaChangeBatch::getId, batchIdList)
                    .eq(PersonalQuotaChangeBatch::getDeleted, 0);
            List<PersonalQuotaChangeBatch> batchList = personQuotaChangeBatchRepository.findByCriteria(batchCriteria);
            batchMap = batchList.stream().collect(Collectors.toMap(AbstractEntity::getId, PersonalQuotaChangeBatch::getBatchName));
        }

        return batchMap;
    }
}
