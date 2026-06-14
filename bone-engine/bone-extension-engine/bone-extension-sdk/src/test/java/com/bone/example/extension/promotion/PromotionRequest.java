package com.bone.example.extension.promotion;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

/** 促销计算请求 支持复杂SpEL条件表达式匹配和多维度促销策略 */
public class PromotionRequest {
  private String userId;
  private List<OrderItem> items;
  private BigDecimal subtotal;
  private String userLevel;
  private String promotionCode;
  private String orderType; // 订单类型，用于SpEL条件表达式匹配
  private UserInfo userInfo; // 用户信息，用于复杂条件匹配

  /**
   * 获取用户ID
   *
   * @return 用户ID
   */
  public String getUserId() {
    return userId;
  }

  /**
   * 获取订单商品列表
   *
   * @return 订单商品列表
   */
  public List<OrderItem> getItems() {
    return items;
  }

  /**
   * 获取订单小计金额
   *
   * @return 订单小计金额
   */
  public BigDecimal getSubtotal() {
    return subtotal;
  }

  /**
   * 获取用户等级
   *
   * @return 用户等级
   */
  public String getUserLevel() {
    return userLevel;
  }

  /**
   * 获取促销码
   *
   * @return 促销码
   */
  public String getPromotionCode() {
    return promotionCode;
  }

  /**
   * 获取订单类型
   *
   * @return 订单类型
   */
  public String getOrderType() {
    return orderType;
  }

  /**
   * 获取用户信息
   *
   * @return 用户信息
   */
  public UserInfo getUserInfo() {
    return userInfo;
  }

  /**
   * 创建构建器实例
   *
   * @return PromotionRequest构建器
   */
  public static PromotionRequestBuilder builder() {
    return new PromotionRequestBuilder();
  }

  /** PromotionRequest构建器类 */
  public static class PromotionRequestBuilder {
    private String userId;
    private List<OrderItem> items = new ArrayList<>();
    private BigDecimal subtotal;
    private String userLevel;
    private String promotionCode;
    private String orderType;
    private UserInfo userInfo;

    public PromotionRequestBuilder userId(String userId) {
      this.userId = userId;
      return this;
    }

    public PromotionRequestBuilder items(List<OrderItem> items) {
      this.items = items;
      return this;
    }

    public PromotionRequestBuilder addItem(OrderItem item) {
      this.items.add(item);
      return this;
    }

    public PromotionRequestBuilder subtotal(BigDecimal subtotal) {
      this.subtotal = subtotal;
      return this;
    }

    public PromotionRequestBuilder userLevel(String userLevel) {
      this.userLevel = userLevel;
      return this;
    }

    public PromotionRequestBuilder promotionCode(String promotionCode) {
      this.promotionCode = promotionCode;
      return this;
    }

    public PromotionRequestBuilder orderType(String orderType) {
      this.orderType = orderType;
      return this;
    }

    public PromotionRequestBuilder userInfo(UserInfo userInfo) {
      this.userInfo = userInfo;
      return this;
    }

    public PromotionRequest build() {
      PromotionRequest request = new PromotionRequest();
      request.userId = this.userId;
      request.items = this.items;
      request.subtotal = this.subtotal;
      request.userLevel = this.userLevel;
      request.promotionCode = this.promotionCode;
      request.orderType = this.orderType;
      request.userInfo = this.userInfo;
      return request;
    }
  }

  /** 订单商品项 */
  public static class OrderItem {
    private String productId;
    private String productName;
    private BigDecimal unitPrice;
    private int quantity;
    private String category;

    /**
     * 获取商品ID
     *
     * @return 商品ID
     */
    public String getProductId() {
      return productId;
    }

    /**
     * 设置商品ID
     *
     * @param productId 商品ID
     */
    public void setProductId(String productId) {
      this.productId = productId;
    }

    /**
     * 获取商品名称
     *
     * @return 商品名称
     */
    public String getProductName() {
      return productName;
    }

    /**
     * 设置商品名称
     *
     * @param productName 商品名称
     */
    public void setProductName(String productName) {
      this.productName = productName;
    }

    /**
     * 获取单价
     *
     * @return 商品单价
     */
    public BigDecimal getUnitPrice() {
      return unitPrice;
    }

    /**
     * 设置单价
     *
     * @param unitPrice 商品单价
     */
    public void setUnitPrice(BigDecimal unitPrice) {
      this.unitPrice = unitPrice;
    }

