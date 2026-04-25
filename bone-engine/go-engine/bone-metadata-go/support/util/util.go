package util

import (
	"fmt"
	"reflect"
	"strconv"
	"strings"
	"time"
)

func IsZero(v interface{}) bool {
	if v == nil {
		return true
	}
	value := reflect.ValueOf(v)
	return value.IsZero()
}

func ToString(v interface{}) string {
	if v == nil {
		return ""
	}
	switch val := v.(type) {
	case string:
		return val
	case []byte:
		return string(val)
	case int, int8, int16, int32, int64, uint, uint8, uint16, uint32, uint64:
		return fmt.Sprintf("%d", val)
	case float32, float64:
		return fmt.Sprintf("%f", val)
	case bool:
		return strconv.FormatBool(val)
	case time.Time:
		return val.Format(time.RFC3339)
	default:
		return fmt.Sprintf("%v", val)
	}
}

func ToInt(v interface{}) int {
	if v == nil {
		return 0
	}
	switch val := v.(type) {
	case int:
		return val
	case int8:
		return int(val)
	case int16:
		return int(val)
	case int32:
		return int(val)
	case int64:
		return int(val)
	case uint:
		return int(val)
	case uint8:
		return int(val)
	case uint16:
		return int(val)
	case uint32:
		return int(val)
	case uint64:
		return int(val)
	case float32:
		return int(val)
	case float64:
		return int(val)
	case string:
		if i, err := strconv.Atoi(val); err == nil {
			return i
		}
	}
	return 0
}

func ToInt64(v interface{}) int64 {
	if v == nil {
		return 0
	}
	switch val := v.(type) {
	case int64:
		return val
	case int:
		return int64(val)
	case int8:
		return int64(val)
	case int16:
		return int64(val)
	case int32:
		return int64(val)
	case uint:
		return int64(val)
	case uint8:
		return int64(val)
	case uint16:
		return int64(val)
	case uint32:
		return int64(val)
	case uint64:
		return int64(val)
	case float32:
		return int64(val)
	case float64:
		return int64(val)
	case string:
		if i, err := strconv.ParseInt(val, 10, 64); err == nil {
			return i
		}
	}
	return 0
}

func ToBool(v interface{}) bool {
	if v == nil {
		return false
	}
	switch val := v.(type) {
	case bool:
		return val
	case string:
		b, _ := strconv.ParseBool(val)
		return b
	case int, int8, int16, int32, int64, uint, uint8, uint16, uint32, uint64:
		return val != 0
	}
	return false
}

func ToFloat64(v interface{}) float64 {
	if v == nil {
		return 0
	}
	switch val := v.(type) {
	case float64:
		return val
	case float32:
		return float64(val)
	case int, int8, int16, int32, int64, uint, uint8, uint16, uint32, uint64:
		return float64(val.(int))
	case string:
		if f, err := strconv.ParseFloat(val, 64); err == nil {
			return f
		}
	}
	return 0
}

func ToDuration(v interface{}) time.Duration {
	if v == nil {
		return 0
	}
	switch val := v.(type) {
	case time.Duration:
		return val
	case int64:
		return time.Duration(val)
	case int:
		return time.Duration(val)
	case string:
		if d, err := time.ParseDuration(val); err == nil {
			return d
		}
	}
	return 0
}

func ToTime(v interface{}) time.Time {
	if v == nil {
		return time.Time{}
	}
	switch val := v.(type) {
	case time.Time:
		return val
	case string:
		for _, layout := range []string{
			time.RFC3339,
			"2006-01-02 15:04:05",
			"2006-01-02",
		} {
			if t, err := time.Parse(layout, val); err == nil {
				return t
			}
		}
	}
	return time.Time{}
}

func StructToMap(v interface{}) map[string]interface{} {
	result := make(map[string]interface{})
	if v == nil {
		return result
	}
	val := reflect.ValueOf(v)
	if val.Kind() == reflect.Ptr {
		val = val.Elem()
	}
	if val.Kind() != reflect.Struct {
		return result
	}
	typ := val.Type()
	for i := 0; i < val.NumField(); i++ {
		field := typ.Field(i)
		if !field.IsExported() {
			continue
		}
		name := field.Name
		if tag := field.Tag.Get("json"); tag != "" {
			if tag == "-" {
				continue
			}
			if idx := strings.Index(tag, ","); idx != -1 {
				name = tag[:idx]
			} else {
				name = tag
			}
		}
		result[name] = val.Field(i).Interface()
	}
	return result
}

