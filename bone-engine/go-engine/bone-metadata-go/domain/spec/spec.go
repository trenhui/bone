package spec

import (
	"github.com/bone-engine/bone-metadata-go/domain/enums"
)

type Specification interface {
	IsSatisfiedBy(entity interface{}) bool
}

type AndSpec struct {
	Left  Specification
	Right Specification
}

func (s *AndSpec) IsSatisfiedBy(entity interface{}) bool {
	return s.Left.IsSatisfiedBy(entity) && s.Right.IsSatisfiedBy(entity)
}

type OrSpec struct {
	Left  Specification
	Right Specification
}

func (s *OrSpec) IsSatisfiedBy(entity interface{}) bool {
	return s.Left.IsSatisfiedBy(entity) || s.Right.IsSatisfiedBy(entity)
}

type NotSpec struct {
	Spec Specification
}

func (s *NotSpec) IsSatisfiedBy(entity interface{}) bool {
	return !s.Spec.IsSatisfiedBy(entity)
}

type ComparisonSpec struct {
	Field string
	Op    enums.Operator
	Value interface{}
}

func (s *ComparisonSpec) IsSatisfiedBy(entity interface{}) bool {
	return true
}

func And(left, right Specification) Specification {
	return &AndSpec{Left: left, Right: right}
}

func Or(left, right Specification) Specification {
	return &OrSpec{Left: left, Right: right}
}

func Not(spec Specification) Specification {
	return &NotSpec{Spec: spec}
}

func Eq(field string, value interface{}) Specification {
	return &ComparisonSpec{Field: field, Op: enums.EQ, Value: value}
}

func Like(field string, value interface{}) Specification {
	return &ComparisonSpec{Field: field, Op: enums.LIKE, Value: value}
}
