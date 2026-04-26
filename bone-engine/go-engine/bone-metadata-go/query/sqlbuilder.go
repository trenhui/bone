package query

import (
	"fmt"
	"reflect"
	"strings"

	"github.com/bone-engine/bone-metadata-go/domain/enums"
	"github.com/bone-engine/bone-metadata-go/domain/spec"
	"github.com/bone-engine/bone-metadata-go/query/criteria"
)

// SQLBuilder SQL构建器
type SQLBuilder struct {
	dialect         enums.DialectType
	metadataResolver spec.EntityMetadataResolver
}

// NewSQLBuilder 创建SQLBuilder
func NewSQLBuilder(dialect enums.DialectType, metadataResolver spec.EntityMetadataResolver) *SQLBuilder {
	return &SQLBuilder{
		dialect:         dialect,
		metadataResolver: metadataResolver,
	}
}

// CompiledQuery 编译后的查询
type CompiledQuery struct {
	SQL    string
	Params map[string]interface{}
}

// BatchCompiledQuery 批量编译查询
type BatchCompiledQuery struct {
	SQL           string
	BatchParams  []map[string]interface{}
}

// BuildSelect 构建SELECT查询
func (sb *SQLBuilder) BuildSelect(entityType reflect.Type, c *criteria.Criteria, includeDeleted bool) (*CompiledQuery, error) {
	metadata, err := sb.metadataResolver.Resolve(entityType)
	if err != nil {
		return nil, err
	}

	params := make(map[string]interface{})
	if c != nil {
		for k, v := range c.GetParams() {
			params[k] = v
		}
	}

	var sbQuery strings.Builder
	sbQuery.WriteString("SELECT ")

	// 构建列
	if len(metadata.Columns) > 0 {
		var columns []string
		for _, col := range metadata.Columns {
			columns = append(columns, col.ColumnName)
		}
		sbQuery.WriteString(strings.Join(columns, ", "))
	} else {
		sbQuery.WriteString("*")
	}

	sbQuery.WriteString(" FROM ")
	sbQuery.WriteString(metadata.TableName)

	// 构建WHERE
	var whereConditions []string

	if metadata.SoftDeletable && !includeDeleted {
		whereConditions = append(whereConditions, "deleted = ?")
	}

	if c != nil {
		// 主表条件
		for _, cond := range c.GetMainConditions() {
			condSql := sb.buildCondition(cond, params)
			if condSql != "" {
				whereConditions = append(whereConditions, condSql)
			}
		}

		// 扩展表条件
		if c.RequiresExtJoin() {
			whereConditions = append(whereConditions, c.WhereSql())
		}
	}

	if len(whereConditions) > 0 {
		sbQuery.WriteString(" WHERE ")
		sbQuery.WriteString(strings.Join(whereConditions, " AND "))
	}

	// 构建ORDER BY
	if c != nil && len(c.GetSortItems()) > 0 {
		var sortParts []string
		for _, sort := range c.GetSortItems() {
			sortParts = append(sortParts, fmt.Sprintf("%s %s", sort.Field, sort.Direction))
		}
		sbQuery.WriteString(" ORDER BY ")
		sbQuery.WriteString(strings.Join(sortParts, ", "))
	}

	// 构建分页
	if c != nil && c.GetPageNo() > 0 && c.GetPageSize() > 0 {
		offset := (c.GetPageNo() - 1) * c.GetPageSize()
		switch sb.dialect {
		case enums.MySQL:
			sbQuery.WriteString(fmt.Sprintf(" LIMIT %d, %d", offset, c.GetPageSize()))
		case enums.Postgres:
			sbQuery.WriteString(fmt.Sprintf(" LIMIT %d OFFSET %d", c.GetPageSize(), offset))
		case enums.SQLite:
			sbQuery.WriteString(fmt.Sprintf(" LIMIT %d OFFSET %d", c.GetPageSize(), offset))
		}
	}

	return &CompiledQuery{
		SQL:    sbQuery.String(),
		Params: params,
	}, nil
}

// BuildCount 构建COUNT查询
func (sb *SQLBuilder) BuildCount(entityType reflect.Type, c *criteria.Criteria, includeDeleted bool) (*CompiledQuery, error) {
	metadata, err := sb.metadataResolver.Resolve(entityType)
	if err != nil {
		return nil, err
	}

	params := make(map[string]interface{})
	if c != nil {
		for k, v := range c.GetParams() {
			params[k] = v
		}
	}

	var sbQuery strings.Builder
	sbQuery.WriteString("SELECT COUNT(*) FROM ")
	sbQuery.WriteString(metadata.TableName)

	// 构建WHERE
	var whereConditions []string

	if metadata.SoftDeletable && !includeDeleted {
		whereConditions = append(whereConditions, "deleted = ?")
	}

	if c != nil {
		for _, cond := range c.GetMainConditions() {
			condSql := sb.buildCondition(cond, params)
			if condSql != "" {
				whereConditions = append(whereConditions, condSql)
			}
		}
	}

	if len(whereConditions) > 0 {
		sbQuery.WriteString(" WHERE ")
		sbQuery.WriteString(strings.Join(whereConditions, " AND "))
	}

	return &CompiledQuery{
		SQL:    sbQuery.String(),
		Params: params,
	}, nil
}

