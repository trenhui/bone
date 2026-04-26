package criteria

import (
	"strings"
	"github.com/bone-engine/bone-metadata-go/domain/enums"
)

// Criteria 支持主表和扩展表条件、排序与分页的通用查询构造器
type Criteria struct {
	mainConditions []Condition
	extConditions  []Condition
	parameters     map[string]interface{}
	columnCounter  map[string]int
	sortItems      []SortItem
	pageSize       int
	pageNo         int
}

// Condition 查询条件
type Condition struct {
	FieldName string
	Column    string
	ParamName string
	Operator  enums.Operator
	IsExt     bool
	Values    []interface{}
}

// SortItem 排序项
type SortItem struct {
	Field     string
	Direction enums.OrderDirection
}

func New() *Criteria {
	return &Criteria{
		mainConditions: make([]Condition, 0),
		extConditions:  make([]Condition, 0),
		parameters:     make(map[string]interface{}),
		columnCounter:  make(map[string]int),
		sortItems:      make([]SortItem, 0),
		pageSize:       5000,
		pageNo:         1,
	}
}

func Builder() *Criteria {
	return New()
}

func Create() *Criteria {
	return New()
}

// Page 设置分页，pageNumber从1开始
func (c *Criteria) Page(pageNumber, pageSize int) *Criteria {
	c.pageNo = pageNumber
	c.pageSize = pageSize
	return c
}

// GetOffset 计算偏移量
func (c *Criteria) GetOffset() int {
	return (c.pageNo - 1) * c.pageSize
}

// GetPageNo 获取页码
func (c *Criteria) GetPageNo() int {
	return c.pageNo
}

// SetPageNo 设置页码
func (c *Criteria) SetPageNo(pageNo int) {
	c.pageNo = pageNo
}

// GetPageSize 获取每页大小
func (c *Criteria) GetPageSize() int {
	return c.pageSize
}

// SetPageSize 设置每页大小
func (c *Criteria) SetPageSize(pageSize int) {
	c.pageSize = pageSize
}

// 内部工具方法
func (c *Criteria) generateParamName(column string) string {
	counter := c.columnCounter[column]
	c.columnCounter[column] = counter + 1
	return column + "_" + string(rune('0'+counter))
}

func (c *Criteria) add(target *[]Condition, fieldName string, operator enums.Operator, isExt bool, values ...interface{}) *Criteria {
	column := toSnakeCase(fieldName)
	paramName := c.generateParamName(column)
	cond := Condition{
		FieldName: fieldName,
		Column:    column,
		ParamName: paramName,
		Operator:  operator,
		IsExt:     isExt,
		Values:    values,
	}
	*target = append(*target, cond)
	c.bindParam(cond)
	return c
}

func (c *Criteria) bindParam(cond Condition) {
	switch cond.Operator {
	case enums.BETWEEN, enums.NOTBETWEEN:
		if len(cond.Values) >= 2 {
			c.parameters[cond.ParamName+"_0"] = cond.Values[0]
			c.parameters[cond.ParamName+"_1"] = cond.Values[1]
		}
	case enums.IN, enums.NOTIN:
		c.parameters[cond.ParamName] = cond.Values
	case enums.ISNULL, enums.ISNOTNULL:
	default:
		if len(cond.Values) > 0 {
			c.parameters[cond.ParamName] = cond.Values[0]
		}
	}
}

func toSnakeCase(s string) string {
	var result strings.Builder
	for i, r := range s {
		if i > 0 && 'A' <= r && r <= 'Z' {
			result.WriteRune('_')
		}
		result.WriteRune(r)
	}
	return strings.ToLower(result.String())
}

// ========== 主表条件方法 ==========

func (c *Criteria) Eq(field string, value interface{}) *Criteria {
	return c.add(&c.mainConditions, field, enums.EQ, false, value)
}

func (c *Criteria) Ne(field string, value interface{}) *Criteria {
	return c.add(&c.mainConditions, field, enums.NE, false, value)
}

func (c *Criteria) Gt(field string, value interface{}) *Criteria {
	return c.add(&c.mainConditions, field, enums.GT, false, value)
}

func (c *Criteria) Ge(field string, value interface{}) *Criteria {
	return c.add(&c.mainConditions, field, enums.GE, false, value)
}

func (c *Criteria) Lt(field string, value interface{}) *Criteria {
	return c.add(&c.mainConditions, field, enums.LT, false, value)
}

func (c *Criteria) Le(field string, value interface{}) *Criteria {
	return c.add(&c.mainConditions, field, enums.LE, false, value)
}

func (c *Criteria) Like(field string, value interface{}) *Criteria {
	return c.add(&c.mainConditions, field, enums.LIKE, false, value)
}

