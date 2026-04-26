package bonemetadata

import (
	"context"
	"database/sql"
	"fmt"
	"reflect"
	"strings"

	"github.com/bone-engine/bone-metadata-go/domain/enums"
	"github.com/bone-engine/bone-metadata-go/domain/model"
	"github.com/bone-engine/bone-metadata-go/domain/spec"
	"github.com/bone-engine/bone-metadata-go/extension/handler"
	"github.com/bone-engine/bone-metadata-go/query/builder"
	"github.com/bone-engine/bone-metadata-go/query/criteria"
	"github.com/bone-engine/bone-metadata-go/sql/executor"
)

const (
	MaxBatchSize          = 1000
	MaxPaginationThreshold = 1000
	DefaultPageSize       = 10
	DefaultPageNumber     = 1
)

// Repository 通用存储库接口
type Repository[T model.Entity, ID comparable] interface {
	// 查询方法
	FindById(ctx context.Context, id ID) (*T, error)
	FindByIdIncludingDeleted(ctx context.Context, id ID) (*T, error)
	FindByIds(ctx context.Context, ids []ID) ([]*T, error)
	FindByIdsIncludingDeleted(ctx context.Context, ids []ID) ([]*T, error)
	FindByCriteria(ctx context.Context, c *criteria.Criteria) ([]*T, error)
	FindOneByCriteria(ctx context.Context, c *criteria.Criteria) (*T, error)
	PageByCriteria(ctx context.Context, c *criteria.Criteria) (*model.Page, error)
	CountByCriteria(ctx context.Context, c *criteria.Criteria) (int64, error)
	FindAll(ctx context.Context) ([]*T, error)
	Count(ctx context.Context) (int64, error)

	// 插入方法
	Insert(ctx context.Context, entity *T) (ID, error)
	BatchInsert(ctx context.Context, entities []*T) error

	// 更新方法
	Update(ctx context.Context, entity *T) (bool, error)
	UpdateByCriteria(ctx context.Context, entity *T, c *criteria.Criteria) (int, error)

	// 保存方法（插入或更新）
	Save(ctx context.Context, entity *T) (ID, error)
	BatchSave(ctx context.Context, entities []*T) error

	// 删除方法
	DeleteById(ctx context.Context, id ID) (bool, error)
	DeleteByIds(ctx context.Context, ids []ID) error
	Delete(ctx context.Context, entity *T) error
	DeleteAll(ctx context.Context) error

	// 存在检查
	ExistsById(ctx context.Context, id ID) (bool, error)

	// 查询转换
	QueryByCondition(ctx context.Context, queryParams []QueryParam, sortingFields []SortingField, pageNo int, pageSize int, bizIdentityCode string) (*model.Page, error)
	Query(ctx context.Context, queryParam *Query) ([]*T, error)
	QueryPage(ctx context.Context, pageParam *PageParam) (*model.Page, error)

	// 聚合查询
	Aggregate(ctx context.Context, aggregations []string, c *criteria.Criteria, groupBy []string) ([]map[string]interface{}, error)
	AggregateWithHaving(ctx context.Context, aggregations []string, c *criteria.Criteria, groupBy []string, having []string) ([]map[string]interface{}, error)
	AggregateSingle(ctx context.Context, aggregations []string, c *criteria.Criteria) (map[string]interface{}, error)
	AggregateWithPagination(ctx context.Context, aggregations []string, c *criteria.Criteria, groupBy []string, having []string, pageNumber int, pageSize int) (*model.Page, error)

	// 获取执行器
	GetSqlExecutor() executor.Executor
	GetEntityClass() reflect.Type
}

// BaseRepository 通用存储库实现
type BaseRepository[T model.Entity, ID comparable] struct {
	entityType          reflect.Type
	metadataResolver    spec.EntityMetadataResolver
	extensionCoordinator handler.ExtensionCoordinator
	executor            executor.Executor
	entityMetadata      *model.EntityMetadata
}

// NewBaseRepository 创建新的BaseRepository
func NewBaseRepository[T model.Entity, ID comparable](exec executor.Executor) *BaseRepository[T, ID] {
	var t T
	entityType := reflect.TypeOf(t)
	if entityType.Kind() == reflect.Ptr {
		entityType = entityType.Elem()
	}

	resolver := spec.NewEntityMetadataResolver()
	metadata, _ := resolver.Resolve(entityType)

	return &BaseRepository[T, ID]{
		entityType:          entityType,
		metadataResolver:    resolver,
		extensionCoordinator: handler.NewExtensionCoordinator(),
		executor:            exec,
		entityMetadata:      metadata,
	}
}

// ==================== 内部工具方法 ====================

// partition 列表分区
func partition[T any](list []T, size int) [][]T {
	if list == nil || len(list) == 0 {
		return [][]T{}
	}

	var result [][]T
	for i := 0; i < len(list); i += size {
		end := i + size
		if end > len(list) {
			end = len(list)
		}
		result = append(result, list[i:end])
	}
	return result
}

