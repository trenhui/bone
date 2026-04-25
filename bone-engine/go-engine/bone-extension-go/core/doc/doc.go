package doc

type Documentation struct {
	Title       string
	Description string
	Version     string
	Points      []*PointDoc
	Extensions  []*ExtensionDoc
}

type PointDoc struct {
	Name        string
	Description string
	Extensions  []string
}

type ExtensionDoc struct {
	Name        string
	Point       string
	Description string
	Priority    int
}

type DocGenerator interface {
	Generate() (*Documentation, error)
}

type DefaultDocGenerator struct {
}

func NewDefaultDocGenerator() *DefaultDocGenerator {
	return &DefaultDocGenerator{}
}

func (g *DefaultDocGenerator) Generate() (*Documentation, error) {
	return &Documentation{}, nil
}
