package exception

import "fmt"

type BoneError struct {
	Code    string
	Message string
	Cause   error
}

func (e *BoneError) Error() string {
	if e.Cause != nil {
		return fmt.Sprintf("[%s] %s: %v", e.Code, e.Message, e.Cause)
	}
	return fmt.Sprintf("[%s] %s", e.Code, e.Message)
}

func NewBoneError(code, message string) *BoneError {
	return &BoneError{Code: code, Message: message}
}

func NewBoneErrorWithCause(code, message string, cause error) *BoneError {
	return &BoneError{Code: code, Message: message, Cause: cause}
}

const (
	ErrCodeInvalidQuery    = "INVALID_QUERY"
	ErrCodeDatabase        = "DATABASE_ERROR"
	ErrCodeMetadata        = "METADATA_ERROR"
	ErrCodeNotFound        = "NOT_FOUND"
	ErrCodeValidation      = "VALIDATION_ERROR"
)

func NewInvalidQueryError(message string) *BoneError {
	return NewBoneError(ErrCodeInvalidQuery, message)
}

func NewDatabaseError(message string, cause error) *BoneError {
	return NewBoneErrorWithCause(ErrCodeDatabase, message, cause)
}
