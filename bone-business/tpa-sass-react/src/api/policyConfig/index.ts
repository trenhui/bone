import request from "@/utils/request";

const BASE_URL = "/data-api";

class PolicyConfigAPI {
  static toggleConfig(policyNo: string) {
    return request({
      url: `${BASE_URL}/tpa/adjust/plan/toggleconfig`,
      method: "get",
      params: { policyNo },
    });
  }
}

export default PolicyConfigAPI;
