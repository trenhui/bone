package qry

// OrderDetailQuery 订单详情查询
type OrderDetailQuery struct {
	OrderID int64 `json:"order_id"`
}

// OrderPageQuery 订单分页查询
type OrderPageQuery struct {
	CustomerID   int64  `json:"customer_id"`
	Status       string `json:"status"`
	PageNo       int    `json:"page_no"`
	PageSize     int    `json:"page_size"`
	OrderBy      string `json:"order_by"`
	OrderDir     string `json:"order_dir"`
}