    /**
     * 获取数量
     *
     * @return 商品数量
     */
    public int getQuantity() {
      return quantity;
    }

    /**
     * 设置数量
     *
     * @param quantity 商品数量
     */
    public void setQuantity(int quantity) {
      this.quantity = quantity;
    }

    /**
     * 获取商品分类
     *
     * @return 商品分类
     */
    public String getCategory() {
      return category;
    }

    /**
     * 设置商品分类
     *
     * @param category 商品分类
     */
    public void setCategory(String category) {
      this.category = category;
    }

    /**
     * 创建构建器实例
     *
     * @return OrderItem构建器
     */
    public static OrderItemBuilder builder() {
      return new OrderItemBuilder();
    }

    /** OrderItem构建器类 */
    public static class OrderItemBuilder {
      private String productId;
      private String productName;
      private BigDecimal unitPrice;
      private int quantity;
      private String category;

      public OrderItemBuilder productId(String productId) {
        this.productId = productId;
        return this;
      }

      public OrderItemBuilder productName(String productName) {
        this.productName = productName;
        return this;
      }

      public OrderItemBuilder unitPrice(BigDecimal unitPrice) {
        this.unitPrice = unitPrice;
        return this;
      }

      public OrderItemBuilder quantity(int quantity) {
        this.quantity = quantity;
        return this;
      }

      public OrderItemBuilder category(String category) {
        this.category = category;
        return this;
      }

      public OrderItem build() {
        OrderItem orderItem = new OrderItem();
        orderItem.productId = this.productId;
        orderItem.productName = this.productName;
        orderItem.unitPrice = this.unitPrice;
        orderItem.quantity = this.quantity;
        orderItem.category = this.category;
        return orderItem;
      }
    }
  }

  /** 用户信息类，用于复杂SpEL条件表达式 */
  /** 用户信息类，用于复杂SpEL条件表达式 */
  public static class UserInfo {
    private String memberLevel;
    private int memberPoints;
    private int orderCount;
    private String registrationDate;

    /**
     * 获取会员等级
     *
     * @return 会员等级
     */
    public String getMemberLevel() {
      return memberLevel;
    }

    /**
     * 设置会员等级
     *
     * @param memberLevel 会员等级
     */
    public void setMemberLevel(String memberLevel) {
      this.memberLevel = memberLevel;
    }

    /**
     * 获取会员积分
     *
     * @return 会员积分
     */
    public int getMemberPoints() {
      return memberPoints;
    }

    /**
     * 设置会员积分
     *
     * @param memberPoints 会员积分
     */
    public void setMemberPoints(int memberPoints) {
      this.memberPoints = memberPoints;
    }

    /**
     * 获取订单数量
     *
     * @return 订单数量
     */
    public int getOrderCount() {
      return orderCount;
    }

    /**
     * 设置订单数量
     *
     * @param orderCount 订单数量
     */
    public void setOrderCount(int orderCount) {
      this.orderCount = orderCount;
    }

    /**
     * 获取注册日期
     *
     * @return 注册日期
     */
    public String getRegistrationDate() {
      return registrationDate;
    }

    /**
     * 设置注册日期
     *
     * @param registrationDate 注册日期
     */
    public void setRegistrationDate(String registrationDate) {
      this.registrationDate = registrationDate;
    }

    /**
     * 创建构建器实例
     *
     * @return UserInfo构建器
     */
    public static UserInfoBuilder builder() {
      return new UserInfoBuilder();
    }

    /** UserInfo构建器类 */
    public static class UserInfoBuilder {
      private String memberLevel;
      private int memberPoints;
      private int orderCount;
      private String registrationDate;

      public UserInfoBuilder memberLevel(String memberLevel) {
        this.memberLevel = memberLevel;
        return this;
      }

      public UserInfoBuilder memberPoints(int memberPoints) {
        this.memberPoints = memberPoints;
        return this;
      }

      public UserInfoBuilder orderCount(int orderCount) {
        this.orderCount = orderCount;
        return this;
      }

      public UserInfoBuilder registrationDate(String registrationDate) {
        this.registrationDate = registrationDate;
        return this;
      }

      public UserInfo build() {
        UserInfo userInfo = new UserInfo();
        userInfo.memberLevel = this.memberLevel;
        userInfo.memberPoints = this.memberPoints;
        userInfo.orderCount = this.orderCount;
        userInfo.registrationDate = this.registrationDate;
        return userInfo;
      }
    }
  }
}
