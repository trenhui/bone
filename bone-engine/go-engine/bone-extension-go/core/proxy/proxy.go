package proxy

import (
	"reflect"
	"time"

	"github.com/bone-engine/bone-extension-go/api/model"
	"github.com/bone-engine/bone-extension-go/api/spi"
)

type Proxy interface {
	Target() interface{}
	Invoke(ctx *model.Context, method string, args ...interface{}) (interface{}, error)
}

type ExtensionProxy struct {
	target       spi.Extension
	interceptors []spi.Interceptor
}

func NewExtensionProxy(target spi.Extension) *ExtensionProxy {
	return &ExtensionProxy{
		target:       target,
		interceptors: make([]spi.Interceptor, 0),
	}
}

func (p *ExtensionProxy) Target() interface{} {
	return p.target
}

func (p *ExtensionProxy) AddInterceptor(interceptor spi.Interceptor) {
	p.interceptors = append(p.interceptors, interceptor)
}

func (p *ExtensionProxy) Invoke(ctx *model.Context, method string, args ...interface{}) (interface{}, error) {
	for _, interceptor := range p.interceptors {
		if err := interceptor.Before(ctx); err != nil {
			return nil, err
		}
	}

	var result interface{}
	var err error

	targetValue := reflect.ValueOf(p.target)
	targetMethod := targetValue.MethodByName(method)
	if targetMethod.IsValid() {
		in := make([]reflect.Value, len(args)+1)
		in[0] = reflect.ValueOf(ctx)
		for i, arg := range args {
			in[i+1] = reflect.ValueOf(arg)
		}
		out := targetMethod.Call(in)
		if len(out) > 0 {
			if len(out) > 1 {
				err, _ = out[1].Interface().(error)
			}
			result = out[0].Interface()
		}
	}

	for i := len(p.interceptors) - 1; i >= 0; i-- {
		res := &model.Result{Success: err == nil, Data: result}
		if err := p.interceptors[i].After(ctx, res, err); err != nil {
			return result, err
		}
	}

	return result, err
}

type CachingProxy struct {
	target spi.Extension
	cache  Cache
}

type Cache interface {
	Get(key string) (interface{}, bool)
	Set(key string, value interface{}, ttl time.Duration)
}

func NewCachingProxy(target spi.Extension, cache Cache) *CachingProxy {
	return &CachingProxy{
		target: target,
		cache:  cache,
	}
}

func (p *CachingProxy) Target() interface{} {
	return p.target
}

func (p *CachingProxy) Invoke(ctx *model.Context, method string, args ...interface{}) (interface{}, error) {
	cacheKey := method
	for _, arg := range args {
		cacheKey += ":" + string(arg)
	}

	if result, ok := p.cache.Get(cacheKey); ok {
		return result, nil
	}

	proxy := NewExtensionProxy(p.target)
	result, err := proxy.Invoke(ctx, method, args...)
	if err == nil {
		p.cache.Set(cacheKey, result, 5*time.Minute)
	}
	return result, err
}
