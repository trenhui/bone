package com.bone.tpa.sdk.claim.model;

import com.bone.metadata.sdk.domain.annotation.Table;
import lombok.*;


/**
 * ss_claim_stakeholder DO
 *
 * @author 0
 */
@Table("ss_claim_stakeholder")
@EqualsAndHashCode(callSuper = true)
@ToString(callSuper = true)
@NoArgsConstructor
@AllArgsConstructor
public class ClaimStakeholder extends ExtraStoreBase<Long> {

    /**
     * 关联赔案id
     */
    private Long relatedId;
    /**
     * 相关人类型
     */
    private String personType;
    /**
     * 姓名
     */
    private String name;
    /**
     * 性别
     */
    private String gender;
    /**
     * 出生年月
     */
    private String birthday;
    /**
     * 证件类型
     */
    private String identityType;

    /**
     * 证件类型中文
     */
    private String identityTypeCn;
    /**
     * 证件号
     */
    private String identityNo;
    /**
     * 证件有效期
     */
    private String identityDatePeriod;
    /**
     * 职业
     */
    private String occupation;

    /**
     * 职业中文描述
     */
    private String occupationCn;
    /**
     * 国籍
     */
    private String nationality;
    /**
     * 国籍中文
     */
    private String nationalityCn;
    /**
     * 联系方式
     */
    private String phone;


    /**
     * 区域
     * {
     *  "code":",["1","2","3"]
     * "desc":["浙江省","杭州市","西湖区"]
     * }
     */
    private String contactRegion;

    /**
     * 联系地址
     */
    private String contactAddress;
    /**
     * 与出险人的关系
     */
    private String relationToOutInsure;

    /**
     * 与出险人的关系中文
     */
    private String relationToOutInsureCn;


    /**
     * 与主被保人的关系
     */
    private String relationToMainInsure;

    /**
     * 与主被保人的关系中文
     */
    private String relationToMainInsureCn;
    /**
     * 与受益人的关系
     */
    private String relationToBenefit;

    /**
     * 与受益人的关系中文
     */
    private String relationToBenefitCn;
    /**
     * 受益比例
     */
    private String benefitPercentage;
    /**
     * 单位名称
     */
    private String businessName;
    /**
     * 单位证件描述
     */
    private String businessIdentityDisc;
    /**
     * 单位证件类型code
     */
    private String businessIdentityType;
    /**
     * 单位证件类型
     */
    private String businessIdentityTypeCn;
    /**
     * 单位证件号码
     */
    private String businessIdentityNo;
    /**
     * 单位证件有效期
     */
    private String businessIdentityDatePeriod;
    /**
     * 单位经营场所
     */
    private String businessPlace;
    /**
     * 单位经营范围
     */
    private String businessRange;
    /**
     * 转账方式
     * 对公，对私
     */
    private String transferMethodType;
    /**
     * 给付方式
     */
    private String paymentMethodType;

    /**
     * 给付方式中文
     */
    private String paymentMethodTypeCn;
    /**
     * 银行账号
     */
    private String accountNo;
    /**
     * 开户行code
     */
    private String bankCode;
    /**
     * 开户行中文
     */
    private String bankCodeCn;
    /**
     * 开户行分行code
     */
    private String branchCode;

    /**
     * 开户行分行中文
     */
    private String branchCodeCn;
    /**
     * 银行所属省市
     * {
     *  "code":",["1","2"]
     * "desc":["浙江省","杭州市]
     * }
     */
    private String bankRegion;

    /**
     * 开户行地址
     */
    private String bankAddress;

    /**
     * 对应tpa 的id
     */
    private Long tpaId;

    // Getters and Setters
    public Long getRelatedId() {
        return relatedId;
    }

    public void setRelatedId(Long relatedId) {
        this.relatedId = relatedId;
    }

    public String getPersonType() {
        return personType;
    }

