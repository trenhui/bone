package expression

import (
	"context"
	"fmt"
	"reflect"

	"github.com/expr-lang/expr"

	"github.com/bone-engine/bone-extension-go/api/exception"
	"github.com/bone-engine/bone-extension-go/api/model"
)

// ExpressionEvaluator 表达式评估器接口
type ExpressionEvaluator interface {
	Evaluate(ctx context.Context, expr string, data map[string]interface{}) (interface{}, error)
	EvaluateBoolean(ctx context.Context, expr string, data map[string]interface{}) (bool, error)
	EvaluateNumber(ctx context.Context, expr string, data map[string]interface{}) (float64, error)
	EvaluateString(ctx context.Context, expr string, data map[string]interface{}) (string, error)
	Validate(expr string) error
}

// ExprExpressionEvaluator 基于 expr 库的表达式评估器
type ExprExpressionEvaluator struct {
	options []expr.Option
}

// NewExprExpressionEvaluator 创建表达式评估器
func NewExprExpressionEvaluator() *ExprExpressionEvaluator {
	return &ExprExpressionEvaluator{
		options: []expr.Option{
			expr.AllowUndefinedVariables(),
			expr.Env(map[string]interface{}{}),
			// 可以添加自定义函数
			// expr.Function("contains", ...),
		},
	}
}

// Evaluate 评估表达式
func (e *ExprExpressionEvaluator) Evaluate(ctx context.Context, exprStr string, data map[string]interface{}) (interface{}, error) {
	if exprStr == "" {
		return nil, fmt.Errorf("expression cannot be empty")
	}

	// 编译表达式
	program, err := expr.Compile(exprStr, e.options...)
	if err != nil {
		return nil, exception.NewExpressionEvaluationError(exprStr, err)
	}

	// 执行表达式
	result, err := expr.Run(program, data)
	if err != nil {
		return nil, exception.NewExpressionEvaluationError(exprStr, err)
	}

	return result, nil
}

// EvaluateBoolean 评估布尔表达式
func (e *ExprExpressionEvaluator) EvaluateBoolean(ctx context.Context, exprStr string, data map[string]interface{}) (bool, error) {
	result, err := e.Evaluate(ctx, exprStr, data)
	if err != nil {
		return false, err
	}

	// 类型转换
	boolValue, ok := result.(bool)
	if !ok {
		return false, fmt.Errorf("expression result is not a boolean: %v", result)
	}

	return boolValue, nil
}

// EvaluateNumber 评估数字表达式
func (e *ExprExpressionEvaluator) EvaluateNumber(ctx context.Context, exprStr string, data map[string]interface{}) (float64, error) {
	result, err := e.Evaluate(ctx, exprStr, data)
	if err != nil {
		return 0, err
	}

	// 类型转换
	switch v := result.(type) {
	case float64:
		return v, nil
	case int:
		return float64(v), nil
	case int64:
		return float64(v), nil
	case float32:
		return float64(v), nil
	default:
		return 0, fmt.Errorf("expression result is not a number: %v", result)
	}
}

// EvaluateString 评估字符串表达式
func (e *ExprExpressionEvaluator) EvaluateString(ctx context.Context, exprStr string, data map[string]interface{}) (string, error) {
	result, err := e.Evaluate(ctx, exprStr, data)
	if err != nil {
		return "", err
	}

	// 类型转换
	strValue, ok := result.(string)
	if !ok {
		return "", fmt.Errorf("expression result is not a string: %v", result)
	}

	return strValue, nil
}

// Validate 验证表达式
func (e *ExprExpressionEvaluator) Validate(exprStr string) error {
	if exprStr == "" {
		return fmt.Errorf("expression cannot be empty")
	}

	_, err := expr.Compile(exprStr, e.options...)
	if err != nil {
		return exception.NewExpressionEvaluationError(exprStr, err)
	}

	return nil
}

// ExpressionContext 表达式上下文
type ExpressionContext struct {
	Data map[string]interface{}
}

