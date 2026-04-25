package exception

import "fmt"

type ExtensionError struct {
	Code    string
	Message string
	Cause   error
}

func (e *ExtensionError) Error() string {
	if e.Cause != nil {
		return fmt.Sprintf("[%s] %s: %v", e.Code, e.Message, e.Cause)
	}
	return fmt.Sprintf("[%s] %s", e.Code, e.Message)
}

func NewExtensionError(code, message string) *ExtensionError {
	return &ExtensionError{Code: code, Message: message}
}

func NewExtensionErrorWithCause(code, message string, cause error) *ExtensionError {
	return &ExtensionError{Code: code, Message: message, Cause: cause}
}

const (
	ErrCodeExtensionNotFound  = "EXTENSION_NOT_FOUND"
	ErrCodePointNotFound      = "POINT_NOT_FOUND"
	ErrCodeExecutionFailed    = "EXECUTION_FAILED"
	ErrCodeRegistrationFailed = "REGISTRATION_FAILED"
	ErrCodeInvalidExtension   = "INVALID_EXTENSION"
)

func NewExtensionNotFoundError(name string) *ExtensionError {
	return NewExtensionError(ErrCodeExtensionNotFound, fmt.Sprintf("extension %s not found", name))
}

func NewPointNotFoundError(name string) *ExtensionError {
	return NewExtensionError(ErrCodePointNotFound, fmt.Sprintf("extension point %s not found", name))
}

func NewExecutionFailedError(name string, cause error) *ExtensionError {
	return NewExtensionErrorWithCause(ErrCodeExecutionFailed, fmt.Sprintf("execute extension %s failed", name), cause)
}

func NewRegistrationFailedError(name string, cause error) *ExtensionError {
	return NewExtensionErrorWithCause(ErrCodeRegistrationFailed, fmt.Sprintf("register extension %s failed", name), cause)
}
