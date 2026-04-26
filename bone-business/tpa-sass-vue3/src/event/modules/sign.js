import UploadPersonListDialog from "@/views/jobManage/groupSign/create/UploadPersonListDialog.vue";
import ImgDocCheckDialog from "@/components/RenderEngine/base/Upload/ImgDocCheckDialog.vue";
import UploadRecordDialog from "@/views/jobManage/groupSign/detail/UploadRecordDialog.vue";
import SignAPI from "@/api/sign";
import UploadAPI, { UPLOAD_CODE } from "@/api/upload";
import { isEmpty } from "lodash-es";
import EventBus from "@/utils/eventBus";

export function handleEventSignDetail(dataGetter) {
  const row = dataGetter("tableRow");
  const router = dataGetter("router");

  if (!row || !router) {
    ElMessage.error("事件所需数据未获取到，请检查事件配置");
    return;
  }

  router.push({
    name: "GroupInsuranceSignDetail",
    query: {
      id: row.main.id,
      tenantId: row.tenantId,
      bizIdentityCode: row.bizIdentityCode,
    },
  });
}

export function handleToUploadPersonList(dataGetter) {
  const modalManager = dataGetter("modalManager");
  const row = dataGetter("tableRow");
  const refreshTable = dataGetter("refreshTable");
  if (!row || !refreshTable) {
    ElMessage.error("事件所需数据未获取到，请检查事件配置");
    return;
  }

  modalManager.showModal(
    UploadPersonListDialog,
    {
      id: row.main.id,
      batchNo: row.main.batchNo,
      tenantId: row.main.tenantId,
    },
    (isUploaded = false) => {
      if (isUploaded) {
        refreshTable && refreshTable();
      }
    }
  );
}

export async function handleNewSignSave(dataGetter) {
  const validateManager = dataGetter("validateManager");
  const modalManager = dataGetter("modalManager");
  const data = dataGetter("data");

  if (!validateManager || !data.value) {
    ElMessage.error("事件所需数据未获取到，请检查事件配置");
    return;
  }

  if (isEmpty(data.value)) {
    ElMessage.error("请填写完整信息");
    return;
  }

  try {
    // 表单验证，包括业务字段规则、字段规则、表格间提交规则
    let allRulesPassed = await validateManager.validate();

    if (allRulesPassed) {
      const newBatchInfo = await SignAPI.createNewBatchSign(data.value);

      modalManager.showModal(UploadPersonListDialog, {
        id: newBatchInfo.id,
        batchNo: newBatchInfo.batchNo,
        tenantId: newBatchInfo.tenantId,
      });
    }
  } catch (error) {
    console.error(error);
  }
}

export async function handleToUploadImgDoc(dataGetter) {
  const modalManager = dataGetter("modalManager");
  const id = dataGetter("id");
  const tenantId = dataGetter("tenantId");

  if (!id || !tenantId) {
    ElMessage.error("事件所需数据未获取到，请检查事件配置");
    return;
  }

  const uploadComponent = await UploadAPI.getUploadImgDocByCode(
    UPLOAD_CODE.SIGN_DETAIL_UPLOAD_IMAGE
  );

  if (!uploadComponent) {
    ElMessage.error("获取上传组件失败，请检查事件配置");
    return;
  }

  modalManager.showModal(ImgDocCheckDialog, {
    uploadComponent: uploadComponent,
    uploadData: {
      relatedId: id,
      configId: uploadComponent?.id,
      tenantId: tenantId,
    },
  });
}

export function handleToImgDocDetail(dataGetter) {
  const router = dataGetter("router");
  const row = dataGetter("tableRow");

  if (!row || !router) {
    ElMessage.error("事件所需数据未获取到，请检查事件配置");
    return;
  }

  console.log(row);

  const route = router.resolve({
    name: "ClaimImageDetail",
    query: {
      claimId: row.main.id,
      tenantId: row.tenantId,
      bizIdentityCode: row.main.bizIdentityCode,
    },
  });

  window.open(route.href, "_blank");
}

export function handleConfirmSign(dataGetter) {
  const id = dataGetter("id");
  const refreshTable = dataGetter("refreshTable");

  console.log(id, refreshTable);

  if (!id || !refreshTable) {
    ElMessage.error("事件所需数据未获取到，请检查事件配置");
    return;
  }

  ElMessageBox.confirm(
    "请确认本批次下所有赔案都已完成影像上传。",
    "确认完成签收",
    {
      confirmButtonText: "确认",
      cancelButtonText: "取消",
      type: "warning",
    }
  )
    .then(() => {
      SignAPI.confirmSign(id)
        .then((res) => {
          ElMessage.success("确认成功");
          EventBus.emit("event_sign_confirm");
          refreshTable();
        })
        .catch((error) => {
          console.error(error);
        });
    })
    .catch(() => {});
}

export function handleToUploadRecord(dataGetter) {
  const modalManager = dataGetter("modalManager");
  const id = dataGetter("id");
  const tenantId = dataGetter("tenantId");

  if (!id || !tenantId) {
    ElMessage.error("事件所需数据未获取到，请检查事件配置");
    return;
  }

  modalManager.showModal(UploadRecordDialog, {
    id,
    tenantId,
  });
}