func MapToStruct(m map[string]interface{}, v interface{}) error {
	if m == nil || v == nil {
		return nil
	}
	val := reflect.ValueOf(v)
	if val.Kind() != reflect.Ptr {
		return fmt.Errorf("target must be a pointer")
	}
	val = val.Elem()
	if val.Kind() != reflect.Struct {
		return fmt.Errorf("target must be a struct")
	}
	typ := val.Type()
	for i := 0; i < val.NumField(); i++ {
		field := typ.Field(i)
		if !field.IsExported() {
			continue
		}
		name := field.Name
		if tag := field.Tag.Get("json"); tag != "" {
			if tag == "-" {
				continue
			}
			if idx := strings.Index(tag, ","); idx != -1 {
				name = tag[:idx]
			} else {
				name = tag
			}
		}
		if v, ok := m[name]; ok {
			fieldVal := val.Field(i)
			if fieldVal.CanSet() {
				fieldVal.Set(reflect.ValueOf(v))
			}
		}
	}
	return nil
}

func CopyProperties(dst, src interface{}) error {
	if dst == nil || src == nil {
		return nil
	}
	dstVal := reflect.ValueOf(dst)
	if dstVal.Kind() != reflect.Ptr {
		return fmt.Errorf("destination must be a pointer")
	}
	dstVal = dstVal.Elem()
	if dstVal.Kind() != reflect.Struct {
		return fmt.Errorf("destination must be a struct")
	}
	srcVal := reflect.ValueOf(src)
	if srcVal.Kind() == reflect.Ptr {
		srcVal = srcVal.Elem()
	}
	if srcVal.Kind() != reflect.Struct {
		return fmt.Errorf("source must be a struct")
	}
	srcTyp := srcVal.Type()
	dstTyp := dstVal.Type()
	for i := 0; i < srcVal.NumField(); i++ {
		srcField := srcTyp.Field(i)
		if !srcField.IsExported() {
			continue
		}
		if dstField, ok := dstTyp.FieldByName(srcField.Name); ok && dstField.Type == srcField.Type && dstField.IsExported() {
			if dstVal.FieldByName(srcField.Name).CanSet() {
				dstVal.FieldByName(srcField.Name).Set(srcVal.Field(i))
			}
		}
	}
	return nil
}

func NewInstance(t reflect.Type) interface{} {
	if t.Kind() == reflect.Ptr {
		return reflect.New(t.Elem()).Interface()
	}
	return reflect.New(t).Interface()
}

func Indirect(v reflect.Value) reflect.Value {
	for v.Kind() == reflect.Ptr {
		v = v.Elem()
	}
	return v
}

func IsSlice(v interface{}) bool {
	if v == nil {
		return false
	}
	return reflect.TypeOf(v).Kind() == reflect.Slice
}

func IsMap(v interface{}) bool {
	if v == nil {
		return false
	}
	return reflect.TypeOf(v).Kind() == reflect.Map
}

func IsStruct(v interface{}) bool {
	if v == nil {
		return false
	}
	t := reflect.TypeOf(v)
	if t.Kind() == reflect.Ptr {
		t = t.Elem()
	}
	return t.Kind() == reflect.Struct
}

func SliceContains(slice interface{}, item interface{}) bool {
	if slice == nil {
		return false
	}
	val := reflect.ValueOf(slice)
	if val.Kind() != reflect.Slice {
		return false
	}
	for i := 0; i < val.Len(); i++ {
		if reflect.DeepEqual(val.Index(i).Interface(), item) {
			return true
		}
	}
	return false
}

func MapKeys(m interface{}) []interface{} {
	if m == nil {
		return nil
	}
	val := reflect.ValueOf(m)
	if val.Kind() != reflect.Map {
		return nil
	}
	keys := val.MapKeys()
	result := make([]interface{}, len(keys))
	for i, key := range keys {
		result[i] = key.Interface()
	}
	return result
}

func MapValues(m interface{}) []interface{} {
	if m == nil {
		return nil
	}
	val := reflect.ValueOf(m)
	if val.Kind() != reflect.Map {
		return nil
	}
	result := make([]interface{}, 0, val.Len())
	for _, key := range val.MapKeys() {
		result = append(result, val.MapIndex(key).Interface())
	}
	return result
}
