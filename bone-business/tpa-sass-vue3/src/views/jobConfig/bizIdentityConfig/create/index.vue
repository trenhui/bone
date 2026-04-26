<script setup>
import BizIdentityAPI from "@/api/bizIdentity";
import PageAPI from "@/api/page";
import {
  BizIdentityTypeEnum,
  BizIdentityTypeOptions,
} from "@/enums/BizIdentityTypeEnum";

defineOptions({
  name: "CreateBizIdentity",
});

const router = useRouter();
const confirmLoading = ref(false);
const form = ref({
  bizIdentityType: "",
  insuranceCompany: {
    code: "",
    name: "",
  },
  insuranceCompanyBranch: {
    code: "",
    name: "",
  },
  insuredCompany: {
    code: "",
    name: "",
  },
  policyNo: {
    code: "",
    name: "",
  },
});

const insuranceCompanyList = ref([]);
const insuranceCompanyBranchList = ref([]);
const insuredCompanyList = ref([]);
const policyNoList = ref([]);

const initList = async (type, parentCode, parentName, nameLike) => {
  try {
    const res = await BizIdentityAPI.getBizIdentityOptions(
      type,
      parentCode,
      parentName,
      nameLike
    );
    return res;
  } catch (error) {
    console.log(error);
  }
};

// 初始化业务主体列表
const initBizIdentityList = async () => {
  const type = form.value.bizIdentityType;
  if (!type) return;

  // 重置相关数据
  form.value.insuranceCompany.code = "";
  form.value.insuranceCompany.name = "";
  form.value.insuranceCompanyBranch.code = "";
  form.value.insuranceCompanyBranch.name = "";
  form.value.insuredCompany.code = "";
  form.value.insuredCompany.name = "";
  form.value.policyNo.code = "";
  form.value.policyNo.name = "";
  insuranceCompanyBranchList.value = [];
  policyNoList.value = [];

  try {
    switch (type) {
      case BizIdentityTypeEnum.INSURANCE_COMPANY:
        insuranceCompanyList.value = await initList(type, null, null, null);
        break;
      case BizIdentityTypeEnum.INSURANCE_COMPANY_BRANCH:
        insuranceCompanyList.value = await initList(
          BizIdentityTypeEnum.INSURANCE_COMPANY,
          null,
          null,
          null
        );
        break;
      case BizIdentityTypeEnum.INSURED_COMPANY:
        insuredCompanyList.value = await initList(type, null, null, null);
        break;
      case BizIdentityTypeEnum.INSURANCE_COMPANY_BRANCH_WITH_POLICY_NUMBER:
        insuranceCompanyList.value = await initList(
          BizIdentityTypeEnum.INSURANCE_COMPANY,
          null,
          null,
          null
        );
        break;
    }
  } catch (error) {
    console.error("获取业务主体列表失败:", error);
  }
};

const getBizIdentityName = () => {
  switch (form.value.bizIdentityType) {
    case BizIdentityTypeEnum.INSURANCE_COMPANY:
      return form.value.insuranceCompany.name;
    case BizIdentityTypeEnum.INSURANCE_COMPANY_BRANCH:
      return (
        form.value.insuranceCompany.name +
        "-" +
        form.value.insuranceCompanyBranch.name
      );
    case BizIdentityTypeEnum.INSURED_COMPANY:
      return form.value.insuredCompany.name;
    case BizIdentityTypeEnum.INSURANCE_COMPANY_BRANCH_WITH_POLICY_NUMBER:
      return (
        form.value.insuranceCompany.name +
        "-" +
        form.value.insuranceCompanyBranch.name +
        "-" +
        form.value.policyNo.name
      );
    default:
      return "";
  }
};

// 监听保险公司变化
watch(
  () => form.value.insuranceCompany.code,
  async (newVal) => {
    if (!newVal) {
      insuranceCompanyBranchList.value = [];
      return;
    }

    const insuranceCompany = insuranceCompanyList.value.find(
      (item) => item.code === newVal
    );

    form.value.insuranceCompany.name = insuranceCompany.name;

    form.value.insuranceCompanyBranch.code = "";
    form.value.insuranceCompanyBranch.name = "";
    policyNoList.value = [];

    if (
      form.value.bizIdentityType ===
        BizIdentityTypeEnum.INSURANCE_COMPANY_BRANCH ||
      form.value.bizIdentityType ===
        BizIdentityTypeEnum.INSURANCE_COMPANY_BRANCH_WITH_POLICY_NUMBER
    ) {
      try {
        insuranceCompanyBranchList.value = await initList(
          BizIdentityTypeEnum.INSURANCE_COMPANY_BRANCH,
          newVal,
          form.value.insuranceCompany.name,
          null
        );
      } catch (error) {
        console.error("获取保险分公司列表失败:", error);
      }
    }
  }
);