    public void setPersonType(String personType) {
        this.personType = personType;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getGender() {
        return gender;
    }

    public void setGender(String gender) {
        this.gender = gender;
    }

    public String getBirthday() {
        return birthday;
    }

    public void setBirthday(String birthday) {
        this.birthday = birthday;
    }

    public String getIdentityType() {
        return identityType;
    }

    public void setIdentityType(String identityType) {
        this.identityType = identityType;
    }

    public String getIdentityTypeCn() {
        return identityTypeCn;
    }

    public void setIdentityTypeCn(String identityTypeCn) {
        this.identityTypeCn = identityTypeCn;
    }

    public String getIdentityNo() {
        return identityNo;
    }

    public void setIdentityNo(String identityNo) {
        this.identityNo = identityNo;
    }

    public String getIdentityDatePeriod() {
        return identityDatePeriod;
    }

    public void setIdentityDatePeriod(String identityDatePeriod) {
        this.identityDatePeriod = identityDatePeriod;
    }

    public String getOccupation() {
        return occupation;
    }

    public void setOccupation(String occupation) {
        this.occupation = occupation;
    }

    public String getOccupationCn() {
        return occupationCn;
    }

    public void setOccupationCn(String occupationCn) {
        this.occupationCn = occupationCn;
    }

    public String getNationality() {
        return nationality;
    }

    public void setNationality(String nationality) {
        this.nationality = nationality;
    }

    public String getNationalityCn() {
        return nationalityCn;
    }

    public void setNationalityCn(String nationalityCn) {
        this.nationalityCn = nationalityCn;
    }

    public String getPhone() {
        return phone;
    }

    public void setPhone(String phone) {
        this.phone = phone;
    }

    public String getContactRegion() {
        return contactRegion;
    }

    public void setContactRegion(String contactRegion) {
        this.contactRegion = contactRegion;
    }

    public String getContactAddress() {
        return contactAddress;
    }

    public void setContactAddress(String contactAddress) {
        this.contactAddress = contactAddress;
    }

    public String getRelationToOutInsure() {
        return relationToOutInsure;
    }

    public void setRelationToOutInsure(String relationToOutInsure) {
        this.relationToOutInsure = relationToOutInsure;
    }

    public String getRelationToOutInsureCn() {
        return relationToOutInsureCn;
    }

    public void setRelationToOutInsureCn(String relationToOutInsureCn) {
        this.relationToOutInsureCn = relationToOutInsureCn;
    }

    public String getRelationToMainInsure() {
        return relationToMainInsure;
    }

    public void setRelationToMainInsure(String relationToMainInsure) {
        this.relationToMainInsure = relationToMainInsure;
    }

    public String getRelationToMainInsureCn() {
        return relationToMainInsureCn;
    }

    public void setRelationToMainInsureCn(String relationToMainInsureCn) {
        this.relationToMainInsureCn = relationToMainInsureCn;
    }

    public String getRelationToBenefit() {
        return relationToBenefit;
    }

    public void setRelationToBenefit(String relationToBenefit) {
        this.relationToBenefit = relationToBenefit;
    }

    public String getRelationToBenefitCn() {
        return relationToBenefitCn;
    }

    public void setRelationToBenefitCn(String relationToBenefitCn) {
        this.relationToBenefitCn = relationToBenefitCn;
    }

    public String getBenefitPercentage() {
        return benefitPercentage;
    }

    public void setBenefitPercentage(String benefitPercentage) {
        this.benefitPercentage = benefitPercentage;
    }

    public String getBusinessName() {
        return businessName;
    }

    public void setBusinessName(String businessName) {
        this.businessName = businessName;
    }

    public String getBusinessIdentityDisc() {
        return businessIdentityDisc;
    }

    public void setBusinessIdentityDisc(String businessIdentityDisc) {
        this.businessIdentityDisc = businessIdentityDisc;
    }

    public String getBusinessIdentityType() {
        return businessIdentityType;
    }

    public void setBusinessIdentityType(String businessIdentityType) {
        this.businessIdentityType = businessIdentityType;
    }

    public String getBusinessIdentityTypeCn() {
        return businessIdentityTypeCn;
    }

    public void setBusinessIdentityTypeCn(String businessIdentityTypeCn) {
        this.businessIdentityTypeCn = businessIdentityTypeCn;
    }

    public String getBusinessIdentityNo() {
        return businessIdentityNo;
    }

    public void setBusinessIdentityNo(String businessIdentityNo) {
        this.businessIdentityNo = businessIdentityNo;
    }

    public String getBusinessIdentityDatePeriod() {
        return businessIdentityDatePeriod;
    }

    public void setBusinessIdentityDatePeriod(String businessIdentityDatePeriod) {
        this.businessIdentityDatePeriod = businessIdentityDatePeriod;
    }

    public String getBusinessPlace() {
        return businessPlace;
    }

    public void setBusinessPlace(String businessPlace) {
        this.businessPlace = businessPlace;
    }

    public String getBusinessRange() {
        return businessRange;
    }

    public void setBusinessRange(String businessRange) {
        this.businessRange = businessRange;
    }

    public String getTransferMethodType() {
        return transferMethodType;
    }

    public void setTransferMethodType(String transferMethodType) {
        this.transferMethodType = transferMethodType;
    }

    public String getPaymentMethodType() {
        return paymentMethodType;
    }

    public void setPaymentMethodType(String paymentMethodType) {
        this.paymentMethodType = paymentMethodType;
    }

    public String getPaymentMethodTypeCn() {
        return paymentMethodTypeCn;
    }

    public void setPaymentMethodTypeCn(String paymentMethodTypeCn) {
        this.paymentMethodTypeCn = paymentMethodTypeCn;
    }

    public String getAccountNo() {
        return accountNo;
    }

    public void setAccountNo(String accountNo) {
        this.accountNo = accountNo;
    }

    public String getBankCode() {
        return bankCode;
    }

    public void setBankCode(String bankCode) {
        this.bankCode = bankCode;
    }

    public String getBankCodeCn() {
        return bankCodeCn;
    }

    public void setBankCodeCn(String bankCodeCn) {
        this.bankCodeCn = bankCodeCn;
    }

    public String getBranchCode() {
        return branchCode;
    }

    public void setBranchCode(String branchCode) {
        this.branchCode = branchCode;
    }

    public String getBranchCodeCn() {
        return branchCodeCn;
    }

    public void setBranchCodeCn(String branchCodeCn) {
        this.branchCodeCn = branchCodeCn;
    }

    public String getBankRegion() {
        return bankRegion;
    }

    public void setBankRegion(String bankRegion) {
        this.bankRegion = bankRegion;
    }

    public String getBankAddress() {
        return bankAddress;
    }

    public void setBankAddress(String bankAddress) {
        this.bankAddress = bankAddress;
    }

    public Long getTpaId() {
        return tpaId;
    }

    public void setTpaId(Long tpaId) {
        this.tpaId = tpaId;
    }
}