// ensureIdInitialized 确保ID已初始化
func (r *BaseRepository[T, ID]) ensureIdInitialized(entity *T, metadata *model.EntityMetadata) error {
	if metadata.PrimaryKey == nil {
		return nil
	}
	return nil
}

// setEntityId 设置实体ID
func (r *BaseRepository[T, ID]) setEntityId(entity *T, id interface{}) error {
	entityValue := reflect.ValueOf(entity).Elem()
	idField := entityValue.FieldByName("ID")
	if !idField.IsValid() || !idField.CanSet() {
		return fmt.Errorf("cannot set ID field")
	}

	idValue := reflect.ValueOf(id)
	if idValue.Type().AssignableTo(idField.Type()) {
		idField.Set(idValue)
	} else if idValue.Type().ConvertibleTo(idField.Type()) {
		idField.Set(idValue.Convert(idField.Type()))
	} else {
		return fmt.Errorf("cannot convert id type %v to %v", idValue.Type(), idField.Type())
	}
	return nil
}

// getAllocationContext 获取分配上下文
func (r *BaseRepository[T, ID]) getAllocationContext(entityType reflect.Type) *model.AllocationContext {
	return model.NewAllocationContext("", "", "", entityType.Name(), nil, nil)
}

// validateCriteriaFields 验证条件字段
func (r *BaseRepository[T, ID]) validateCriteriaFields(c *criteria.Criteria) error {
	if c == nil {
		return nil
	}
	return nil
}

// ==================== 查询方法实现 ====================

// FindById 根据ID查询实体
func (r *BaseRepository[T, ID]) FindById(ctx context.Context, id ID) (*T, error) {
	return r.findByIdInternal(ctx, id, false)
}

// FindByIdIncludingDeleted 根据ID查询实体（包括已删除）
func (r *BaseRepository[T, ID]) FindByIdIncludingDeleted(ctx context.Context, id ID) (*T, error) {
	return r.findByIdInternal(ctx, id, true)
}

// findByIdInternal 内部实现
func (r *BaseRepository[T, ID]) findByIdInternal(ctx context.Context, id ID, includeDeleted bool) (*T, error) {
	metadata, err := r.metadataResolver.Resolve(r.entityType)
	if err != nil {
		return nil, err
	}

	if metadata.PrimaryKey == nil {
		return nil, fmt.Errorf("no primary key defined")
	}

	qb := builder.NewBuilder()
	qb.Select("*").From(metadata.TableName)
	if metadata.SoftDeletable && !includeDeleted {
		qb.Where("deleted", enums.EQ, false)
	}
	qb.Where(metadata.PrimaryKey.ColumnName, enums.EQ, id)

	sqlStr, args := qb.BuildSQL()
	row := r.executor.QueryRow(ctx, sqlStr, args...)

	entity, err := r.mapRowToEntity(row, metadata)
	if err != nil {
		return nil, err
	}

	if entity != nil {
		r.loadExtensionFields(ctx, entity, metadata)
	}

	return entity, nil
}

// FindByIds 根据ID列表查询实体
func (r *BaseRepository[T, ID]) FindByIds(ctx context.Context, ids []ID) ([]*T, error) {
	return r.findByIdsInternal(ctx, ids, false)
}

// FindByIdsIncludingDeleted 根据ID列表查询实体（包括已删除）
func (r *BaseRepository[T, ID]) FindByIdsIncludingDeleted(ctx context.Context, ids []ID) ([]*T, error) {
	return r.findByIdsInternal(ctx, ids, true)
}

// findByIdsInternal 内部实现
func (r *BaseRepository[T, ID]) findByIdsInternal(ctx context.Context, ids []ID, includeDeleted bool) ([]*T, error) {
	if len(ids) == 0 {
		return []*T{}, nil
	}

	metadata, err := r.metadataResolver.Resolve(r.entityType)
	if err != nil {
		return nil, err
	}

	if metadata.PrimaryKey == nil {
		return nil, fmt.Errorf("no primary key defined")
	}

	idInterfaces := make([]interface{}, len(ids))
	for i, id := range ids {
		idInterfaces[i] = id
	}

	qb := builder.NewBuilder()
	qb.Select("*").From(metadata.TableName)
	if metadata.SoftDeletable && !includeDeleted {
		qb.Where("deleted", enums.EQ, false)
	}
	qb.Where(metadata.PrimaryKey.ColumnName, enums.IN, idInterfaces)

	sqlStr, args := qb.BuildSQL()
	rows, err := r.executor.Query(ctx, sqlStr, args...)
	if err != nil {
		return nil, err
	}
	defer rows.Close()

	entities, err := r.mapRowsToEntities(rows, metadata)
	if err != nil {
		return nil, err
	}

	for _, entity := range entities {
		r.loadExtensionFields(ctx, entity, metadata)
	}

	return entities, nil
}