func (c *Criteria) NotLike(field string, value interface{}) *Criteria {
	return c.add(&c.mainConditions, field, enums.NOTLIKE, false, value)
}

func (c *Criteria) LikeLeft(field string, value interface{}) *Criteria {
	if str, ok := value.(string); ok {
		return c.add(&c.mainConditions, field, enums.LIKE, false, "%"+str)
	}
	return c.add(&c.mainConditions, field, enums.LIKE, false, value)
}

func (c *Criteria) LikeRight(field string, value interface{}) *Criteria {
	if str, ok := value.(string); ok {
		return c.add(&c.mainConditions, field, enums.LIKE, false, str+"%")
	}
	return c.add(&c.mainConditions, field, enums.LIKE, false, value)
}

func (c *Criteria) In(field string, values ...interface{}) *Criteria {
	if len(values) == 0 || (len(values) == 1 && values[0] == nil) {
		return c
	}
	return c.add(&c.mainConditions, field, enums.IN, false, values...)
}

func (c *Criteria) NotIn(field string, values ...interface{}) *Criteria {
	if len(values) == 0 || (len(values) == 1 && values[0] == nil) {
		return c
	}
	return c.add(&c.mainConditions, field, enums.NOTIN, false, values...)
}

func (c *Criteria) Between(field string, min, max interface{}) *Criteria {
	return c.add(&c.mainConditions, field, enums.BETWEEN, false, min, max)
}

func (c *Criteria) NotBetween(field string, min, max interface{}) *Criteria {
	return c.add(&c.mainConditions, field, enums.NOTBETWEEN, false, min, max)
}

func (c *Criteria) IsNull(field string) *Criteria {
	return c.add(&c.mainConditions, field, enums.ISNULL, false)
}

func (c *Criteria) IsNotNull(field string) *Criteria {
	return c.add(&c.mainConditions, field, enums.ISNOTNULL, false)
}

// ========== 条件化方法（带条件判断） ==========

func (c *Criteria) EqIf(condition bool, field string, value interface{}) *Criteria {
	if condition {
		return c.Eq(field, value)
	}
	return c
}

func (c *Criteria) NeIf(condition bool, field string, value interface{}) *Criteria {
	if condition {
		return c.Ne(field, value)
	}
	return c
}

func (c *Criteria) GtIf(condition bool, field string, value interface{}) *Criteria {
	if condition {
		return c.Gt(field, value)
	}
	return c
}

func (c *Criteria) GeIf(condition bool, field string, value interface{}) *Criteria {
	if condition {
		return c.Ge(field, value)
	}
	return c
}

func (c *Criteria) LtIf(condition bool, field string, value interface{}) *Criteria {
	if condition {
		return c.Lt(field, value)
	}
	return c
}

func (c *Criteria) LeIf(condition bool, field string, value interface{}) *Criteria {
	if condition {
		return c.Le(field, value)
	}
	return c
}

func (c *Criteria) LikeIf(condition bool, field string, value interface{}) *Criteria {
	if condition {
		return c.Like(field, value)
	}
	return c
}

func (c *Criteria) LikeIfPresent(field string, value interface{}) *Criteria {
	if str, ok := value.(string); ok && str != "" {
		return c.Like(field, value)
	}
	return c
}

func (c *Criteria) EqOrNull(field string, value interface{}) *Criteria {
	if value == nil {
		return c.IsNull(field)
	}
	return c.Eq(field, value)
}

// ========== 扩展表条件方法 ==========

func (c *Criteria) ExtEq(field string, value interface{}) *Criteria {
	return c.add(&c.extConditions, field, enums.EQ, true, value)
}

func (c *Criteria) ExtNe(field string, value interface{}) *Criteria {
	return c.add(&c.extConditions, field, enums.NE, true, value)
}

func (c *Criteria) ExtGt(field string, value interface{}) *Criteria {
	return c.add(&c.extConditions, field, enums.GT, true, value)
}

func (c *Criteria) ExtGe(field string, value interface{}) *Criteria {
	return c.add(&c.extConditions, field, enums.GE, true, value)
}

func (c *Criteria) ExtLt(field string, value interface{}) *Criteria {
	return c.add(&c.extConditions, field, enums.LT, true, value)
}

func (c *Criteria) ExtLe(field string, value interface{}) *Criteria {
	return c.add(&c.extConditions, field, enums.LE, true, value)
}

func (c *Criteria) ExtLike(field string, value interface{}) *Criteria {
	return c.add(&c.extConditions, field, enums.LIKE, true, value)
}

func (c *Criteria) ExtIn(field string, values ...interface{}) *Criteria {
	if len(values) == 0 || (len(values) == 1 && values[0] == nil) {
		return c
	}
	return c.add(&c.extConditions, field, enums.IN, true, values...)
}

