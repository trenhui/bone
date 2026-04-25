package extractor

import (
	"reflect"
	"strings"
)

type Extractor interface {
	Extract(obj interface{}, field string) (interface{}, error)
}

type ReflectExtractor struct {
}

func NewReflectExtractor() *ReflectExtractor {
	return &ReflectExtractor{}
}

func (e *ReflectExtractor) Extract(obj interface{}, field string) (interface{}, error) {
	if obj == nil {
		return nil, nil
	}

	val := reflect.ValueOf(obj)
	if val.Kind() == reflect.Ptr {
		val = val.Elem()
	}

	if val.Kind() == reflect.Map {
		keyVal := reflect.ValueOf(field)
		mapVal := val.MapIndex(keyVal)
		if mapVal.IsValid() {
			return mapVal.Interface(), nil
		}
		return nil, nil
	}

	if val.Kind() == reflect.Struct {
		fieldVal := val.FieldByName(field)
		if fieldVal.IsValid() && fieldVal.CanInterface() {
			return fieldVal.Interface(), nil
		}
	}

	return nil, nil
}

type NestedExtractor struct {
	extractor Extractor
}

func NewNestedExtractor(extractor Extractor) *NestedExtractor {
	if extractor == nil {
		extractor = NewReflectExtractor()
	}
	return &NestedExtractor{extractor: extractor}
}

func (e *NestedExtractor) Extract(obj interface{}, path string) (interface{}, error) {
	parts := strings.Split(path, ".")
	current := obj

	for _, part := range parts {
		var err error
		current, err = e.extractor.Extract(current, part)
		if err != nil {
			return nil, err
		}
		if current == nil {
			return nil, nil
		}
	}

	return current, nil
}