// NewExpressionContext 创建表达式上下文
func NewExpressionContext(ctx *model.Context) *ExpressionContext {
	data := make(map[string]interface{})

	// 从模型上下文复制数据
	if ctx != nil && ctx.Data != nil {
		for k, v := range ctx.Data {
			data[k] = v
		}
	}

	// 添加默认变量
	data["ctx"] = ctx
	data["context"] = ctx

	return &ExpressionContext{
		Data: data,
	}
}

// Set 设置变量
func (ec *ExpressionContext) Set(key string, value interface{}) {
	ec.Data[key] = value
}

// Get 获取变量
func (ec *ExpressionContext) Get(key string) (interface{}, bool) {
	value, ok := ec.Data[key]
	return value, ok
}

// Has 检查变量是否存在
func (ec *ExpressionContext) Has(key string) bool {
	_, ok := ec.Data[key]
	return ok
}

// Remove 删除变量
func (ec *ExpressionContext) Remove(key string) {
	delete(ec.Data, key)
}

// Clear 清空上下文
func (ec *ExpressionContext) Clear() {
	ec.Data = make(map[string]interface{})
}

// ExpressionManager 表达式管理器
type ExpressionManager struct {
	evaluator ExpressionEvaluator
	cache     map[string]interface{}
}

// NewExpressionManager 创建表达式管理器
func NewExpressionManager(evaluator ExpressionEvaluator) *ExpressionManager {
	if evaluator == nil {
		evaluator = NewExprExpressionEvaluator()
	}

	return &ExpressionManager{
		evaluator: evaluator,
		cache:     make(map[string]interface{}),
	}
}

// Evaluate 评估表达式
func (em *ExpressionManager) Evaluate(ctx context.Context, expr string, data map[string]interface{}) (interface{}, error) {
	return em.evaluator.Evaluate(ctx, expr, data)
}

// EvaluateWithContext 评估表达式（使用上下文）
func (em *ExpressionManager) EvaluateWithContext(ctx context.Context, expr string, ec *ExpressionContext) (interface{}, error) {
	return em.evaluator.Evaluate(ctx, expr, ec.Data)
}

// EvaluateBoolean 评估布尔表达式
func (em *ExpressionManager) EvaluateBoolean(ctx context.Context, expr string, data map[string]interface{}) (bool, error) {
	return em.evaluator.EvaluateBoolean(ctx, expr, data)
}

// EvaluateNumber 评估数字表达式
func (em *ExpressionManager) EvaluateNumber(ctx context.Context, expr string, data map[string]interface{}) (float64, error) {
	return em.evaluator.EvaluateNumber(ctx, expr, data)
}

// EvaluateString 评估字符串表达式
func (em *ExpressionManager) EvaluateString(ctx context.Context, expr string, data map[string]interface{}) (string, error) {
	return em.evaluator.EvaluateString(ctx, expr, data)
}

// Validate 验证表达式
func (em *ExpressionManager) Validate(expr string) error {
	return em.evaluator.Validate(expr)
}

// CacheExpression 缓存表达式结果
func (em *ExpressionManager) CacheExpression(expr string, result interface{}) {
	em.cache[expr] = result
}

// GetCachedExpression 获取缓存的表达式结果
func (em *ExpressionManager) GetCachedExpression(expr string) (interface{}, bool) {
	result, ok := em.cache[expr]
	return result, ok
}

// ClearCache 清空缓存
func (em *ExpressionManager) ClearCache() {
	em.cache = make(map[string]interface{})
}

// DefaultExpressionEvaluator 默认表达式评估器
var DefaultExpressionEvaluator ExpressionEvaluator

// DefaultExpressionManager 默认表达式管理器
var DefaultExpressionManager *ExpressionManager

func init() {
	DefaultExpressionEvaluator = NewExprExpressionEvaluator()
	DefaultExpressionManager = NewExpressionManager(DefaultExpressionEvaluator)
}

// GetDefaultExpressionEvaluator 获取默认表达式评估器
func GetDefaultExpressionEvaluator() ExpressionEvaluator {
	return DefaultExpressionEvaluator
}

// GetDefaultExpressionManager 获取默认表达式管理器
func GetDefaultExpressionManager() *ExpressionManager {
	return DefaultExpressionManager
}
