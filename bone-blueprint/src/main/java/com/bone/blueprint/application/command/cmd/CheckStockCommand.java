package com.bone.blueprint.application.command.cmd;

import java.util.List;
import lombok.Builder;
import lombok.Value;

@Value
@Builder
public class CheckStockCommand {

  List<Item> items;

  @Value
  @Builder
  public static class Item {
    Long productId;
    Integer quantity;
  }
}
