package router

import (
	"regexp"
	"sort"

	"github.com/bone-engine/bone-extension-go/api/model"
	"github.com/bone-engine/bone-extension-go/api/spi"
)

type Router struct {
	rules    []Rule
	registry spi.ExtensionRegistry
}

type Rule struct {
	Pattern *regexp.Regexp
	Filter  func(ctx *model.Context, ext spi.Extension) bool
}

func NewRouter(registry spi.ExtensionRegistry) *Router {
	return &Router{
		rules:    make([]Rule, 0),
		registry: registry,
	}
}

func (r *Router) AddRule(pattern string, filter func(ctx *model.Context, ext spi.Extension) bool) error {
	re, err := regexp.Compile(pattern)
	if err != nil {
		return err
	}
	r.rules = append(r.rules, Rule{
		Pattern: re,
		Filter:  filter,
	})
	return nil
}

func (r *Router) Route(ctx *model.Context, point string) []spi.Extension {
	extensions := r.registry.GetByPoint(point)

	filtered := make([]spi.Extension, 0, len(extensions))
	for _, ext := range extensions {
		included := true
		for _, rule := range r.rules {
			if rule.Pattern.MatchString(ext.Name()) {
				if rule.Filter != nil {
					included = rule.Filter(ctx, ext)
				}
			}
		}
		if included {
			filtered = append(filtered, ext)
		}
	}

	sort.Slice(filtered, func(i, j int) bool {
		priorityI := 0
		if priorityProvider, ok := filtered[i].(interface{ Priority() int }); ok {
			priorityI = priorityProvider.Priority()
		}
		priorityJ := 0
		if priorityProvider, ok := filtered[j].(interface{ Priority() int }); ok {
			priorityJ = priorityProvider.Priority()
		}
		return priorityI < priorityJ
	})

	return filtered
}

type DefaultSelector struct {
	rules []func(ctx *model.Context, ext spi.Extension) bool
}

func NewDefaultSelector() *DefaultSelector {
	return &DefaultSelector{
		rules: make([]func(ctx *model.Context, ext spi.Extension) bool, 0),
	}
}

func (s *DefaultSelector) AddRule(rule func(ctx *model.Context, ext spi.Extension) bool) {
	s.rules = append(s.rules, rule)
}

func (s *DefaultSelector) Select(ctx *model.Context, extensions []spi.Extension) []spi.Extension {
	selected := make([]spi.Extension, 0, len(extensions))
	for _, ext := range extensions {
		matches := true
		for _, rule := range s.rules {
			if !rule(ctx, ext) {
				matches = false
				break
			}
		}
		if matches {
			selected = append(selected, ext)
		}
	}
	return selected
}
