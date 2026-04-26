package com.bone.tpa.task.impl;

import com.alibaba.fastjson.JSON;
import com.bone.metadata.sdk.query.criteria.Criteria;
import com.bone.core.util.PkListUtil;
import com.bone.core.util.SpringContextUtils;
import com.bone.tpa.api.ApiResult;
import com.bone.tpa.api.enums.PKImageMapEnum;
import com.bone.tpa.core.synctask.AlertRobotManager;
import com.bone.tpa.core.synctask.SyncTaskTemplate;
import com.bone.tpa.core.util.localImageTool.LocalImageToolFeignClient;
import com.bone.tpa.core.util.localImageTool.request.ClassifyParam;
import com.bone.tpa.core.util.localImageTool.response.ImageToolResponse;
import com.bone.tpa.facade.request.TpaAddLogRequest;
import com.bone.tpa.sdk.dao.ClaimImageRepository;
import com.bone.tpa.sdk.dao.ClaimRepository;
import com.bone.tpa.facade.feign.TpaDataSyncFeign;
import com.bone.tpa.facade.vo.InsuranceCompanyImageVO;
import com.bone.tpa.sdk.claim.model.Claim;
import com.bone.tpa.sdk.claim.model.ClaimImage;
import com.bone.tpa.sdk.claim.model.SyncTask;
import com.xxl.job.core.handler.annotation.XxlJob;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.CollectionUtils;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.text.MessageFormat;
import java.util.Date;
import java.util.List;

@Slf4j
@Service
public class AutoPreExameTrigger extends SyncTaskTemplate {
    @Autowired
    private TpaDataSyncFeign tpaDataSyncFeign;
    @Autowired
    private ClaimImageRepository imageRepository;

    @Autowired
    private ClaimRepository claimRepository;

    @Autowired
    private AutoPreameCompleteTrigger completeTrigger;

    @Autowired
    private LocalImageToolFeignClient imageToolFeignClient;


    @Value("${saas.auto.precheck.category.threshold:0.9}")
    private BigDecimal categoryThreshold;//影像件分类结果的可能性大于此阈值才可用

    @Value("${saas.auto.precheck.imageturn.threshold:0.9}")
    private BigDecimal xuanzhuanThreshold;//影像件方向识别结果的可能性大于此阈值才可用

    /**
     * 所属业务
     */
    @Override
    public String getBizType() {
        return "auto-pre-exame";
    }

    /**
     * 获取告警url
     */
    @Override
    public String getAlertUrl() {
        return dingAlertUrl;
    }

    @Override
    public String getAlertMode() {
        return AlertRobotManager.QIWEI_MODE;
    }

    @XxlJob("autoPreExameJob")
    public void autoPreExameJobXxljob() {
        execute();
    }

    /**
     * 执行一个任务
     * 在这里加Transaction标记没用
     */
    @Override
    public void syncOneData(SyncTask task) {
        Long claimNumber = null;
        try {
            claimNumber = Long.valueOf(task.getData());
            SpringContextUtils.getBean(AutoPreExameTrigger.class).doAutoFirstExam(claimNumber);
        } catch (Exception e) {
            log.error("赔案自动化初审发生异常,claimNumber:" + claimNumber, e);
        } finally {
            try {
                TpaAddLogRequest tpaAddLogRequest = new TpaAddLogRequest();
                tpaAddLogRequest.setClaimNumber( claimNumber);
                tpaAddLogRequest.setOperation("自动化初审完成");
                tpaAddLogRequest.setRemark("");
                tpaAddLogRequest.setCreateBy("System");
                tpaAddLogRequest.setCreateTime( (new Date()).getTime());
                tpaDataSyncFeign.addLog(tpaAddLogRequest);
            } catch (Exception e) {
                //为了不阻塞
                log.error("add log error",e);
            }
            SpringContextUtils.getBean(AutoPreameCompleteTrigger.class).addJobAndTryFire(task.getData(), 1, 3);
        }
    }

