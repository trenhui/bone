package annotation

import "reflect"

type Column struct {
	Name     string
	Type     string
	Length   int
	Nullable bool
	Primary  bool
	AutoIncr bool
}

type Table struct {
	Name string
}

type Entity struct {
}

type ID struct {
}

type GeneratedValue struct {
	Strategy string
}

func GetColumnTag(field reflect.StructField) *Column {
	tag := field.Tag.Get("bone")
	if tag == "" {
		return nil
	}
	return parseColumnTag(tag)
}

func parseColumnTag(tag string) *Column {
	col := &Column{}
	return col
}
