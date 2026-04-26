package spec

import (
	"reflect"
	"strings"

	"github.com/bone-engine/bone-metadata-go/domain/model"
)

type EntityMetadataResolver interface {
	Resolve(entityType reflect.Type) (*model.EntityMetadata, error)
}

type DefaultEntityMetadataResolver struct {
}

func NewEntityMetadataResolver() *DefaultEntityMetadataResolver {
	return &DefaultEntityMetadataResolver{}
}

func (r *DefaultEntityMetadataResolver) Resolve(entityType reflect.Type) (*model.EntityMetadata, error) {
	if entityType.Kind() == reflect.Ptr {
		entityType = entityType.Elem()
	}

	tableName := r.getTableName(entityType)
	columns := r.getColumns(entityType)
	primaryKey := r.findPrimaryKey(columns)

	return &model.EntityMetadata{
		TableName:    tableName,
		Columns:      columns,
		PrimaryKey:   primaryKey,
		EntityType:   entityType,
		SoftDeletable: r.hasDeletedField(columns),
	}, nil
}

func (r *DefaultEntityMetadataResolver) getTableName(entityType reflect.Type) string {
	// 简单实现：将驼峰命名转换为下划线命名
	name := entityType.Name()
	tableName := ""
	for i, c := range name {
		if i > 0 && c >= 'A' && c <= 'Z' {
			tableName += "_"
		}
		tableName += strings.ToLower(string(c))
	}
	return tableName
}

func (r *DefaultEntityMetadataResolver) getColumns(entityType reflect.Type) []model.ColumnMetadata {
	columns := make([]model.ColumnMetadata, 0)

	for i := 0; i < entityType.NumField(); i++ {
		field := entityType.Field(i)
		boneTag := field.Tag.Get("bone")
		if boneTag == "" {
			continue
		}

		tagParts := strings.Split(boneTag, ",")
		columnName := tagParts[0]
		isPrimary := false
		isAutoIncr := false
		generationStrategy := ""

		for _, part := range tagParts[1:] {
			switch part {
			case "primary":
				isPrimary = true
			case "autoincr":
				isAutoIncr = true
				generationStrategy = "IDENTITY"
			}
		}

		columns = append(columns, model.ColumnMetadata{
			FieldName:         field.Name,
			ColumnName:        columnName,
			ColumnType:        r.getColumnType(field.Type),
			IsPrimary:         isPrimary,
			IsNullable:        !isPrimary,
			IsAutoIncr:        isAutoIncr,
			FieldType:         field.Type,
			GenerationStrategy: generationStrategy,
		})
	}

	return columns
}

func (r *DefaultEntityMetadataResolver) getColumnType(fieldType reflect.Type) string {
	switch fieldType.Kind() {
	case reflect.Int, reflect.Int32, reflect.Int64:
		return "BIGINT"
	case reflect.Float32, reflect.Float64:
		return "DOUBLE"
	case reflect.Bool:
		return "BOOLEAN"
	case reflect.String:
		return "VARCHAR(255)"
	case reflect.Struct:
		if fieldType.String() == "time.Time" {
			return "DATETIME"
		}
		return "JSON"
	default:
		return "VARCHAR(255)"
	}
}

func (r *DefaultEntityMetadataResolver) findPrimaryKey(columns []model.ColumnMetadata) *model.ColumnMetadata {
	for i := range columns {
		if columns[i].IsPrimary {
			return &columns[i]
		}
	}
	return nil
}

func (r *DefaultEntityMetadataResolver) hasDeletedField(columns []model.ColumnMetadata) bool {
	for _, col := range columns {
		if col.ColumnName == "deleted" {
			return true
		}
	}
	return false
}