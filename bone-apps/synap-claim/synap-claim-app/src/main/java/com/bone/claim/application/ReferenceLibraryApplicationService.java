package com.bone.claim.application;

import com.bone.claim.application.port.out.TenantPort;
import com.bone.claim.common.PageBounds;
import com.bone.claim.domain.model.reference.RefDisease;
import com.bone.claim.domain.model.reference.RefDrug;
import com.bone.claim.domain.model.reference.RefFeeCategory;
import com.bone.claim.domain.model.reference.RefHospital;
import com.bone.claim.domain.repository.RefDiseaseRepository;
import com.bone.claim.domain.repository.RefDrugRepository;
import com.bone.claim.domain.repository.RefFeeCategoryRepository;
import com.bone.claim.domain.repository.RefHospitalRepository;
import com.bone.core.annotation.NoDomainEvent;
import com.bone.core.exception.DomainException;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 基础目录库应用服务（0094）：医院 / 疾病 / 药品 / 费用类别四库的租户级维护。
 *
 * <p>目录是责任配置的<b>编码来源</b>：配置面从库里选编码进名单，理算只认编码串。 因此目录 CRUD 不校验"是否被责任引用"——改目录项不影响已配置责任；
 * 唯一约束是同租户同编码（UK），写入前显式校验以给出可读错误而非 500。
 *
 * <p><b>E-5.4 豁免（@NoDomainEvent）</b>：目录项是<b>主数据</b>而非业务状态机——创建/编辑不发生任何
 * 领域状态迁移（无状态字段流转，也不驱动理赔链路行为变化），属"技术中间态/主数据维护"豁免类； 发布领域事件只会让下游误以为有业务事实发生。
 */
@Service
@NoDomainEvent
@RequiredArgsConstructor
public class ReferenceLibraryApplicationService {

  private final RefHospitalRepository hospitalRepository;
  private final RefDiseaseRepository diseaseRepository;
  private final RefDrugRepository drugRepository;
  private final RefFeeCategoryRepository feeCategoryRepository;
  private final TenantPort tenantPort;

  // ===================== 医院 =====================

  @Transactional(readOnly = true)
  public Page<HospitalView> listHospitals(String keyword, int page, int size) {
    long tenantId = tenantPort.currentTenantId();
    PageBounds bounds = PageBounds.of(page, size);
    List<HospitalView> records =
        hospitalRepository.findPage(tenantId, keyword, bounds.page(), bounds.size()).stream()
            .map(HospitalView::from)
            .toList();
    long total = hospitalRepository.countPage(tenantId, keyword);
    return new Page<>(records, total, bounds.page(), bounds.size());
  }

  @Transactional
  public HospitalView createHospital(HospitalCommand command) {
    long tenantId = tenantPort.currentTenantId();
    assertCodeBlank("医院编码", command.code());
    if (hospitalRepository.findByHospitalCode(tenantId, command.code()) != null) {
      throw new DomainException("医院编码已存在: " + command.code());
    }
    RefHospital hospital =
        RefHospital.of(
            nextId(),
            tenantId,
            command.code(),
            command.name(),
            command.level(),
            command.socialFlag(),
            command.province(),
            command.city());
    hospitalRepository.save(hospital);
    return HospitalView.from(hospital);
  }

  @Transactional
  public HospitalView updateHospital(long id, HospitalCommand command) {
    RefHospital hospital = requireHospital(id);
    hospital.applyEdit(
        command.name(), command.level(), command.socialFlag(), command.province(), command.city());
    hospitalRepository.save(hospital);
    return HospitalView.from(hospital);
  }

  private RefHospital requireHospital(long id) {
    RefHospital hospital = hospitalRepository.findById(id);
    if (hospital == null) {
      throw new DomainException("医院不存在: " + id);
    }
    return hospital;
  }

  // ===================== 疾病 =====================

  @Transactional(readOnly = true)
  public Page<DiseaseView> listDiseases(String keyword, int page, int size) {
    long tenantId = tenantPort.currentTenantId();
    PageBounds bounds = PageBounds.of(page, size);
    List<DiseaseView> records =
        diseaseRepository.findPage(tenantId, keyword, bounds.page(), bounds.size()).stream()
            .map(DiseaseView::from)
            .toList();
    long total = diseaseRepository.countPage(tenantId, keyword);
    return new Page<>(records, total, bounds.page(), bounds.size());
  }

  @Transactional
  public DiseaseView createDisease(DiseaseCommand command) {
    long tenantId = tenantPort.currentTenantId();
    assertCodeBlank("疾病编码", command.code());
    if (diseaseRepository.findByDiseaseCode(tenantId, command.code()) != null) {
      throw new DomainException("疾病编码已存在: " + command.code());
    }
    RefDisease disease =
        RefDisease.of(nextId(), tenantId, command.code(), command.name(), command.icdCategory());
    diseaseRepository.save(disease);
    return DiseaseView.from(disease);
  }

  @Transactional
  public DiseaseView updateDisease(long id, DiseaseCommand command) {
    RefDisease disease = requireDisease(id);
    disease.applyEdit(command.name(), command.icdCategory());
    diseaseRepository.save(disease);
    return DiseaseView.from(disease);
  }

  private RefDisease requireDisease(long id) {
    RefDisease disease = diseaseRepository.findById(id);
    if (disease == null) {
      throw new DomainException("疾病不存在: " + id);
    }
    return disease;
  }

  // ===================== 药品 =====================

