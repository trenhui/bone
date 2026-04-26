package cmd

// PayOrderCommand 支付订单命令
type PayOrderCommand struct {
	OrderID     int64   `json:"order_id"`
	PaymentMethod string `json:"payment_method"`
	TransactionID string `json:"transaction_id"`
}