    @Transactional(rollbackFor = Throwable.class)
    public void doAutoFirstExam(Long claimNumber) {
        Criteria<ClaimImage> imageCriteria = new Criteria<>();
        imageCriteria.eq(ClaimImage::getRelatedId, claimNumber);
        List<ClaimImage> imageList = imageRepository.findByCriteria(imageCriteria);
        if (CollectionUtils.isEmpty(imageList)) {
            throw new RuntimeException("赔案影像件缺失,claimNumber:" + claimNumber);
        }

        Claim claim = claimRepository.findById(claimNumber);
        if (claim == null) {
            throw new RuntimeException("找不到赔案,claimNumber:" + claimNumber);
        }
        ApiResult<List<InsuranceCompanyImageVO>> remoteCategory = tpaDataSyncFeign.getImageMapDetail(claim.getInsuranceName());
        if (!remoteCategory.isSuccess()) {
            throw new RuntimeException("tpa返回获取影像分类失败,保险公司名称:" + claim.getInsuranceName());
        }
        List<InsuranceCompanyImageVO> typeVOList = remoteCategory.getData();
        if (PkListUtil.isEmpty(typeVOList)) {
            throw new RuntimeException("tpa返回影像分类为空,保险公司名称:" + claim.getInsuranceName());
        }

        log.info("=================自动化初审开始执行,claimNumber:{},影像件数量:{}", claimNumber, imageList.size());
        int classifyFailCount = 0;
        for (ClaimImage image : imageList) {
            String imagePath = image.getImagePath();
            String info = MessageFormat.format("影像件信息: claimNumber:{0}, 影像件id:{1}, 影像件url:{2}", claimNumber.toString(), image.getId().toString(), imagePath);
            if (!StringUtils.hasText(imagePath)) {
                log.error("影像件初审失败,因为影像件url缺失," + info);
                classifyFailCount++;
                continue;
            }

            //影像件分类
            try {
                ImageToolResponse response = imageToolFeignClient.classify(new ClassifyParam(imagePath));
                log.info("调用影像件分类接口完成," + info + ",结果:" + JSON.toJSONString(response));

                if (!response.success()) {
                    log.error("影像件分类失败," + info);
                    classifyFailCount++;
                    continue;
                }

                ImageToolResponse.Content data = response.getData();
                if (data == null || !StringUtils.hasText(data.getClassify()) || data.getProb() < categoryThreshold.doubleValue()) {
                    log.error("影像件分类失败,因为分类结果不可用,结果:{}, 阈值:{}", data, categoryThreshold);
                    classifyFailCount++;
                    continue;
                }

                String pkCode = PKImageMapEnum.getCodeByClassifyName(data.getClassify());//获取普康分类code
                if (!StringUtils.hasText(pkCode)) {
                    log.error("影像件分类失败,因为无法映射到普康分类code," + info);
                    classifyFailCount++;
                    continue;
                } else {
                    ClaimImage newImage = new ClaimImage();
                    newImage.setId(image.getId());
                    newImage.setImagePkType(pkCode);
                    newImage.setUpdateTime(new Date());
                    imageRepository.update(newImage);
                    log.info("初审仅仅更新普康分类,普康分类:{},发票信息:{}", pkCode, info);
                }

                List<String> insuranceImageTypeList = typeVOList.stream()
                        .filter(i -> pkCode.equals(i.getImageMapCode()))
                        .map(InsuranceCompanyImageVO::getImageClassifyCode)
                        .toList();//获取保司分类code
                if (CollectionUtils.isEmpty(insuranceImageTypeList) || insuranceImageTypeList.size() > 1) {
                    String msg = MessageFormat.format("普康分类code:{0},保司分类code:{1};", pkCode, insuranceImageTypeList.toString());
                    log.error("影像件分类失败,因为普康分类code映射到的保司分类code为空或者超过1个," + msg + info);
                    classifyFailCount++;
                    continue;
                }

                String insuranceImageType = insuranceImageTypeList.get(0);
                ClaimImage newImage = new ClaimImage();
                newImage.setId(image.getId());
                newImage.setImageType(insuranceImageType);
                newImage.setImagePkType(pkCode);
                newImage.setUpdateTime(new Date());
                imageRepository.update(newImage);
                log.info("初审影像件分类成功,更新两种分类,普康分类:{},保司分类:{},影像件信息:{}", pkCode, insuranceImageType, info);
            } catch (Exception e) {
                log.error("影像件分类失败,因为发生异常," + info, e);
                classifyFailCount++;
            }

            //影像件旋转
            try {
                ImageToolResponse response = imageToolFeignClient.rotate(new ClassifyParam(imagePath));
                log.info("影像件方向识别完成," + info + ",结果:" + JSON.toJSONString(response));

                if (!response.success()) {
                    log.error("影像件方向识别失败," + info);
                    continue;
                }

                ImageToolResponse.Content data = response.getData();
                String angle = data.getClassify();
                double prob = data.getProb();
                if (!StringUtils.hasText(angle) || prob < xuanzhuanThreshold.doubleValue()) {
                    log.error("影像件方向识别结果不可用,angle:{}, prob:{}, 阈值:{}", angle, prob, xuanzhuanThreshold);
                    continue;
                }

                if (!"0".equals(angle)) {
                    log.info("影像件需要逆时针旋转角度:" + angle + info);
                } else {
                    log.info("影像件不需要旋转," + info);
                }
            } catch (Exception e) {
                log.error("影像件旋转发生异常," + info, e);
            }
        }
        log.info("=================自动化初审执行完成,claimNumber:{},分类失败数量:{}", claimNumber, classifyFailCount);
    }
}