  @Transactional(readOnly = true)
  public Page<DrugView> listDrugs(String keyword, int page, int size) {
    long tenantId = tenantPort.currentTenantId();
    PageBounds bounds = PageBounds.of(page, size);
    List<DrugView> records =
        drugRepository.findPage(tenantId, keyword, bounds.page(), bounds.size()).stream()
            .map(DrugView::from)
            .toList();
    long total = drugRepository.countPage(tenantId, keyword);
    return new Page<>(records, total, bounds.page(), bounds.size());
  }

  @Transactional
  public DrugView createDrug(DrugCommand command) {
    long tenantId = tenantPort.currentTenantId();
    assertCodeBlank("药品编码", command.code());
    if (drugRepository.findByDrugCode(tenantId, command.code()) != null) {
      throw new DomainException("药品编码已存在: " + command.code());
    }
    RefDrug drug =
        RefDrug.of(
            nextId(),
            tenantId,
            command.code(),
            command.name(),
            command.socialFlag(),
            command.medicalType());
    drugRepository.save(drug);
    return DrugView.from(drug);
  }

  @Transactional
  public DrugView updateDrug(long id, DrugCommand command) {
    RefDrug drug = requireDrug(id);
    drug.applyEdit(command.name(), command.socialFlag(), command.medicalType());
    drugRepository.save(drug);
    return DrugView.from(drug);
  }

  private RefDrug requireDrug(long id) {
    RefDrug drug = drugRepository.findById(id);
    if (drug == null) {
      throw new DomainException("药品不存在: " + id);
    }
    return drug;
  }

  // ===================== 费用类别 =====================

  @Transactional(readOnly = true)
  public Page<FeeCategoryView> listFeeCategories(String keyword, int page, int size) {
    long tenantId = tenantPort.currentTenantId();
    PageBounds bounds = PageBounds.of(page, size);
    List<FeeCategoryView> records =
        feeCategoryRepository.findPage(tenantId, keyword, bounds.page(), bounds.size()).stream()
            .map(FeeCategoryView::from)
            .toList();
    long total = feeCategoryRepository.countPage(tenantId, keyword);
    return new Page<>(records, total, bounds.page(), bounds.size());
  }

  /** 全量下拉（费用规则编辑用，不分页）。 */
  @Transactional(readOnly = true)
  public List<FeeCategoryView> listAllFeeCategories() {
    long tenantId = tenantPort.currentTenantId();
    return feeCategoryRepository.listAll(tenantId).stream().map(FeeCategoryView::from).toList();
  }

  @Transactional
  public FeeCategoryView createFeeCategory(FeeCategoryCommand command) {
    long tenantId = tenantPort.currentTenantId();
    assertCodeBlank("费用类别编码", command.code());
    if (feeCategoryRepository.findByFeeCode(tenantId, command.code()) != null) {
      throw new DomainException("费用类别编码已存在: " + command.code());
    }
    RefFeeCategory category =
        RefFeeCategory.of(nextId(), tenantId, command.code(), command.name(), command.sortNo());
    feeCategoryRepository.save(category);
    return FeeCategoryView.from(category);
  }

  @Transactional
  public FeeCategoryView updateFeeCategory(long id, FeeCategoryCommand command) {
    RefFeeCategory category = requireFeeCategory(id);
    category.applyEdit(command.name(), command.sortNo());
    feeCategoryRepository.save(category);
    return FeeCategoryView.from(category);
  }

  private RefFeeCategory requireFeeCategory(long id) {
    RefFeeCategory category = feeCategoryRepository.findById(id);
    if (category == null) {
      throw new DomainException("费用类别不存在: " + id);
    }
    return category;
  }

  // ===================== 内部 =====================

  private static void assertCodeBlank(String label, String code) {
    if (code == null || code.isBlank()) {
      throw new DomainException(label + "不能为空");
    }
  }

  private static long nextId() {
    return com.bone.core.util.DistributedIdGenerator.generateLongId();
  }

  /** 通用分页信封（id 一律转 string，与配置面其它视图同一契约）。 */
  public record Page<V>(List<V> records, long total, int page, int size) {}

  // ===================== 命令与视图 =====================

  public record HospitalCommand(
      String code, String name, String level, Integer socialFlag, String province, String city) {}

  public record HospitalView(
      String id,
      String hospitalCode,
      String hospitalName,
      String hospitalLevel,
      Integer socialFlag,
      String province,
      String city) {

    static HospitalView from(RefHospital h) {
      return new HospitalView(
          String.valueOf(h.getId()),
          h.getHospitalCode(),
          h.getHospitalName(),
          h.getHospitalLevel(),
          h.getSocialFlag(),
          h.getProvince(),
          h.getCity());
    }
  }

  public record DiseaseCommand(String code, String name, String icdCategory) {}

  public record DiseaseView(String id, String diseaseCode, String diseaseName, String icdCategory) {

    static DiseaseView from(RefDisease d) {
      return new DiseaseView(
          String.valueOf(d.getId()), d.getDiseaseCode(), d.getDiseaseName(), d.getIcdCategory());
    }
  }

  public record DrugCommand(String code, String name, Integer socialFlag, String medicalType) {}

  public record DrugView(
      String id, String drugCode, String drugName, Integer socialFlag, String medicalType) {

    static DrugView from(RefDrug d) {
      return new DrugView(
          String.valueOf(d.getId()),
          d.getDrugCode(),
          d.getDrugName(),
          d.getSocialFlag(),
          d.getMedicalType());
    }
  }

  public record FeeCategoryCommand(String code, String name, Integer sortNo) {}

  public record FeeCategoryView(String id, String feeCode, String feeName, Integer sortNo) {

    static FeeCategoryView from(RefFeeCategory c) {
      return new FeeCategoryView(
          String.valueOf(c.getId()), c.getFeeCode(), c.getFeeName(), c.getSortNo());
    }
  }
}
