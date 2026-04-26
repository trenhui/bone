import request from "@/utils/request";
import { PageCodeEnum } from "@/enums/PageCodeEnum";
import { EventTypeEnum } from "@/enums/event/EventTypeEnum";

const EVENT_BASE_URL = "/page-api/cfg/event";

class EventAPI {
  static create(data: any) {
    return request({
      url: `${EVENT_BASE_URL}/create`,
      method: "post",
      data,
    });
  }
  static list(data: any) {
    return request<any, any>({
      url: `${EVENT_BASE_URL}/list`,
      method: "post",
      data,
    });
  }
  static get(id: string) {
    return request({
      url: `${EVENT_BASE_URL}/get`,
      method: "get",
      params: { eventId: id },
    });
  }
  static update(data: any) {
    return request({
      url: `${EVENT_BASE_URL}/update`,
      method: "post",
      data,
    });
  }
  static createEventTrigger(data: any) {
    return request({
      url: `${EVENT_BASE_URL}/createEventTrigger`,
      method: "post",
      data,
    });
  }
  static deleteEventTriggerById(eventTriggerId: string) {
    return request({
      url: `${EVENT_BASE_URL}/deleteEventTriggerById`,
      method: "get",
      params: { eventTriggerId },
    });
  }
  static getByPage(
    pageCode: PageCodeEnum,
    type?: EventTypeEnum,
    bizIdentityCode: string = ""
  ) {
    return request({
      url: `${EVENT_BASE_URL}/getByPage`,
      method: "get",
      params: { pageCode, bizIdentityCode, type },
    });
  }
}

export default EventAPI;
