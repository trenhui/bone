package result

// ApiResponse 与 bone-core ApiResponse 字段对齐的 JSON 包装
type ApiResponse[T any] struct {
	Code    int    `json:"code"`
	Message string `json:"message"`
	Data    T      `json:"data,omitempty"`
	Success bool   `json:"success"`
}

func OK[T any](data T) ApiResponse[T] {
	return ApiResponse[T]{Code: 0, Message: "ok", Data: data, Success: true}
}

func Fail(code int, message string) ApiResponse[any] {
	return ApiResponse[any]{Code: code, Message: message, Success: false}
}
