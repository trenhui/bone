package cmd

// CancelOrderCommand 取消订单命令
type CancelOrderCommand struct {
	OrderID   int64  `json:"order_id"`
	Reason    string `json:"reason"`
	OperatorID int64 `json:"operator_id"`
}
