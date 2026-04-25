package metrics

import (
	"sync"
	"time"
)

type Metrics struct {
	Counters   map[string]int64
	Timers     map[string]time.Duration
	Gauges     map[string]int64
	mu         sync.RWMutex
}

func NewMetrics() *Metrics {
	return &Metrics{
		Counters: make(map[string]int64),
		Timers:   make(map[string]time.Duration),
		Gauges:   make(map[string]int64),
	}
}

func (m *Metrics) Inc(name string) {
	m.mu.Lock()
	defer m.mu.Unlock()
	m.Counters[name]++
}

func (m *Metrics) Add(name string, value int64) {
	m.mu.Lock()
	defer m.mu.Unlock()
	m.Counters[name] += value
}

func (m *Metrics) SetGauge(name string, value int64) {
	m.mu.Lock()
	defer m.mu.Unlock()
	m.Gauges[name] = value
}

func (m *Metrics) RecordTime(name string, duration time.Duration) {
	m.mu.Lock()
	defer m.mu.Unlock()
	m.Timers[name] += duration
}

func (m *Metrics) GetCounter(name string) int64 {
	m.mu.RLock()
	defer m.mu.RUnlock()
	return m.Counters[name]
}

func (m *Metrics) GetGauge(name string) int64 {
	m.mu.RLock()
	defer m.mu.RUnlock()
	return m.Gauges[name]
}

func (m *Metrics) GetTimer(name string) time.Duration {
	m.mu.RLock()
	defer m.mu.RUnlock()
	return m.Timers[name]
}

func (m *Metrics) Reset() {
	m.mu.Lock()
	defer m.mu.Unlock()
	m.Counters = make(map[string]int64)
	m.Timers = make(map[string]time.Duration)
	m.Gauges = make(map[string]int64)
}

type MetricsCollector interface {
	Collect() *Metrics
}

type DefaultMetricsCollector struct {
	metrics *Metrics
}

func NewDefaultMetricsCollector() *DefaultMetricsCollector {
	return &DefaultMetricsCollector{
		metrics: NewMetrics(),
	}
}

func (c *DefaultMetricsCollector) Collect() *Metrics {
	return c.metrics
}

func (c *DefaultMetricsCollector) Inc(name string) {
	c.metrics.Inc(name)
}

func (c *DefaultMetricsCollector) RecordTime(name string, duration time.Duration) {
	c.metrics.RecordTime(name, duration)
}

type Timer struct {
	name    string
	start   time.Time
	metrics *Metrics
}

func NewTimer(name string, metrics *Metrics) *Timer {
	return &Timer{
		name:    name,
		start:   time.Now(),
		metrics: metrics,
	}
}

func (t *Timer) Stop() {
	t.metrics.RecordTime(t.name, time.Since(t.start))
}