// FindAll 查询所有实体
func (r *BaseRepository[T, ID]) FindAll(ctx context.Context) ([]*T, error) {
	metadata, err := r.metadataResolver.Resolve(r.entityType)
	if err != nil {
		return nil, err
	}

	qb := builder.NewBuilder()
	qb.Select("*").From(metadata.TableName)
	if metadata.SoftDeletable {
		qb.Where("deleted", enums.EQ, false)
	}

	sqlStr, args := qb.BuildSQL()
	rows, err := r.executor.Query(ctx, sqlStr, args...)
	if err != nil {
		return nil, err
	}
	defer rows.Close()

	entities, err := r.mapRowsToEntities(rows, metadata)
	if err != nil {
		return nil, err
	}

	for _, entity := range entities {
		r.loadExtensionFields(ctx, entity, metadata)
	}

	return entities, nil
}

// Count 查询总数量
func (r *BaseRepository[T, ID]) Count(ctx context.Context) (int64, error) {
	metadata, err := r.metadataResolver.Resolve(r.entityType)
	if err != nil {
		return 0, err
	}

	qb := builder.NewBuilder()
	qb.Select("COUNT(*)").From(metadata.TableName)
	if metadata.SoftDeletable {
		qb.Where("deleted", enums.EQ, false)
	}

	sqlStr, args := qb.BuildCountSQL()
	row := r.executor.QueryRow(ctx, sqlStr, args...)

	var count int64
	err = row.Scan(&count)
	if err != nil {
		return 0, err
	}

	return count, nil
}

// FindByCriteria 根据条件查询
func (r *BaseRepository[T, ID]) FindByCriteria(ctx context.Context, c *criteria.Criteria) ([]*T, error) {
	if err := r.validateCriteriaFields(c); err != nil {
		return nil, err
	}

	metadata, err := r.metadataResolver.Resolve(r.entityType)
	if err != nil {
		return nil, err
	}

	qb := builder.NewBuilder()
	qb.Select("*").From(metadata.TableName)
	if metadata.SoftDeletable {
		qb.Where("deleted", enums.EQ, false)
	}

	if c != nil {
		for _, cond := range c.GetMainConditions() {
			var val interface{}
			if len(cond.Values) > 0 {
				val = cond.Values[0]
			}
			qb.Where(cond.Column, cond.Operator, val)
		}

		for _, sort := range c.GetSortItems() {
			qb.OrderBy(sort.Field, sort.Direction)
		}

		if c.GetPageNo() > 0 && c.GetPageSize() > 0 {
			qb.Page(c.GetPageNo(), c.GetPageSize())
		}
	}

	sqlStr, args := qb.BuildSQL()
	rows, err := r.executor.Query(ctx, sqlStr, args...)
	if err != nil {
		return nil, err
	}
	defer rows.Close()

	entities, err := r.mapRowsToEntities(rows, metadata)
	if err != nil {
		return nil, err
	}

	for _, entity := range entities {
		r.loadExtensionFields(ctx, entity, metadata)
	}

	return entities, nil
}

// FindOneByCriteria 根据条件查询单个实体
func (r *BaseRepository[T, ID]) FindOneByCriteria(ctx context.Context, c *criteria.Criteria) (*T, error) {
	metadata, err := r.metadataResolver.Resolve(r.entityType)
	if err != nil {
		return nil, err
	}

	qb := builder.NewBuilder()
	qb.Select("*").From(metadata.TableName)
	if metadata.SoftDeletable {
		qb.Where("deleted", enums.EQ, false)
	}

	if c != nil {
		for _, cond := range c.GetMainConditions() {
			var val interface{}
			if len(cond.Values) > 0 {
				val = cond.Values[0]
			}
			qb.Where(cond.Column, cond.Operator, val)
		}
	}

	qb.Page(1, 1)
	sqlStr, args := qb.BuildSQL()
	row := r.executor.QueryRow(ctx, sqlStr, args...)

	entity, err := r.mapRowToEntity(row, metadata)
	if err != nil {
		return nil, err
	}

	if entity != nil {
		r.loadExtensionFields(ctx, entity, metadata)
	}

	return entity, nil
}

