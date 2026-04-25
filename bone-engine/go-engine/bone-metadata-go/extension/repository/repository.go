package repository

type RepositoryExtension interface {
	BeforeSave(entity interface{}) error
	AfterSave(entity interface{}) error
	BeforeDelete(entity interface{}) error
	AfterDelete(entity interface{}) error
	BeforeFind(id interface{}) error
	AfterFind(entity interface{}) error
}

type BaseRepositoryExtension struct{}

func (e *BaseRepositoryExtension) BeforeSave(entity interface{}) error {
	return nil
}

func (e *BaseRepositoryExtension) AfterSave(entity interface{}) error {
	return nil
}

func (e *BaseRepositoryExtension) BeforeDelete(entity interface{}) error {
	return nil
}

func (e *BaseRepositoryExtension) AfterDelete(entity interface{}) error {
	return nil
}

func (e *BaseRepositoryExtension) BeforeFind(id interface{}) error {
	return nil
}

func (e *BaseRepositoryExtension) AfterFind(entity interface{}) error {
	return nil
}

type ExtensionRegistry struct {
	extensions []RepositoryExtension
}

func NewExtensionRegistry() *ExtensionRegistry {
	return &ExtensionRegistry{
		extensions: make([]RepositoryExtension, 0),
	}
}

func (r *ExtensionRegistry) Register(ext RepositoryExtension) {
	r.extensions = append(r.extensions, ext)
}

func (r *ExtensionRegistry) BeforeSave(entity interface{}) error {
	for _, ext := range r.extensions {
		if err := ext.BeforeSave(entity); err != nil {
			return err
		}
	}
	return nil
}

func (r *ExtensionRegistry) AfterSave(entity interface{}) error {
	for _, ext := range r.extensions {
		if err := ext.AfterSave(entity); err != nil {
			return err
		}
	}
	return nil
}
