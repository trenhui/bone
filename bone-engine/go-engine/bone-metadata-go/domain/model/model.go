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
	SoftDeletable bool
}

func (e *EntityMetadata) GetPrimaryKey() *ColumnMetadata {
	return e.PrimaryKey
}

func (e *EntityMetadata) IsSoftDeletable() bool {
	return e.SoftDeletable
}

type ColumnMetadata struct {
	FieldName         string
	ColumnName        string
	ColumnType        string
	IsPrimary         bool
	IsNullable        bool
	IsAutoIncr        bool
	FieldType         reflect.Type
	GenerationStrategy string
}

type Entity interface {
	GetID() interface{}
}

type BaseEntity struct {
	ID        int64     `bone:"id,primary,autoincr"`
	CreatedAt time.Time `bone:"created_at"`
	UpdatedAt time.Time `bone:"updated_at"`
	Deleted   bool      `bone:"deleted"`
}

func (b *BaseEntity) GetID() interface{} {
	return b.ID
}

type TenantEntity struct {
	BaseEntity
	TenantID        string `bone:"tenant_id"`
	BizIdentityCode string `bone:"biz_identity_code"`
}

func (t *TenantEntity) GetBizIdentityCode() string {
	return t.BizIdentityCode
}

type Extensible interface {
	GetExtraProperties() map[string]interface{}
	SetExtraProperties(map[string]interface{})
	MergeExtraProperties(map[string]interface{})
	GetBizIdentityCode() string
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

type AllocationContext struct {
	TenantID        string
	AppCode         string
	BizIdentityCode string
	EntityName      string
	EntityID        interface{}
	ExtraProperties map[string]interface{}
}

func NewAllocationContext(tenantID, appCode, bizIdentityCode, entityName string, entityID interface{}, extraProperties map[string]interface{}) *AllocationContext {
	return &AllocationContext{
		TenantID:        tenantID,
		AppCode:         appCode,
		BizIdentityCode: bizIdentityCode,
		EntityName:      entityName,
		EntityID:        entityID,
		ExtraProperties: extraProperties,
	}
}

func (c *AllocationContext) GetTenantID() string {
	return c.TenantID
}

func (c *AllocationContext) GetAppCode() string {
	return c.AppCode
}

func (c *AllocationContext) GetBizIdentityCode() string {
	return c.BizIdentityCode
}

func (c *AllocationContext) GetEntityName() string {
	return c.EntityName
}

func (c *AllocationContext) GetEntityID() interface{} {
	return c.EntityID
}

func (c *AllocationContext) GetExtraProperties() map[string]interface{} {
	return c.ExtraProperties
}