// PageByCriteria 分页查询
func (r *BaseRepository[T, ID]) PageByCriteria(ctx context.Context, c *criteria.Criteria) (*model.Page, error) {
	if err := r.validateCriteriaFields(c); err != nil {
		return nil, err
	}

	_, err := r.metadataResolver.Resolve(r.entityType)
	if err != nil {
		return nil, err
	}

	pageNo := DefaultPageNumber
	pageSize := DefaultPageSize
	if c != nil {
		if c.GetPageNo() > 0 {
			pageNo = c.GetPageNo()
		}
		if c.GetPageSize() > 0 && c.GetPageSize() <= MaxPaginationThreshold {
			pageSize = c.GetPageSize()
		}
	}

	originalPageNo := 0
	originalPageSize := 0
	if c != nil {
		originalPageNo = c.GetPageNo()
		originalPageSize = c.GetPageSize()
		defer func() {
			c.SetPageNo(originalPageNo)
			c.SetPageSize(originalPageSize)
		}()
	}

	var total int64
	var entities []*T

	if pageNo == 1 && pageSize <= 100 {
		c.SetPageNo(1)
		c.SetPageSize(pageSize)
		entities, err = r.FindByCriteria(ctx, c)
		if err != nil {
			return nil, err
		}

		if len(entities) < pageSize {
			total = int64(len(entities))
		} else {
			total, err = r.CountByCriteria(ctx, c)
			if err != nil {
				return nil, err
			}
		}
	} else {
		total, err = r.CountByCriteria(ctx, c)
		if err != nil {
			return nil, err
		}

		if total == 0 {
			return model.NewPage([]*T{}, 0, pageNo, pageSize), nil
		}

		c.SetPageNo(pageNo)
		c.SetPageSize(pageSize)
		entities, err = r.FindByCriteria(ctx, c)
		if err != nil {
			return nil, err
		}
	}

	return model.NewPage(entities, total, pageNo, pageSize), nil
}

// CountByCriteria 根据条件计数
func (r *BaseRepository[T, ID]) CountByCriteria(ctx context.Context, c *criteria.Criteria) (int64, error) {
	metadata, err := r.metadataResolver.Resolve(r.entityType)
	if err != nil {
		return 0, err
	}

	qb := builder.NewBuilder()
	qb.Select("COUNT(*)").From(metadata.TableName)
	if metadata.SoftDeletable {
		qb.Where("deleted", enums.EQ, false)
	}

	if c != nil {
		for _, cond := range c.GetMainConditions() {
			var val interface{}
			if len(cond.Values) > 0 {
				val = cond.Values[0]
			}
			qb.Where(cond.Column, cond.Operator, val)
		}
	}

	sqlStr, args := qb.BuildCountSQL()
	row := r.executor.QueryRow(ctx, sqlStr, args...)

	var count int64
	err = row.Scan(&count)
	if err != nil {
		return 0, err
	}

	return count, nil
}

// ==================== 插入方法实现 ====================

// Insert 插入单个实体
func (r *BaseRepository[T, ID]) Insert(ctx context.Context, entity *T) (ID, error) {
	if entity == nil {
		var zero ID
		return zero, fmt.Errorf("entity cannot be nil")
	}

	metadata, err := r.metadataResolver.Resolve(r.entityType)
	if err != nil {
		var zero ID
		return zero, err
	}

	insertedEntity, err := r.insert(ctx, entity, metadata)
	if err != nil {
		var zero ID
		return zero, err
	}

	return (*insertedEntity).GetID().(ID), nil
}

// BatchInsert 批量插入
func (r *BaseRepository[T, ID]) BatchInsert(ctx context.Context, entities []*T) error {
	if len(entities) == 0 {
		return nil
	}

	metadata, err := r.metadataResolver.Resolve(r.entityType)
	if err != nil {
		return err
	}

	batches := partition(entities, MaxBatchSize)
	for _, batch := range batches {
		for _, entity := range batch {
			_, err := r.insert(ctx, entity, metadata)
			if err != nil {
				return err
			}
		}
	}

	return nil
}

// insert 内部插入实现
func (r *BaseRepository[T, ID]) insert(ctx context.Context, entity *T, metadata *model.EntityMetadata) (*T, error) {
	var columns []string
	var values []interface{}

	entityValue := reflect.ValueOf(entity).Elem()
	for _, col := range metadata.Columns {
		if col.IsAutoIncr {
			continue
		}

		field := entityValue.FieldByName(col.FieldName)
		if !field.IsValid() || !field.CanInterface() {
			continue
		}

		columns = append(columns, col.ColumnName)
		values = append(values, field.Interface())
	}

	result, err := r.executor.Insert(ctx, metadata.TableName, columns, values)
	if err != nil {
		return nil, err
	}

	if metadata.PrimaryKey != nil && metadata.PrimaryKey.IsAutoIncr {
		id, err := result.LastInsertId()
		if err == nil {
			r.setEntityId(entity, id)
		}
	}

	r.saveExtensionFields(ctx, entity, metadata)
	return entity, nil
}

// ==================== 更新方法实现 ====================

