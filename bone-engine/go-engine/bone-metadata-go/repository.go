package bonemetadata

import (
	"context"

	"github.com/bone-engine/bone-metadata-go/domain/model"
	"github.com/bone-engine/bone-metadata-go/domain/query"
	"github.com/bone-engine/bone-metadata-go/query/criteria"
)

type Repository[T any] interface {
	Save(ctx context.Context, entity *T) (*T, error)
	SaveAll(ctx context.Context, entities []*T) ([]*T, error)
	FindByID(ctx context.Context, id interface{}) (*T, error)
	FindAll(ctx context.Context) ([]*T, error)
	FindAllByID(ctx context.Context, ids []interface{}) ([]*T, error)
	FindPage(ctx context.Context, pageNum, pageSize int) (*model.Page, error)
	FindByCriteria(ctx context.Context, criteria *criteria.Criteria) ([]*T, error)
	FindOneByCriteria(ctx context.Context, criteria *criteria.Criteria) (*T, error)
	Count(ctx context.Context) (int64, error)
	CountByCriteria(ctx context.Context, criteria *criteria.Criteria) (int64, error)
	Delete(ctx context.Context, entity *T) error
	DeleteByID(ctx context.Context, id interface{}) error
	DeleteAll(ctx context.Context) error
	DeleteAllByID(ctx context.Context, ids []interface{}) error
	ExistsByID(ctx context.Context, id interface{}) (bool, error)
}

type QuerySpecification[T any] interface {
	Find(ctx context.Context, spec Specification[T]) ([]*T, error)
	FindOne(ctx context.Context, spec Specification[T]) (*T, error)
	Count(ctx context.Context, spec Specification[T]) (int64, error)
}

type Specification[T any] interface {
	ToPredicate(ctx context.Context, q *query.Query) string
}

type BaseRepository[T any] struct {
}

func NewBaseRepository[T any]() *BaseRepository[T] {
	return &BaseRepository[T]{}
}

func (r *BaseRepository[T]) Save(ctx context.Context, entity *T) (*T, error) {
	return entity, nil
}

func (r *BaseRepository[T]) SaveAll(ctx context.Context, entities []*T) ([]*T, error) {
	return entities, nil
}

func (r *BaseRepository[T]) FindByID(ctx context.Context, id interface{}) (*T, error) {
	return nil, nil
}

func (r *BaseRepository[T]) FindAll(ctx context.Context) ([]*T, error) {
	return nil, nil
}

func (r *BaseRepository[T]) FindAllByID(ctx context.Context, ids []interface{}) ([]*T, error) {
	return nil, nil
}

func (r *BaseRepository[T]) FindPage(ctx context.Context, pageNum, pageSize int) (*model.Page, error) {
	return nil, nil
}

func (r *BaseRepository[T]) FindByCriteria(ctx context.Context, criteria *criteria.Criteria) ([]*T, error) {
	return nil, nil
}

func (r *BaseRepository[T]) FindOneByCriteria(ctx context.Context, criteria *criteria.Criteria) (*T, error) {
	return nil, nil
}

func (r *BaseRepository[T]) Count(ctx context.Context) (int64, error) {
	return 0, nil
}

func (r *BaseRepository[T]) CountByCriteria(ctx context.Context, criteria *criteria.Criteria) (int64, error) {
	return 0, nil
}

func (r *BaseRepository[T]) Delete(ctx context.Context, entity *T) error {
	return nil
}

func (r *BaseRepository[T]) DeleteByID(ctx context.Context, id interface{}) error {
	return nil
}

func (r *BaseRepository[T]) DeleteAll(ctx context.Context) error {
	return nil
}

func (r *BaseRepository[T]) DeleteAllByID(ctx context.Context, ids []interface{}) error {
	return nil
}

func (r *BaseRepository[T]) ExistsByID(ctx context.Context, id interface{}) (bool, error) {
	return false, nil
}

type RepositoryBuilder[T any] struct {
}

func NewRepositoryBuilder[T any]() *RepositoryBuilder[T] {
	return &RepositoryBuilder[T]{}
}

func (b *RepositoryBuilder[T]) Build() Repository[T] {
	return NewBaseRepository[T]()
}
