import request from "@/utils/request";

const SIGN_BASE_URL = "/data-api/tpa/sign";

class SignAPI {
  static createNewBatchSign(data: any) {
    return request({
      url: SIGN_BASE_URL + "/create",
      method: "post",
      data,
    });
  }
  static uploadSignFile(id: string) {
    return request({
      url: SIGN_BASE_URL + "/upload",
      method: "get",
      params: { id },
    });
  }
  static singDetail(id: string) {
    return request({
      url: SIGN_BASE_URL + "/detail",
      method: "get",
      params: { id },
    });
  }
  static confirmSign(id: string) {
    return request({
      url: SIGN_BASE_URL + "/confirm",
      method: "get",
      params: { id },
    });
  }

  static passSign(id: string) {
    return request({
      url: SIGN_BASE_URL + "/pass",
      method: "get",
      params: { id },
    });
  }
}

export default SignAPI;
