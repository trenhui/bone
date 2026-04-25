package model

import (
	"reflect"
	"time"
)

type EntityMetadata struct {
	TableName   string
	Columns     []ColumnMetadata
	PrimaryKey  *ColumnMetadata
	EntityType  reflect.Type
}

type ColumnMetadata struct {
	FieldName   string
	ColumnName  string
	ColumnType  string
	IsPrimary   bool
	IsNullable  bool
	IsAutoIncr  bool
	FieldType   reflect.Type
}

type Entity interface {
	GetID() interface{}
}

type BaseEntity struct {
	ID        int64     `bone:"id,primary,autoincr"`
	CreatedAt time.Time `bone:"created_at"`
	UpdatedAt time.Time `bone:"updated_at"`
}

func (b *BaseEntity) GetID() interface{} {
	return b.ID
}

type Page struct {
	Data       interface{}
	Total      int64
	PageNum    int
	PageSize   int
	TotalPages int
}

func NewPage(data interface{}, total int64, pageNum, pageSize int) *Page {
	totalPages := int(total) / pageSize
	if int(total)%pageSize > 0 {
		totalPages++
	}
	return &Page{
		Data:       data,
		Total:      total,
		PageNum:    pageNum,
		PageSize:   pageSize,
		TotalPages: totalPages,
	}
}