// 监听保险分公司变化
watch(
  () => form.value.insuranceCompanyBranch.code,
  async (newVal) => {
    if (!newVal) {
      policyNoList.value = [];
      return;
    }

    const insuranceCompanyBranch = insuranceCompanyBranchList.value.find(
      (item) => item.code === newVal
    );

    form.value.insuranceCompanyBranch.name = insuranceCompanyBranch.name;

    form.value.policyNo.code = "";
    form.value.policyNo.name = "";

    if (
      form.value.bizIdentityType ===
      BizIdentityTypeEnum.INSURANCE_COMPANY_BRANCH_WITH_POLICY_NUMBER
    ) {
      try {
        policyNoList.value = await initList(
          BizIdentityTypeEnum.INSURANCE_COMPANY_BRANCH_WITH_POLICY_NUMBER,
          newVal,
          form.value.insuranceCompanyBranch.name,
          null
        );
      } catch (error) {
        console.error("获取保单号列表失败:", error);
      }
    }
  }
);

const handleConfirm = async () => {
  if (!form.value.bizIdentityType) {
    return ElMessage.error("请选择业务主体类型");
  }

  // 构建请求参数
  const params = {
    bizType: form.value.bizIdentityType,
  };

  // 根据不同的业务主体类型设置参数和获取业务主体信息
  switch (form.value.bizIdentityType) {
    case BizIdentityTypeEnum.INSURANCE_COMPANY:
      if (!form.value.insuranceCompany.code) {
        return ElMessage.error("请选择保险公司");
      }
      params.insuranceCompanyCode = form.value.insuranceCompany.code;
      params.insuranceCompanyName = form.value.insuranceCompany.name;
      break;
    case BizIdentityTypeEnum.INSURANCE_COMPANY_BRANCH:
      if (!form.value.insuranceCompanyBranch.code) {
        return ElMessage.error("请选择保险分公司");
      }
      params.insuranceCompanyCode = form.value.insuranceCompany.code;
      params.insuranceCompanyName = form.value.insuranceCompany.name;
      params.insuranceCompanyBranchCode =
        form.value.insuranceCompanyBranch.code;
      params.insuranceCompanyBranchName =
        form.value.insuranceCompanyBranch.name;
      break;
    case BizIdentityTypeEnum.INSURED_COMPANY:
      if (!form.value.insuredCompany.code) {
        return ElMessage.error("请选择投保公司");
      }
      params.insuredCompanyCode = form.value.insuredCompany.code;
      params.insuredCompanyName = form.value.insuredCompany.name;
      break;
    case BizIdentityTypeEnum.INSURANCE_COMPANY_BRANCH_WITH_POLICY_NUMBER:
      if (!form.value.policyNo.code) {
        return ElMessage.error("请选择保单号");
      }
      params.insuranceCompanyCode = form.value.insuranceCompany.code;
      params.insuranceCompanyName = form.value.insuranceCompany.name;
      params.insuranceCompanyBranchCode =
        form.value.insuranceCompanyBranch.code;
      params.insuranceCompanyBranchName =
        form.value.insuranceCompanyBranch.name;
      params.policyNo = form.value.policyNo.code;
      break;
  }

  confirmLoading.value = true;
  try {
    const res = await PageAPI.createExclusivePage(params);
    router.push({
      name: "BizIdentityConfig",
      query: {
        code: res.bizCode,
        type: res.bizType,
        name: res.bizName,
      },
    });
  } catch (error) {
    console.log(error);
  } finally {
    confirmLoading.value = false;
  }
};

const handleCancel = () => {
  router.back();
};

// 监听业务主体类型变化
watch(
  () => form.value.bizIdentityType,
  () => {
    initBizIdentityList();
  },
  { immediate: true }
);

const handlePolicyNoChange = (newVal) => {
  const policyNo = policyNoList.value.find((item) => item.code === newVal);
  form.value.policyNo.name = policyNo.name;
};

const handleInsuredCompanyChange = (newVal) => {
  const insuredCompany = insuredCompanyList.value.find(
    (item) => item.code === newVal
  );
  form.value.insuredCompany.name = insuredCompany.name;
};

const searchInsuranceCompany = async (query) => {
  insuranceCompanyList.value = await initList(
    BizIdentityTypeEnum.INSURANCE_COMPANY,
    null,
    null,
    query
  );
};

const searchInsuranceCompanyBranch = async (query) => {
  insuranceCompanyBranchList.value = await initList(
    BizIdentityTypeEnum.INSURANCE_COMPANY_BRANCH,
    form.value.insuranceCompany.code,
    form.value.insuranceCompany.name,
    query
  );
};

