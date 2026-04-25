package criteria

import (
	"github.com/bone-engine/bone-metadata-go/domain/enums"
)

type Criteria struct {
	conditions []Condition
}

type Condition struct {
	Field    string
	Operator enums.Operator
	Value    interface{}
}

func New() *Criteria {
	return &Criteria{
		conditions: make([]Condition, 0),
	}
}

func (c *Criteria) Eq(field string, value interface{}) *Criteria {
	c.conditions = append(c.conditions, Condition{
		Field:    field,
		Operator: enums.EQ,
		Value:    value,
	})
	return c
}

func (c *Criteria) Ne(field string, value interface{}) *Criteria {
	c.conditions = append(c.conditions, Condition{
		Field:    field,
		Operator: enums.NE,
		Value:    value,
	})
	return c
}

func (c *Criteria) Gt(field string, value interface{}) *Criteria {
	c.conditions = append(c.conditions, Condition{
		Field:    field,
		Operator: enums.GT,
		Value:    value,
	})
	return c
}

func (c *Criteria) Ge(field string, value interface{}) *Criteria {
	c.conditions = append(c.conditions, Condition{
		Field:    field,
		Operator: enums.GE,
		Value:    value,
	})
	return c
}

func (c *Criteria) Lt(field string, value interface{}) *Criteria {
	c.conditions = append(c.conditions, Condition{
		Field:    field,
		Operator: enums.LT,
		Value:    value,
	})
	return c
}

func (c *Criteria) Le(field string, value interface{}) *Criteria {
	c.conditions = append(c.conditions, Condition{
		Field:    field,
		Operator: enums.LE,
		Value:    value,
	})
	return c
}

func (c *Criteria) Like(field string, value interface{}) *Criteria {
	c.conditions = append(c.conditions, Condition{
		Field:    field,
		Operator: enums.LIKE,
		Value:    value,
	})
	return c
}

func (c *Criteria) In(field string, values ...interface{}) *Criteria {
	c.conditions = append(c.conditions, Condition{
		Field:    field,
		Operator: enums.IN,
		Value:    values,
	})
	return c
}

func (c *Criteria) IsNull(field string) *Criteria {
	c.conditions = append(c.conditions, Condition{
		Field:    field,
		Operator: enums.ISNULL,
	})
	return c
}

func (c *Criteria) IsNotNull(field string) *Criteria {
	c.conditions = append(c.conditions, Condition{
		Field:    field,
		Operator: enums.ISNOTNULL,
	})
	return c
}

func (c *Criteria) GetConditions() []Condition {
	return c.conditions
}
