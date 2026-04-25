package converter

import (
	"database/sql"
	"reflect"
	"time"
)

type TypeConverter interface {
	ToDB(value interface{}) (interface{}, error)
	FromDB(src interface{}, dest reflect.Value) error
}

type DefaultConverter struct{}

func NewDefaultConverter() *DefaultConverter {
	return &DefaultConverter{}
}

func (c *DefaultConverter) ToDB(value interface{}) (interface{}, error) {
	if value == nil {
		return nil, nil
	}

	switch v := value.(type) {
	case time.Time:
		return v, nil
	case *time.Time:
		if v == nil {
			return nil, nil
		}
		return *v, nil
	default:
		return value, nil
	}
}

func (c *DefaultConverter) FromDB(src interface{}, dest reflect.Value) error {
	if src == nil {
		dest.Set(reflect.Zero(dest.Type()))
		return nil
	}

	switch dest.Kind() {
	case reflect.String:
		if s, ok := src.(string); ok {
			dest.SetString(s)
		}
	case reflect.Int, reflect.Int8, reflect.Int16, reflect.Int32, reflect.Int64:
		if i, ok := src.(int64); ok {
			dest.SetInt(i)
		}
	case reflect.Bool:
		if b, ok := src.(bool); ok {
			dest.SetBool(b)
		}
	case reflect.Float32, reflect.Float64:
		if f, ok := src.(float64); ok {
			dest.SetFloat(f)
		}
	case reflect.Struct:
		if dest.Type() == reflect.TypeOf(time.Time{}) {
			if t, ok := src.(time.Time); ok {
				dest.Set(reflect.ValueOf(t))
			}
		}
	}
	return nil
}

type NullConverter struct{}

func NewNullConverter() *NullConverter {
	return &NullConverter{}
}

func (c *NullConverter) ToDB(value interface{}) (interface{}, error) {
	if value == nil {
		return sql.NullString{Valid: false}, nil
	}
	return value, nil
}

func (c *NullConverter) FromDB(src interface{}, dest reflect.Value) error {
	return nil
}