// Update 更新单个实体
func (r *BaseRepository[T, ID]) Update(ctx context.Context, entity *T) (bool, error) {
	if entity == nil {
		return false, fmt.Errorf("entity cannot be nil")
	}

	metadata, err := r.metadataResolver.Resolve(r.entityType)
	if err != nil {
		return false, err
	}

	if metadata.PrimaryKey == nil {
		return false, fmt.Errorf("no primary key defined")
	}

	updateMap := make(map[string]interface{})
	entityValue := reflect.ValueOf(entity).Elem()

	for _, col := range metadata.Columns {
		if col.IsPrimary {
			continue
		}

		field := entityValue.FieldByName(col.FieldName)
		if !field.IsValid() || !field.CanInterface() {
			continue
		}

		updateMap[col.ColumnName] = field.Interface()
	}

	if len(updateMap) == 0 {
		return false, nil
	}

	id := (*entity).GetID()
	where := fmt.Sprintf("%s = ?", metadata.PrimaryKey.ColumnName)
	result, err := r.executor.Update(ctx, metadata.TableName, updateMap, where, id)
	if err != nil {
		return false, err
	}

	r.saveExtensionFields(ctx, entity, metadata)
	affectedRows, _ := result.RowsAffected()
	return affectedRows > 0, nil
}

// UpdateByCriteria 根据条件更新
func (r *BaseRepository[T, ID]) UpdateByCriteria(ctx context.Context, entity *T, c *criteria.Criteria) (int, error) {
	if entity == nil || c == nil {
		return 0, fmt.Errorf("entity and criteria cannot be nil")
	}

	metadata, err := r.metadataResolver.Resolve(r.entityType)
	if err != nil {
		return 0, err
	}

	updateMap := make(map[string]interface{})
	entityValue := reflect.ValueOf(entity).Elem()

	for _, col := range metadata.Columns {
		if col.IsPrimary {
			continue
		}

		field := entityValue.FieldByName(col.FieldName)
		if !field.IsValid() || !field.CanInterface() {
			continue
		}

		updateMap[col.ColumnName] = field.Interface()
	}

	if len(updateMap) == 0 {
		return 0, nil
	}

	whereClause := c.WhereSql()
	where := strings.TrimPrefix(whereClause, " WHERE ")
	args := make([]interface{}, 0)
	for _, v := range c.GetParams() {
		args = append(args, v)
	}

	result, err := r.executor.Update(ctx, metadata.TableName, updateMap, where, args...)
	if err != nil {
		return 0, err
	}

	r.saveExtensionFields(ctx, entity, metadata)
	affectedRows, _ := result.RowsAffected()
	return int(affectedRows), nil
}

// ==================== 保存方法实现 ====================

// Save 保存实体（插入或更新）
func (r *BaseRepository[T, ID]) Save(ctx context.Context, entity *T) (ID, error) {
	if entity == nil {
		var zero ID
		return zero, fmt.Errorf("entity cannot be nil")
	}

	r.ensureIdInitialized(entity, r.entityMetadata)

	id := (*entity).GetID()
	exists := false

	if id != nil {
		found, err := r.FindByIdIncludingDeleted(ctx, id.(ID))
		if err == nil && found != nil {
			exists = true
		}
	}

	if exists {
		_, err := r.Update(ctx, entity)
		if err != nil {
			var zero ID
			return zero, err
		}
	} else {
		insertedId, err := r.Insert(ctx, entity)
		if err != nil {
			var zero ID
			return zero, err
		}
		return insertedId, nil
	}

	return id.(ID), nil
}

// BatchSave 批量保存
func (r *BaseRepository[T, ID]) BatchSave(ctx context.Context, entities []*T) error {
	if len(entities) == 0 {
		return nil
	}

	_, err := r.metadataResolver.Resolve(r.entityType)
	if err != nil {
		return err
	}

	toInsert := make([]*T, 0)
	maybeToUpdate := make([]*T, 0)

	for _, entity := range entities {
		if (*entity).GetID() == nil {
			toInsert = append(toInsert, entity)
		} else {
			maybeToUpdate = append(maybeToUpdate, entity)
		}
	}

	if len(toInsert) > 0 {
		if err := r.BatchInsert(ctx, toInsert); err != nil {
			return err
		}
	}

	if len(maybeToUpdate) > 0 {
		batches := partition(maybeToUpdate, MaxBatchSize)
		for _, batch := range batches {
			ids := make([]ID, 0)
			for _, entity := range batch {
				ids = append(ids, (*entity).GetID().(ID))
			}

			existing, err := r.FindByIds(ctx, ids)
			if err != nil {
				return err
			}

			existingIdSet := make(map[ID]bool)
			for _, e := range existing {
				existingIdSet[(*e).GetID().(ID)] = true
			}

			toUpdate := make([]*T, 0)
			toInsertFromUpdate := make([]*T, 0)

			for _, entity := range batch {
				id := (*entity).GetID().(ID)
				if existingIdSet[id] {
					toUpdate = append(toUpdate, entity)
				} else {
					toInsertFromUpdate = append(toInsertFromUpdate, entity)
				}
			}

			for _, entity := range toUpdate {
				if _, err := r.Update(ctx, entity); err != nil {
					return err
				}
			}

			if len(toInsertFromUpdate) > 0 {
				if err := r.BatchInsert(ctx, toInsertFromUpdate); err != nil {
					return err
				}
			}
		}
	}

	return nil
}

