package util

import (
	"reflect"
	"strings"
)

func IsNil(v interface{}) bool {
	if v == nil {
		return true
	}
	val := reflect.ValueOf(v)
	switch val.Kind() {
	case reflect.Chan, reflect.Func, reflect.Interface, reflect.Map, reflect.Ptr, reflect.Slice:
		return val.IsNil()
	}
	return false
}

func IsBlank(s string) bool {
	return strings.TrimSpace(s) == ""
}

func IsNotBlank(s string) bool {
	return !IsBlank(s)
}

func Coalesce(values ...interface{}) interface{} {
	for _, v := range values {
		if !IsNil(v) {
			if s, ok := v.(string); ok {
				if IsNotBlank(s) {
					return v
				}
				continue
			}
			return v
		}
	}
	return nil
}

func ToSlice(v interface{}) []interface{} {
	if v == nil {
		return nil
	}

	val := reflect.ValueOf(v)
	if val.Kind() == reflect.Slice {
		result := make([]interface{}, val.Len())
		for i := 0; i < val.Len(); i++ {
			result[i] = val.Index(i).Interface()
		}
		return result
	}

	return []interface{}{v}
}

func MapToSlice(m map[string]interface{}) []interface{} {
	result := make([]interface{}, 0, len(m))
	for _, v := range m {
		result = append(result, v)
	}
	return result
}

func SliceToMap(slice []interface{}, keyField string) map[string]interface{} {
	result := make(map[string]interface{})
	for _, item := range slice {
		if key, ok := getField(item, keyField); ok {
			if keyStr, ok := key.(string); ok {
				result[keyStr] = item
			}
		}
	}
	return result
}

func getField(obj interface{}, field string) (interface{}, bool) {
	val := reflect.ValueOf(obj)
	if val.Kind() == reflect.Ptr {
		val = val.Elem()
	}

	if val.Kind() == reflect.Map {
		mapVal := val.MapIndex(reflect.ValueOf(field))
		if mapVal.IsValid() {
			return mapVal.Interface(), true
		}
	}

	if val.Kind() == reflect.Struct {
		fieldVal := val.FieldByName(field)
		if fieldVal.IsValid() && fieldVal.CanInterface() {
			return fieldVal.Interface(), true
		}
	}

	return nil, false
}
