import request from "@/utils/request";

class RedirectAPI {
  static getSsoAuthUrl(params: any) {
    return request({
      url: "/sso/getSsoAuthUrl",
      method: "get",
      params,
    });
  }
}

export default RedirectAPI;
