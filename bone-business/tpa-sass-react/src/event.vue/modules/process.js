import DataAPI from "@/api/data";
import { DisplayModeEnum } from "@/enums";
import { isEmpty } from "lodash-es";
import {
  getRuleVerifyTypeShortLabel,
  RuleVerifyTypeEnum,
} from "@/enums/rule/RuleVerifyTypeEnum";

/** 处理暂存事件 */
export const handleStaging = (dataGetter) => {
  const claimId = dataGetter("claimId");
  const data = dataGetter("data");

  if (!claimId || isEmpty(data.value)) {
    ElMessage.error("事件所需数据未获取到，请检查事件配置");
    return;
  }

  dataGetter("setLoading")("staging");
  localStorage.setItem(`data-${claimId}`, JSON.stringify(data.value));
  ElMessage.success("暂存成功");
  dataGetter("removeLoading")("staging");
};

/** 处理保存事件 */
export const handleSave = async (dataGetter) => {
  const claimId = dataGetter("claimId");
  const validateManager = dataGetter("validateManager");
  const data = dataGetter("data");
  const displayMode = dataGetter("displayMode");

  if (!validateManager) {
    ElMessage.error("事件所需数据未获取到，请检查事件配置");
    return;
  }

  if (isEmpty(data.value)) {
    ElMessage.warning("表单数据为空, 请检查表单数据");
    return;
  }

  if (!claimId && displayMode.value !== DisplayModeEnum.PREVIEW) {
    ElMessage.error("赔案不存在");
    return;
  }

  dataGetter("setLoading")("save");

  try {
    // 表单验证，包括业务字段规则、字段规则、表格间提交规则
    let allRulesPassed = await validateManager.validate();

    if (displayMode.value === DisplayModeEnum.PREVIEW) {
      if (allRulesPassed.success) {
        ElMessage.success("当前为预览状态，校验成功，不进行保存");
        return;
      }
    }

    if (allRulesPassed.success) {
      data.value["action"] = "DRAFT";
      await DataAPI.saveForm(data.value);
      localStorage.removeItem(`data-${claimId}`);
      ElMessage.success("保存成功");
    } else {
      const isStopSave = allRulesPassed.messages.some((message) =>
        message.includes(
          `【${getRuleVerifyTypeShortLabel(RuleVerifyTypeEnum.STRONG_VERIFY)}】`
        )
      );
      ElMessageBox.confirm(
        "错误信息如下:<br>" + allRulesPassed.messages.join("<br>"),
        "校验失败",
        {
          confirmButtonText: "忽略并继续提交",
          cancelButtonText: "取消",
          dangerouslyUseHTMLString: true,
          draggable: true,
          closeOnClickModal: false,
          closeOnPressEscape: false,
          showClose: false,
          showCancelButton: true,
          showConfirmButton: !isStopSave,
          lockScroll: false,
        }
      )
        .then(async () => {
          if (displayMode.value === DisplayModeEnum.PREVIEW) {
            ElMessage.success("当前为预览状态，校验成功，不进行保存");
            return;
          }

          data.value["action"] = "DRAFT";
          await DataAPI.saveForm(data.value);
          localStorage.removeItem(`data-${claimId}`);
          ElMessage.success("保存成功");
        })
        .catch(() => {});
    }
  } catch (error) {
    console.error(error);
  } finally {
    dataGetter("removeLoading")("save");
  }
};

/** 处理提交事件 */
export const handleSubmit = async (dataGetter) => {
  const claimId = dataGetter("claimId");
  const validateManager = dataGetter("validateManager");
  const data = dataGetter("data");
  const displayMode = dataGetter("displayMode");

  if (!validateManager) {
    ElMessage.error("事件所需数据未获取到，请检查事件配置");
    return;
  }

  if (isEmpty(data.value)) {
    ElMessage.warning("表单数据为空, 请检查表单数据");
    return;
  }

  if (!claimId && displayMode.value !== DisplayModeEnum.PREVIEW) {
    ElMessage.error("赔案不存在");
    return;
  }

  dataGetter("setLoading")("submit");

  try {
    // 表单验证，包括业务字段规则、字段规则、表格间提交规则
    let allRulesPassed = await validateManager.validate();

    if (displayMode.value === DisplayModeEnum.PREVIEW) {
      if (allRulesPassed.success) {
        ElMessage.success("当前为预览状态，校验成功，不进行提交");
        return;
      }
    }

    if (allRulesPassed.success) {
      data.value["action"] = "COMPLETE";
      await DataAPI.saveForm(data.value);
      localStorage.removeItem(`data-${claimId}`);
      ElMessage.success("提交成功, 正在关闭页面中, 若未关闭请手动关闭");

      //关闭该标签页
      setTimeout(() => {
        window.close();
      }, 2000);
    } else {
      const isStopSave = allRulesPassed.messages.some((message) =>
        message.includes(
          `【${getRuleVerifyTypeShortLabel(RuleVerifyTypeEnum.STRONG_VERIFY)}】`
        )
      );
      ElMessageBox.confirm(
        "错误信息如下:<br>" + allRulesPassed.messages.join("<br>"),
        "校验失败",
        {
          confirmButtonText: "忽略并继续提交",
          cancelButtonText: "取消",
          dangerouslyUseHTMLString: true,
          draggable: true,
          closeOnClickModal: false,
          closeOnPressEscape: false,
          showClose: false,
          showCancelButton: true,
          showConfirmButton: !isStopSave,
          lockScroll: false,
        }
      )
        .then(async () => {
          if (displayMode.value === DisplayModeEnum.PREVIEW) {
            ElMessage.success("当前为预览状态，校验成功，不进行保存");
            return;
          }

          data.value["action"] = "COMPLETE";
          await DataAPI.saveForm(data.value);
          localStorage.removeItem(`data-${claimId}`);
          ElMessage.success("提交成功, 正在关闭页面中, 若未关闭请手动关闭");

          //关闭该标签页
          setTimeout(() => {
            window.close();
          }, 2000);
        })
        .catch(() => {});
    }
  } catch (error) {
    console.error(error);
  } finally {
    dataGetter("removeLoading")("submit");
  }
};

/** 处理编辑事件 */
export const handleEdit = (dataGetter) => {
  const updateSchema = dataGetter("updateSchema");

  if (updateSchema) {
    updateSchema(DisplayModeEnum.EDIT);
  }
};

/** 处理退回事件 */
export const handleReturn = (params) => {
  console.log("处理退回事件", params);
  // 在这里添加处理退回事件的逻辑
};
