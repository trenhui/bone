package registry

import (
	"errors"
	"sort"
	"sync"

	"github.com/bone-engine/bone-extension-go/extension"
)

var (
	ErrExtensionExists = errors.New("extension already exists")
	ErrExtensionNotFound = errors.New("extension not found")
)

type Registry struct {
	mu         sync.RWMutex
	extensions map[string][]*extension.Extension
	points     map[string]*extension.ExtensionPoint
}

func NewRegistry() *Registry {
	return &Registry{
		extensions: make(map[string][]*extension.Extension),
		points:     make(map[string]*extension.ExtensionPoint),
	}
}

func (r *Registry) Register(ext *extension.Extension) error {
	r.mu.Lock()
	defer r.mu.Unlock()

	for _, e := range r.extensions[ext.Point] {
		if e.ID == ext.ID {
			return ErrExtensionExists
		}
	}

	exts := r.extensions[ext.Point]
	exts = append(exts, ext)
	sort.Slice(exts, func(i, j int) bool {
		return exts[i].Priority > exts[j].Priority
	})
	r.extensions[ext.Point] = exts

	if _, exists := r.points[ext.Point]; !exists {
		r.points[ext.Point] = &extension.ExtensionPoint{
			Name:        ext.Point,
			Description: "",
			Extensions:  make([]*extension.Extension, 0),
		}
	}

	return nil
}

func (r *Registry) RegisterPoint(point *extension.ExtensionPoint) {
	r.mu.Lock()
	defer r.mu.Unlock()
	r.points[point.Name] = point
}

func (r *Registry) Unregister(point, id string) error {
	r.mu.Lock()
	defer r.mu.Unlock()

	exts := r.extensions[point]
	found := false
	filtered := make([]*extension.Extension, 0, len(exts))
	for _, ext := range exts {
		if ext.ID == id {
			found = true
			continue
		}
		filtered = append(filtered, ext)
	}

	if !found {
		return ErrExtensionNotFound
	}

	if len(filtered) == 0 {
		delete(r.extensions, point)
	} else {
		r.extensions[point] = filtered
	}

	return nil
}

func (r *Registry) GetExtensions(point string) []*extension.Extension {
	r.mu.RLock()
	defer r.mu.RUnlock()
	return r.extensions[point]
}

func (r *Registry) GetExtension(point, id string) (*extension.Extension, bool) {
	r.mu.RLock()
	defer r.mu.RUnlock()

	for _, ext := range r.extensions[point] {
		if ext.ID == id {
			return ext, true
		}
	}
	return nil, false
}

func (r *Registry) ListPoints() []string {
	r.mu.RLock()
	defer r.mu.RUnlock()
	points := make([]string, 0, len(r.points))
	for point := range r.points {
		points = append(points, point)
	}
	return points
}

func (r *Registry) GetPoint(name string) (*extension.ExtensionPoint, bool) {
	r.mu.RLock()
	defer r.mu.RUnlock()
	point, ok := r.points[name]
	return point, ok
}

func (r *Registry) ListAllExtensions() []*extension.Extension {
	r.mu.RLock()
	defer r.mu.RUnlock()

	var result []*extension.Extension
	for _, exts := range r.extensions {
		result = append(result, exts...)
	}
	return result
}

func (r *Registry) ListExtensionsByPoint(point string) []*extension.Extension {
	r.mu.RLock()
	defer r.mu.RUnlock()
	return r.extensions[point]
}

func (r *Registry) EnableExtension(point, id string) error {
	r.mu.Lock()
	defer r.mu.Unlock()

	for _, ext := range r.extensions[point] {
		if ext.ID == id {
			ext.Enabled = true
			return nil
		}
	}
	return ErrExtensionNotFound
}

func (r *Registry) DisableExtension(point, id string) error {
	r.mu.Lock()
	defer r.mu.Unlock()

	for _, ext := range r.extensions[point] {
		if ext.ID == id {
			ext.Enabled = false
			return nil
		}
	}
	return ErrExtensionNotFound
}

func (r *Registry) Clear() {
	r.mu.Lock()
	defer r.mu.Unlock()
	r.extensions = make(map[string][]*extension.Extension)
	r.points = make(map[string]*extension.ExtensionPoint)
}