// BuildBatchInsert 构建批量INSERT查询
func (sb *SQLBuilder) BuildBatchInsert(entityType reflect.Type, entities []interface{}) (*BatchCompiledQuery, error) {
	if len(entities) == 0 {
		return nil, fmt.Errorf("no entities to insert")
	}

	metadata, err := sb.metadataResolver.Resolve(entityType)
	if err != nil {
		return nil, err
	}

	// 构建列定义
	var columns []string
	for _, col := range metadata.Columns {
		if col.IsAutoIncr {
			continue
		}
		columns = append(columns, col.ColumnName)
	}

	if len(columns) == 0 {
		return nil, fmt.Errorf("no columns to insert")
	}

	// 构建SQL
	var sbQuery strings.Builder
	sbQuery.WriteString("INSERT INTO ")
	sbQuery.WriteString(metadata.TableName)
	sbQuery.WriteString(" (")
	sbQuery.WriteString(strings.Join(columns, ", "))
	sbQuery.WriteString(") VALUES ")

	// 构建值占位符
	var valuePlaceholders []string
	for range columns {
		valuePlaceholders = append(valuePlaceholders, "?")
	}
	valuesPart := "(" + strings.Join(valuePlaceholders, ", ") + ")"

	// 添加多个值组
	var allValueGroups []string
	for range entities {
		allValueGroups = append(allValueGroups, valuesPart)
	}
	sbQuery.WriteString(strings.Join(allValueGroups, ", "))

	// 构建批量参数
	var batchParams []map[string]interface{}
	for i := range entities {
		// 这里简化处理，实际需要从实体获取值
		// 每个实体一组参数
		batchParams = append(batchParams, map[string]interface{}{
			"entity_index": i,
		})
	}

	return &BatchCompiledQuery{
		SQL:           sbQuery.String(),
		BatchParams:  batchParams,
	}, nil
}

// BuildDynamicUpdate 构建动态UPDATE查询
func (sb *SQLBuilder) BuildDynamicUpdate(entityType reflect.Type, entity interface{}) (*CompiledQuery, error) {
	metadata, err := sb.metadataResolver.Resolve(entityType)
	if err != nil {
		return nil, err
	}

	if metadata.PrimaryKey == nil {
		return nil, fmt.Errorf("no primary key defined")
	}

	params := make(map[string]interface{})
	var sbQuery strings.Builder
	var setClauses []string

	for _, col := range metadata.Columns {
		if col.IsPrimary {
			continue
		}
		setClauses = append(setClauses, fmt.Sprintf("%s = ?", col.ColumnName))
	}

	if len(setClauses) == 0 {
		return nil, fmt.Errorf("no columns to update")
	}

	sbQuery.WriteString("UPDATE ")
	sbQuery.WriteString(metadata.TableName)
	sbQuery.WriteString(" SET ")
	sbQuery.WriteString(strings.Join(setClauses, ", "))
	sbQuery.WriteString(" WHERE ")
	sbQuery.WriteString(metadata.PrimaryKey.ColumnName)
	sbQuery.WriteString(" = ?")

	return &CompiledQuery{
		SQL:    sbQuery.String(),
		Params: params,
	}, nil
}

// BuildConditionalUpdate 构建条件UPDATE查询
func (sb *SQLBuilder) BuildConditionalUpdate(entityType reflect.Type, entity interface{}, c *criteria.Criteria) (*CompiledQuery, error) {
	metadata, err := sb.metadataResolver.Resolve(entityType)
	if err != nil {
		return nil, err
	}

	params := make(map[string]interface{})
	if c != nil {
		for k, v := range c.GetParams() {
			params[k] = v
		}
	}

	var sbQuery strings.Builder
	var setClauses []string

	for _, col := range metadata.Columns {
		if col.IsPrimary {
			continue
		}
		setClauses = append(setClauses, fmt.Sprintf("%s = ?", col.ColumnName))
	}

	if len(setClauses) == 0 {
		return nil, fmt.Errorf("no columns to update")
	}

	sbQuery.WriteString("UPDATE ")
	sbQuery.WriteString(metadata.TableName)
	sbQuery.WriteString(" SET ")
	sbQuery.WriteString(strings.Join(setClauses, ", "))

	if c != nil {
		whereSql := c.WhereSql()
		if whereSql != "" {
			sbQuery.WriteString(" WHERE ")
			sbQuery.WriteString(whereSql)
		}
	}

	return &CompiledQuery{
		SQL:    sbQuery.String(),
		Params: params,
	}, nil
}

