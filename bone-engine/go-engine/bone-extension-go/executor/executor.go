package executor

import (
	"errors"
	"fmt"
	"sync"

	"github.com/bone-engine/bone-extension-go/extension"
	"github.com/bone-engine/bone-extension-go/registry"
)

type ExecutionError struct {
	ExtensionID string
	Err         error
}

func (e *ExecutionError) Error() string {
	return fmt.Sprintf("extension %s: %v", e.ExtensionID, e.Err)
}

func (e *ExecutionError) Unwrap() error {
	return e.Err
}

type Executor struct {
	registry      *registry.Registry
	lifecycleHooks []extension.LifecycleHook
	mu             sync.RWMutex
}

func New(reg *registry.Registry) *Executor {
	return &Executor{
		registry:      reg,
		lifecycleHooks: make([]extension.LifecycleHook, 0),
	}
}

func (e *Executor) AddLifecycleHook(hook extension.LifecycleHook) {
	e.mu.Lock()
	defer e.mu.Unlock()
	e.lifecycleHooks = append(e.lifecycleHooks, hook)
}

func (e *Executor) executeLifecycleHooks(ctx *extension.Context, ext *extension.Extension, stage string) error {
	e.mu.RLock()
	defer e.mu.RUnlock()
	for _, hook := range e.lifecycleHooks {
		if err := hook(ctx, ext, stage); err != nil {
			return err
		}
	}
	return nil
}

func (e *Executor) Execute(point string, ctx *extension.Context) error {
	exts := e.registry.GetExtensions(point)
	if len(exts) == 0 {
		return nil
	}

	var errs []error
	for _, ext := range exts {
		if !ext.ShouldExecute(ctx) {
			continue
		}

		if err := e.executeLifecycleHooks(ctx, ext, extension.LifecycleBeforeExecute); err != nil {
			errs = append(errs, &ExecutionError{ExtensionID: ext.ID, Err: err})
			continue
		}

		if err := ext.Handler(ctx); err != nil {
			execErr := &ExecutionError{ExtensionID: ext.ID, Err: err}
			ctx.Error = execErr
			if hookErr := e.executeLifecycleHooks(ctx, ext, extension.LifecycleOnError); hookErr != nil {
				errs = append(errs, hookErr)
			}
			errs = append(errs, execErr)
			continue
		}

		if err := e.executeLifecycleHooks(ctx, ext, extension.LifecycleAfterExecute); err != nil {
			errs = append(errs, &ExecutionError{ExtensionID: ext.ID, Err: err})
		}
	}

	if len(errs) > 0 {
		return errors.Join(errs...)
	}
	return nil
}

func (e *Executor) ExecuteChain(point string, ctx *extension.Context) error {
	exts := e.registry.GetExtensions(point)
	if len(exts) == 0 {
		return nil
	}

	for _, ext := range exts {
		if !ext.ShouldExecute(ctx) {
			continue
		}

		if err := e.executeLifecycleHooks(ctx, ext, extension.LifecycleBeforeExecute); err != nil {
			return &ExecutionError{ExtensionID: ext.ID, Err: err}
		}

		if err := ext.Handler(ctx); err != nil {
			execErr := &ExecutionError{ExtensionID: ext.ID, Err: err}
			ctx.Error = execErr
			if hookErr := e.executeLifecycleHooks(ctx, ext, extension.LifecycleOnError); hookErr != nil {
				return hookErr
			}
			return execErr
		}

		if err := e.executeLifecycleHooks(ctx, ext, extension.LifecycleAfterExecute); err != nil {
			return &ExecutionError{ExtensionID: ext.ID, Err: err}
		}
	}
	return nil
}

func (e *Executor) ExecuteParallel(point string, ctx *extension.Context) error {
	exts := e.registry.GetExtensions(point)
	if len(exts) == 0 {
		return nil
	}

	var wg sync.WaitGroup
	errChan := make(chan error, len(exts))

	for _, ext := range exts {
		if !ext.ShouldExecute(ctx) {
			continue
		}

		wg.Add(1)
		go func(ext *extension.Extension) {
			defer wg.Done()

			if err := e.executeLifecycleHooks(ctx, ext, extension.LifecycleBeforeExecute); err != nil {
				errChan <- &ExecutionError{ExtensionID: ext.ID, Err: err}
				return
			}

			if err := ext.Handler(ctx); err != nil {
				execErr := &ExecutionError{ExtensionID: ext.ID, Err: err}
				if hookErr := e.executeLifecycleHooks(ctx, ext, extension.LifecycleOnError); hookErr != nil {
					errChan <- hookErr
				}
				errChan <- execErr
				return
			}

			if err := e.executeLifecycleHooks(ctx, ext, extension.LifecycleAfterExecute); err != nil {
				errChan <- &ExecutionError{ExtensionID: ext.ID, Err: err}
			}
		}(ext)
	}

	wg.Wait()
	close(errChan)

	var errs []error
	for err := range errChan {
		errs = append(errs, err)
	}

	if len(errs) > 0 {
		return errors.Join(errs...)
	}
	return nil
}

func (e *Executor) ExecuteFirst(point string, ctx *extension.Context) error {
	exts := e.registry.GetExtensions(point)
	if len(exts) == 0 {
		return nil
	}

	for _, ext := range exts {
		if !ext.ShouldExecute(ctx) {
			continue
		}

		if err := e.executeLifecycleHooks(ctx, ext, extension.LifecycleBeforeExecute); err != nil {
			continue
		}

		if err := ext.Handler(ctx); err != nil {
			ctx.Error = &ExecutionError{ExtensionID: ext.ID, Err: err}
			e.executeLifecycleHooks(ctx, ext, extension.LifecycleOnError)
			continue
		}

		e.executeLifecycleHooks(ctx, ext, extension.LifecycleAfterExecute)
		return nil
	}

	return nil
}

func (e *Executor) ExecuteUntilSuccess(point string, ctx *extension.Context) error {
	exts := e.registry.GetExtensions(point)
	if len(exts) == 0 {
		return nil
	}

	for _, ext := range exts {
		if !ext.ShouldExecute(ctx) {
			continue
		}

		if err := e.executeLifecycleHooks(ctx, ext, extension.LifecycleBeforeExecute); err != nil {
			continue
		}

		if err := ext.Handler(ctx); err != nil {
			ctx.Error = &ExecutionError{ExtensionID: ext.ID, Err: err}
			e.executeLifecycleHooks(ctx, ext, extension.LifecycleOnError)
			continue
		}

		e.executeLifecycleHooks(ctx, ext, extension.LifecycleAfterExecute)
		return nil
	}

	return fmt.Errorf("no extension executed successfully")
}

type BatchResult struct {
	Success []string
	Errors  map[string]error
}

func (e *Executor) ExecuteBatch(points []string, ctx *extension.Context) *BatchResult {
	result := &BatchResult{
		Success: make([]string, 0),
		Errors:  make(map[string]error),
	}

	for _, point := range points {
		if err := e.Execute(point, ctx); err != nil {
			result.Errors[point] = err
		} else {
			result.Success = append(result.Success, point)
		}
	}

	return result
}
