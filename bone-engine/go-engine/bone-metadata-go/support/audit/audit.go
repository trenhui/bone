package audit

import (
	"context"
	"time"
)

type AuditLog struct {
	ID        int64
	Operation string
	Table     string
	RecordID  interface{}
	Before    interface{}
	After     interface{}
	CreatedBy string
	CreatedAt time.Time
}

type Auditor interface {
	Log(ctx context.Context, log *AuditLog) error
}

type DefaultAuditor struct {
	logs []*AuditLog
}

func NewDefaultAuditor() *DefaultAuditor {
	return &DefaultAuditor{
		logs: make([]*AuditLog, 0),
	}
}

func (a *DefaultAuditor) Log(ctx context.Context, log *AuditLog) error {
	log.CreatedAt = time.Now()
	a.logs = append(a.logs, log)
	return nil
}

func (a *DefaultAuditor) GetLogs() []*AuditLog {
	return a.logs
}

func (a *DefaultAuditor) Clear() {
	a.logs = make([]*AuditLog, 0)
}

type Auditable interface {
	SetCreatedAt(t time.Time)
	SetUpdatedAt(t time.Time)
	SetCreatedBy(user string)
	SetUpdatedBy(user string)
}

type DefaultAuditable struct {
	CreatedAt time.Time
	UpdatedAt time.Time
	CreatedBy string
	UpdatedBy string
}

func (a *DefaultAuditable) SetCreatedAt(t time.Time) {
	a.CreatedAt = t
}

func (a *DefaultAuditable) SetUpdatedAt(t time.Time) {
	a.UpdatedAt = t
}

func (a *DefaultAuditable) SetCreatedBy(user string) {
	a.CreatedBy = user
}

func (a *DefaultAuditable) SetUpdatedBy(user string) {
	a.UpdatedBy = user
}