// BuildAggregation 构建聚合查询
func (sb *SQLBuilder) BuildAggregation(entityType reflect.Type, aggregations []string, c *criteria.Criteria, groupBy []string, having []string) (*CompiledQuery, error) {
	metadata, err := sb.metadataResolver.Resolve(entityType)
	if err != nil {
		return nil, err
	}

	params := make(map[string]interface{})
	if c != nil {
		for k, v := range c.GetParams() {
			params[k] = v
		}
	}

	var sbQuery strings.Builder
	sbQuery.WriteString("SELECT ")

	if len(aggregations) > 0 {
		sbQuery.WriteString(strings.Join(aggregations, ", "))
	} else {
		sbQuery.WriteString("COUNT(*)")
	}

	sbQuery.WriteString(" FROM ")
	sbQuery.WriteString(metadata.TableName)

	// WHERE
	if c != nil {
		whereSql := c.WhereSql()
		if whereSql != "" {
			sbQuery.WriteString(" WHERE ")
			sbQuery.WriteString(whereSql)
		}
	}

	// GROUP BY
	if len(groupBy) > 0 {
		sbQuery.WriteString(" GROUP BY ")
		sbQuery.WriteString(strings.Join(groupBy, ", "))
	}

	// HAVING
	if len(having) > 0 {
		sbQuery.WriteString(" HAVING ")
		sbQuery.WriteString(strings.Join(having, " AND "))
	}

	// ORDER BY and LIMIT for pagination
	if c != nil {
		if len(c.GetSortItems()) > 0 {
			var sortParts []string
			for _, sort := range c.GetSortItems() {
				sortParts = append(sortParts, fmt.Sprintf("%s %s", sort.Field, sort.Direction))
			}
			sbQuery.WriteString(" ORDER BY ")
			sbQuery.WriteString(strings.Join(sortParts, ", "))
		}

		if c.GetPageNo() > 0 && c.GetPageSize() > 0 {
			offset := (c.GetPageNo() - 1) * c.GetPageSize()
			switch sb.dialect {
			case enums.MySQL:
				sbQuery.WriteString(fmt.Sprintf(" LIMIT %d, %d", offset, c.GetPageSize()))
			case enums.Postgres:
				sbQuery.WriteString(fmt.Sprintf(" LIMIT %d OFFSET %d", c.GetPageSize(), offset))
			case enums.SQLite:
				sbQuery.WriteString(fmt.Sprintf(" LIMIT %d OFFSET %d", c.GetPageSize(), offset))
			}
		}
	}

	return &CompiledQuery{
		SQL:    sbQuery.String(),
		Params: params,
	}, nil
}

// BuildCountAggregation 构建聚合计数查询
func (sb *SQLBuilder) BuildCountAggregation(entityType reflect.Type, c *criteria.Criteria, groupBy []string, having []string) (*CompiledQuery, error) {
	// 如果有GROUP BY，我们需要计算分组的数量
	if len(groupBy) > 0 {
		// 可以包装一层COUNT
		return sb.BuildAggregation(entityType, []string{"COUNT(*)"}, c, groupBy, having)
	}
	return sb.BuildCount(entityType, c, false)
}

// buildCondition 构建条件SQL
func (sb *SQLBuilder) buildCondition(cond criteria.Condition, params map[string]interface{}) string {
	switch cond.Operator {
	case enums.ISNULL:
		return fmt.Sprintf("%s IS NULL", cond.Column)
	case enums.ISNOTNULL:
		return fmt.Sprintf("%s IS NOT NULL", cond.Column)
	case enums.BETWEEN, enums.NOTBETWEEN:
		if len(cond.Values) >= 2 {
			params[cond.ParamName+"_0"] = cond.Values[0]
			params[cond.ParamName+"_1"] = cond.Values[1]
			return fmt.Sprintf("%s %s :%s_0 AND :%s_1", cond.Column, cond.Operator, cond.ParamName, cond.ParamName)
		}
	case enums.IN, enums.NOTIN:
		if len(cond.Values) > 0 {
			params[cond.ParamName] = cond.Values
			return fmt.Sprintf("%s %s (:%s)", cond.Column, cond.Operator, cond.ParamName)
		}
	default:
		if len(cond.Values) > 0 {
			params[cond.ParamName] = cond.Values[0]
			return fmt.Sprintf("%s %s :%s", cond.Column, cond.Operator, cond.ParamName)
		}
	}
	return ""
}