func (c *Criteria) ExtNotIn(field string, values ...interface{}) *Criteria {
	if len(values) == 0 || (len(values) == 1 && values[0] == nil) {
		return c
	}
	return c.add(&c.extConditions, field, enums.NOTIN, true, values...)
}

func (c *Criteria) ExtBetween(field string, min, max interface{}) *Criteria {
	return c.add(&c.extConditions, field, enums.BETWEEN, true, min, max)
}

func (c *Criteria) ExtIsNull(field string) *Criteria {
	return c.add(&c.extConditions, field, enums.ISNULL, true)
}

func (c *Criteria) ExtIsNotNull(field string) *Criteria {
	return c.add(&c.extConditions, field, enums.ISNOTNULL, true)
}

// ========== 排序方法 ==========

func (c *Criteria) OrderBy(field string, direction enums.OrderDirection) *Criteria {
	c.sortItems = append(c.sortItems, SortItem{
		Field:     field,
		Direction: direction,
	})
	return c
}

func (c *Criteria) OrderByAsc(field string) *Criteria {
	return c.OrderBy(field, enums.ASC)
}

func (c *Criteria) OrderByDesc(field string) *Criteria {
	return c.OrderBy(field, enums.DESC)
}

func (c *Criteria) AddSort(field string, direction enums.OrderDirection) *Criteria {
	return c.OrderBy(field, direction)
}

func (c *Criteria) AddSorts(sorts map[string]enums.OrderDirection) *Criteria {
	for field, direction := range sorts {
		c.OrderBy(field, direction)
	}
	return c
}

// ========== OR条件 ==========

func (c *Criteria) Or(orBuilder func(*Criteria)) *Criteria {
	if orBuilder == nil {
		return c
	}
	orCriteria := New()
	orBuilder(orCriteria)
	if len(orCriteria.mainConditions) > 0 || len(orCriteria.extConditions) > 0 {
		for k, v := range orCriteria.parameters {
			c.parameters[k] = v
		}
	}
	return c
}

// ========== 清空条件 ==========

func (c *Criteria) Clear() *Criteria {
	c.mainConditions = c.mainConditions[:0]
	c.extConditions = c.extConditions[:0]
	c.parameters = make(map[string]interface{})
	c.columnCounter = make(map[string]int)
	c.sortItems = c.sortItems[:0]
	return c
}

// ========== 参数管理 ==========

func (c *Criteria) AddParam(key string, value interface{}) *Criteria {
	c.parameters[key] = value
	return c
}

func (c *Criteria) GetParams() map[string]interface{} {
	return c.parameters
}

func (c *Criteria) SetParams(params map[string]interface{}) *Criteria {
	c.parameters = params
	return c
}

// ========== 获取方法 ==========

func (c *Criteria) GetMainConditions() []Condition {
	return c.mainConditions
}

func (c *Criteria) GetExtConditions() []Condition {
	return c.extConditions
}

func (c *Criteria) GetSortItems() []SortItem {
	return c.sortItems
}

// ========== 检查是否需要扩展表JOIN ==========

func (c *Criteria) RequiresExtJoin() bool {
	return len(c.extConditions) > 0
}

// ========== SQL片段生成 ==========

func (c *Criteria) WhereSql() string {
	var parts []string
	for _, cond := range c.mainConditions {
		parts = append(parts, c.conditionToSql(cond))
	}
	for _, cond := range c.extConditions {
		parts = append(parts, c.conditionToSql(cond))
	}
	return strings.Join(parts, " AND ")
}

func (c *Criteria) conditionToSql(cond Condition) string {
	col := cond.Column
	if cond.IsExt {
		col = "ext." + col
	}
	switch cond.Operator {
	case enums.ISNULL:
		return col + " IS NULL"
	case enums.ISNOTNULL:
		return col + " IS NOT NULL"
	case enums.BETWEEN, enums.NOTBETWEEN:
		return col + " " + string(cond.Operator) + " :" + cond.ParamName + "_0 AND :" + cond.ParamName + "_1"
	case enums.IN, enums.NOTIN:
		return col + " " + string(cond.Operator) + " (:" + cond.ParamName + ")"
	default:
		return col + " " + string(cond.Operator) + " :" + cond.ParamName
	}
}

func (c *Criteria) ToSql() string {
	var sb strings.Builder
	where := c.WhereSql()
	if where != "" {
		sb.WriteString(" WHERE ")
		sb.WriteString(where)
	}
	if len(c.sortItems) > 0 {
		sb.WriteString(" ORDER BY ")
		var sortParts []string
		for _, sort := range c.sortItems {
			sortParts = append(sortParts, toSnakeCase(sort.Field)+" "+string(sort.Direction))
		}
		sb.WriteString(strings.Join(sortParts, ", "))
	}
	return sb.String()
}
