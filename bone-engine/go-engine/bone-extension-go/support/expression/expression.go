package expression

import (
	"fmt"
	"regexp"
	"strings"
)

type Evaluator interface {
	Evaluate(expr string, context map[string]interface{}) (interface{}, error)
}

type SimpleEvaluator struct {
}

func NewSimpleEvaluator() *SimpleEvaluator {
	return &SimpleEvaluator{}
}

func (e *SimpleEvaluator) Evaluate(expr string, context map[string]interface{}) (interface{}, error) {
	expr = strings.TrimSpace(expr)

	if strings.HasPrefix(expr, "${") && strings.HasSuffix(expr, "}") {
		key := expr[2 : len(expr)-1]
		if val, ok := context[key]; ok {
			return val, nil
		}
		return nil, fmt.Errorf("variable %s not found", key)
	}

	return expr, nil
}

type Template struct {
	raw  string
	expr *regexp.Regexp
}

func NewTemplate(template string) *Template {
	return &Template{
		raw:  template,
		expr: regexp.MustCompile(`\$\{([^}]+)\}`),
	}
}

func (t *Template) Render(context map[string]interface{}) string {
	result := t.expr.ReplaceAllStringFunc(t.raw, func(m string) string {
		key := m[2 : len(m)-1]
		if val, ok := context[key]; ok {
			return fmt.Sprintf("%v", val)
		}
		return m
	})
	return result
}