// ==================== 删除方法实现 ====================

// DeleteById 根据ID删除
func (r *BaseRepository[T, ID]) DeleteById(ctx context.Context, id ID) (bool, error) {
	metadata, err := r.metadataResolver.Resolve(r.entityType)
	if err != nil {
		return false, err
	}

	if metadata.PrimaryKey == nil {
		return false, fmt.Errorf("no primary key defined")
	}

	var affected int64
	var result sql.Result
	if metadata.SoftDeletable {
		set := map[string]interface{}{"deleted": true}
		where := fmt.Sprintf("%s = ?", metadata.PrimaryKey.ColumnName)
		result, err = r.executor.Update(ctx, metadata.TableName, set, where, id)
	} else {
		where := fmt.Sprintf("%s = ?", metadata.PrimaryKey.ColumnName)
		result, err = r.executor.Delete(ctx, metadata.TableName, where, id)
	}

	if err != nil {
		return false, err
	}

	affected, _ = result.RowsAffected()
	return affected > 0, nil
}

// DeleteByIds 根据ID列表批量删除
func (r *BaseRepository[T, ID]) DeleteByIds(ctx context.Context, ids []ID) error {
	if len(ids) == 0 {
		return nil
	}

	metadata, err := r.metadataResolver.Resolve(r.entityType)
	if err != nil {
		return err
	}

	if metadata.PrimaryKey == nil {
		return fmt.Errorf("no primary key defined")
	}

	idInterfaces := make([]interface{}, len(ids))
	for i, id := range ids {
		idInterfaces[i] = id
	}

	if metadata.SoftDeletable {
		set := map[string]interface{}{"deleted": true}
		placeholders := make([]string, len(ids))
		for i := range placeholders {
			placeholders[i] = "?"
		}
		where := fmt.Sprintf("%s IN (%s)", metadata.PrimaryKey.ColumnName, strings.Join(placeholders, ","))
		_, err = r.executor.Update(ctx, metadata.TableName, set, where, idInterfaces...)
	} else {
		placeholders := make([]string, len(ids))
		for i := range placeholders {
			placeholders[i] = "?"
		}
		where := fmt.Sprintf("%s IN (%s)", metadata.PrimaryKey.ColumnName, strings.Join(placeholders, ","))
		_, err = r.executor.Delete(ctx, metadata.TableName, where, idInterfaces...)
	}

	return err
}

// Delete 删除实体
func (r *BaseRepository[T, ID]) Delete(ctx context.Context, entity *T) error {
	if entity == nil {
		return fmt.Errorf("entity cannot be nil")
	}

	id := (*entity).GetID()
	if id == nil {
		return fmt.Errorf("entity ID cannot be nil")
	}

	_, err := r.DeleteById(ctx, id.(ID))
	return err
}

// DeleteAll 删除所有实体
func (r *BaseRepository[T, ID]) DeleteAll(ctx context.Context) error {
	metadata, err := r.metadataResolver.Resolve(r.entityType)
	if err != nil {
		return err
	}

	if metadata.SoftDeletable {
		set := map[string]interface{}{"deleted": true}
		_, err = r.executor.Update(ctx, metadata.TableName, set, "", nil)
	} else {
		_, err = r.executor.Delete(ctx, metadata.TableName, "", nil)
	}

	return err
}

// ==================== 存在检查 ====================

// ExistsById 检查ID是否存在
func (r *BaseRepository[T, ID]) ExistsById(ctx context.Context, id ID) (bool, error) {
	entity, err := r.FindById(ctx, id)
	if err != nil {
		return false, err
	}
	return entity != nil, nil
}

// ==================== 查询转换方法 ====================

// QueryByCondition 通用条件查询
func (r *BaseRepository[T, ID]) QueryByCondition(ctx context.Context, queryParams []QueryParam, sortingFields []SortingField, pageNo int, pageSize int, bizIdentityCode string) (*model.Page, error) {
	c := criteria.New()
	c = r.buildCriteria(c, queryParams, bizIdentityCode)
	c = r.addSortingToCriteria(c, sortingFields)
	c.Page(pageNo, pageSize)
	return r.PageByCriteria(ctx, c)
}

// Query 通用查询
func (r *BaseRepository[T, ID]) Query(ctx context.Context, queryParam *Query) ([]*T, error) {
	if queryParam == nil {
		return []*T{}, nil
	}
	c := criteria.New()
	return r.FindByCriteria(ctx, c)
}

