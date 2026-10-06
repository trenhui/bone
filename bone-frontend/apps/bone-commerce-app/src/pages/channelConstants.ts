
/** 渠道展示元数据：与后端 ChannelCode 枚举一一对应，漏一个就会显示原始码。 */
export const CHANNEL_META: Record<string, { label: string; color: string }> = {
  TAOBAO: { label: '淘宝', color: 'orange' },
  JD: { label: '京东', color: 'red' },
  DOUYIN: { label: '抖音', color: 'magenta' },
  PDD: { label: '拼多多', color: 'volcano' },
};

/** 上架状态 → 中文 / 颜色。 */
export const LISTING_STATUS_META: Record<string, { label: string; color: string }> = {
  UNLISTED: { label: '未上架', color: 'default' },
  LISTING: { label: '上架中', color: 'processing' },
  ONLINE: { label: '已上架', color: 'success' },
  DELISTING: { label: '下架中', color: 'warning' },
  OFFLINE: { label: '已下架', color: 'default' },
  FAILED: { label: '失败', color: 'error' },
};

/** 发货单状态 → 中文 / 颜色。 */
export const SHIPMENT_STATUS_META: Record<string, { label: string; color: string }> = {
  CREATED: { label: '待发货', color: 'default' },
  SHIPPED: { label: '已发货', color: 'cyan' },
  IN_TRANSIT: { label: '运输中', color: 'processing' },
  SIGNED: { label: '已签收', color: 'success' },
  FAILED: { label: '发货失败', color: 'error' },
};

/** 渠道 SKU 前缀：拉单时渠道侧商品编码形如 `TB<内部商品ID>`。 */
export const CHANNEL_SKU_PREFIX: Record<string, string> = {
  TAOBAO: 'TB',
  JD: 'JD',
  DOUYIN: 'DY',
  PDD: 'PDD',
};

/** 渠道买家绑定来源 → 中文 / 颜色。影子=待运营补绑，与「人工绑定」必须在视觉上区分。 */
export const BINDING_SOURCE_META: Record<string, { label: string; color: string }> = {
  MANUAL: { label: '人工绑定', color: 'success' },
  AUTO_SHADOW: { label: '待绑定（影子）', color: 'warning' },
};

/** 库存广播任务状态 → 中文 / 颜色。FAILED=死信，需人工重试。 */
export const BROADCAST_STATUS_META: Record<string, { label: string; color: string }> = {
  PENDING: { label: '待投递', color: 'default' },
  PROCESSING: { label: '投递中', color: 'processing' },
  SENT: { label: '已投递', color: 'success' },
  FAILED: { label: '失败（死信）', color: 'error' },
};
