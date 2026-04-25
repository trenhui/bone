package router

import (
	"fmt"
	"regexp"
	"sync"

	"github.com/bone-engine/bone-extension-go/extension"
	"github.com/bone-engine/bone-extension-go/registry"
)

type Route struct {
	Path         string
	Point        string
	Method       string
	Pattern      *regexp.Regexp
	Params       []string
	Condition    extension.Condition
	Metadata     map[string]interface{}
	Priority     int
}

type Router struct {
	mu       sync.RWMutex
	routes   map[string][]*Route
	pathMap  map[string]*regexp.Regexp
	registry *registry.Registry
}

func New(reg *registry.Registry) *Router {
	return &Router{
		routes:   make(map[string][]*Route),
		pathMap:  make(map[string]*regexp.Regexp),
		registry: reg,
	}
}

func (r *Router) RegisterRoute(path, point, method string) *Route {
	return r.RegisterRouteWithOptions(path, point, method, nil)
}

func (r *Router) RegisterRouteWithOptions(path, point, method string, condition extension.Condition) *Route {
	r.mu.Lock()
	defer r.mu.Unlock()

	pattern, params := parsePathPattern(path)
	key := method + ":" + path

	route := &Route{
		Path:      path,
		Point:     point,
		Method:    method,
		Pattern:   pattern,
		Params:    params,
		Condition: condition,
		Metadata:  make(map[string]interface{}),
		Priority:  0,
	}

	r.routes[method] = append(r.routes[method], route)
	r.pathMap[key] = pattern

	return route
}

func (r *Router) UnregisterRoute(path, method string) {
	r.mu.Lock()
	defer r.mu.Unlock()

	key := method + ":" + path
	delete(r.pathMap, key)

	if routes, ok := r.routes[method]; ok {
		filtered := make([]*Route, 0, len(routes))
		for _, route := range routes {
			if route.Path != path {
				filtered = append(filtered, route)
			}
		}
		r.routes[method] = filtered
	}
}

func (r *Router) Match(path, method string) (*Route, map[string]string, bool) {
	r.mu.RLock()
	defer r.mu.RUnlock()

	routes, ok := r.routes[method]
	if !ok {
		return nil, nil, false
	}

	for _, route := range routes {
		if params, matched := matchPath(route, path); matched {
			return route, params, true
		}
	}

	return nil, nil, false
}

func (r *Router) MatchAll(path, method string) ([]*Route, []map[string]string) {
	r.mu.RLock()
	defer r.mu.RUnlock()

	var matchedRoutes []*Route
	var matchedParams []map[string]string

	routes, ok := r.routes[method]
	if !ok {
		return nil, nil
	}

	for _, route := range routes {
		if params, matched := matchPath(route, path); matched {
			matchedRoutes = append(matchedRoutes, route)
			matchedParams = append(matchedParams, params)
		}
	}

	return matchedRoutes, matchedParams
}

func (r *Router) ListRoutes() []*Route {
	r.mu.RLock()
	defer r.mu.RUnlock()

	var result []*Route
	for _, routes := range r.routes {
		result = append(result, routes...)
	}
	return result
}

func (r *Router) ListRoutesByMethod(method string) []*Route {
	r.mu.RLock()
	defer r.mu.RUnlock()
	return r.routes[method]
}

func (r *Router) Clear() {
	r.mu.Lock()
	defer r.mu.Unlock()
	r.routes = make(map[string][]*Route)
	r.pathMap = make(map[string]*regexp.Regexp)
}

func parsePathPattern(path string) (*regexp.Regexp, []string) {
	var params []string
	pattern := regexp.MustCompile(`\{([^}]+)\}`)
	
	result := pattern.ReplaceAllStringFunc(path, func(m string) string {
		param := m[1 : len(m)-1]
		params = append(params, param)
		return `([^/]+)`
	})
	
	re, _ := regexp.Compile("^" + result + "$")
	return re, params
}

func matchPath(route *Route, path string) (map[string]string, bool) {
	if route.Pattern == nil {
		if route.Path == path {
			return make(map[string]string), true
		}
		return nil, false
	}

	matches := route.Pattern.FindStringSubmatch(path)
	if matches == nil {
		return nil, false
	}

	params := make(map[string]string)
	for i, param := range route.Params {
		if i+1 < len(matches) {
			params[param] = matches[i+1]
		}
	}

	return params, true
}

func (r *Router) ExecuteRoute(ctx *extension.Context, path, method string) error {
	route, params, ok := r.Match(path, method)
	if !ok {
		return fmt.Errorf("no route matched for %s %s", method, path)
	}

	for key, value := range params {
		ctx.Set("param:"+key, value)
	}

	if route.Condition != nil && !route.Condition(ctx) {
		return fmt.Errorf("route condition not satisfied")
	}

	return nil
}