// QueryPage 通用分页查询
func (r *BaseRepository[T, ID]) QueryPage(ctx context.Context, pageParam *PageParam) (*model.Page, error) {
	if pageParam == nil {
		return model.NewPage([]*T{}, 0, DefaultPageNumber, DefaultPageSize), nil
	}
	c := criteria.New()
	c.Page(pageParam.PageNo, pageParam.PageSize)
	return r.PageByCriteria(ctx, c)
}

// buildCriteria 构建查询条件
func (r *BaseRepository[T, ID]) buildCriteria(c *criteria.Criteria, queryParams []QueryParam, bizIdentityCode string) *criteria.Criteria {
	if bizIdentityCode != "" {
		c.Eq("biz_identity_code", bizIdentityCode)
	}

	for _, param := range queryParams {
		switch param.Operator {
		case enums.EQ:
			c.Eq(param.Field, param.Value)
		case enums.NE:
			c.Ne(param.Field, param.Value)
		case enums.GT:
			c.Gt(param.Field, param.Value)
		case enums.GE:
			c.Ge(param.Field, param.Value)
		case enums.LT:
			c.Lt(param.Field, param.Value)
		case enums.LE:
			c.Le(param.Field, param.Value)
		case enums.LIKE:
			c.Like(param.Field, param.Value)
		case enums.IN:
			if list, ok := param.Value.([]interface{}); ok {
				c.In(param.Field, list...)
			}
		case enums.BETWEEN:
			if list, ok := param.Value.([]interface{}); ok && len(list) >= 2 {
				c.Between(param.Field, list[0], list[1])
			}
		}
	}

	return c
}

// addSortingToCriteria 添加排序条件
func (r *BaseRepository[T, ID]) addSortingToCriteria(c *criteria.Criteria, sortingFields []SortingField) *criteria.Criteria {
	for _, sort := range sortingFields {
		direction := enums.ASC
		if strings.ToUpper(sort.Direction) == "DESC" {
			direction = enums.DESC
		}
		c.OrderBy(sort.Field, direction)
	}
	return c
}

// ==================== 聚合查询方法 ====================

// Aggregate 执行聚合查询
func (r *BaseRepository[T, ID]) Aggregate(ctx context.Context, aggregations []string, c *criteria.Criteria, groupBy []string) ([]map[string]interface{}, error) {
	return r.AggregateWithHaving(ctx, aggregations, c, groupBy, nil)
}

// AggregateWithHaving 执行带HAVING的聚合查询
func (r *BaseRepository[T, ID]) AggregateWithHaving(ctx context.Context, aggregations []string, c *criteria.Criteria, groupBy []string, having []string) ([]map[string]interface{}, error) {
	metadata, err := r.metadataResolver.Resolve(r.entityType)
	if err != nil {
		return nil, err
	}

	if len(aggregations) == 0 {
		return []map[string]interface{}{}, nil
	}

	qb := builder.NewBuilder()
	for _, agg := range aggregations {
		qb.Select(agg)
	}
	qb.From(metadata.TableName)

	if metadata.SoftDeletable {
		qb.Where("deleted", enums.EQ, false)
	}

	if c != nil {
		for _, cond := range c.GetMainConditions() {
			var val interface{}
			if len(cond.Values) > 0 {
				val = cond.Values[0]
			}
			qb.Where(cond.Column, cond.Operator, val)
		}
	}

	if len(groupBy) > 0 {
		for _, gb := range groupBy {
			qb.GroupBy(gb)
		}
	}

	sqlStr, args := qb.BuildSQL()
	rows, err := r.executor.Query(ctx, sqlStr, args...)
	if err != nil {
		return nil, err
	}
	defer rows.Close()

	results := make([]map[string]interface{}, 0)
	for rows.Next() {
		columns, _ := rows.Columns()
		values := make([]interface{}, len(columns))
		valuePtrs := make([]interface{}, len(columns))
		for i := range values {
			valuePtrs[i] = &values[i]
		}

		if err := rows.Scan(valuePtrs...); err == nil {
			result := make(map[string]interface{})
			for i, col := range columns {
				result[col] = values[i]
			}
			results = append(results, result)
		}
	}

	return results, nil
}

// AggregateSingle 执行无GROUP BY的聚合查询
func (r *BaseRepository[T, ID]) AggregateSingle(ctx context.Context, aggregations []string, c *criteria.Criteria) (map[string]interface{}, error) {
	results, err := r.Aggregate(ctx, aggregations, c, nil)
	if err != nil {
		return nil, err
	}

	if len(results) > 0 {
		return results[0], nil
	}

	return make(map[string]interface{}), nil
}