const searchInsuredCompany = async (query) => {
  insuredCompanyList.value = await initList(
    BizIdentityTypeEnum.INSURED_COMPANY,
    null,
    null,
    query
  );
};

const searchPolicyNo = async (query) => {
  policyNoList.value = await initList(
    BizIdentityTypeEnum.INSURANCE_COMPANY_BRANCH_WITH_POLICY_NUMBER,
    form.value.insuranceCompanyBranch.code,
    form.value.insuranceCompanyBranch.name,
    query
  );
};
</script>

<template>
  <div class="app-container">
    <div class="search-container">
      <div class="mb-5 ml-2 font-bold text-[#303133]">创建主体专属页面</div>
    </div>
    <el-card class="p-2" shadow="never">
      <div class="flex items-center mb-4 text-size-sm">
        <div class="underline-zinc mr-2">基本信息</div>
        <el-tooltip effect="dark" content="主体信息" placement="top">
          <svg-icon icon-class="prompt" size="1em" />
        </el-tooltip>
      </div>
      <div class="flex justify-between">
        <el-form label-width="auto">
          <el-form-item label="业务主体类型">
            <el-select
              v-model="form.bizIdentityType"
              placeholder="请选择业务主体类型"
              style="width: 300px"
            >
              <el-option
                v-for="item in BizIdentityTypeOptions"
                :key="item.value"
                :label="item.label"
                :value="item.value"
              />
            </el-select>
          </el-form-item>
          <el-form-item
            label="保险公司"
            v-if="
              form.bizIdentityType &&
              form.bizIdentityType !== BizIdentityTypeEnum.INSURED_COMPANY
            "
          >
            <el-select
              v-model="form.insuranceCompany.code"
              filterable
              remote
              reserve-keyword
              :remote-method="searchInsuranceCompany"
              remote-show-suffix
              placeholder="请选择保险公司"
              style="width: 300px"
            >
              <el-option
                v-for="item in insuranceCompanyList"
                :key="item.code"
                :label="item.name"
                :value="item.code"
              />
            </el-select>
          </el-form-item>
          <el-form-item
            label="保险分公司"
            v-if="
              form.bizIdentityType ===
                BizIdentityTypeEnum.INSURANCE_COMPANY_BRANCH ||
              form.bizIdentityType ===
                BizIdentityTypeEnum.INSURANCE_COMPANY_BRANCH_WITH_POLICY_NUMBER
            "
          >
            <el-select
              v-model="form.insuranceCompanyBranch.code"
              filterable
              remote
              reserve-keyword
              remote-show-suffix
              :remote-method="searchInsuranceCompanyBranch"
              placeholder="请选择保险分公司"
              style="width: 300px"
            >
              <el-option
                v-for="item in insuranceCompanyBranchList"
                :key="item.code"
                :label="item.name"
                :value="item.code"
              />
            </el-select>
          </el-form-item>
          <el-form-item
            label="投保公司"
            v-if="form.bizIdentityType === BizIdentityTypeEnum.INSURED_COMPANY"
          >
            <el-select
              v-model="form.insuredCompany.code"
              filterable
              remote
              reserve-keyword
              remote-show-suffix
              :remote-method="searchInsuredCompany"
              placeholder="请选择投保公司"
              style="width: 300px"
              @change="handleInsuredCompanyChange"
            >
              <el-option
                v-for="item in insuredCompanyList"
                :key="item.code"
                :label="item.name"
                :value="item.code"
              />
            </el-select>
          </el-form-item>
          <el-form-item
            label="保单号"
            v-if="
              form.bizIdentityType ===
              BizIdentityTypeEnum.INSURANCE_COMPANY_BRANCH_WITH_POLICY_NUMBER
            "
          >
            <el-select
              v-model="form.policyNo.code"
              filterable
              remote
              reserve-keyword
              remote-show-suffix
              :remote-method="searchPolicyNo"
              placeholder="请选择保单号"
              style="width: 300px"
              @change="handlePolicyNoChange"
            >
              <el-option
                v-for="item in policyNoList"
                :key="item.code"
                :label="item.name"
                :value="item.code"
              />
            </el-select>
          </el-form-item>
        </el-form>
        <div>
          <el-button link type="primary" disabled>找不到主体?去配置</el-button>
        </div>
      </div>
      <div class="flex justify-center items-center mt-30 mb-10">
        <el-button type="default" link @click="handleCancel">取消</el-button>
        <el-button
          type="primary"
          @click="handleConfirm"
          :loading="confirmLoading"
        >
          保存,下一步
        </el-button>
      </div>
    </el-card>
  </div>
</template>

<style lang="scss" scoped></style>