// AggregateWithPagination 执行带分页的聚合查询
func (r *BaseRepository[T, ID]) AggregateWithPagination(ctx context.Context, aggregations []string, c *criteria.Criteria, groupBy []string, having []string, pageNumber int, pageSize int) (*model.Page, error) {
	_, err := r.metadataResolver.Resolve(r.entityType)
	if err != nil {
		return nil, err
	}

	var total int64
	if len(groupBy) == 0 {
		total, err = r.CountByCriteria(ctx, c)
		if err != nil {
			return nil, err
		}
	} else {
		countAgg := []string{"COUNT(*)"}
		countResults, err := r.Aggregate(ctx, countAgg, c, groupBy)
		if err != nil {
			return nil, err
		}
		total = int64(len(countResults))
	}

	if c != nil {
		c.Page(pageNumber, pageSize)
	}

	results, err := r.AggregateWithHaving(ctx, aggregations, c, groupBy, having)
	if err != nil {
		return nil, err
	}

	return model.NewPage(results, total, pageNumber, pageSize), nil
}

// ==================== 执行器和类型访问 ====================

// GetSqlExecutor 获取SQL执行器
func (r *BaseRepository[T, ID]) GetSqlExecutor() executor.Executor {
	return r.executor
}

// GetEntityClass 获取实体类型
func (r *BaseRepository[T, ID]) GetEntityClass() reflect.Type {
	return r.entityType
}

// ==================== 扩展字段处理 ====================

// saveExtensionFields 保存扩展字段
func (r *BaseRepository[T, ID]) saveExtensionFields(ctx context.Context, entity *T, metadata *model.EntityMetadata) error {
	if ext, ok := interface{}(entity).(model.Extensible); ok {
		extra := ext.GetExtraProperties()
		if extra != nil && len(extra) > 0 {
			allocCtx := model.NewAllocationContext(
				"",
				"",
				ext.GetBizIdentityCode(),
				metadata.TableName,
				(*entity).GetID(),
				extra,
			)
			return r.extensionCoordinator.Save(ctx, allocCtx)
		}
	}
	return nil
}

// loadExtensionFields 加载扩展字段
func (r *BaseRepository[T, ID]) loadExtensionFields(ctx context.Context, entity *T, metadata *model.EntityMetadata) error {
	if ext, ok := interface{}(entity).(model.Extensible); ok {
		allocCtx := model.NewAllocationContext(
			"",
			"",
			ext.GetBizIdentityCode(),
			metadata.TableName,
			(*entity).GetID(),
			nil,
		)
		extra, err := r.extensionCoordinator.Load(ctx, allocCtx)
		if err != nil {
			return err
		}
		ext.MergeExtraProperties(extra)
	}
	return nil
}

// ==================== 行映射 ====================

// mapRowToEntity 映射行到实体
func (r *BaseRepository[T, ID]) mapRowToEntity(row *sql.Row, metadata *model.EntityMetadata) (*T, error) {
	var entity T
	entityValue := reflect.ValueOf(&entity).Elem()

	scanArgs := make([]interface{}, len(metadata.Columns))
	for i, col := range metadata.Columns {
		field := entityValue.FieldByName(col.FieldName)
		if field.IsValid() && field.CanSet() {
			scanArgs[i] = field.Addr().Interface()
		} else {
			var temp interface{}
			scanArgs[i] = &temp
		}
	}

	err := row.Scan(scanArgs...)
	if err == sql.ErrNoRows {
		return nil, nil
	}
	if err != nil {
		return nil, err
	}

	return &entity, nil
}

// mapRowsToEntities 映射多行到实体列表
func (r *BaseRepository[T, ID]) mapRowsToEntities(rows *sql.Rows, metadata *model.EntityMetadata) ([]*T, error) {
	entities := make([]*T, 0)

	for rows.Next() {
		var entity T
		entityValue := reflect.ValueOf(&entity).Elem()

		scanArgs := make([]interface{}, len(metadata.Columns))
		for i, col := range metadata.Columns {
			field := entityValue.FieldByName(col.FieldName)
			if field.IsValid() && field.CanSet() {
				scanArgs[i] = field.Addr().Interface()
			} else {
				var temp interface{}
				scanArgs[i] = &temp
			}
		}

		err := rows.Scan(scanArgs...)
		if err != nil {
			return nil, err
		}

		entities = append(entities, &entity)
	}

	return entities, nil
}

// ==================== 支持类型定义 ====================

// QueryParam 查询参数
type QueryParam struct {
	Field    string
	Operator enums.Operator
	Value    interface{}
}

// SortingField 排序字段
type SortingField struct {
	Field     string
	Direction string
}

// Query 查询对象
type Query struct {
	Params []QueryParam
	Sorts  []SortingField
}

// PageParam 分页查询参数
type PageParam struct {
	Query
	PageNo   int
	PageSize int
}

// RepositoryFactory 存储库工厂
type RepositoryFactory struct {
	executor executor.Executor
}

// NewRepositoryFactory 创建存储库工厂
func NewRepositoryFactory(exec executor.Executor) *RepositoryFactory {
	return &RepositoryFactory{
		executor: exec,
	}
}

// CreateRepository 创建存储库（简化版）
func (f *RepositoryFactory) CreateRepository() interface{} {
	return nil
}
